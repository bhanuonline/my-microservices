package com.angle.trading.dto;
import java.util.List;
/**
 * Opening Range Breakout analysis computed from today's intraday candles.
 * Works for any symbol (not limited to configured instruments).
 *
 * Checked for 3 standard timeframes: 15m, 30m, 1h.
 * Signal per timeframe:
 *   FORMING  — opening range still building (not enough candles yet)
 *   WAITING  — OR complete, price inside range — no breakout yet
 *   BUY      — price closed ABOVE the opening range high
 *   SHORT    — price closed BELOW the opening range low
 */
public record OrbDto(List<OrbTimeframeDto> timeframes) {}
