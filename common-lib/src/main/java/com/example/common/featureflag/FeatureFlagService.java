package com.example.common.featureflag;

import com.example.common.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flag lookup + sticky evaluation.
 *
 * Cache: in-process map with a tight TTL (5s) so toggles propagate without a
 * restart. For cluster-wide instant propagation, publish a `flag.updated`
 * Kafka event on {@link FeatureFlagAdminController} write and have each
 * instance evict on consume — not implemented here (keep scope).
 *
 * Stickiness: percentage rollouts hash on (userId:flagKey) so user X is in
 * either the 25% bucket forever or never, no UI flicker on refresh.
 */
@Service
public class FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);

    private final FeatureFlagRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public FeatureFlagService(FeatureFlagRepository repo) {
        this.repo = repo;
    }

    public boolean isEnabled(String key) {
        return isEnabled(key, EvalContext.tenant(TenantContext.get()));
    }

    public boolean isEnabled(String key, EvalContext ctx) {
        Optional<FeatureFlag> flag = lookup(key);
        if (flag.isEmpty() || !flag.get().isEnabled()) return false;
        FlagRules rules = parseRules(flag.get().getRulesJson());

        if (ctx.tenantId() != null && rules.tenantWhitelist().contains(ctx.tenantId())) return true;
        if (ctx.userId() != null && rules.userWhitelist().contains(ctx.userId())) return true;
        if (rules.percentage() <= 0) return false;
        if (rules.percentage() >= 100) return true;

        String principal = ctx.userId() != null ? ctx.userId()
                        : ctx.tenantId() != null ? ctx.tenantId()
                        : "anonymous";
        int bucket = Math.floorMod(stableHash(principal + ":" + key), 100);
        return bucket < rules.percentage();
    }

    private Optional<FeatureFlag> lookup(String key) {
        CacheEntry e = cache.get(key);
        if (e != null && e.expiresAt.isAfter(Instant.now())) return Optional.ofNullable(e.flag);
        Optional<FeatureFlag> fresh = repo.findById(key);
        cache.put(key, new CacheEntry(fresh.orElse(null), Instant.now().plus(CACHE_TTL)));
        return fresh;
    }

    /** Called by the admin controller after a flag change to drop any stale cached value. */
    public void invalidate(String key) {
        cache.remove(key);
    }

    private FlagRules parseRules(String json) {
        if (json == null || json.isBlank()) return FlagRules.empty();
        try {
            return mapper.readValue(json, FlagRules.class);
        } catch (JsonProcessingException e) {
            log.warn("malformed rules json, defaulting to empty: {}", e.getMessage());
            return FlagRules.empty();
        }
    }

    /** Deterministic hash — don't swap to String.hashCode; its JVM-dependence breaks reproducibility across releases. */
    private static int stableHash(String s) {
        // 32-bit FNV-1a. Enough for 100 buckets.
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int h = 0x811c9dc5;
        for (byte b : bytes) {
            h ^= (b & 0xff);
            h *= 0x01000193;
        }
        return h;
    }

    private record CacheEntry(FeatureFlag flag, Instant expiresAt) {}
}
