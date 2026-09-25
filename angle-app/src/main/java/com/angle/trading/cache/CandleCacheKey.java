package com.angle.trading.cache;

import java.time.LocalDate;

/**
 * Composite key for a candle fetch.
 *
 * Java {@code record} auto-generates equals/hashCode across all fields —
 * two keys with the same (broker, exchange, token, interval, from, to)
 * hash and compare equal, which is exactly what a cache map needs.
 *
 * Fields are typed as strings so different broker/interval enums don't
 * accidentally split the cache — we normalize at construction time.
 */
public record CandleCacheKey(
        String broker,       // "ANGEL"
        String exchange,     // "NSE", "MCX", ...
        String symbolToken,  // "99926000"
        String interval,     // "FIVE_MINUTE"
        LocalDate from,
        LocalDate to
) {
    public CandleCacheKey {
        // Normalize so ANGEL / angel / Angel all collide correctly.
        broker    = broker    == null ? null : broker.toUpperCase();
        exchange  = exchange  == null ? null : exchange.toUpperCase();
        interval  = interval  == null ? null : interval.toUpperCase();
    }
}
