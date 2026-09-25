package com.angle.trading.cache.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.cache.CandleCache;
import com.angle.trading.cache.CandleCacheKey;
import com.angle.trading.cache.CandleCacheProperties;
import com.angle.trading.cache.RangeLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * External Redis-backed candle cache (L2 tier).
 *
 * Key format: {keyPrefix}:{broker}:{exchange}:{token}:{interval}:{from}:{to}
 * Value: List<Candle> serialised via the configured RedisTemplate.
 *
 * Stats are tracked locally (Redis doesn't push hit/miss to clients).
 *
 * Failure model:
 *   - Redis unreachable → get() treats as MISS, invalidate/clear log warning
 *   - Never throws to caller (LayeredCandleCache uses this — layered mode
 *     must degrade gracefully when Redis is down)
 */
@Slf4j
public class RedisCandleCache implements CandleCache {

    private final RedisTemplate<String, List<Candle>> template;
    private final Duration ttl;
    private final String keyPrefix;

    private final AtomicLong hits   = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public RedisCandleCache(RedisTemplate<String, List<Candle>> template, CandleCacheProperties props) {
        this.template  = template;
        this.ttl       = Duration.ofSeconds(props.getRedis().getTtlSeconds());
        this.keyPrefix = props.getRedis().getKeyPrefix();
        log.info("RedisCandleCache initialised — ttl={}s, keyPrefix={}", ttl.getSeconds(), keyPrefix);
    }

    @Override
    public List<Candle> get(CandleCacheKey key, RangeLoader loader) {
        String k = redisKey(key);
        List<Candle> existing = safeGet(k);
        if (existing != null) {
            hits.incrementAndGet();
            log.debug("Redis HIT  → {} ({} candles)", k, existing.size());
            return existing;
        }
        misses.incrementAndGet();
        log.debug("Redis MISS → {}", k);
        List<Candle> loaded = loader.load(key.from(), key.to());
        safePut(k, loaded);
        return loaded;
    }

    @Override
    public void invalidate(CandleCacheKey key) {
        try {
            template.delete(redisKey(key));
        } catch (Exception e) {
            log.warn("Redis invalidate failed: {}", e.getMessage());
        }
    }

    @Override
    public int invalidateByToken(String symbolToken) {
        if (symbolToken == null || symbolToken.isBlank()) return 0;
        String pattern = keyPrefix + ":*:*:" + symbolToken + ":*";
        try {
            Set<String> matches = template.keys(pattern);
            if (matches == null || matches.isEmpty()) return 0;
            template.delete(matches);
            log.info("Redis invalidateByToken({}) — dropped {} entries", symbolToken, matches.size());
            return matches.size();
        } catch (Exception e) {
            log.warn("Redis invalidateByToken failed: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public void clear() {
        String pattern = keyPrefix + ":*";
        try {
            Set<String> keys = template.keys(pattern);
            if (keys == null || keys.isEmpty()) return;
            template.delete(keys);
            log.info("Redis cleared — dropped {} entries under prefix {}", keys.size(), keyPrefix);
        } catch (Exception e) {
            log.warn("Redis clear failed: {}", e.getMessage());
        }
    }

    @Override
    public CacheStats stats() {
        long h = hits.get(), m = misses.get(), total = h + m;
        double rate = total == 0 ? 0.0 : (double) h / total;
        long size = 0;
        try {
            Set<String> keys = template.keys(keyPrefix + ":*");
            size = keys == null ? 0 : keys.size();
        } catch (Exception ignored) { /* stats are best-effort */ }
        return new CacheStats(h, m, size, rate);
    }

    @Override
    public Map<CandleCacheKey, Integer> snapshot() {
        Map<CandleCacheKey, Integer> out = new HashMap<>();
        try {
            Set<String> keys = template.keys(keyPrefix + ":*");
            if (keys == null) return out;
            for (String k : keys) {
                CandleCacheKey parsed = parseKey(k);
                if (parsed == null) continue;
                List<Candle> v = safeGet(k);
                out.put(parsed, v == null ? 0 : v.size());
            }
        } catch (Exception e) {
            log.warn("Redis snapshot failed: {}", e.getMessage());
        }
        return out;
    }

    // ---------- helpers ----------

    private String redisKey(CandleCacheKey k) {
        return keyPrefix + ":" + k.broker() + ":" + k.exchange() + ":" + k.symbolToken()
                + ":" + k.interval() + ":" + k.from() + ":" + k.to();
    }

    /** Reverse of redisKey — returns null if the key doesn't match the expected shape. */
    private CandleCacheKey parseKey(String k) {
        String[] parts = k.split(":");
        // prefix, broker, exchange, token, interval, from, to  → 7 parts
        if (parts.length != 7) return null;
        try {
            return new CandleCacheKey(
                    parts[1], parts[2], parts[3], parts[4],
                    java.time.LocalDate.parse(parts[5]),
                    java.time.LocalDate.parse(parts[6]));
        } catch (Exception e) {
            return null;
        }
    }

    private List<Candle> safeGet(String k) {
        try {
            return template.opsForValue().get(k);
        } catch (Exception e) {
            log.warn("Redis get failed for {}: {}", k, e.getMessage());
            return null;
        }
    }

    private void safePut(String k, List<Candle> v) {
        if (v == null || v.isEmpty()) return;   // don't cache empty results
        try {
            template.opsForValue().set(k, v, ttl);
        } catch (Exception e) {
            log.warn("Redis put failed for {}: {}", k, e.getMessage());
        }
    }
}
