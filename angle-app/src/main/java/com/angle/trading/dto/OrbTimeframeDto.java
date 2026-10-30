package com.angle.trading.dto;
import java.math.BigDecimal;
/**
 * ORB state for one opening-range timeframe.
 *   minutes  — opening range duration (15, 30, 60)
 *   signal   — FORMING | WAITING | BUY | SHORT
 *   orHigh   — opening range high (top of the range)
 *   orLow    — opening range low  (bottom of the range)
 *   orRange  — orHigh - orLow (range width in ₹)
 */
public record OrbTimeframeDto(
        int        minutes,
        String     signal,
        BigDecimal orHigh,
        BigDecimal orLow,
        BigDecimal orRange
) {}
