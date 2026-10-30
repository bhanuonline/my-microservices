package com.angle.trading.cache;

import com.angle.trading.config.IndicatorCacheProperties;
import com.angle.trading.dto.IndicatorSnapshotDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Three-layer indicator value cache:
 *   L1 — ConcurrentHashMap (in-memory, sub-ms, lost on restart)
 *   L2 — Redis (persists across restarts, survives deploys)
 *   L3 — DB  (permanent; day-breakdown snapshots only)
 *
 * Read path:  L1 hit → return.
 *             L1 miss → L2 Redis hit → populate L1 → return.
 *             L2 miss → L3 DB hit (day type only) → populate L1+L2 → return.
 *             All miss → caller computes → put() → saves to L1+L2+L3.
 *
 * Write path: always writes L1 + L2. Day-breakdown writes also go to L3 DB.
 * Evict:      removes from L1 + L2 + L3 (DB delete).
 *
 * Redis key format: {prefix}:{original-cache-key}
 *   e.g.  ind:indicators|RELIANCE-EQ|ONE_DAY|300|2026-10-29
 *
 * Graceful degradation: if Redis or DB is down, upper layers still serve.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndicatorCacheStore {

    private record L1Entry(Object value, Instant expiresAt) {
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }

    private record ParsedKey(String type, String symbol, String interval, int days, LocalDate date) {}

    private final ConcurrentHashMap<String, L1Entry> l1 = new ConcurrentHashMap<>();
    private final IndicatorCacheProperties props;
    private final RedisTemplate<String, Object> indicatorRedisTemplate;
    private final IndicatorDbStore dbStore;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        if (!props.isEnabled()) return Optional.empty();

        // L1 check
        L1Entry entry = l1.get(key);
        if (entry != null) {
            if (!entry.isExpired()) {
                log.debug("Indicator L1 HIT: {}", key);
                return Optional.of((T) entry.value());
            }
            l1.remove(key);
        }

        // L2 Redis check
        Object redisVal = safeRedisGet(redisKey(key));
        if (redisVal != null) {
            log.debug("Indicator L2 Redis HIT: {}", key);
            l1.put(key, new L1Entry(redisVal, Instant.now().plus(props.getTtl())));
            return Optional.of((T) redisVal);
        }

        // L3 DB check — only for day-breakdown keys
        ParsedKey pk = parseKey(key);
        if (pk != null && "day".equals(pk.type())) {
            List<IndicatorSnapshotDto> dbRows = dbStore.findDayBreakdown(pk.symbol(), pk.interval(), pk.date());
            if (!dbRows.isEmpty()) {
                log.debug("Indicator L3 DB HIT: {}", key);
                java.time.Duration ttl = props.getTtl();
                l1.put(key, new L1Entry(dbRows, Instant.now().plus(ttl)));
                safeRedisPut(redisKey(key), dbRows, ttl);
                return Optional.of((T) dbRows);
            }
        }

        return Optional.empty();
    }

    public void put(String key, Object value) {
        put(key, value, props.getTtl());
    }

    public void put(String key, Object value, java.time.Duration ttl) {
        if (!props.isEnabled()) return;
        Instant expiresAt = Instant.now().plus(ttl);
        l1.put(key, new L1Entry(value, expiresAt));
        safeRedisPut(redisKey(key), value, ttl);
        log.debug("Indicator cached (L1+L2) ttl={}: {}", ttl, key);

        // L3 DB — persist day-breakdown snapshots only for PAST dates (complete data).
        // Today's data is still growing every hour — saving it permanently would
        // cache incomplete candles forever.
        ParsedKey pk = parseKey(key);
        if (pk != null && "day".equals(pk.type())
                && pk.date().isBefore(LocalDate.now())     // ← past dates only
                && value instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof IndicatorSnapshotDto) {
            @SuppressWarnings("unchecked")
            List<IndicatorSnapshotDto> snapshots = (List<IndicatorSnapshotDto>) list;
            dbStore.saveDayBreakdown(pk.symbol(), pk.interval(), pk.date(), snapshots);
        }
    }

    /** Evict all entries for a symbol from L1 + L2 + L3. Returns count evicted. */
    public int evictBySymbol(String symbol) {
        String fragment = "|" + symbol + "|";

        // L1
        int[] count = {0};
        l1.keySet().removeIf(k -> { if (k.contains(fragment)) { count[0]++; return true; } return false; });

        // L2 Redis
        count[0] += safeRedisEvictPattern(props.getRedisPrefix() + ":*" + fragment + "*");

        // L3 DB
        dbStore.deleteBySymbol(symbol);

        return count[0];
    }

    /** Evict everything from L1 + L2 + L3. */
    public int evictAll() {
        int l1size = l1.size();
        l1.clear();
        int l2size = safeRedisEvictPattern(props.getRedisPrefix() + ":*");

        // L3 DB
        dbStore.deleteAll();

        return l1size + l2size;
    }

    /** Returns a snapshot of all live entries (key → ISO expiry). Reads from Redis. */
    public Map<String, String> liveKeys() {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Set<String> redisKeys = indicatorRedisTemplate.keys(props.getRedisPrefix() + ":*");
            if (redisKeys != null) {
                for (String rk : redisKeys) {
                    Long ttlSec = indicatorRedisTemplate.getExpire(rk,
                            java.util.concurrent.TimeUnit.SECONDS);
                    String expiry = (ttlSec != null && ttlSec > 0)
                            ? Instant.now().plusSeconds(ttlSec).toString()
                            : "no-expiry";
                    // Strip prefix to show the original cache key
                    out.put(rk.substring(props.getRedisPrefix().length() + 1), expiry);
                }
            }
        } catch (Exception e) {
            log.warn("Redis liveKeys failed — falling back to L1: {}", e.getMessage());
            Instant now = Instant.now();
            l1.forEach((k, v) -> { if (!v.isExpired()) out.put(k, v.expiresAt().toString()); });
        }
        return out;
    }

    public int size() {
        try {
            Set<String> keys = indicatorRedisTemplate.keys(props.getRedisPrefix() + ":*");
            return keys == null ? 0 : keys.size();
        } catch (Exception e) {
            return (int) l1.values().stream().filter(e2 -> !e2.isExpired()).count();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String redisKey(String cacheKey) {
        return props.getRedisPrefix() + ":" + cacheKey;
    }

    /** Parse "type|symbol|interval|days|date" into a typed record. Returns null on malformed keys. */
    private static ParsedKey parseKey(String key) {
        if (key == null) return null;
        String[] p = key.split("\\|");
        if (p.length < 5) return null;
        try {
            return new ParsedKey(p[0], p[1], p[2], Integer.parseInt(p[3]), LocalDate.parse(p[4]));
        } catch (Exception e) {
            return null;
        }
    }

    private Object safeRedisGet(String key) {
        try {
            return indicatorRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis get failed for indicator key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void safeRedisPut(String key, Object value, java.time.Duration ttl) {
        try {
            indicatorRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis put failed for indicator key {}: {}", key, e.getMessage());
        }
    }

    private int safeRedisEvictPattern(String pattern) {
        try {
            Set<String> keys = indicatorRedisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) return 0;
            indicatorRedisTemplate.delete(keys);
            return keys.size();
        } catch (Exception e) {
            log.warn("Redis evict failed for pattern {}: {}", pattern, e.getMessage());
            return 0;
        }
    }
}
