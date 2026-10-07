package com.example.orderservice.saga;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Business-level metrics for the order saga.
 *
 * Framework metrics (http.server.requests, kafka.consumer.*) tell you the
 * saga's plumbing is alive. They don't tell you whether sagas finish, or
 * why they fail. This class surfaces that:
 *
 *   orders.saga.started         Counter  — one per start()
 *   orders.saga.terminal        Counter  — tagged {outcome, reason}
 *   orders.saga.duration        Timer    — STARTED → terminal, p50/p95/p99
 *   orders.saga.resumed         Counter  — tagged {state}, one per command
 *                                           re-fired by OrderSagaResumer
 *
 * Duration is measured from OrderSaga.createdAt (persisted), so it stays
 * accurate even if the service restarted mid-saga.
 */
@Component
public class SagaMetrics {

    public static final String OUTCOME_COMPLETED   = "completed";
    public static final String OUTCOME_FAILED      = "failed";
    public static final String OUTCOME_COMPENSATED = "compensated";

    private final MeterRegistry registry;
    private final Counter started;
    // Timer per outcome, built lazily so each one gets its own histogram.
    private final ConcurrentMap<String, Timer> durationByOutcome = new ConcurrentHashMap<>();

    public SagaMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.started = Counter.builder("orders.saga.started")
                .description("Number of order sagas started")
                .register(registry);
    }

    public void started() {
        started.increment();
    }

    /**
     * Record a terminal transition.
     *
     * @param outcome   one of OUTCOME_* constants
     * @param reason    short failure/compensation reason; use "ok" for success
     * @param startedAt saga.createdAt — ground truth, survives restarts
     */
    public void terminal(String outcome, String reason, Instant startedAt) {
        String safeReason = (reason == null || reason.isBlank()) ? "ok" : truncate(reason);

        Counter.builder("orders.saga.terminal")
                .description("Terminal saga outcomes")
                .tag("outcome", outcome)
                .tag("reason", safeReason)
                .register(registry)
                .increment();

        if (startedAt != null) {
            durationByOutcome
                    .computeIfAbsent(outcome, this::buildDurationTimer)
                    .record(Duration.between(startedAt, Instant.now()));
        }
    }

    /**
     * Record that OrderSagaResumer re-fired the next command for a saga that
     * was stuck in {@code state} at boot. Separate from started() so dashboards
     * can tell first-time starts apart from boot-time retries.
     */
    public void resumed(OrderSaga.State state) {
        Counter.builder("orders.saga.resumed")
                .description("Sagas whose next command was re-fired by OrderSagaResumer on boot")
                .tag("state", state.name())
                .register(registry)
                .increment();
    }

    private Timer buildDurationTimer(String outcome) {
        return Timer.builder("orders.saga.duration")
                .description("End-to-end saga duration from STARTED to terminal state")
                .tag("outcome", outcome)
                .publishPercentileHistogram()  // enables p95/p99 + exemplar linking
                .register(registry);
    }

    // Keep the 'reason' label bounded; prevents an unbounded cardinality blowup
    // if a downstream ever returns a reason containing an id or timestamp.
    private static String truncate(String reason) {
        if (reason.length() <= 48) return reason;
        return reason.substring(0, 48);
    }
}
