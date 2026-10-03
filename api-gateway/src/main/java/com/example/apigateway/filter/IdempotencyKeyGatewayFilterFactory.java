package com.example.apigateway.filter;

import com.example.apigateway.config.IdempotencyProperties;
import com.example.apigateway.idempotency.IdempotencyStore;
import com.example.apigateway.idempotency.IdempotencyStore.CachedResponse;
import com.example.apigateway.metrics.GatewayMetrics;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Idempotency-Key filter (Stripe pattern).
 *
 *   FIRST SEE (cache miss)     → acquire Redis lock, forward, cache 2xx response
 *   IN FLIGHT (lock exists)    → 409 Conflict
 *   COMPLETED (cache hit)      → replay cached response, skip downstream
 *   BODY MISMATCH              → 422 Unprocessable Entity
 *
 * Only applies to methods in `gateway.idempotency.methods` (POST, PATCH by default).
 * Must run AFTER RequestFingerprint (so X-Request-Fingerprint header exists for mismatch check).
 *
 * Safety features:
 *   - stripHeaders     → sensitive response headers (Set-Cookie, Authorization, Date)
 *                        never enter Redis / are never replayed to other clients
 *   - streaming skip   → responses with Content-Type in streamingContentTypes bypass
 *                        buffering entirely (protects against unbounded memory)
 *   - fail-open        → Redis errors bypass the filter when failOpenOnStoreError=true
 */
@Component
@ConditionalOnProperty(prefix = "gateway.idempotency", name = "enabled", havingValue = "true")
public class IdempotencyKeyGatewayFilterFactory
        extends AbstractGatewayFilterFactory<IdempotencyKeyGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyGatewayFilterFactory.class);

    private final IdempotencyStore store;
    private final IdempotencyProperties props;
    private final GatewayMetrics metrics;

    public IdempotencyKeyGatewayFilterFactory(IdempotencyStore store,
                                              IdempotencyProperties props,
                                              GatewayMetrics metrics) {
        super(Config.class);
        this.store = store;
        this.props = props;
        this.metrics = metrics;
    }

    @Override
    public GatewayFilter apply(Config config) {
        GatewayFilter filter = (exchange, chain) -> {
            // Runtime kill switch — flip `gateway.idempotency.enabled: false` +
            // POST /actuator/refresh to disable without a restart. Needed because
            // @ConditionalOnProperty is boot-only; without this check, the bean
            // still processes requests even when the flag flips.
            if (!props.isEnabled()) {
                return chain.filter(exchange);
            }

            HttpMethod method = exchange.getRequest().getMethod();
            if (method == null || !props.getMethods().contains(method)) {
                return chain.filter(exchange);
            }

            String key = resolveKey(exchange);
            if (key == null) {
                if (props.isRequireHeader()) {
                    return writeError(exchange, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                            "missing_idempotency_key",
                            "Header '" + props.getHeaderName() + "' is required for " + method);
                }
                return chain.filter(exchange);
            }

            String routeId = routeId(exchange);
            String fingerprint = exchange.getRequest().getHeaders().getFirst(props.getFingerprintHeader());
            String requestId = UUID.randomUUID().toString();

            return store.lookup(routeId, key)
                    .flatMap(cached -> {
                        if (cached.status() > 0) {
                            if (props.isVerifyFingerprint()
                                    && fingerprint != null
                                    && !fingerprint.isBlank()
                                    && !fingerprint.equals(cached.fingerprint())) {
                                metrics.idempotency("mismatch");
                                return writeError(exchange, HttpStatus.UNPROCESSABLE_ENTITY,
                                        "idempotency_key_mismatch",
                                        "Idempotency-Key reused with a different request body");
                            }
                            metrics.idempotency("hit");
                            return replayCached(exchange, cached);
                        }
                        return acquireAndForward(exchange, chain, routeId, key, requestId, fingerprint);
                    })
                    .switchIfEmpty(acquireAndForward(exchange, chain, routeId, key, requestId, fingerprint))
                    .onErrorResume(err -> handleStoreError(exchange, chain, "lookup", err));
        };

        return new OrderedGatewayFilter(filter, 0);
    }

    private String resolveKey(ServerWebExchange exchange) {
        String key = exchange.getRequest().getHeaders().getFirst(props.getHeaderName());
        if ((key == null || key.isBlank()) && props.isDeriveFromFingerprint()) {
            key = exchange.getRequest().getHeaders().getFirst(props.getFingerprintHeader());
        }
        return (key == null || key.isBlank()) ? null : key;
    }

    private String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "unknown";
    }

    private Mono<Void> acquireAndForward(ServerWebExchange exchange,
                                         GatewayFilterChain chain,
                                         String routeId, String key, String requestId, String fingerprint) {
        return store.tryLock(routeId, key, requestId)
                .flatMap(locked -> {
                    if (!Boolean.TRUE.equals(locked)) {
                        metrics.idempotency("conflict");
                        return writeError(exchange, HttpStatus.CONFLICT,
                                "duplicate_request_in_flight",
                                "Another request with the same Idempotency-Key is being processed");
                    }
                    metrics.idempotency("miss");
                    ServerHttpResponse originalResponse = exchange.getResponse();
                    DataBufferFactory bufferFactory = originalResponse.bufferFactory();

                    ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(originalResponse) {
                        @Override
                        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                            HttpStatusCode status = getStatusCode();
                            if (isStreamingResponse(getHeaders())) {
                                // Streaming/large payload — release lock and pass through unmodified
                                log.debug("Skipping idempotency cache for streaming response (Content-Type={})",
                                        getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
                                return store.releaseLock(routeId, key, requestId)
                                        .onErrorResume(err -> Mono.empty())
                                        .then(super.writeWith(body));
                            }
                            if (status != null && status.is2xxSuccessful() && body instanceof Flux<? extends DataBuffer> fluxBody) {
                                return DataBufferUtils.join(fluxBody).flatMap(joined -> {
                                    byte[] content = new byte[joined.readableByteCount()];
                                    joined.read(content);
                                    DataBufferUtils.release(joined);

                                    CachedResponse cached = new CachedResponse(
                                            status.value(),
                                            fingerprint == null ? "" : fingerprint,
                                            Base64.getEncoder().encodeToString(content),
                                            serializeHeadersForCache(getHeaders()));

                                    return store.save(routeId, key, cached)
                                            .onErrorResume(err -> {
                                                log.warn("Idempotency cache save failed (key={}): {}", key, err.getMessage());
                                                return Mono.empty();
                                            })
                                            .then(super.writeWith(Mono.just(bufferFactory.wrap(content))));
                                });
                            }
                            // non-2xx → release lock so retries can try again, don't cache
                            return store.releaseLock(routeId, key, requestId)
                                    .onErrorResume(err -> Mono.empty())
                                    .then(super.writeWith(body));
                        }

                        @Override
                        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                            return writeWith(Flux.from(body).flatMapSequential(p -> p));
                        }
                    };

                    return chain.filter(exchange.mutate().response(decorated).build())
                            .doOnError(err -> store.releaseLock(routeId, key, requestId)
                                    .onErrorResume(e -> Mono.empty())
                                    .subscribe());
                })
                .onErrorResume(err -> handleStoreError(exchange, chain, "lock", err));
    }

    private Mono<Void> handleStoreError(ServerWebExchange exchange, GatewayFilterChain chain,
                                        String op, Throwable err) {
        if (props.isFailOpenOnStoreError()) {
            log.warn("Idempotency store {} failed — failing open (bypassing filter): {}", op, err.getMessage());
            exchange.getResponse().getHeaders().add("X-Idempotency-Bypassed", op + "-error");
            metrics.idempotency("bypassed");
            return chain.filter(exchange);
        }
        log.error("Idempotency store {} failed and fail-open disabled: {}", op, err.getMessage());
        metrics.idempotency("store_error");
        return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                "idempotency_store_unavailable",
                "Idempotency store is unavailable, please retry later");
    }

    private Mono<Void> replayCached(ServerWebExchange exchange, CachedResponse cached) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(HttpStatusCode.valueOf(cached.status()));
        deserializeHeaders(cached.headers()).forEach((k, v) -> resp.getHeaders().put(k, v));
        resp.getHeaders().add("X-Idempotent-Replay", "true");
        byte[] bytes = Base64.getDecoder().decode(cached.body());
        DataBuffer buf = resp.bufferFactory().wrap(bytes);
        return resp.writeWith(Mono.just(buf));
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String error, String message) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(status);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}";
        return resp.writeWith(Mono.just(resp.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * True when the response should NOT be buffered/cached — streaming or opaque
     * binary content whose full size is unknown or too large to fit in memory.
     */
    private boolean isStreamingResponse(HttpHeaders headers) {
        if (!props.isSkipStreamingContent()) return false;
        String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        for (String streaming : props.getStreamingContentTypes()) {
            if (lower.startsWith(streaming.toLowerCase())) return true;
        }
        return false;
    }

    /** Serialize response headers for cache, stripping the configured sensitive ones. */
    private String serializeHeadersForCache(HttpHeaders headers) {
        List<String> stripLower = props.getStripHeaders().stream()
                .map(String::toLowerCase)
                .toList();
        return headers.entrySet().stream()
                .filter(e -> !stripLower.contains(e.getKey().toLowerCase()))
                .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
                .collect(Collectors.joining("|"));
    }

    private Map<String, List<String>> deserializeHeaders(String serialized) {
        if (serialized == null || serialized.isBlank()) return Map.of();
        return java.util.Arrays.stream(serialized.split("\\|"))
                .filter(s -> s.contains("="))
                .map(s -> s.split("=", 2))
                .collect(Collectors.toMap(
                        a -> a[0],
                        a -> java.util.Arrays.asList(a[1].split(",")),
                        (a, b) -> a));
    }

    public static class Config {
        // Reserved for future per-route overrides (e.g. custom TTL, method list).
        // Currently all knobs come from IdempotencyProperties (global).
    }
}
