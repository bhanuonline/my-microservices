package com.example.apigateway.apikey;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Extracts the API key from the request header and wraps it in an
 * unauthenticated token for the AuthenticationManager to validate.
 *
 * Skips (returns Mono.empty()) if:
 *   - header absent → next filter in chain (JWT) tries
 *   - prefix mismatch → treated as absent
 *   - path is in excluded-paths (actuator, fallback, etc.)
 */
public class ApiKeyAuthenticationConverter implements ServerAuthenticationConverter {

    private final ApiKeyProperties props;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public ApiKeyAuthenticationConverter(ApiKeyProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        for (String excluded : props.getExcludedPaths()) {
            if (matcher.match(excluded, path)) return Mono.empty();
        }

        String key = exchange.getRequest().getHeaders().getFirst(props.getHeaderName());
        if (key == null || key.isBlank()) return Mono.empty();
        if (props.getKeyPrefix() != null && !props.getKeyPrefix().isBlank()
                && !key.startsWith(props.getKeyPrefix())) {
            return Mono.empty();
        }

        // Unauthenticated placeholder — real validation in the manager.
        // Use the raw key as "credentials" (never logged; never leaves the manager).
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(key, key);
        token.setAuthenticated(false);
        return Mono.just(token);
    }
}
