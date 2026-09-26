package com.angle.trading.broker.angel.stream;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Interval;

/**
 * Emitted by {@link CandleAggregator} when a live-built candle closes.
 *
 * Consumers subscribe via {@code @EventListener} — same pattern as {@link TickEvent}.
 * The candle is fully populated (OHLC) except that volume may be null when
 * the stream is running in Mode 1 (LTP-only) — Angel doesn't send per-tick
 * volume in that mode.
 */
public record CandleClosedEvent(
        int      exchangeCode,   // Angel exchange numeric (1=NSE, 5=MCX, ...)
        String   symbolToken,
        Interval interval,       // FIVE_MINUTE, ONE_MINUTE, ...
        Candle   candle          // ts=bar start, OHLC, volume (may be null)
) {}
