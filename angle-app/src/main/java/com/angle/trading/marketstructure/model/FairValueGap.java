package com.angle.trading.marketstructure.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fair Value Gap — a 3-candle imbalance where price moves too fast to fill.
 *
 *   Bullish FVG — candle[i-2].high < candle[i].low; gap = (low[i-2..i].max-high, low[i].min-low)
 *   Bearish FVG — candle[i-2].low  > candle[i].high; symmetric.
 *
 *   The gap is expected to act as support (bullish) or resistance (bearish)
 *   if price returns to it. Once price fully closes through, it's "filled".
 *
 *   top / bottom       — inclusive boundaries of the gap
 *   direction          — what fills it going forward
 *   formedAtIndex      — the 3rd candle in the pattern (i)
 *   mitigated          — true once price has traded back into the zone
 *   mitigatedAtIndex   — first candle that re-entered the zone (null if unmitigated)
 */
public record FairValueGap(
        Instant timestamp,
        BigDecimal top,
        BigDecimal bottom,
        Direction direction,
        int formedAtIndex,
        boolean mitigated,
        Integer mitigatedAtIndex
) {}
