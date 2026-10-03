package com.example.apigateway;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Injects an X-Correlation-Id into every request/response pair and publishes
 * it into the Reactor Context under the key `correlationId`.
 *
 * MDC integration:
 *   Spring Boot 3 + Micrometer Context Propagation lift Reactor Context values
 *   into MDC automatically at log-emission time — so if you put a value into
 *   the Reactor Context (via .contextWrite(...)), it appears in JSON logs via
 *   the MDC keys listed in logback-spring.xml WITHOUT direct MDC.put() calls.
 *
 *   Direct MDC.put() in doOnEach() (the old approach) is fragile because
 *   Reactor freely hops threads; MDC is ThreadLocal. contextWrite + the
 *   Micrometer bridge is the correct reactive pattern.
 *
 * Ordering: HIGHEST_PRECEDENCE + a small offset so it runs BEFORE Spring
 * Security, BodyLogging, and everything else — so downstream filters see the
 * correlation id both in headers and in the reactive context.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : incoming;

        // Downstream services see the same header (TokenRelay etc. forward it).
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.header(HEADER, correlationId))
                .build();

        // Response echo — clients can correlate their request with our logs.
        mutated.getResponse().getHeaders().set(HEADER, correlationId);

        // Publish into Reactor Context. Micrometer's context-propagation bridge
        // reads this at log-emission time and puts it into MDC on whichever
        // thread actually formats the log line — no thread hopping issues.
        return chain.filter(mutated)
                .contextWrite(ctx -> ctx.put(MDC_KEY, correlationId))
                // Belt-and-suspenders MDC put/clear: helps loggers on the
                // subscription thread even when context propagation isn't
                // fully wired for a particular library.
                .doOnSubscribe(sub -> MDC.put(MDC_KEY, correlationId))
                .doFinally(sig -> MDC.remove(MDC_KEY));
    }
}
