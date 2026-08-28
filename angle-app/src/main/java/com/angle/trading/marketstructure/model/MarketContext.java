package com.angle.trading.marketstructure.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A snapshot of everything a strategy needs to know at a specific candle index.
 *
 * Composed by {@code MarketContextBuilder} from Phase 1 (structure) and
 * Phase 2 (zones) outputs — all filtered so nothing "from the future"
 * leaks in when built as-of a past candle for backtesting.
 *
 *   asOfIndex        — the candle this snapshot represents; strategies see [0..asOfIndex]
 *   asOfTimestamp    — that candle's timestamp
 *   currentPrice     — candle close at asOfIndex
 *   bias             — BULLISH / BEARISH / null (no confirmed structure yet)
 *   lastEvent        — most recent BOS/CHoCH, or null
 *   latestSwingHigh  — most recent confirmed swing high, or null
 *   latestSwingLow   — most recent confirmed swing low, or null
 *   activeBullishOBs — bullish order blocks not yet mitigated
 *   activeBearishOBs — bearish order blocks not yet mitigated
 *   activeBullishFvgs — bullish FVGs not yet mitigated
 *   activeBearishFvgs — bearish FVGs not yet mitigated
 *   unsweptBSL        — buy-side liquidity pools not yet taken
 *   unsweptSSL        — sell-side liquidity pools not yet taken
 *   recentSweeps      — sweeps ordered by index (most recent last)
 */
public record MarketContext(
        int asOfIndex,
        Instant asOfTimestamp,
        BigDecimal currentPrice,
        Direction bias,
        StructureEvent lastEvent,
        SwingPoint latestSwingHigh,
        SwingPoint latestSwingLow,
        List<OrderBlock> activeBullishOBs,
        List<OrderBlock> activeBearishOBs,
        List<FairValueGap> activeBullishFvgs,
        List<FairValueGap> activeBearishFvgs,
        List<LiquidityLevel> unsweptBSL,
        List<LiquidityLevel> unsweptSSL,
        List<LiquiditySweep> recentSweeps
) {

    public boolean isBullish() { return bias == Direction.BULLISH; }
    public boolean isBearish() { return bias == Direction.BEARISH; }

    /** Closest bullish OB whose top is at or below the given price (support below). */
    public Optional<OrderBlock> nearestBullishOBBelow(BigDecimal price) {
        return activeBullishOBs.stream()
                .filter(ob -> ob.top().compareTo(price) <= 0)
                .max(Comparator.comparing(OrderBlock::top));  // highest top = closest support
    }

    /** Closest bearish OB whose bottom is at or above the given price (resistance above). */
    public Optional<OrderBlock> nearestBearishOBAbove(BigDecimal price) {
        return activeBearishOBs.stream()
                .filter(ob -> ob.bottom().compareTo(price) >= 0)
                .min(Comparator.comparing(OrderBlock::bottom));  // lowest bottom = closest resistance
    }

    /** Closest bullish FVG whose top is at or below the given price. */
    public Optional<FairValueGap> nearestBullishFvgBelow(BigDecimal price) {
        return activeBullishFvgs.stream()
                .filter(f -> f.top().compareTo(price) <= 0)
                .max(Comparator.comparing(FairValueGap::top));
    }

    /** Closest bearish FVG whose bottom is at or above the given price. */
    public Optional<FairValueGap> nearestBearishFvgAbove(BigDecimal price) {
        return activeBearishFvgs.stream()
                .filter(f -> f.bottom().compareTo(price) >= 0)
                .min(Comparator.comparing(FairValueGap::bottom));
    }

    /** Nearest unswept BSL above current price — next upward liquidity target. */
    public Optional<LiquidityLevel> nearestUnsweptBSLAbove(BigDecimal price) {
        return unsweptBSL.stream()
                .filter(l -> l.price().compareTo(price) > 0)
                .min(Comparator.comparing(LiquidityLevel::price));
    }

    /** Nearest unswept SSL below current price — next downward liquidity target. */
    public Optional<LiquidityLevel> nearestUnsweptSSLBelow(BigDecimal price) {
        return unsweptSSL.stream()
                .filter(l -> l.price().compareTo(price) < 0)
                .max(Comparator.comparing(LiquidityLevel::price));
    }

    /** Sweeps that happened within the last N candles. */
    public List<LiquiditySweep> sweepsWithin(int candleWindow) {
        int cutoff = asOfIndex - candleWindow;
        return recentSweeps.stream()
                .filter(s -> s.index() > cutoff)
                .toList();
    }
}
