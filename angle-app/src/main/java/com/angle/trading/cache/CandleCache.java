package com.angle.trading.cache;

import com.angle.trading.broker.model.Candle;

import java.util.List;
import java.util.Map;

/**
 * Pluggable cache for broker candle results.
 *
 * The business layer (MarketDataService) depends ONLY on this interface —
 * it doesn't know or care which implementation is active (Caffeine, Redis,
 * Ehcache, or noop). Swap providers by changing the bias.cache.provider
 * property, no Java changes needed.
 *
 * Semantics:
 *   get()         — return cached value if fresh, else call the loader and cache the result
 *   invalidate()  — drop a single entry (e.g. after we know Angel has newer data)
 *   clear()       — drop everything (debug/admin endpoint)
 *   stats()       — for /actuator or logs — hit rate helps tune TTL
 */
public interface CandleCache {

    /**
     * Fetch candles for the given key. On cache miss the loader is called
     * with the (from, to) it should ask the broker for — enabling gap-fill
     * (only fetch missing dates).
     */
    List<Candle> get(CandleCacheKey key, RangeLoader loader);

    void invalidate(CandleCacheKey key);

    /**
     * Drop every cached entry for a specific instrument token (across all intervals,
     * date ranges, broker/exchange combos). Returns count of entries removed.
     * Useful for "refresh this instrument's data" without wiping the whole cache.
     */
    int invalidateByToken(String symbolToken);

    void clear();

    CacheStats stats();

    /**
     * Snapshot of every currently-cached entry: key → candle count.
     * Cheap (no values copied); safe to call from a debug endpoint.
     * Returns an empty map for noop or empty caches.
     */
    Map<CandleCacheKey, Integer> snapshot();

    /** Snapshot of counters; hit rate is (hits / (hits + misses)) when denom > 0. */
    record CacheStats(long hits, long misses, long size, double hitRate) {}
}
