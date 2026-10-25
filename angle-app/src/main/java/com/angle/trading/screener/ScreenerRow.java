package com.angle.trading.screener;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One row in the F&O screener — one instrument's snapshot.
 *
 * Score breakdown (0–6):
 *   +1  ADX > 25          (strong trend exists)
 *   +1  ADX > 35          (very strong trend bonus)
 *   +1  Close > EMA200    (price in uptrend)
 *   +1  EMA50 > EMA200    (golden cross alignment)
 *   +1  RSI 40–65         (momentum, not overbought)
 *   +1  Has open signal   (system already fired on this)
 */
@Data
@Builder
public class ScreenerRow {

    private String  symbol;
    private String  symbolToken;
    private String  exchange;
    private int     lotSize;

    // ── Price snapshot ────────────────────────────────────────────────
    private BigDecimal lastClose;
    private BigDecimal ema50;
    private BigDecimal ema200;

    // ── Indicators ────────────────────────────────────────────────────
    private BigDecimal rsi;
    private BigDecimal adx;
    private BigDecimal atr;         // absolute move per day

    // ── Derived ───────────────────────────────────────────────────────
    private String  trendBias;      // BULLISH / BEARISH / NEUTRAL
    private int     score;          // 0-6
    private int     openSignals;    // count of OPEN signals in feed

    /** Non-null when data fetch failed; row still shown but greyed out. */
    private String  error;

    // ── Criteria flags (used by template for green/red dots) ──────────
    private boolean adxStrong;      // ADX > 25
    private boolean aboveEma200;    // close > EMA200
    private boolean emaCross;       // EMA50 > EMA200
    private boolean rsiHealthy;     // RSI 40–65
    private boolean hasSignal;      // openSignals > 0
}
