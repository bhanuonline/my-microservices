package com.example.apigateway.responsecache;

import com.example.apigateway.responsecache.ResponseCacheStore.CachedEntry;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Captures 2xx GET/HEAD responses coming back from downstream and persists them
 * to Redis via ResponseCacheStore. Fallback controller reads them back when the
 * CircuitBreaker opens.
 *
 * Order = -10 → runs before the response goes to the client, but AFTER
 * BodyLoggingGlobalFilter (-20). Sits inside the reactive filter pipeline so it
 * can wrap the ServerHttpResponse with a decorator that intercepts writeWith().
 */
@Component
@ConditionalOnProperty(prefix = "gateway.response-cache", name = "enabled", havingValue = "true")
public class ResponseCacheGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ResponseCacheGlobalFilter.class);

    private final ResponseCacheStore store;
    private final ResponseCacheProperties props;
    private final com.example.apigateway.metrics.GatewayMetrics metrics;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public ResponseCacheGlobalFilter(ResponseCacheStore store,
                                     ResponseCacheProperties props,
                                     com.example.apigateway.metrics.GatewayMetrics metrics) {
        this.store = store;
        this.props = props;
        this.metrics = metrics;
    }

    @Override
    public int getOrder() {
        return -10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!isCacheableRequest(exchange)) return chain.filter(exchange);

        ServerHttpResponse original = exchange.getResponse();
        DataBufferFactory bufferFactory = original.bufferFactory();
        String routeId = routeId(exchange);
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getURI().getPath();
        String query = exchange.getRequest().getURI().getQuery();
        String originalUrl = exchange.getRequest().getURI().toString();

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                HttpStatusCode status = getStatusCode();
                if (!isCacheableResponse(status, getHeaders())) {
                    return super.writeWith(body);
                }
                if (!(body instanceof Flux<? extends DataBuffer> fluxBody)) {
                    return super.writeWith(body);
                }
                return DataBufferUtils.join(fluxBody).flatMap(joined -> {
                    byte[] content = new byte[joined.readableByteCount()];
                    joined.read(content);
                    DataBufferUtils.release(joined);

                    if (content.length > props.getMaxCachedBytes()) {
                        log.debug("Skipping cache — response too large: {} > {} bytes",
                                content.length, props.getMaxCachedBytes());
                        metrics.responseCache("skip_size");
                        return super.writeWith(Mono.just(bufferFactory.wrap(content)));
                    }

                    CachedEntry entry = new CachedEntry(
                            status.value(),
                            Base64.getEncoder().encodeToString(content),
                            serializeHeaders(getHeaders()),
                            Instant.now(),
                            originalUrl);

                    return store.save(routeId, method != null ? method.name() : "GET", path, query,
                                    entry, props.ttlFor(routeId))
                            .doOnSuccess(v -> metrics.responseCache("write"))
                            .onErrorResume(err -> {
                                log.warn("Failed to cache response: {}", err.getMessage());
                                metrics.responseCache("store_error");
                                return Mono.empty();
                            })
                            .then(super.writeWith(Mono.just(bufferFactory.wrap(content))));
                });
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMapSequential(p -> p));
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build());
    }

    private boolean isCacheableRequest(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == null || !props.getCacheableMethods().contains(method.name())) return false;

        String path = exchange.getRequest().getURI().getPath();
        for (String excluded : props.getExcludedPaths()) {
            if (matcher.match(excluded, path)) return false;
        }
        return true;
    }

    private boolean isCacheableResponse(HttpStatusCode status, HttpHeaders headers) {
        if (status == null || !status.is2xxSuccessful()) return false;

        String cacheControl = headers.getFirst(HttpHeaders.CACHE_CONTROL);
        if (cacheControl != null) {
            String lower = cacheControl.toLowerCase();
            if (lower.contains("no-store") || lower.contains("private")) return false;
        }

        String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) return false;
        String lowerCt = contentType.toLowerCase();
        for (String allowed : props.getCacheableContentTypes()) {
            if (lowerCt.startsWith(allowed.toLowerCase())) return true;
        }
        return false;
    }

    private String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route != null ? route.getId() : "unknown";
    }

    private String serializeHeaders(HttpHeaders headers) {
        List<String> stripLower = props.getStripHeaders().stream()
                .map(String::toLowerCase)
                .toList();
        return headers.entrySet().stream()
                .filter(e -> !stripLower.contains(e.getKey().toLowerCase()))
                .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
                .collect(Collectors.joining("|"));
    }

    /** Public helper — same shape used by FallbackController to rehydrate headers. */
    public static Map<String, List<String>> deserializeHeaders(String serialized) {
        if (serialized == null || serialized.isBlank()) return Map.of();
        return java.util.Arrays.stream(serialized.split("\\|"))
                .filter(s -> s.contains("="))
                .map(s -> s.split("=", 2))
                .collect(Collectors.toMap(
                        a -> a[0],
                        a -> java.util.Arrays.asList(a[1].split(",")),
                        (a, b) -> a));
    }
}
