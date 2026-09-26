package com.angle.trading.broker.angel.stream.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One tick decoded from Angel SmartStream binary payload.
 *
 * We keep it minimal for Phase 1 (Mode 1 / LTP). When we move to Mode 2 / 3
 * we'll add bid/ask/OI fields as a separate record and dispatch on the mode.
 *
 *   exchangeCode — Angel's numeric exchange (1=NSE_CM, 2=NSE_FO, 3=BSE_CM,
 *                  4=BSE_FO, 5=MCX, 7=NCX_FO, 13=CDS_FO)
 *   symbolToken  — Angel token, e.g. "99926000"
 *   ltp          — last traded price, scaled to rupees
 *   exchangeTime — exchange-reported timestamp (may be a few ms behind wall clock)
 *   receivedAt   — when we deserialized the tick (for latency measurement)
 */
public record Tick(
        int         exchangeCode,
        String      symbolToken,
        BigDecimal  ltp,
        Instant     exchangeTime,
        Instant     receivedAt
) {}
