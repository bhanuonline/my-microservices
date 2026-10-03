package com.example.apigateway.config;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties.class)
@ConditionalOnProperty(prefix = "gateway.circuitbreaker", name = "enabled", havingValue = "true")
public class CircuitBreakerCustomization {

    /**
     * Registry holding SemaphoreBulkhead instances keyed by name (same names
     * as the CircuitBreakers). Exposed as a bean so Micrometer auto-registers
     * resilience4j_bulkhead_* metrics for Prometheus.
     */
    @Bean
    public BulkheadRegistry bulkheadRegistry(CircuitBreakerProperties props) {
        BulkheadRegistry registry = BulkheadRegistry.of(bhConfig(props.getDefaults()));
        props.getInstances().forEach((name, inst) ->
                registry.bulkhead(name, bhConfig(inst)));
        return registry;
    }

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> cbCustomizer(
            CircuitBreakerProperties props,
            BulkheadRegistry bulkheadRegistry) {
        return factory -> {
            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(cbConfig(props.getDefaults()))
                    .timeLimiterConfig(tlConfig(props.getDefaults()))
                    .build());

            props.getInstances().forEach((name, inst) ->
                    factory.configure(builder -> builder
                            .circuitBreakerConfig(cbConfig(inst))
                            .timeLimiterConfig(tlConfig(inst))
                            .build(), name));
        };
    }

    private CircuitBreakerConfig cbConfig(CircuitBreakerProperties.Instance i) {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(i.getSlidingWindowSize())
                .minimumNumberOfCalls(i.getMinimumNumberOfCalls())
                .failureRateThreshold(i.getFailureRateThreshold())
                .slowCallRateThreshold(i.getSlowCallRateThreshold())
                .slowCallDurationThreshold(i.getSlowCallDurationThreshold())
                .waitDurationInOpenState(i.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(i.getPermittedCallsInHalfOpenState())
                // Bulkhead rejections are gateway self-protection, not downstream failure.
                // Counting them would cascade — bulkhead saturation opens the CB.
                .ignoreExceptions(BulkheadFullException.class)
                .build();
    }

    private TimeLimiterConfig tlConfig(CircuitBreakerProperties.Instance i) {
        return TimeLimiterConfig.custom()
                .timeoutDuration(i.getSlowCallDurationThreshold())
                .build();
    }

    private BulkheadConfig bhConfig(CircuitBreakerProperties.Instance i) {
        return BulkheadConfig.custom()
                .maxConcurrentCalls(i.getBulkhead().getMaxConcurrentCalls())
                .maxWaitDuration(i.getBulkhead().getMaxWaitDuration())
                .build();
    }
}
