package com.angle.trading.dto;

import java.math.BigDecimal;

/**
 * Trend indicators.
 *   adx14    — trend strength (>25 = trending, <20 = ranging)
 *   plusDi   — bullish directional indicator (+DI)
 *   minusDi  — bearish directional indicator (-DI)
 *              plusDi > minusDi → bulls in control
 *              plusDi < minusDi → bears in control
 *   supertrend — current direction + line level
 */
public record TrendDto(
        BigDecimal adx14,
        BigDecimal plusDi,
        BigDecimal minusDi,
        SupertrendDto supertrend
) {}
