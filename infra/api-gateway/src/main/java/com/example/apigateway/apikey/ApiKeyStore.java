package com.example.apigateway.apikey;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
@EnableConfigurationProperties(ApiKeyProperties.class)
@ConditionalOnProperty(prefix = "gateway.apikey", name = "enabled", havingValue = "true")
public class ApiKeyStore {

    private static final String KEY_PREFIX = "apikey:";
    private static final Duration DEFAULT_TTL = Duration.ofDays(365);

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;

    public ApiKeyStore(ReactiveStringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /**
     * Lookup by RAW key. Hashes it internally; the raw key is never stored.
     * Filters out disabled or expired records.
     */
    public Mono<ApiKeyRecord> lookup(String rawKey) {
        String redisKey = KEY_PREFIX + sha256(rawKey);
        return redis.opsForValue().get(redisKey)
                .flatMap(json -> {
                    try {
                        return Mono.just(mapper.readValue(json, ApiKeyRecord.class));
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .filter(ApiKeyRecord::isEnabled)
                .filter(rec -> rec.getExpiresAt() == null || rec.getExpiresAt().isAfter(Instant.now()));
    }

    /**
     * Persist a record. The rawKey must be preserved by the caller for return
     * to the user — we store only its hash + metadata.
     */
    public Mono<Void> save(ApiKeyRecord record, String rawKey) {
        String redisKey = KEY_PREFIX + sha256(rawKey);
        Duration ttl = record.getExpiresAt() != null
                ? Duration.between(Instant.now(), record.getExpiresAt())
                : DEFAULT_TTL;
        if (ttl.isNegative() || ttl.isZero()) return Mono.empty();
        try {
            String json = mapper.writeValueAsString(record);
            return redis.opsForValue().set(redisKey, json, ttl).then();
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("Failed to serialize ApiKeyRecord", e));
        }
    }

    /** Instantly revoke — same-hash lookups will now miss. */
    public Mono<Boolean> revokeByRawKey(String rawKey) {
        return redis.delete(KEY_PREFIX + sha256(rawKey)).map(n -> n > 0);
    }

    /**
     * Revoke by public key ID. Requires a scan since we don't index by id.
     * For real production, maintain a secondary index (id → hash).
     */
    public Mono<Boolean> revokeById(String keyId) {
        return redis.keys(KEY_PREFIX + "*")
                .flatMap(k -> redis.opsForValue().get(k)
                        .flatMap(json -> {
                            try {
                                ApiKeyRecord rec = mapper.readValue(json, ApiKeyRecord.class);
                                if (keyId.equals(rec.getId())) {
                                    return redis.delete(k).map(n -> n > 0);
                                }
                            } catch (Exception ignored) {}
                            return Mono.just(false);
                        }))
                .any(Boolean::booleanValue);
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
}
