package com.example.apigateway.apikey;

import com.example.apigateway.metrics.GatewayMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates the raw API key (attached to the unauthenticated token by the
 * converter) against Redis via ApiKeyStore. On success, produces a fully-
 * populated ApiKeyAuthentication with scope-based authorities.
 */
@Component
@ConditionalOnProperty(prefix = "gateway.apikey", name = "enabled", havingValue = "true")
public class ApiKeyReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final ApiKeyStore store;
    private final GatewayMetrics metrics;

    public ApiKeyReactiveAuthenticationManager(ApiKeyStore store, GatewayMetrics metrics) {
        this.store = store;
        this.metrics = metrics;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        Object credentials = authentication.getCredentials();
        if (!(credentials instanceof String rawKey) || rawKey.isBlank()) {
            metrics.apiKeyAuth("invalid");
            return Mono.error(new BadCredentialsException("Empty API key"));
        }

        return store.lookup(rawKey)
                .map(rec -> {
                    List<SimpleGrantedAuthority> authorities = (rec.getScopes() == null
                            ? List.<String>of()
                            : rec.getScopes()).stream()
                            .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                            .collect(Collectors.toList());
                    metrics.apiKeyAuth("success");
                    return (Authentication) new ApiKeyAuthentication(
                            rec.getId(),
                            rec.getOwnerId(),
                            rec.getPrefix(),
                            authorities);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    metrics.apiKeyAuth("invalid");
                    return Mono.error(new BadCredentialsException("Invalid or expired API key"));
                }));
    }
}
