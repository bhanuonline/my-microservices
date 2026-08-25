package com.angle.trading.analyst;

import com.angle.trading.analyst.model.AnalystReport;
import com.angle.trading.analyst.model.AnalystReport.Consensus;
import com.angle.trading.analyst.model.AnalystReport.Recommendation;
import com.angle.trading.analyst.model.AnalystReport.Signal;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.marketdata.NiftyFileLoader;
import com.angle.trading.marketstructure.MarketContextBuilder;
import com.angle.trading.marketstructure.model.LiquidityLevel;
import com.angle.trading.marketstructure.model.MarketContext;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.StrategyRegistry;
import com.angle.trading.strategy.model.IntentAction;
import com.angle.trading.strategy.model.TradeIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Runs every registered strategy against the same candle series, aggregates
 * their votes, and produces a single recommendation.
 *
 * Two entry points:
 *   analyseFromCsv()      — uses the bundled Nifty daily CSV (no broker needed)
 *   analyseLive(...)      — fetches live candles from a broker first
 *
 * Recommendation rules:
 *   - Majority action wins. Ties → HOLD.
 *   - Strength = agreement level (STRONG ≥4, MODERATE =3, WEAK =2, MINIMAL =1, NONE =0)
 *   - Entry = current close
 *   - Stop / target = first non-null level from agreeing strategies in this priority:
 *       sweep-fvg → ob-retest → macd-crossover → rsi-mean-reversion → moving-average-crossover
 *     If none provide levels, falls back to MarketContext (nearest OB / nearest liquidity).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalystService {

    /** Preferred strategies to pull stop/target from — SMC-first, indicator-last. */
    private static final List<String> LEVEL_PRIORITY = List.of(
            "sweep-fvg", "ob-retest",
            "macd-crossover", "rsi-mean-reversion", "moving-average-crossover"
    );

    private final StrategyRegistry     strategyRegistry;
    private final MarketContextBuilder marketContextBuilder;
    private final NiftyFileLoader      niftyFileLoader;
    private final MarketDataService    marketDataService;

    public AnalystReport analyseFromCsv() {
        List<Candle> candles = niftyFileLoader.load();
        return analyse(candles, "csv");
    }

    public AnalystReport analyseLive(String broker, Exchange exchange, String symbolToken,
                                     Interval interval, LocalDate from, LocalDate to) {
        List<Candle> candles = marketDataService.getCandles(broker, exchange, symbolToken, interval, from, to);
        String source = String.format("%s:%s@%s:%s", broker, symbolToken, exchange, interval);
        return analyse(candles, source);
    }

    private AnalystReport analyse(List<Candle> candles, String source) {
        if (candles.isEmpty()) {
            return emptyReport(source);
        }
        Candle last = candles.get(candles.size() - 1);
        MarketContext ctx = marketContextBuilder.build(candles);

        List<Signal> signals = new ArrayList<>();
        for (String name : strategyRegistry.availableNames()) {
            Strategy s = strategyRegistry.get(name);
            List<TradeIntent> intents = s.evaluate(candles);
            TradeIntent latest = intents.get(intents.size() - 1);
            signals.add(new Signal(name, latest.action(), latest.entry(),
                    latest.stop(), latest.target(), latest.rationale()));
        }

        Consensus consensus = buildConsensus(signals);
        Recommendation recommendation = buildRecommendation(signals, consensus, last, ctx);
        AnalystReport.MarketSummary summary = buildMarketSummary(ctx, last.close());

        AnalystReport report = new AnalystReport(
                Instant.now(),
                new AnalystReport.InstrumentInfo(source, candles.size(), last.close()),
                summary,
                signals,
                consensus,
                recommendation
        );
        log.info("Analyst report: source={} consensus={} strength={} confidence={} action={}",
                source, consensus.decision(), consensus.strength(),
                String.format("%.2f", consensus.confidence()), recommendation.action());
        return report;
    }

    private static Consensus buildConsensus(List<Signal> signals) {
        int longs = 0, shorts = 0, holds = 0, exits = 0;
        for (Signal s : signals) {
            switch (s.action()) {
                case ENTER_LONG  -> longs++;
                case ENTER_SHORT -> shorts++;
                case HOLD        -> holds++;
                case EXIT        -> exits++;
            }
        }
        int total = signals.size();

        Consensus.Decision decision;
        int agreeing;
        if (longs > shorts && longs > 0) {
            decision = Consensus.Decision.LONG;
            agreeing = longs;
        } else if (shorts > longs && shorts > 0) {
            decision = Consensus.Decision.SHORT;
            agreeing = shorts;
        } else {
            decision = Consensus.Decision.HOLD;
            agreeing = 0;
        }

        Consensus.Strength strength;
        if      (agreeing >= 4) strength = Consensus.Strength.STRONG;
        else if (agreeing == 3) strength = Consensus.Strength.MODERATE;
        else if (agreeing == 2) strength = Consensus.Strength.WEAK;
        else if (agreeing == 1) strength = Consensus.Strength.MINIMAL;
        else                    strength = Consensus.Strength.NONE;

        double confidence = total > 0 ? (double) agreeing / total : 0.0;
        return new Consensus(longs, shorts, holds, exits, total, decision, strength, confidence);
    }

    private static Recommendation buildRecommendation(List<Signal> signals, Consensus consensus,
                                                       Candle current, MarketContext ctx) {
        // Don't trade weak setups
        if (consensus.decision() == Consensus.Decision.HOLD
                || consensus.strength() == Consensus.Strength.MINIMAL
                || consensus.strength() == Consensus.Strength.NONE) {
            String why = String.format("no consensus (%d long, %d short, %d hold)",
                    consensus.longs(), consensus.shorts(), consensus.holds());
            return new Recommendation(IntentAction.HOLD, null, null, null, null, null, null, why);
        }

        IntentAction action = consensus.decision() == Consensus.Decision.LONG
                ? IntentAction.ENTER_LONG
                : IntentAction.ENTER_SHORT;
        BigDecimal entry = current.close();

        BigDecimal stop = null, target = null;
        String stopSource = null, targetSource = null;

        // 1. Pull stop/target from agreeing strategies, following priority.
        for (String stratName : LEVEL_PRIORITY) {
            for (Signal s : signals) {
                if (!s.strategy().equals(stratName)) continue;
                if (s.action() != action) continue;
                if (stop == null && s.stop() != null) {
                    stop = s.stop();
                    stopSource = s.strategy();
                }
                if (target == null && s.target() != null) {
                    target = s.target();
                    targetSource = s.strategy();
                }
            }
        }

        // 2. Fall back to MarketContext when strategies didn't set levels.
        if (stop == null) {
            if (action == IntentAction.ENTER_LONG) {
                stop = ctx.nearestBullishOBBelow(entry).map(ob -> ob.bottom()).orElse(null);
            } else {
                stop = ctx.nearestBearishOBAbove(entry).map(ob -> ob.top()).orElse(null);
            }
            if (stop != null) stopSource = "market-context";
        }
        if (target == null) {
            if (action == IntentAction.ENTER_LONG) {
                target = ctx.nearestUnsweptBSLAbove(entry).map(LiquidityLevel::price).orElse(null);
            } else {
                target = ctx.nearestUnsweptSSLBelow(entry).map(LiquidityLevel::price).orElse(null);
            }
            if (target != null) targetSource = "market-context";
        }

        // 3. Risk / reward
        BigDecimal rr = null;
        if (stop != null && target != null) {
            BigDecimal risk   = entry.subtract(stop).abs();
            BigDecimal reward = target.subtract(entry).abs();
            if (risk.signum() > 0) {
                rr = reward.divide(risk, 2, RoundingMode.HALF_UP);
            }
        }

        int agreeing = consensus.decision() == Consensus.Decision.LONG
                ? consensus.longs() : consensus.shorts();
        String rationale = String.format("%d/%d strategies %s; strength=%s; stop=%s; target=%s",
                agreeing, consensus.totalStrategies(),
                consensus.decision().name().toLowerCase(),
                consensus.strength(),
                stopSource   == null ? "none" : "from " + stopSource,
                targetSource == null ? "none" : "from " + targetSource);

        return new Recommendation(action, entry, stop, target, stopSource, targetSource, rr, rationale);
    }

    private static AnalystReport.MarketSummary buildMarketSummary(MarketContext ctx, BigDecimal currentPrice) {
        List<BigDecimal> nearbyBSL = ctx.unsweptBSL().stream()
                .filter(l -> l.price().compareTo(currentPrice) > 0)
                .sorted(Comparator.comparing(LiquidityLevel::price))
                .limit(3)
                .map(LiquidityLevel::price)
                .toList();
        List<BigDecimal> nearbySSL = ctx.unsweptSSL().stream()
                .filter(l -> l.price().compareTo(currentPrice) < 0)
                .sorted(Comparator.comparing(LiquidityLevel::price).reversed())
                .limit(3)
                .map(LiquidityLevel::price)
                .toList();
        return new AnalystReport.MarketSummary(
                ctx.bias(),
                ctx.lastEvent(),
                ctx.activeBullishOBs().size(),
                ctx.activeBearishOBs().size(),
                nearbyBSL,
                nearbySSL
        );
    }

    private static AnalystReport emptyReport(String source) {
        return new AnalystReport(
                Instant.now(),
                new AnalystReport.InstrumentInfo(source, 0, null),
                new AnalystReport.MarketSummary(null, null, 0, 0, Collections.emptyList(), Collections.emptyList()),
                Collections.emptyList(),
                new Consensus(0, 0, 0, 0, 0, Consensus.Decision.HOLD, Consensus.Strength.NONE, 0.0),
                new Recommendation(IntentAction.HOLD, null, null, null, null, null, null, "no candles")
        );
    }
}
