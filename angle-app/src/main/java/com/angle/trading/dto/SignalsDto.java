package com.angle.trading.dto;
/**
 * Human-readable signal labels derived from raw indicator values.
 * rsi:         OVERSOLD | NEUTRAL | OVERBOUGHT
 * adx:         RANGING | DEVELOPING | TRENDING
 * trend:       BULLISH | BEARISH  (from SuperTrend direction)
 * macd:        BULLISH | BEARISH  (histogram sign)
 * bbPosition:  ABOVE_UPPER | UPPER_HALF | LOWER_HALF | BELOW_LOWER
 * vwapPosition: ABOVE | BELOW
 */
public record SignalsDto(
        String rsi,
        String adx,
        String trend,
        String macd,
        String bbPosition,
        String vwapPosition
) {}
