package com.example.productservice.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Distributed single-flight. Only one caller runs the loader for a given key;
 * everyone else waits briefly then re-reads the cache (populated by the winner).
 *
 * Not fancy — a reasonable minimum that prevents dogpiles on hot keys when
 * their TTL expires. For stronger guarantees use Redisson's RLock.
 */
@Component
public class StampedeLock {

    private static final Logger log = LoggerFactory.getLogger(StampedeLock.class);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final long BUSY_WAIT_MS = 50;
    private static final int  MAX_SPINS   = 20;             // 20 × 50 ms = 1 s ceiling

    private final StringRedisTemplate redis;

    public StampedeLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Attempts to acquire the lock. If successful runs {@code loader} and
     * returns its result. If another caller holds the lock, waits for it,
     * then returns the output of {@code cacheReader} (which should return
     * the now-populated cache value).
     */
    public <T> T single(String key, Callable<T> loader, Callable<T> cacheReader) throws Exception {
        String lockKey = "lock:" + key;
        String token = UUID.randomUUID().toString();

        Boolean won = redis.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
        if (Boolean.TRUE.equals(won)) {
            try {
                return loader.call();
            } finally {
                // Best-effort release — don't delete someone else's lock if ours expired.
                String current = redis.opsForValue().get(lockKey);
                if (token.equals(current)) redis.delete(lockKey);
            }
        }

        // Lost the race — wait briefly, then read whatever winner wrote.
        for (int i = 0; i < MAX_SPINS; i++) {
            Thread.sleep(BUSY_WAIT_MS);
            T cached = cacheReader.call();
            if (cached != null) return cached;
        }
        log.warn("stampede lock timeout for key={} — falling back to direct load", key);
        return loader.call();
    }
}
