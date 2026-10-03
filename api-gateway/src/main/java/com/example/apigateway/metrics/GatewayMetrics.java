package com.example.apigateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Facade over Micrometer counters for gateway-specific business signals.
 *
 * Every method is a one-liner that increments a Counter. Micrometer memoizes
 * counters by name+tags, so repeated calls are cheap (single AtomicLong incr).
 *
 * Naming convention:
 *   gateway.<feature>.<what>          Micrometer name
 *   gateway_<feature>_<what>_total    Prometheus scrape name (dots → underscores, _total suffix on counters)
 *
 * Kept low-cardinality on purpose: outcome tags are enums (small finite set),
 * NEVER user IDs / request IDs / free-form strings — those would blow up
 * Prometheus TSDB with millions of unique series.
 */
@Component
public class GatewayMetrics {

    private static final String IDEMPOTENCY = "gateway.idempotency.result";
    private static final String RESPONSE_CACHE = "gateway.response_cache.result";
    private static final String AUDIT = "gateway.route_audit.persist";
    private static final String APIKEY = "gateway.apikey.auth";
    private static final String FALLBACK = "gateway.fallback.served";
    private static final String BODY_LOGGING = "gateway.body_logging.samples";
    private static final String CANARY_ROUTED = "gateway.canary.routed";

    private final MeterRegistry registry;

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** outcome: hit | miss | conflict | mismatch | bypassed | store_error */
    public void idempotency(String outcome) {
        Counter.builder(IDEMPOTENCY).tag("outcome", outcome).register(registry).increment();
    }

    /** outcome: hit | miss | write | skip_streaming | skip_size | store_error */
    public void responseCache(String outcome) {
        Counter.builder(RESPONSE_CACHE).tag("outcome", outcome).register(registry).increment();
    }

    /** outcome: success | failure */
    public void audit(String outcome) {
        Counter.builder(AUDIT).tag("outcome", outcome).register(registry).increment();
    }

    /** outcome: success | invalid | disabled */
    public void apiKeyAuth(String outcome) {
        Counter.builder(APIKEY).tag("outcome", outcome).register(registry).increment();
    }

    /**
     * service: user | product | order | ...  (path segment from /fallback/{service})
     * reason:  bulkhead-full | circuit-breaker-open | timeout | unknown
     */
    public void fallback(String service, String reason) {
        Counter.builder(FALLBACK)
                .tag("service", service)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    /** outcome: logged | skipped_sampled | skipped_excluded | skipped_binary */
    public void bodyLoggingSample(String outcome) {
        Counter.builder(BODY_LOGGING).tag("outcome", outcome).register(registry).increment();
    }

    /**
     * Records which canary version served a request.
     *   version: v1 | v2
     *   reason:  weight-split | header-override
     */
    public void canaryRouted(String version, String reason) {
        Counter.builder(CANARY_ROUTED)
                .tag("version", version)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }
}
