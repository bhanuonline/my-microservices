package com.angle.trading.marketstructure.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Order Block — the last opposing candle before a strong displacement (BOS/CHoCH).
 *
 *   Bullish OB — last DOWN candle before a bullish break; expected support if retested.
 *   Bearish OB — last UP candle before a bearish break; expected resistance if retested.
 *
 *   top / bottom      — the OB candle's high / low (full OHLC range used, not just body)
 *   direction         — direction of the following impulse (what the OB "produces")
 *   formedAtIndex     — index of the OB candle itself
 *   confirmedAtIndex  — index of the BOS candle that made this OB significant
 *   mitigated         — true once a later candle has traded back into the zone
 *   mitigatedAtIndex  — first candle that re-entered the zone (null if unmitigated)
 */
public record OrderBlock(
        int index,
        Instant timestamp,
        BigDecimal top,
        BigDecimal bottom,
        Direction direction,
        int formedAtIndex,
        int confirmedAtIndex,
        boolean mitigated,
        Integer mitigatedAtIndex
) {}
