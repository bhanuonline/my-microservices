package com.angle.trading.strategy.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.config.AnalysisProperties;
import com.angle.trading.marketstructure.MarketContextBuilder;
import com.angle.trading.marketstructure.model.LiquidityLevel;
import com.angle.trading.marketstructure.model.MarketContext;
import com.angle.trading.marketstructure.model.OrderBlock;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.StrategyRegistry;
import com.angle.trading.strategy.model.IntentAction;
import com.angle.trading.strategy.model.TradeIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * "Committee vote" strategy: combines the five other strategies into one decision per candle.
 *
 * At each candle:
 *   1. Ask each child what it wants to do.
 *   2. Count LONG / SHORT votes.
 *   3. If a direction has strictly more votes than the other AND the winning
 *      side has ≥ {@code minAgreement} votes, that's the ensemble's action.
 *      Otherwise HOLD.
 *   4. Stop / target: take the first non-null value from an agreeing child,
 *      in priority order (SMC first). Fall back to MarketContext when no
 *      child provided one.
 *   5. Never enter without both a stop AND a target — refuse to trade blind.
 *
 * Two ways to control minAgreement:
 *   - default from analysis.strategy.ensemble.min-agreement in properties
 *   - override per-invocation via {@link #withMinAgreement(int)} — used by the
 *     controller when {@code ?minAgreement=N} is passed to /backtest
 */
@Slf4j
@Component
public class EnsembleStrategy implements Strategy {

    /** Priority order for pulling stop / target — SMC strategies first. */
    private static final List<String> CHILDREN_IN_PRIORITY = List.of(
            "sweep-fvg",
            "ob-retest",
            "macd-crossover",
            "rsi-mean-reversion",
            "moving-average-crossover"
    );

    private final StrategyRegistry     strategyRegistry;
    private final MarketContextBuilder marketContextBuilder;
    private final AnalysisProperties   analysisProperties;

    /** Optional runtime override — null means "read from config". */
    private final Integer overrideMinAgreement;

    /** Spring-created bean — no override. */
    @Autowired
    public EnsembleStrategy(@Lazy StrategyRegistry strategyRegistry,
                            MarketContextBuilder marketContextBuilder,
                            AnalysisProperties analysisProperties) {
        this(strategyRegistry, marketContextBuilder, analysisProperties, null);
    }

    /** Internal constructor used by {@link #withMinAgreement}. Not a Spring entry point. */
    private EnsembleStrategy(StrategyRegistry strategyRegistry,
                             MarketContextBuilder marketContextBuilder,
                             AnalysisProperties analysisProperties,
                             Integer overrideMinAgreement) {
        this.strategyRegistry     = strategyRegistry;
        this.marketContextBuilder = marketContextBuilder;
        this.analysisProperties   = analysisProperties;
        this.overrideMinAgreement = overrideMinAgreement;
    }

    /**
     * Return a copy of this strategy that uses the given minAgreement instead
     * of the configured value — for one-off backtests via query param.
     */
    public EnsembleStrategy withMinAgreement(int minAgreement) {
        if (minAgreement < 1) {
            throw new IllegalArgumentException("minAgreement must be >= 1");
        }
        return new EnsembleStrategy(strategyRegistry, marketContextBuilder,
                analysisProperties, minAgreement);
    }

    @Override
    public String name() {
        return "ensemble";
    }

    @Override
    public List<TradeIntent> evaluate(List<Candle> candles) {
        int minAgreement = overrideMinAgreement != null
                ? overrideMinAgreement
                : analysisProperties.getStrategy().getEnsemble().getMinAgreement();

        // Run each child ONCE for the whole series, then look up per candle.
        List<List<TradeIntent>> childSeries = new ArrayList<>(CHILDREN_IN_PRIORITY.size());
        for (String childName : CHILDREN_IN_PRIORITY) {
            Strategy child = strategyRegistry.get(childName);
            childSeries.add(child.evaluate(candles));
        }

        List<TradeIntent> out = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            List<TradeIntent> childIntentsAtI = new ArrayList<>(CHILDREN_IN_PRIORITY.size());
            for (List<TradeIntent> series : childSeries) {
                childIntentsAtI.add(series.get(i));
            }
            out.add(aggregate(i, candles, childIntentsAtI, minAgreement));
        }
        return out;
    }

    private TradeIntent aggregate(int i, List<Candle> candles,
                                  List<TradeIntent> childIntents, int minAgreement) {
        int longs = 0, shorts = 0;
        for (TradeIntent it : childIntents) {
            if (it.action() == IntentAction.ENTER_LONG)  longs++;
            if (it.action() == IntentAction.ENTER_SHORT) shorts++;
        }

        IntentAction action;
        int agreeing;
        if (longs > shorts && longs >= minAgreement) {
            action = IntentAction.ENTER_LONG;
            agreeing = longs;
        } else if (shorts > longs && shorts >= minAgreement) {
            action = IntentAction.ENTER_SHORT;
            agreeing = shorts;
        } else {
            return TradeIntent.hold();
        }

        Candle c = candles.get(i);

        // Pull stop / target from agreeing children, priority order.
        BigDecimal stop = null, target = null;
        String stopSource = null, targetSource = null;
        for (int k = 0; k < childIntents.size(); k++) {
            TradeIntent it = childIntents.get(k);
            if (it.action() != action) continue;
            if (stop == null && it.stop() != null) {
                stop = it.stop();
                stopSource = CHILDREN_IN_PRIORITY.get(k);
            }
            if (target == null && it.target() != null) {
                target = it.target();
                targetSource = CHILDREN_IN_PRIORITY.get(k);
            }
        }

        // Fallback to MarketContext for missing levels.
        if (stop == null || target == null) {
            MarketContext ctx = marketContextBuilder.buildAsOf(candles, i);
            if (stop == null) {
                stop = (action == IntentAction.ENTER_LONG)
                        ? ctx.nearestBullishOBBelow(c.close()).map(OrderBlock::bottom).orElse(null)
                        : ctx.nearestBearishOBAbove(c.close()).map(OrderBlock::top).orElse(null);
                if (stop != null) stopSource = "market-context";
            }
            if (target == null) {
                target = (action == IntentAction.ENTER_LONG)
                        ? ctx.nearestUnsweptBSLAbove(c.close()).map(LiquidityLevel::price).orElse(null)
                        : ctx.nearestUnsweptSSLBelow(c.close()).map(LiquidityLevel::price).orElse(null);
                if (target != null) targetSource = "market-context";
            }
        }

        // Refuse to trade blind — need both stop AND target.
        if (stop == null || target == null) {
            return TradeIntent.hold();
        }

        String rationale = String.format("%d/%d %s (stop:%s, target:%s)",
                agreeing, childIntents.size(),
                action == IntentAction.ENTER_LONG ? "long" : "short",
                stopSource, targetSource);

        return action == IntentAction.ENTER_LONG
                ? TradeIntent.enterLong(c.close(),  stop, target, rationale)
                : TradeIntent.enterShort(c.close(), stop, target, rationale);
    }
}
