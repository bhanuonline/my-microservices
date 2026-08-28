package com.angle.trading.analyst.model;

import com.angle.trading.marketstructure.model.Direction;
import com.angle.trading.marketstructure.model.StructureEvent;
import com.angle.trading.strategy.model.IntentAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Analyst report — a single unified answer to "what should I do right now?"
 *
 * Aggregates:
 *   - one {@link Signal} per registered strategy (its latest decision)
 *   - a {@link Consensus} summary (how many long/short/hold)
 *   - a {@link Recommendation} — the final action with entry/stop/target
 *
 * Nothing here trades. It's a report the caller reads and decides on.
 */
public record AnalystReport(
        Instant asOf,
        InstrumentInfo instrument,
        MarketSummary market,
        List<Signal> signals,
        Consensus consensus,
        Recommendation recommendation
) {

    /** Where the candles came from + basic price info. */
    public record InstrumentInfo(
            String source,          // "csv" or "ANGEL:99926000@NSE:ONE_DAY"
            int candleCount,
            BigDecimal currentPrice
    ) {}

    /** SMC context snapshot — high-level bias + nearby liquidity. */
    public record MarketSummary(
            Direction bias,
            StructureEvent lastEvent,
            int activeBullishOBs,
            int activeBearishOBs,
            List<BigDecimal> nearbyBSL,   // next 3 unswept liquidity above current price
            List<BigDecimal> nearbySSL    // next 3 unswept liquidity below current price
    ) {}

    /** What one strategy currently says. */
    public record Signal(
            String strategy,
            IntentAction action,
            BigDecimal entry,
            BigDecimal stop,
            BigDecimal target,
            String rationale
    ) {}

    /** Aggregation across all strategies. */
    public record Consensus(
            int longs,
            int shorts,
            int holds,
            int exits,
            int totalStrategies,
            Decision decision,
            Strength strength,
            double confidence      // agreeing / total, 0.0 .. 1.0
    ) {
        public enum Decision  { LONG, SHORT, HOLD }
        public enum Strength  { STRONG, MODERATE, WEAK, MINIMAL, NONE }
    }

    /** The actual "here's what to do" call. */
    public record Recommendation(
            IntentAction action,
            BigDecimal entry,
            BigDecimal stop,
            BigDecimal target,
            String stopSource,          // which strategy the stop came from ("market-context" if fallback)
            String targetSource,
            BigDecimal riskRewardRatio, // reward/risk, null if either missing
            String rationale
    ) {}
}
