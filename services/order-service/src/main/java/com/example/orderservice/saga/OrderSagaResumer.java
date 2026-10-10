package com.example.orderservice.saga;

import com.example.common.saga.NotifyUserCommand;
import com.example.common.saga.PaymentCommand;
import com.example.common.saga.RefundCommand;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Resumes order sagas that were in-flight when order-service last stopped.
 *
 * <h3>Why</h3>
 * {@link OrderSagaOrchestrator} persists each state transition before sending
 * the next command, but there is no in-memory handler for "service came back
 * up, keep going." Without this class, a saga wedged in STARTED / PAID /
 * COMPENSATING never terminates after a restart — it just sits there until
 * SagaInFlightStuck fires at 15 minutes.
 *
 * <h3>What it does</h3>
 * On {@link ApplicationReadyEvent} it queries non-terminal sagas older than
 * {@code stale-after} and re-fires the appropriate command for each state:
 * <ul>
 *   <li>STARTED      → re-send PaymentCommand (amount read from Order row)</li>
 *   <li>PAID         → re-send NotifyUserCommand</li>
 *   <li>COMPENSATING → re-send RefundCommand</li>
 * </ul>
 *
 * <h3>Safety design</h3>
 * <ul>
 *   <li><b>Stale-after floor.</b> Skip sagas younger than 30s so an in-flight
 *       reply still on Kafka has a chance to land first. Prevents the common
 *       race of "crash finished, reply was already on broker, but we re-sent
 *       the command anyway → double charge."</li>
 *   <li><b>Max batch.</b> Capped at {@code max-batch} (default 100) so a
 *       backlog from a long outage doesn't flood Kafka at startup.</li>
 *   <li><b>Opt-out.</b> {@code enabled=false} turns the whole thing off; useful
 *       in test contexts where you want deterministic saga state.</li>
 * </ul>
 *
 * <h3>Correctness assumption</h3>
 * Downstream command recipients (payment-service, notification) must be
 * idempotent on the saga ID. Re-sending the same PaymentCommand must not
 * cause a second charge. The project already assumes this via
 * {@code common-lib/IdempotencyGuard} — same guarantee here.
 */
@Component
public class OrderSagaResumer {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaResumer.class);

    // Binding names — must match OrderSagaOrchestrator / application.yml.
    private static final String PAYMENT_COMMANDS = "paymentCommand-out-0";
    private static final String NOTIFY_COMMANDS = "notifyUserCommand-out-0";
    private static final String REFUND_COMMANDS = "refundCommand-out-0";

    private final OrderSagaRepository sagaRepo;
    private final OrderRepository orderRepo;
    private final StreamBridge streamBridge;
    private final SagaMetrics metrics;

    // Not `final` so SagaIntegrationTest can flip `enabled` on for the resumer test
    // without rebooting the whole Spring context. Treat as effectively final at runtime.
    private boolean enabled;
    private Duration staleAfter;
    private int maxBatch;

    public OrderSagaResumer(OrderSagaRepository sagaRepo,
                            OrderRepository orderRepo,
                            StreamBridge streamBridge,
                            SagaMetrics metrics,
                            @Value("${orderservice.saga.resume.enabled:true}") boolean enabled,
                            @Value("${orderservice.saga.resume.stale-after:PT30S}") Duration staleAfter,
                            @Value("${orderservice.saga.resume.max-batch:100}") int maxBatch) {
        this.sagaRepo = sagaRepo;
        this.orderRepo = orderRepo;
        this.streamBridge = streamBridge;
        this.metrics = metrics;
        this.enabled = enabled;
        this.staleAfter = staleAfter;
        this.maxBatch = maxBatch;
    }

    /** Test seam. Not used in production. */
    void setEnabled(boolean enabled) { this.enabled = enabled; }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void resumeOnBoot() {
        if (!enabled) {
            log.info("Saga resume disabled (orderservice.saga.resume.enabled=false)");
            return;
        }

        Instant cutoff = Instant.now().minus(staleAfter);
        List<OrderSaga> stuck = sagaRepo.findNonTerminalOlderThan(cutoff, PageRequest.of(0, maxBatch));

        if (stuck.isEmpty()) {
            log.info("Saga resume: no in-flight sagas older than {}", staleAfter);
            return;
        }

        log.info("Saga resume: found {} in-flight sagas older than {} (max batch {})",
                stuck.size(), staleAfter, maxBatch);

        int resumed = 0;
        int skipped = 0;
        for (OrderSaga saga : stuck) {
            try {
                if (resumeOne(saga)) {
                    resumed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                // One bad saga must not kill the whole resume pass.
                skipped++;
                log.error("Saga resume: failed for sagaId={} state={}: {}",
                        saga.getId(), saga.getState(), e.getMessage(), e);
            }
        }

        log.info("Saga resume: complete. resumed={} skipped={}", resumed, skipped);
    }

    /** @return true if a command was re-sent, false if the saga was skipped. */
    private boolean resumeOne(OrderSaga saga) {
        switch (saga.getState()) {
            case STARTED -> {
                Order order = orderRepo.findById(saga.getOrderId()).orElse(null);
                if (order == null) {
                    log.warn("Saga resume: STARTED sagaId={} references missing orderId={}, skipping",
                            saga.getId(), saga.getOrderId());
                    return false;
                }
                BigDecimal amount = order.getAmount();
                PaymentCommand cmd = new PaymentCommand(saga.getId(), saga.getOrderId(), amount);
                streamBridge.send(PAYMENT_COMMANDS, cmd);
                metrics.resumed(OrderSaga.State.STARTED);
                log.info("Saga resume: STARTED sagaId={} → re-sent PaymentCommand (amount={})",
                        saga.getId(), amount);
                return true;
            }
            case PAID -> {
                NotifyUserCommand notify = new NotifyUserCommand(saga.getId(), saga.getOrderId(),
                        "Your order " + saga.getOrderId() + " has been paid.");
                streamBridge.send(NOTIFY_COMMANDS, notify);
                metrics.resumed(OrderSaga.State.PAID);
                log.info("Saga resume: PAID sagaId={} → re-sent NotifyUserCommand", saga.getId());
                return true;
            }
            case COMPENSATING -> {
                RefundCommand refund = new RefundCommand(saga.getId(), saga.getOrderId(),
                        saga.getPaymentId(), saga.getFailureReason());
                streamBridge.send(REFUND_COMMANDS, refund);
                metrics.resumed(OrderSaga.State.COMPENSATING);
                log.info("Saga resume: COMPENSATING sagaId={} → re-sent RefundCommand", saga.getId());
                return true;
            }
            default -> {
                // Terminal states (NOTIFIED, FAILED) are filtered out by the repo query,
                // so this branch is defensive — logs if someone ever widens the filter.
                log.debug("Saga resume: skipping sagaId={} in terminal state {}",
                        saga.getId(), saga.getState());
                return false;
            }
        }
    }
}
