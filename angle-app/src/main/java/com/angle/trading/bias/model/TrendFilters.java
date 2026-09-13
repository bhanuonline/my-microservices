package com.angle.trading.bias.model;

import java.math.BigDecimal;

/**
 * Sections 3-8 of the bias sheet — every indicator value.
 * All fields nullable when the indicator isn't computable (not enough data).
 */
public record TrendFilters(
        // Trend
        BigDecimal ema20,
        BigDecimal ema50,
        BigDecimal ema200,
        Boolean    ema20AboveEma50,     // Bullish trend
        Boolean    priceAboveEma200,    // Macro filter

        // Intraday direction
        BigDecimal vwap,
        Boolean    priceAboveVwap,

        // Strength
        BigDecimal adx14,
        String     adxStrength,         // "WEAK" / "DEVELOPING" / "STRONG"

        // Volatility
        BigDecimal atr14,
        BigDecimal atrPercentOfPrice,

        // Momentum
        BigDecimal rsi14,
        Boolean    rsiBullish,          // rsi > 50

        // Confirmation
        BigDecimal macdLine,
        BigDecimal macdSignal,
        BigDecimal macdHistogram,
        Boolean    macdBullish          // histogram > 0
) {}
