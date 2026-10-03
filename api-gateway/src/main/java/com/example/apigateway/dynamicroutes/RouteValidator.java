package com.example.apigateway.dynamicroutes;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Validates a RouteDefinition BEFORE it reaches the DB — a bad row would sit
 * forever in the H2 table, silently failing every RefreshRoutesEvent.
 *
 * Rules:
 *   1. id present + non-blank
 *   2. uri parseable + scheme in the accepted set (lb, http, https, ws, wss, forward, no)
 *   3. at least one predicate
 *   4. every predicate NAME resolves to a registered RoutePredicateFactory bean
 *   5. every filter NAME resolves to a registered GatewayFilterFactory bean
 *
 * Does NOT re-execute the filter/predicate configuration binding — Spring
 * Cloud Gateway will do that at route-build time. If args are wrong shape,
 * that failure will surface on RefreshRoutesEvent (usually acceptable).
 */
@Component
@ConditionalOnProperty(prefix = "gateway.dynamic-routes", name = "enabled", havingValue = "true")
public class RouteValidator {

    private static final List<String> ACCEPTED_SCHEMES = List.of(
            "lb", "http", "https", "ws", "wss", "forward", "no"
    );

    private final Map<String, RoutePredicateFactory<?>> predicateFactories;
    private final Map<String, GatewayFilterFactory<?>> filterFactories;

    public RouteValidator(List<RoutePredicateFactory<?>> predicateFactories,
                          List<GatewayFilterFactory<?>> filterFactories) {
        // Factory beans use bean names like `PathRoutePredicateFactory` — strip the suffix
        // to get the YAML shortcut name (Path, RewritePath, RequestRateLimiter, ...).
        this.predicateFactories = predicateFactories.stream().collect(Collectors.toMap(
                RouteValidator::simpleName, Function.identity(), (a, b) -> a));
        this.filterFactories = filterFactories.stream().collect(Collectors.toMap(
                RouteValidator::simpleName, Function.identity(), (a, b) -> a));
    }

    public void validate(RouteDefinition def) throws ValidationException {
        if (def == null) throw new ValidationException("route body is null");

        if (def.getId() == null || def.getId().isBlank()) {
            throw new ValidationException("id is required");
        }
        if (!def.getId().matches("[a-zA-Z0-9._:\\-]{1,64}")) {
            throw new ValidationException("id must match [a-zA-Z0-9._:-]{1,64}");
        }

        URI uri = def.getUri();
        if (uri == null) throw new ValidationException("uri is required");
        String scheme = uri.getScheme();
        if (scheme == null || !ACCEPTED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new ValidationException("uri scheme '" + scheme + "' not in " + ACCEPTED_SCHEMES);
        }

        List<PredicateDefinition> predicates = def.getPredicates();
        if (predicates == null || predicates.isEmpty()) {
            throw new ValidationException("at least one predicate is required");
        }
        for (PredicateDefinition p : predicates) {
            String name = p.getName();
            if (name == null || name.isBlank()) {
                throw new ValidationException("predicate name is required");
            }
            if (!predicateFactories.containsKey(name)) {
                throw new ValidationException("unknown predicate '" + name +
                        "' — available: " + predicateFactories.keySet());
            }
        }

        List<FilterDefinition> filters = def.getFilters();
        if (filters != null) {
            for (FilterDefinition f : filters) {
                String name = f.getName();
                if (name == null || name.isBlank()) {
                    throw new ValidationException("filter name is required");
                }
                if (!filterFactories.containsKey(name)) {
                    throw new ValidationException("unknown filter '" + name +
                            "' — available: " + filterFactories.keySet());
                }
            }
        }
    }

    /** URI parse helper — used to reject un-parseable URIs at controller time. */
    public static URI parseUri(String uri) throws ValidationException {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new ValidationException("uri parse error: " + e.getMessage());
        }
    }

    private static String simpleName(Object factory) {
        String cls = factory.getClass().getSimpleName();
        // e.g. "PathRoutePredicateFactory" → "Path"
        //      "RequestRateLimiterGatewayFilterFactory" → "RequestRateLimiter"
        if (cls.endsWith("RoutePredicateFactory")) {
            return cls.substring(0, cls.length() - "RoutePredicateFactory".length());
        }
        if (cls.endsWith("GatewayFilterFactory")) {
            return cls.substring(0, cls.length() - "GatewayFilterFactory".length());
        }
        return cls;
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String msg) { super(Objects.requireNonNull(msg)); }
    }
}
