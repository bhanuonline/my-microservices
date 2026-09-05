package com.angle.trading.alerts;

/**
 * Alert severity — future-proofing for filtering / routing.
 * Right now all channels treat every level the same, but a future
 * "critical-only" channel (SMS?) could filter to WARN+ERROR.
 */
public enum AlertLevel {
    INFO,
    WARN,
    ERROR
}
