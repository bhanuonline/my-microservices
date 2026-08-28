package com.angle.trading.broker.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single OHLCV candle. Broker-neutral: every broker adapter maps its
 * own response format into this record.
 *
 * Using BigDecimal for prices avoids floating-point drift when summing
 * or comparing values — matters for indicators like moving averages.
 */
public record Candle(
        Instant timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {}
