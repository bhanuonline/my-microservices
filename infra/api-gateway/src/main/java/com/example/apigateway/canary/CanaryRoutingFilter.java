package com.example.apigateway.canary;

import com.example.apigateway.metrics.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Tags each request with which canary version served it and adds a debug header
 * to the response so clients can see the routing decision.
 *
 *   Response header: X-Canary-Route = v1 | v2 | v2-override
 *   Metric:           gateway.canary.routed{version, reason}
 *
 * The actual traffic split is done by Spring Cloud Gateway's built-in Weight
 * predicate (see application.yml). This filter is observational only — it looks
 * at which route matched AFTER selection and records the outcome.
 *
 * Order: runs late (post-routing) so the matched Route attribute is populated.
 * We use +10 to run after most filters but still let downstream see the header
 * (headers can be added right up until writeWith actually flushes).
 */
@Component
@EnableConfigurationProperties(CanaryProperties.class)
@ConditionalOnProperty(prefix = "gateway.canary", name = "enabled", havingValue = "true")
public class CanaryRoutingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CanaryRoutingFilter.class);

    private final CanaryProperties props;
    private final GatewayMetrics metrics;

    public CanaryRoutingFilter(CanaryProperties props, GatewayMetrics metrics) {
        this.props = props;
        this.metrics = metrics;
    }

    @Override
    public int getOrder() {
        // Run AFTER RouteToRequestUrlFilter (which is order 10_000) so exchange
        // has a matched Route. Very late is fine because we only read state.
        return 10_001;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }
        String routeId = route.getId();
        if (routeId == null || (!routeId.contains("-v1") && !routeId.contains("-v2"))) {
            // Not a canary-annotated route → skip
            return chain.filter(exchange);
        }

        String version = routeId.contains("-v2") ? "v2" : "v1";
        boolean overrideHit = props.getOverrideValue().equalsIgnoreCase(
                exchange.getRequest().getHeaders().getFirst(props.getOverrideHeader()));
        String reason = overrideHit ? "header-override" : "weight-split";
        String label = overrideHit && "v2".equals(version) ? "v2-override" : version;

        exchange.getResponse().getHeaders().add(props.getResponseHeader(), label);
        metrics.canaryRouted(version, reason);
        log.debug("Canary routed → route_id={} version={} reason={}", routeId, version, reason);

        return chain.filter(exchange);
    }
}
