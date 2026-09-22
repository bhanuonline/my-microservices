package com.angle.trading.bias.model;

import java.math.BigDecimal;

/**
 * Section 1 of the bias sheet — reference levels + open/close/gap.
 * All prices in instrument's currency. Nulls where data unavailable.
 */
public record MarketContextSection(
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal previousDayHigh,
        BigDecimal previousDayLow,
        BigDecimal dayOpen,
        BigDecimal dayHighSoFar,
        BigDecimal dayLowSoFar,
        BigDecimal gapPoints,        // dayOpen - previousClose
        BigDecimal gapPercent,       // gapPoints / previousClose × 100
        BigDecimal indiaVix          // null until VIX fetch is built
) {}
