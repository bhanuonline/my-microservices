package com.example.apigateway.config;

import com.example.apigateway.apikey.ApiKeyAuthentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(prefix = "gateway.ratelimit", name = "enabled", havingValue = "true")
public class RateLimitConfig {

    private final RateLimitProperties props;

    public RateLimitConfig(RateLimitProperties props) {
        this.props = props;
    }

    @Bean
    @Primary
    public RedisRateLimiter redisRateLimiter() {
        RateLimitProperties.Limit d = props.getDefaults();
        return new RedisRateLimiter(
                d.getReplenishRate(),
                d.getBurstCapacity(),
                d.getRequestedTokens());
    }

    @Bean("userKeyResolver")
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(p -> "user:" + p.getName())
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    var addr = exchange.getRequest().getRemoteAddress();
                    return "anon:" + (addr != null ? addr.getAddress().getHostAddress() : "unknown");
                }));
    }

    @Bean("ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            var addr = exchange.getRequest().getRemoteAddress();
            return Mono.just("ip:" + (addr != null ? addr.getAddress().getHostAddress() : "unknown"));
        };
    }

    /**
     * Uses the AUTHENTICATED principal (ApiKeyAuthentication.keyId), not the raw header.
     * Safer for logs (no raw key), and consistent across renames.
     * Falls back to the raw header if API-key auth is disabled.
     */
    @Bean("apiKeyResolver")
    public KeyResolver apiKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(auth -> auth instanceof ApiKeyAuthentication)
                .map(auth -> "apiKey:" + ((ApiKeyAuthentication) auth).getKeyId())
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    String rawHeader = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
                    return "apiKey:" + (rawHeader != null ? rawHeader : "missing");
                }));
    }
}
