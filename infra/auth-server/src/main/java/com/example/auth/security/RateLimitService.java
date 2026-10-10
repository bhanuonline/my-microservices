package com.example.auth.security;

import com.example.auth.config.FeatureFlags;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages token buckets (Bucket4j) — one per (endpoint, IP-or-client) key.
 *
 * <p>What's a token bucket?
 * <pre>
 *   Bucket has capacity=20 tokens. Refills at 20/minute (1 token every 3 sec).
 *
 *   Request 1  → bucket=19  → allow
 *   Request 2  → bucket=18  → allow
 *   ... (20 requests) ...
 *   Request 21 → bucket=0   → DENY (429). Wait ~3s, then bucket=1, retry.
 * </pre>
 *
 * <p>Two independent bucket families:
 * <ul>
 *   <li><b>login</b> — keyed by IP. Config: 20/min. Human login is slow, this
 *       is generous for typos.</li>
 *   <li><b>token</b> — keyed by client_id (from Basic Auth on /oauth2/token).
 *       Config: 120/min. M2M clients can be chatty.</li>
 * </ul>
 *
 * <p>Two families keeps them isolated: a broken M2M client hammering
 * /oauth2/token can't lock out actual human logins.
 *
 * <p>Storage is {@link ConcurrentHashMap} — bounded only by unique keys seen.
 * Fine for a study project (max thousands of keys). Real prod would swap in:
 * <ul>
 *   <li>Caffeine with {@code expireAfterAccess(5min)} — bounded memory</li>
 *   <li>Redis — shared bucket across multiple auth-server instances</li>
 * </ul>
 * Same interface, different implementation.
 */
@Service
@Profile("jdbc")
public class RateLimitService {

    private final FeatureFlags flags;
    private final ConcurrentMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> tokenBuckets = new ConcurrentHashMap<>();

    public RateLimitService(FeatureFlags flags) {
        this.flags = flags;
    }

    /** Try to consume 1 token. Returns null if allowed, or seconds-to-wait if denied. */
    public Long tryLogin(String ip) {
        var cfg = flags.getRateLimit().getLogin();
        return tryConsume(loginBuckets, "login:" + ip, cfg.getCapacity(), cfg.getRefillPerMinute());
    }

    public Long tryToken(String clientKey) {
        var cfg = flags.getRateLimit().getToken();
        return tryConsume(tokenBuckets, "token:" + clientKey,
                cfg.getCapacity(), cfg.getRefillPerMinute());
    }

    private Long tryConsume(ConcurrentMap<String, Bucket> map, String key,
                             int capacity, int refillPerMinute) {
        Bucket bucket = map.computeIfAbsent(key, k -> build(capacity, refillPerMinute));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return null; // allowed
        }
        long retryAfterSec = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        return retryAfterSec;
    }

    private static Bucket build(int capacity, int refillPerMinute) {
        Bandwidth limit = Bandwidth.classic(capacity,
                Refill.greedy(refillPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
