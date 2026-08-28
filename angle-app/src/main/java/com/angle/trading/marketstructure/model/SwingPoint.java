package com.angle.trading.marketstructure.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A confirmed local high or low in the candle series.
 *
 *   index     — position in the source candle list
 *   timestamp — that candle's timestamp
 *   price     — the extreme value (high for SwingType.HIGH, low for SwingType.LOW)
 *   type      — HIGH or LOW
 *
 * Confirmation requires N candles on each side of `index` to be less extreme,
 * so the earliest swing that can be reported is at index N.
 */
public record SwingPoint(
        int index,
        Instant timestamp,
        BigDecimal price,
        SwingType type
) {}
