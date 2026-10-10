package com.example.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Global post-filter: whenever the downstream pipeline (specifically
 * RequestRateLimiter) sets 429 TOO_MANY_REQUESTS with no body, we emit an
 * RFC 7807 problem+json body + a Retry-After hint so clients can self-regulate.
 *
 * Runs AFTER rate-limiter decision — HIGHEST_PRECEDENCE here means first in the
 * post-handler chain, since we need to inspect the response status set upstream.
 */
@Component
public class RateLimitProblemJsonHandler implements GlobalFilter, Ordered {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public int getOrder() {
        // Low priority — run after RequestRateLimiter has decided + written status.
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.defer(() -> {
            if (exchange.getResponse().getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) {
                return Mono.empty();
            }
            if (exchange.getResponse().isCommitted()) return Mono.empty();   // body already written

            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            if (!headers.containsKey("Retry-After")) headers.add("Retry-After", "1");

            try {
                byte[] body = mapper.writeValueAsBytes(Map.of(
                        "type", "https://errors.example/rate-limit",
                        "title", "Too Many Requests",
                        "status", 429,
                        "detail", "Rate limit exceeded. Retry after the time indicated by Retry-After.",
                        "instance", exchange.getRequest().getPath().value()
                ));
                DataBuffer buf = exchange.getResponse().bufferFactory().wrap(body);
                return exchange.getResponse().writeWith(Mono.just(buf));
            } catch (Exception e) {
                return exchange.getResponse().setComplete();
            }
        }));
    }
}
