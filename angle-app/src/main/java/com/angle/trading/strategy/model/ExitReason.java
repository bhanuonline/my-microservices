package com.angle.trading.strategy.model;

/**
 * Why an open trade was closed.
 *
 *   STOP           — price hit the stop-loss level
 *   TARGET         — price hit the profit target
 *   SIGNAL_EXIT    — strategy emitted EXIT (or opposite-direction entry)
 *   END_OF_SERIES  — backtest ran out of candles; close at last price
 */
public enum ExitReason {
    STOP,
    TARGET,
    SIGNAL_EXIT,
    END_OF_SERIES
}
