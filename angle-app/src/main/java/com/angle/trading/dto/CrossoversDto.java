package com.angle.trading.dto;
/**
 * Boolean flags for key price / EMA relationships at the current candle.
 * null when the indicator hasn't warmed up yet (e.g. EMA200 needs 200 bars).
 */
public record CrossoversDto(
        Boolean ema9AboveEma20,
        Boolean ema20AboveEma50,
        Boolean priceAboveEma200,
        Boolean priceAboveVwap
) {}
