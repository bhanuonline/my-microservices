package com.angle.trading.strategy.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.config.AnalysisProperties;
import com.angle.trading.marketstructure.MarketContextBuilder;
import com.angle.trading.marketstructure.model.FairValueGap;
import com.angle.trading.marketstructure.model.LiquiditySide;
import com.angle.trading.marketstructure.model.LiquiditySweep;
import com.angle.trading.marketstructure.model.MarketContext;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.model.TradeIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Classic ICT/SMC pattern — "liquidity grab, then FVG entry".
 *
 * Bullish setup:
 *   1. Sell-side liquidity (SSL) got swept in the last N candles — price
 *      briefly dropped below a swing low, then closed back up.
 *   2. Current market bias is BULLISH (structure has confirmed the reversal).
 *   3. The displacement leg away from the sweep left a bullish FVG that
 *      has not been mitigated.
 *   4. The current candle taps INTO that FVG.
 *   ⇒ enter long at candle close
 *      stop   = min(sweep candle's low, FVG bottom) — below the "trapped" longs
 *      target = nearest unswept BSL above current price
 *
 * Bearish setup is the mirror image (BSL sweep, bearish bias, bearish FVG,
 * short at FVG tap, stop above sweep high, target at next unswept SSL).
 *
 * Sweep-window is configurable via {@code analysis.smc.sweep.window-candles}.
 * A trade is skipped if there's no unswept liquidity target on the correct side.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiquiditySweepFvgStrategy implements Strategy {

    private final MarketContextBuilder marketContextBuilder;
    private final AnalysisProperties analysisProperties;

    @Override
    public String name() {
        return "sweep-fvg";
    }

    @Override
    public List<TradeIntent> evaluate(List<Candle> candles) {
        int window = analysisProperties.getSmc().getSweep().getWindowCandles();
        List<TradeIntent> intents = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            MarketContext ctx = marketContextBuilder.buildAsOf(candles, i);
            intents.add(intentAt(i, candles, ctx, window));
        }
        return intents;
    }

    private static TradeIntent intentAt(int i, List<Candle> candles,
                                        MarketContext ctx, int window) {
        Candle c = candles.get(i);

        // --- Bullish setup ---
        if (ctx.isBullish()) {
            Optional<LiquiditySweep> sslSweep = mostRecentSweep(ctx.sweepsWithin(window), LiquiditySide.SELL_SIDE);
            if (sslSweep.isPresent()) {
                Optional<FairValueGap> fvgOpt = ctx.nearestBullishFvgBelow(c.high());
                if (fvgOpt.isPresent() && candleTouchesFvg(c, fvgOpt.get())) {
                    FairValueGap fvg = fvgOpt.get();
                    BigDecimal sweepLow = candles.get(sslSweep.get().index()).low();
                    BigDecimal stop = sweepLow.min(fvg.bottom());
                    Optional<BigDecimal> targetOpt = ctx.nearestUnsweptBSLAbove(c.close())
                            .map(l -> l.price());
                    if (targetOpt.isPresent()) {
                        return TradeIntent.enterLong(
                                c.close(), stop, targetOpt.get(),
                                String.format("SSL sweep @ %s → bullish FVG retest %s-%s",
                                        sslSweep.get().levelPrice(), fvg.top(), fvg.bottom())
                        );
                    }
                }
            }
        }

        // --- Bearish setup ---
        if (ctx.isBearish()) {
            Optional<LiquiditySweep> bslSweep = mostRecentSweep(ctx.sweepsWithin(window), LiquiditySide.BUY_SIDE);
            if (bslSweep.isPresent()) {
                Optional<FairValueGap> fvgOpt = ctx.nearestBearishFvgAbove(c.low());
                if (fvgOpt.isPresent() && candleTouchesFvg(c, fvgOpt.get())) {
                    FairValueGap fvg = fvgOpt.get();
                    BigDecimal sweepHigh = candles.get(bslSweep.get().index()).high();
                    BigDecimal stop = sweepHigh.max(fvg.top());
                    Optional<BigDecimal> targetOpt = ctx.nearestUnsweptSSLBelow(c.close())
                            .map(l -> l.price());
                    if (targetOpt.isPresent()) {
                        return TradeIntent.enterShort(
                                c.close(), stop, targetOpt.get(),
                                String.format("BSL sweep @ %s → bearish FVG retest %s-%s",
                                        bslSweep.get().levelPrice(), fvg.top(), fvg.bottom())
                        );
                    }
                }
            }
        }
        return TradeIntent.hold();
    }

    private static Optional<LiquiditySweep> mostRecentSweep(List<LiquiditySweep> sweeps, LiquiditySide side) {
        LiquiditySweep found = null;
        for (LiquiditySweep s : sweeps) {
            if (s.side() == side) found = s;   // list is chronological — last match wins
        }
        return Optional.ofNullable(found);
    }

    private static boolean candleTouchesFvg(Candle c, FairValueGap fvg) {
        return c.low().compareTo(fvg.top()) <= 0
                && c.high().compareTo(fvg.bottom()) >= 0;
    }
}
