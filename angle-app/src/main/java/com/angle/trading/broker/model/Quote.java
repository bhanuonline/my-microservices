package com.angle.trading.broker.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Latest tick for a symbol.
 */
public record Quote(
        String symbol,
        BigDecimal lastPrice,
        BigDecimal change,
        BigDecimal changePercent,
        Instant timestamp
) {}
