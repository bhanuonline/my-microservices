package com.example.apigateway.config;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class RateLimitErrorHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String cid = exchange.getResponse().getHeaders().getFirst("X-Correlation-Id");
            String body = "{\"error\":\"rate_limited\","
                    + "\"message\":\"Too many requests, slow down.\","
                    + "\"correlationId\":\"" + (cid != null ? cid : "") + "\"}";
            DataBuffer buf = exchange.getResponse().bufferFactory().wrap(body.getBytes());
            return exchange.getResponse().writeWith(Mono.just(buf));
        }
        return Mono.error(ex);
    }
}
