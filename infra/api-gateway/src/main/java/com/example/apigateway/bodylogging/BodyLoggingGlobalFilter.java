package com.example.apigateway.bodylogging;

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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Buffers request + response bodies, redacts sensitive content, and emits a
 * single structured audit log per request.
 *
 * Order = -20 so it runs after CorrelationIdWebFilter (so traceId/correlationId
 * are in MDC) but before any body-consuming filter (RequestFingerprint at -1).
 *
 * Buffering strategy:
 *   Request  → DataBufferUtils.join → hash+redact → ServerHttpRequestDecorator replays bytes
 *   Response → ServerHttpResponseDecorator captures writeWith → same pattern
 *
 * Every filter path releases DataBuffers explicitly (Netty pooled = must release).
 */
@Component
@ConditionalOnProperty(prefix = "gateway.body-logging", name = "enabled", havingValue = "true")
public class BodyLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(BodyLoggingGlobalFilter.class);
    private static final String AUDIT_ATTR = "gateway.body-logging.audit";
    private static final String START_ATTR = "gateway.body-logging.start";

    private final BodyLoggingProperties props;
    private final BodyRedactor redactor;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final SecureRandom random = new SecureRandom();

    public BodyLoggingGlobalFilter(BodyLoggingProperties props, BodyRedactor redactor) {
        this.props = props;
        this.redactor = redactor;
    }

    @Override
    public int getOrder() {
        return -20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!shouldLog(exchange)) return chain.filter(exchange);

        AuditEvent event = initEvent(exchange);
        exchange.getAttributes().put(AUDIT_ATTR, event);
        exchange.getAttributes().put(START_ATTR, Instant.now());

        return maybeBufferRequest(exchange, event)
                .flatMap(mutated -> chain.filter(decorateResponse(mutated, event)))
                .doFinally(sig -> emit(exchange, event));
    }

    private boolean shouldLog(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        for (String excluded : props.getExcludedPaths()) {
            if (matcher.match(excluded, path)) return false;
        }
        return random.nextDouble() < props.getSampleRate();
    }

    private AuditEvent initEvent(ServerWebExchange exchange) {
        ServerHttpRequest req = exchange.getRequest();
        AuditEvent e = new AuditEvent();
        e.setCorrelationId(req.getHeaders().getFirst("X-Correlation-Id"));
        e.setMethod(req.getMethod() != null ? req.getMethod().name() : "UNKNOWN");
        e.setPath(req.getURI().getPath());
        e.setQueryString(req.getURI().getQuery());
        e.setUserAgent(req.getHeaders().getFirst(HttpHeaders.USER_AGENT));
        if (req.getRemoteAddress() != null) {
            e.setClientIp(req.getRemoteAddress().getAddress().getHostAddress());
        }
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null) e.setRouteId(route.getId());
        return e;
    }

    private Mono<ServerWebExchange> maybeBufferRequest(ServerWebExchange exchange, AuditEvent event) {
        String contentType = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        long contentLength = exchange.getRequest().getHeaders().getContentLength();

        if (contentLength == 0) {
            return Mono.just(exchange);
        }
        if (!isLoggableContentType(contentType)) {
            event.setRequestBody("<binary or non-text content, type=" + contentType + ">");
            return Mono.just(exchange);
        }

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    event.setRequestBody(truncateAndRedact(new String(bytes, StandardCharsets.UTF_8)));
                    return decorateRequestWithBody(exchange, bytes);
                })
                .defaultIfEmpty(exchange);
    }

    private ServerWebExchange decorateRequestWithBody(ServerWebExchange exchange, byte[] bytes) {
        ServerHttpRequest decorated = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                DataBuffer replay = exchange.getResponse().bufferFactory().wrap(bytes);
                return Flux.just(replay);
            }
        };
        return exchange.mutate().request(decorated).build();
    }

    private ServerWebExchange decorateResponse(ServerWebExchange exchange, AuditEvent event) {
        ServerHttpResponse original = exchange.getResponse();
        DataBufferFactory factory = original.bufferFactory();

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (!shouldLogResponseBody(getStatusCode())) {
                    return super.writeWith(body);
                }
                String contentType = getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                if (!isLoggableContentType(contentType)) {
                    event.setResponseBody("<binary or non-text content, type=" + contentType + ">");
                    return super.writeWith(body);
                }
                if (body instanceof Flux<? extends DataBuffer> fluxBody) {
                    return DataBufferUtils.join(fluxBody).flatMap(joined -> {
                        byte[] content = new byte[joined.readableByteCount()];
                        joined.read(content);
                        DataBufferUtils.release(joined);
                        event.setResponseBody(truncateAndRedact(new String(content, StandardCharsets.UTF_8)));
                        return super.writeWith(Mono.just(factory.wrap(content)));
                    });
                }
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMapSequential(p -> p));
            }
        };
        return exchange.mutate().response(decorated).build();
    }

    private boolean shouldLogResponseBody(HttpStatusCode statusCode) {
        if (!props.getResponse().isEnabled()) return false;
        if (statusCode == null) return true;
        var classes = props.getResponse().getStatusClasses();
        if (classes == null || classes.isEmpty()) return true;
        String prefix = String.valueOf(statusCode.value() / 100);
        for (String cls : classes) {
            if (cls != null && !cls.isBlank() && cls.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isLoggableContentType(String contentType) {
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        for (String allowed : props.getLoggableContentTypes()) {
            if (lower.startsWith(allowed.toLowerCase())) return true;
        }
        return false;
    }

    private String truncateAndRedact(String body) {
        if (body == null) return null;
        String truncated = body.length() > props.getMaxBodyBytes()
                ? body.substring(0, props.getMaxBodyBytes()) + "...[TRUNCATED]"
                : body;
        return redactor.redact(truncated);
    }

    private void emit(ServerWebExchange exchange, AuditEvent event) {
        Instant start = exchange.getAttribute(START_ATTR);
        if (start != null) {
            event.setDurationMs(Duration.between(start, Instant.now()).toMillis());
        }
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        if (statusCode != null) event.setStatus(statusCode.value());

        log.info("audit correlationId={} route={} method={} path={} query={} status={} durationMs={} clientIp={} requestBody={} responseBody={}",
                event.getCorrelationId(),
                event.getRouteId(),
                event.getMethod(),
                event.getPath(),
                event.getQueryString(),
                event.getStatus(),
                event.getDurationMs(),
                event.getClientIp(),
                event.getRequestBody(),
                event.getResponseBody());
    }
}
