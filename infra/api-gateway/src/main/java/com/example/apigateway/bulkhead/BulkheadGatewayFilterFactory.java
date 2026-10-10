package com.example.apigateway.bulkhead;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Wraps the downstream call with a Resilience4j SemaphoreBulkhead.
 *
 * When the bulkhead is saturated:
 *   - maxWaitDuration = 0  → reject immediately with BulkheadFullException
 *   - maxWaitDuration > 0  → wait up to that duration for a permit
 *
 * The BulkheadFullException is propagated. When paired with a CircuitBreaker
 * filter (listed AFTER this one in `filters:`), the CB catches the exception
 * and routes to its fallback URI. FallbackController maps the cause to
 * X-Fallback-Reason.
 *
 * YAML — list Bulkhead BEFORE CircuitBreaker so it gates the CB path too:
 *   filters:
 *     - Bulkhead=userCB
 *     - name: CircuitBreaker
 *       args: { name: userCB, fallbackUri: forward:/fallback/users }
 *
 * Convention: bulkhead name == circuit-breaker name (so metrics + logs align).
 */
@Component
@ConditionalOnBean(BulkheadRegistry.class)
public class BulkheadGatewayFilterFactory
        extends AbstractGatewayFilterFactory<BulkheadGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(BulkheadGatewayFilterFactory.class);
    public static final String BULKHEAD_FULL_ATTR = "gateway.bulkhead.full";

    private final BulkheadRegistry registry;

    public BulkheadGatewayFilterFactory(BulkheadRegistry registry) {
        super(Config.class);
        this.registry = registry;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("name");
    }

    @Override
    public GatewayFilter apply(Config config) {
        Bulkhead bulkhead = registry.bulkhead(config.getName());
        log.info("Bulkhead '{}' initialized: maxConcurrent={} maxWait={}",
                config.getName(),
                bulkhead.getBulkheadConfig().getMaxConcurrentCalls(),
                bulkhead.getBulkheadConfig().getMaxWaitDuration());

        return (exchange, chain) ->
                // Mono.defer + transformDeferred → BulkheadOperator acquires a permit
                // BEFORE the chain is invoked. Without defer, chain.filter() runs eagerly
                // and the bulkhead only wraps the completion signal (no gating).
                Mono.defer(() -> chain.filter(exchange))
                        .transformDeferred(BulkheadOperator.of(bulkhead))
                        .onErrorResume(BulkheadFullException.class, ex -> {
                            log.debug("Bulkhead '{}' full — rejecting", config.getName());
                            exchange.getAttributes().put(BULKHEAD_FULL_ATTR, Boolean.TRUE);
                            return Mono.error(ex);
                        });
    }

    public static class Config {
        /** Bulkhead instance name — should match the CircuitBreaker name for the route. */
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
