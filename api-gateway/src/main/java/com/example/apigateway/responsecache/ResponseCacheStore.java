package com.example.apigateway.responsecache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Redis-backed store for full HTTP responses. Keyed by SHA-256(routeId | method | path | query).
 * Entries stored as Redis Hash so individual fields can be read/updated later.
 *
 *   respcache:<sha256> = {
 *     status:      "200",
 *     body:        "<base64>",
 *     headers:     "Content-Type=application/json|X-Foo=bar",
 *     cachedAt:    "2026-09-29T10:00:00Z",
 *     originalUrl: "/api/v1/products/42"
 *   }
 *   TTL: configurable (default 1h, per-route override)
 */
@Component
@EnableConfigurationProperties(ResponseCacheProperties.class)
@ConditionalOnProperty(prefix = "gateway.response-cache", name = "enabled", havingValue = "true")
public class ResponseCacheStore {

    private static final String KEY_PREFIX = "respcache:";

    private final ReactiveStringRedisTemplate redis;

    public ResponseCacheStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Void> save(String routeId, String method, String path, String query,
                           CachedEntry entry, Duration ttl) {
        String key = buildKey(routeId, method, path, query);
        Map<String, String> fields = Map.of(
                "status",      String.valueOf(entry.status()),
                "body",        entry.body() == null ? "" : entry.body(),
                "headers",     entry.headers() == null ? "" : entry.headers(),
                "cachedAt",    entry.cachedAt().toString(),
                "originalUrl", entry.originalUrl() == null ? "" : entry.originalUrl()
        );
        return redis.opsForHash().putAll(key, fields)
                .then(redis.expire(key, ttl))
                .then();
    }

    public Mono<CachedEntry> lookup(String routeId, String method, String path, String query) {
        String key = buildKey(routeId, method, path, query);
        return redis.opsForHash().entries(key)
                .collectMap(e -> String.valueOf(e.getKey()), e -> String.valueOf(e.getValue()))
                .filter(m -> !m.isEmpty())
                .map(CachedEntry::fromMap);
    }

    public Mono<Boolean> invalidate(String routeId, String method, String path, String query) {
        String key = buildKey(routeId, method, path, query);
        return redis.delete(key).map(n -> n > 0);
    }

    private String buildKey(String routeId, String method, String path, String query) {
        String canonical = (routeId == null ? "" : routeId) + "|" +
                (method == null ? "" : method) + "|" +
                (path == null ? "" : path) + "|" +
                (query == null ? "" : query);
        return KEY_PREFIX + sha256(canonical);
    }

    private String sha256(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record CachedEntry(int status, String body, String headers,
                              Instant cachedAt, String originalUrl) {

        public static CachedEntry fromMap(Map<String, String> m) {
            int st = 0;
            try { st = Integer.parseInt(m.getOrDefault("status", "0")); } catch (NumberFormatException ignored) {}
            Instant ts = Instant.EPOCH;
            try {
                String s = m.getOrDefault("cachedAt", "");
                if (!s.isBlank()) ts = Instant.parse(s);
            } catch (Exception ignored) {}
            return new CachedEntry(
                    st,
                    m.getOrDefault("body", ""),
                    m.getOrDefault("headers", ""),
                    ts,
                    m.getOrDefault("originalUrl", ""));
        }
    }
}
