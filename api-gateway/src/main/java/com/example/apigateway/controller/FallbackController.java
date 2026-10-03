package com.example.apigateway.controller;

import com.example.apigateway.bulkhead.BulkheadGatewayFilterFactory;
import com.example.apigateway.metrics.GatewayMetrics;
import com.example.apigateway.responsecache.ResponseCacheGlobalFilter;
import com.example.apigateway.responsecache.ResponseCacheProperties;
import com.example.apigateway.responsecache.ResponseCacheStore;
import com.example.apigateway.responsecache.ResponseCacheStore.CachedEntry;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Circuit-breaker fallback endpoint.
 *
 * For GET/HEAD: tries ResponseCacheStore first (stale-while-CB-open pattern).
 *   Hit  → replay cached body + Warning: 110 + X-Cache: STALE headers
 *   Miss → static 503 JSON
 *
 * For everything else (POST/PUT/DELETE/PATCH): always static 503.
 *
 * ResponseCacheStore is optional (ObjectProvider) — when
 * gateway.response-cache.enabled=false, we fall back to pure static behavior.
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    private final ObjectProvider<ResponseCacheStore> storeProvider;
    private final ObjectProvider<ResponseCacheProperties> propsProvider;
    private final GatewayMetrics metrics;

    public FallbackController(ObjectProvider<ResponseCacheStore> storeProvider,
                              ObjectProvider<ResponseCacheProperties> propsProvider,
                              GatewayMetrics metrics) {
        this.storeProvider = storeProvider;
        this.propsProvider = propsProvider;
        this.metrics = metrics;
    }

    @RequestMapping(
            value = "/fallback/{service}",
            method = { RequestMethod.GET, RequestMethod.HEAD, RequestMethod.POST,
                       RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH })
    public Mono<Void> fallback(@PathVariable String service, ServerWebExchange exchange) {
        ResponseCacheStore store = storeProvider.getIfAvailable();
        ResponseCacheProperties props = propsProvider.getIfAvailable();

        String reason = resolveReason(exchange);
        exchange.getResponse().getHeaders().add("X-Fallback-Reason", reason);
        metrics.fallback(service, reason);

        HttpMethod originalMethod = originalMethod(exchange);
        String originalPath = originalPath(exchange);
        String originalQuery = originalQuery(exchange);
        String routeId = deriveRouteId(service);

        boolean cacheEligible = store != null
                && originalMethod != null
                && (HttpMethod.GET.equals(originalMethod) || HttpMethod.HEAD.equals(originalMethod));

        if (!cacheEligible) {
            return writeStatic503(exchange, service, reason);
        }

        return store.lookup(routeId, originalMethod.name(), originalPath, originalQuery)
                .flatMap(entry -> {
                    metrics.responseCache("hit");
                    return replayStale(exchange, entry, props);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    metrics.responseCache("miss");
                    return writeStatic503(exchange, service, reason);
                }))
                .onErrorResume(err -> {
                    log.warn("Cache lookup failed, serving static 503: {}", err.getMessage());
                    metrics.responseCache("store_error");
                    return writeStatic503(exchange, service, reason);
                });
    }

    /**
     * Best-effort classification of why the fallback fired. Spring Cloud Gateway
     * stashes the triggering Throwable in an exchange attribute (attr key varies
     * across versions — we scan generously). BulkheadGatewayFilterFactory also
     * sets a dedicated boolean attribute for reliable detection.
     */
    private String resolveReason(ServerWebExchange exchange) {
        if (Boolean.TRUE.equals(exchange.getAttribute(BulkheadGatewayFilterFactory.BULKHEAD_FULL_ATTR))) {
            return "bulkhead-full";
        }
        Throwable cause = findCause(exchange);
        if (cause instanceof BulkheadFullException) return "bulkhead-full";
        if (cause instanceof CallNotPermittedException) return "circuit-breaker-open";
        if (cause instanceof TimeoutException) return "timeout";
        return "unknown";
    }

    private Throwable findCause(ServerWebExchange exchange) {
        for (Map.Entry<String, Object> e : exchange.getAttributes().entrySet()) {
            if (e.getValue() instanceof Throwable t && e.getKey().toLowerCase().contains("exception")) {
                return t;
            }
        }
        return null;
    }

    private Mono<Void> replayStale(ServerWebExchange exchange, CachedEntry entry,
                                    ResponseCacheProperties props) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(HttpStatusCode.valueOf(entry.status()));

        Map<String, List<String>> headers = ResponseCacheGlobalFilter.deserializeHeaders(entry.headers());
        headers.forEach((k, v) -> resp.getHeaders().put(k, v));

        if (props == null || props.isStaleWarning()) {
            resp.getHeaders().add("Warning", "110 - \"Response is stale\"");
            resp.getHeaders().add("X-Cache", "STALE");
            long age = Duration.between(entry.cachedAt(), Instant.now()).toSeconds();
            resp.getHeaders().add("X-Cache-Age", String.valueOf(age));
            if (entry.originalUrl() != null && !entry.originalUrl().isBlank()) {
                resp.getHeaders().add("X-Cache-Original-Url", entry.originalUrl());
            }
        }

        byte[] body = Base64.getDecoder().decode(entry.body());
        DataBuffer buf = resp.bufferFactory().wrap(body);
        return resp.writeWith(Mono.just(buf));
    }

    private Mono<Void> writeStatic503(ServerWebExchange exchange, String service, String reason) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String cid = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
        String body = "{"
                + "\"error\":\"service_unavailable\","
                + "\"service\":\"" + service + "\","
                + "\"reason\":\"" + reason + "\","
                + "\"message\":\"" + service + " is temporarily unavailable, please retry later\","
                + "\"correlationId\":\"" + (cid != null ? cid : "") + "\","
                + "\"timestamp\":\"" + Instant.now() + "\""
                + "}";
        DataBuffer buf = resp.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return resp.writeWith(Mono.just(buf));
    }

    private HttpMethod originalMethod(ServerWebExchange exchange) {
        // On CB fallback, request is forwarded — original method preserved on the exchange
        HttpMethod current = exchange.getRequest().getMethod();
        return current;   // forward:/fallback/... preserves the original method
    }

    private String originalPath(ServerWebExchange exchange) {
        URI original = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (original != null) return original.getPath();
        // Fallback: current URI after forward
        return exchange.getRequest().getURI().getPath();
    }

    private String originalQuery(ServerWebExchange exchange) {
        URI original = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (original != null) return original.getQuery();
        return exchange.getRequest().getURI().getQuery();
    }

    /** Convention: /fallback/users → user-service, /fallback/products → product-service. */
    private String deriveRouteId(String service) {
        if (service == null) return "unknown";
        return service.endsWith("-service") || service.endsWith("service")
                ? service
                : (service.endsWith("s") ? service.substring(0, service.length() - 1) : service) + "-service";
    }
}
