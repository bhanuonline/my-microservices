package com.angle.trading.paper.model;

/**
 * Lifecycle state of a paper-trading session.
 *
 *   RUNNING   — actively consuming candles
 *   COMPLETED — candle source exhausted (replay finished)
 *   STOPPED   — user called /stop
 */
public enum SessionStatus {
    RUNNING,
    COMPLETED,
    STOPPED
}
