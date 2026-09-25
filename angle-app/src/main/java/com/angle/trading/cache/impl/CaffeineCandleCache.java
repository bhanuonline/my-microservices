package com.angle.trading.cache.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.cache.CandleCache;
import com.angle.trading.cache.CandleCacheKey;
import com.angle.trading.cache.CandleCacheProperties;
import com.angle.trading.cache.RangeLoader;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-JVM cache backed by Caffeine.
 *
 * Chosen as the default because:
 *   - fastest Java cache (0.001 ms reads)
 *   - no external infrastructure
 *   - small footprint (~25 MB for typical use)
 *
 * Cache is lost on JVM restart — that's fine for a 60-sec TTL cache
 * (the first request after restart just pays one Angel call).
 */
@Slf4j
public class CaffeineCandleCache implements CandleCache {

    private final Cache<CandleCacheKey, List<Candle>> cache;

    public CaffeineCandleCache(CandleCacheProperties props) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(props.getTtlSeconds()))
                .maximumSize(props.getMaxSize())
                .recordStats()
                .build();
        log.info("CaffeineCandleCache initialised — ttl={}s, maxSize={}",
                props.getTtlSeconds(), props.getMaxSize());
    }

    @Override
    public List<Candle> get(CandleCacheKey key, RangeLoader loader) {
        // Fast path: read-only lookup so we can tell HIT from MISS in logs.
        // Then Caffeine.get(key, loader) does the actual atomic load on miss —
        // only one thread runs the loader per key (no thundering herd).
        List<Candle> existing = cache.getIfPresent(key);
        if (existing != null) {
            log.debug("Cache HIT  → {} ({} candles)", format(key), existing.size());
            return existing;
        }
        log.debug("Cache MISS → {} — calling broker", format(key));
        long start = System.nanoTime();
        List<Candle> loaded = cache.get(key, k -> loader.load(k.from(), k.to()));
        long ms = (System.nanoTime() - start) / 1_000_000;
        log.debug("Cache FILL → {} ({} candles, {} ms)", format(key), loaded == null ? 0 : loaded.size(), ms);
        return loaded;
    }

    @Override
    public void invalidate(CandleCacheKey key) {
        log.info("Cache invalidate → {}", format(key));
        cache.invalidate(key);
    }

    @Override
    public int invalidateByToken(String symbolToken) {
        if (symbolToken == null || symbolToken.isBlank()) return 0;
        // Collect matching keys first, then invalidate — safer than mutating during iteration.
        List<CandleCacheKey> matches = cache.asMap().keySet().stream()
                .filter(k -> symbolToken.equals(k.symbolToken()))
                .toList();
        matches.forEach(cache::invalidate);
        log.info("Cache invalidateByToken({}) — dropped {} entries", symbolToken, matches.size());
        return matches.size();
    }

    @Override
    public void clear() {
        long before = cache.estimatedSize();
        cache.invalidateAll();
        log.info("Cache cleared — dropped {} entries", before);
    }

    /** Compact key format for logs — full object toString is noisy. */
    private static String format(CandleCacheKey k) {
        return k.broker() + ":" + k.exchange() + ":" + k.symbolToken()
                + ":" + k.interval() + " [" + k.from() + "→" + k.to() + "]";
    }

    @Override
    public Map<CandleCacheKey, Integer> snapshot() {
        // asMap() returns a live view backed by the cache; iterate + copy to keep
        // the response stable while the caller consumes it.
        Map<CandleCacheKey, Integer> out = new LinkedHashMap<>();
        cache.asMap().forEach((k, v) -> out.put(k, v == null ? 0 : v.size()));
        return out;
    }

    @Override
    public CacheStats stats() {
        var s = cache.stats();
        long hits   = s.hitCount();
        long misses = s.missCount();
        long total  = hits + misses;
        double hitRate = total == 0 ? 0.0 : (double) hits / total;
        return new CacheStats(hits, misses, cache.estimatedSize(), hitRate);
    }
}
