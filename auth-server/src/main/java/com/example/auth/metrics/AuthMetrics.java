package com.example.auth.metrics;

import com.example.auth.config.FeatureFlags;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Custom Prometheus counters for the events that matter to security dashboards
 * and alerting rules. Everything lives at {@code GET /actuator/prometheus}
 * (in addition to Spring's automatic HTTP + JVM metrics).
 *
 * <p>Counters exposed:
 * <table>
 *   <tr><th>Name</th><th>Tags</th><th>When it increments</th></tr>
 *   <tr><td>auth_tokens_issued_total</td><td>grant, client_id</td>
 *       <td>Every JWT issued (any grant)</td></tr>
 *   <tr><td>auth_login_attempts_total</td><td>result (SUCCESS/FAIL)</td>
 *       <td>Every /login POST outcome</td></tr>
 *   <tr><td>auth_lockouts_total</td><td>(none)</td>
 *       <td>Feature 5 lock fires (5th failed attempt)</td></tr>
 *   <tr><td>auth_rate_limit_denials_total</td><td>endpoint</td>
 *       <td>Feature 6 returns 429</td></tr>
 *   <tr><td>auth_key_rotations_total</td><td>(none)</td>
 *       <td>Feature 3 rotate() called</td></tr>
 * </table>
 *
 * <p>Tag cardinality rules (the trap most people fall into):
 * <ul>
 *   <li><b>SAFE:</b> bounded sets — client_id (thousands max), grant type
 *       (~4 values), endpoint (small enum), result (3 values).</li>
 *   <li><b>NEVER:</b> unbounded — username, IP address, user_agent. Each
 *       unique tag combination creates a new time series in Prometheus.
 *       Add a username tag and you can OOM your monitoring by loading
 *       10,000 login attempts.</li>
 * </ul>
 *
 * <p>Feature flag: when {@code features.metrics.enabled=false}, every method
 * short-circuits — no counters are even registered. Spring's default metrics
 * (http_server_requests, jvm_memory, hikaricp_connections) still flow because
 * that's Spring Boot autoconfig, not us.
 */
@Component
@Profile("jdbc")
public class AuthMetrics {

    private final MeterRegistry registry;
    private final FeatureFlags flags;

    public AuthMetrics(MeterRegistry registry, FeatureFlags flags) {
        this.registry = registry;
        this.flags = flags;
    }

    public void tokenIssued(String grantType, String clientId) {
        if (!on()) return;
        Counter.builder("auth.tokens.issued")
                .description("JWTs issued per grant type + client")
                .tag("grant", nz(grantType))
                .tag("client_id", nz(clientId))
                .register(registry)
                .increment();
    }

    public void loginAttempt(String result) {
        if (!on()) return;
        Counter.builder("auth.login.attempts")
                .description("Login attempts by outcome")
                .tag("result", nz(result))
                .register(registry)
                .increment();
    }

    public void lockoutFired() {
        if (!on()) return;
        Counter.builder("auth.lockouts")
                .description("Total account lockouts triggered")
                .register(registry)
                .increment();
    }

    public void rateLimitDenied(String endpoint) {
        if (!on()) return;
        Counter.builder("auth.rate_limit.denials")
                .description("Requests rejected by rate limiter")
                .tag("endpoint", nz(endpoint))
                .register(registry)
                .increment();
    }

    public void keyRotated() {
        if (!on()) return;
        Counter.builder("auth.key.rotations")
                .description("Signing key rotations performed")
                .register(registry)
                .increment();
    }

    private boolean on() { return flags.getMetrics().isEnabled(); }
    private static String nz(String s) { return s == null || s.isBlank() ? "unknown" : s; }
}
