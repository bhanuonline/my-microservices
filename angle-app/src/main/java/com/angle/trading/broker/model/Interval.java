package com.angle.trading.broker.model;

/**
 * Candle timeframe. Neutral across brokers — each broker adapter maps
 * this enum to its own vendor string (e.g. Angel uses "ONE_MINUTE").
 */
public enum Interval {
    ONE_MINUTE,
    FIVE_MINUTE,
    FIFTEEN_MINUTE,
    THIRTY_MINUTE,
    ONE_HOUR,
    ONE_DAY
}
