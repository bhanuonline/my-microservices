package com.example.apigateway.idempotency;

import com.example.apigateway.config.IdempotencyProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
@EnableConfigurationProperties(IdempotencyProperties.class)
@ConditionalOnProperty(prefix = "gateway.idempotency", name = "enabled", havingValue = "true")
public class IdempotencyStore {

    private static final String LOCK_SUFFIX = ":lock";

    private static final RedisScript<Long> COMPARE_AND_DELETE = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final ReactiveStringRedisTemplate redis;
    private final IdempotencyProperties props;

    public IdempotencyStore(ReactiveStringRedisTemplate redis, IdempotencyProperties props) {
        this.redis = redis;
        this.props = props;
    }

    public Mono<Boolean> tryLock(String route, String key, String requestId) {
        return redis.opsForValue()
                .setIfAbsent(lockKey(route, key), requestId, props.getLockTtl());
    }

    public Mono<Boolean> releaseLock(String route, String key, String requestId) {
        return redis.execute(COMPARE_AND_DELETE,
                        List.of(lockKey(route, key)),
                        List.of(requestId))
                .next()
                .map(l -> l != null && l > 0)
                .defaultIfEmpty(false);
    }

    public Mono<CachedResponse> lookup(String route, String key) {
        String cacheKey = cacheKey(route, key);
        return redis.opsForHash().entries(cacheKey)
                .collectMap(e -> String.valueOf(e.getKey()), e -> String.valueOf(e.getValue()))
                .filter(m -> !m.isEmpty())
                .map(CachedResponse::fromMap);
    }

    public Mono<Void> save(String route, String key, CachedResponse response) {
        String cacheKey = cacheKey(route, key);
        return redis.opsForHash().putAll(cacheKey, response.toMap())
                .then(redis.expire(cacheKey, props.getKeyTtl()))
                .then();
    }

    private String cacheKey(String route, String key) {
        return "idem:" + route + ":" + key;
    }

    private String lockKey(String route, String key) {
        return cacheKey(route, key) + LOCK_SUFFIX;
    }

    public record CachedResponse(int status, String fingerprint, String body, String headers) {

        public static CachedResponse fromMap(Map<String, String> m) {
            int st = 0;
            try { st = Integer.parseInt(m.getOrDefault("status", "0")); } catch (NumberFormatException ignored) {}
            return new CachedResponse(
                    st,
                    m.getOrDefault("fingerprint", ""),
                    m.getOrDefault("body", ""),
                    m.getOrDefault("headers", ""));
        }

        public Map<String, String> toMap() {
            return Map.of(
                    "status", String.valueOf(status),
                    "fingerprint", fingerprint == null ? "" : fingerprint,
                    "body", body == null ? "" : body,
                    "headers", headers == null ? "" : headers);
        }
    }
}
