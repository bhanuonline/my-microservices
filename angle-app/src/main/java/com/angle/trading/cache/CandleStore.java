package com.angle.trading.cache;

import com.angle.trading.broker.model.Candle;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Range-aware persistent store for candles (L3 tier).
 *
 * Different from {@link CandleCache}:
 *   - CandleCache uses exact-key match (broker + exchange + token + interval + from + to)
 *   - CandleStore uses RANGE queries — a request for Sep 1-24 hits any stored
 *     candles in that range, regardless of what "fetch" originally saved them.
 *
 * This range-awareness is what enables:
 *   - overlapping requests to share data
 *   - Phase D (gap-fill: fetch only missing dates from Angel)
 *   - offline backtesting on stored candles
 *
 * All writes go through {@link #saveAsync} — fire-and-forget so a slow DB
 * doesn't block user-facing requests.
 */
public interface CandleStore {

    /**
     * Range query — returns candles matching (broker, exchange, token, interval)
     * whose timestamp falls in [from, to]. Empty list if none.
     */
    List<Candle> findRange(String broker, String exchange, String symbolToken,
                            String intervalType, LocalDate from, LocalDate to);

    /**
     * Save candles asynchronously. Returns immediately with a future the caller
     * can ignore (fire-and-forget). Duplicates are handled via DB upsert.
     */
    CompletableFuture<Integer> saveAsync(String broker, String exchange, String symbolToken,
                                          String intervalType, List<Candle> candles);

    /** Purge candles saved before the given cutoff. Used by retention cron. */
    int deleteOlderThan(java.time.Instant savedAtCutoff);

    /** Wipe all candles for one instrument (all intervals). */
    int deleteByToken(String symbolToken);

    /** Total row count. Handy for /admin/cache stats. */
    long count();

    /**
     * Latest candle timestamp we have for this instrument+interval.
     * Used for gap-fill: if latest is today 15:30, we only need to fetch
     * from Angel starting AFTER that point.
     * Returns empty when the store has no candles for this key.
     */
    Optional<Instant> latestTimestamp(String broker, String exchange,
                                       String symbolToken, String intervalType);
}
