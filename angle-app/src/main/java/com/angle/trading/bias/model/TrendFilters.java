package com.angle.trading.bias.model;

import java.math.BigDecimal;

/**
 * Sections 3-8 of the bias sheet — every indicator value.
 * All fields nullable when the indicator isn't computable (not enough data).
 *
 * EMA choice: 9 / 20 / 50 — faster than the classic 20/50/200 set. Tuned for
 * intraday timeframes where 200-EMA lag makes it a poor filter on 5M/15M data.
 *   ema9  vs ema20  → short-term momentum flip
 *   price vs ema50  → macro trend filter (mid-term)
 */
public record TrendFilters(
        // Trend
        BigDecimal ema9,
        BigDecimal ema20,
        BigDecimal ema50,
        Boolean    ema9AboveEma20,      // Short-term bullish
        Boolean    priceAboveEma50,     // Macro filter

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
        Boolean    macdBullish,         // histogram > 0

        // SuperTrend — trailing stop line + trend direction
        BigDecimal superTrendLine,      // the trailing line value
        Boolean    superTrendBullish,   // true when trend up (line below price)

        // Volume — confirms whether price moves have conviction behind them
        Long       currentVolume,       // volume of the latest candle
        Long       avgVolume20,         // 20-period simple average of volume
        BigDecimal volumeRatio,         // currentVolume / avgVolume20 (1.0 = average, 2.0 = double)
        Boolean    volumeBullish        // true = high volume + green candle (real buying)
) {}
