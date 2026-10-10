package com.example.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Hashes (method | path | userId | body?) into a header sent downstream.
 * Useful for dedup, audit trails, idempotency-key derivation.
 *
 * Fast path — include-body: false. Zero buffering, zero perf cost.
 * Slow path — include-body: true. Body is joined into a single DataBuffer,
 *   hashed, then replayed via a ServerHttpRequestDecorator so downstream
 *   still sees the original payload.
 *
 * Order = -1 so it runs BEFORE any body-modifying filter that might
 * consume the body first.
 */
@Component
public class RequestFingerprintGatewayFilterFactory
        extends AbstractGatewayFilterFactory<RequestFingerprintGatewayFilterFactory.Config> {

    public RequestFingerprintGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("headerName");
    }

    @Override
    public GatewayFilter apply(Config config) {
        GatewayFilter filter = (exchange, chain) -> {
            if (!config.isIncludeBody()) {
                String fp = hash(fingerprintInput(exchange, ""), config.getAlgorithm());
                ServerWebExchange mutated = exchange.mutate()
                        .request(r -> r.header(config.getHeaderName(), fp))
                        .build();
                return chain.filter(mutated);
            }

            return DataBufferUtils.join(exchange.getRequest().getBody())
                    .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                    .flatMap(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);

                        String body = new String(bytes, StandardCharsets.UTF_8);
                        String fp = hash(fingerprintInput(exchange, body), config.getAlgorithm());

                        ServerHttpRequest decorated = new ServerHttpRequestDecorator(exchange.getRequest()) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                DataBuffer replayed = exchange.getResponse().bufferFactory().wrap(bytes);
                                return Flux.just(replayed);
                            }
                        };

                        ServerWebExchange mutated = exchange.mutate()
                                .request(decorated)
                                .build();
                        mutated.getRequest().mutate()
                                .header(config.getHeaderName(), fp)
                                .build();
                        return chain.filter(mutated);
                    });
        };

        return new OrderedGatewayFilter(filter, -1);
    }

    private String fingerprintInput(ServerWebExchange exchange, String body) {
        var req = exchange.getRequest();
        String method = req.getMethod() != null ? req.getMethod().name() : "UNKNOWN";
        String path = req.getURI().getPath();
        String user = req.getHeaders().getFirst("X-User") != null
                ? req.getHeaders().getFirst("X-User")
                : "anon";
        return method + "|" + path + "|" + user + "|" + body;
    }

    private String hash(String input, String algo) {
        try {
            byte[] digest = MessageDigest.getInstance(algo)
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unknown digest algorithm: " + algo, e);
        }
    }

    public static class Config {
        private String headerName = "X-Request-Fingerprint";
        private boolean includeBody = false;
        private String algorithm = "SHA-256";

        public String getHeaderName() { return headerName; }
        public void setHeaderName(String headerName) { this.headerName = headerName; }

        public boolean isIncludeBody() { return includeBody; }
        public void setIncludeBody(boolean includeBody) { this.includeBody = includeBody; }

        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    }
}
