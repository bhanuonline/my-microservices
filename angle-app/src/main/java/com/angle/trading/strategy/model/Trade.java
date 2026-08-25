package com.angle.trading.strategy.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A completed round-trip trade — entry and exit both booked.
 *
 * Shared between the {@code Backtester} (summary output) and the
 * paper-trading runtime (live-accumulated log). Fields cover everything a
 * caller might want to plot, audit, or send to a report.
 *
 *   direction  — ENTER_LONG or ENTER_SHORT (never HOLD/EXIT)
 *   entryTime  — timestamp of the candle that opened the trade
 *   entryPrice — fill price at entry
 *   exitTime   — timestamp of the candle that closed the trade
 *   exitPrice  — fill price at exit
 *   stopLoss   — stop level as of entry, or null
 *   target     — target level as of entry, or null
 *   exitReason — STOP / TARGET / SIGNAL_EXIT / END_OF_SERIES
 *   pnl        — realized profit (long) or (entry - exit) for short
 *   rationale  — free-text "why" from the strategy that opened the trade
 */
public record Trade(
        IntentAction direction,
        Instant entryTime,
        BigDecimal entryPrice,
        Instant exitTime,
        BigDecimal exitPrice,
        BigDecimal stopLoss,
        BigDecimal target,
        ExitReason exitReason,
        BigDecimal pnl,
        String rationale
) {}
