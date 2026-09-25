package com.angle.trading.cache.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.cache.CandleCache;
import com.angle.trading.cache.CandleCacheKey;
import com.angle.trading.cache.RangeLoader;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Cache that does NOT cache. Every get() calls the loader.
 *
 * Use when you want to bypass caching entirely:
 *   - debugging Angel calls (no cache hiding real behavior)
 *   - force-fresh views during development
 *   - unit tests that assert on broker interactions
 *
 * Enable with:
 *   bias.cache.provider=noop
 */
@Slf4j
public class NoopCandleCache implements CandleCache {

    public NoopCandleCache() {
        log.info("NoopCandleCache initialised — caching is DISABLED (every request hits the broker)");
    }

    @Override
    public List<Candle> get(CandleCacheKey key, RangeLoader loader) {
        log.debug("Cache DISABLED → hitting broker for {}:{}:{}:{}",
                key.broker(), key.exchange(), key.symbolToken(), key.interval());
        return loader.load(key.from(), key.to());
    }

    @Override public void invalidate(CandleCacheKey key) { /* no-op */ }
    @Override public int invalidateByToken(String symbolToken) { return 0; }
    @Override public void clear() { /* no-op */ }
    @Override public CacheStats stats() { return new CacheStats(0, 0, 0, 0.0); }
    @Override public Map<CandleCacheKey, Integer> snapshot() { return Map.of(); }
}
