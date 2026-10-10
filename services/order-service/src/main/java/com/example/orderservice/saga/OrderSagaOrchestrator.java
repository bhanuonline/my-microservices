package com.example.orderservice.saga;

import com.example.common.saga.AuthorizePaymentCommand;
import com.example.common.saga.NotifyUserCommand;
import com.example.common.saga.NotifyUserReply;
import com.example.common.saga.PaymentAuthorizedReply;
import com.example.common.saga.PaymentCapturedReply;
import com.example.common.saga.PaymentCommand;
import com.example.common.saga.PaymentReply;
import com.example.common.saga.PaymentVoidedReply;
import com.example.common.saga.RefundCommand;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.saga.admin.SagaRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Central saga coordinator.
 *
 * All state transitions run in @Transactional methods so:
 *   - Saga state + Order state update atomically.
 *   - Next command is sent AFTER the tx commits (using StreamBridge).
 *     Note: this still has dual-write risk (same as any non-outbox publish).
 *     A production system would either use outbox pattern here too, or
 *     rely on saga replay from persisted state to recover.
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    // Binding names — align with application.yml
    private static final String PAYMENT_COMMANDS = "paymentCommand-out-0";
    private static final String NOTIFY_COMMANDS = "notifyUserCommand-out-0";
    private static final String REFUND_COMMANDS = "refundCommand-out-0";
    private static final String AUTHORIZE_COMMANDS = "authorizePaymentCommand-out-0";

    private final OrderSagaRepository sagaRepo;
    private final OrderRepository orderRepo;
    private final StreamBridge streamBridge;
    private final SagaRecorder recorder;
    private final SagaMetrics metrics;
    private final String currency;
    private final Set<String> splitCaptureProviders;

    public OrderSagaOrchestrator(OrderSagaRepository sagaRepo,
                                 OrderRepository orderRepo,
                                 StreamBridge streamBridge,
                                 SagaRecorder recorder,
                                 SagaMetrics metrics,
                                 @Value("${orderservice.saga.currency:INR}") String currency,
                                 @Value("${orderservice.saga.split-capture-providers:checkoutcom}") String splitCaptureCsv) {
        this.sagaRepo = sagaRepo;
        this.orderRepo = orderRepo;
        this.streamBridge = streamBridge;
        this.recorder = recorder;
        this.metrics = metrics;
        this.currency = currency;
        this.splitCaptureProviders = Stream.of(splitCaptureCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
        log.info("Split-capture providers configured: {}", splitCaptureProviders);
    }

    /** Kicks off the saga with the default payment provider ({@code null} → payment-service's config picks). */
    @Transactional
    public void start(String orderId, BigDecimal amount) {
        start(orderId, amount, null);
    }

    /**
     * Kicks off the saga. Called from {@code OrderService.create} and
     * {@code CheckoutController} right after the Order row is saved.
     *
     * @param provider name of the payment provider ("mock", "stripe", ...) or
     *                 null to let payment-service pick the configured default.
     * @return the newly created {@link OrderSaga} — useful for CheckoutController
     *         which needs the sagaId to echo back to the client.
     */
    @Transactional
    public OrderSaga start(String orderId, BigDecimal amount, String provider) {
        OrderSaga saga = sagaRepo.save(new OrderSaga(orderId));
        metrics.started();

        boolean splitPath = provider != null
                && splitCaptureProviders.contains(provider.toLowerCase());
        log.info("Saga {} STARTED for orderId={} provider={} splitPath={}",
                saga.getId(), orderId, provider, splitPath);

        if (splitPath) {
            // Split-capture: first command is AUTHORIZE. The saga will wait at
            // AUTHORIZED until an operator sends a CapturePaymentCommand or
            // VoidPaymentCommand (via the admin endpoints — Commit 3).
            AuthorizePaymentCommand cmd = new AuthorizePaymentCommand(
                    saga.getId(), orderId, amount, currency, provider);
            streamBridge.send(AUTHORIZE_COMMANDS, cmd);
            recorder.recordCommand(saga.getId(), "payment.authorize", cmd);
        } else {
            // Combined flow (existing behavior).
            PaymentCommand cmd = new PaymentCommand(saga.getId(), orderId, amount, provider);
            streamBridge.send(PAYMENT_COMMANDS, cmd);
            recorder.recordCommand(saga.getId(), "payment.charge", cmd);
        }
        return saga;
    }

    /** Called by consumer when payment-service replies. */
    @Transactional
    public void onPaymentReply(PaymentReply reply) {
        OrderSaga saga = sagaRepo.findById(reply.sagaId()).orElse(null);
        if (saga == null) {
            log.warn("PaymentReply for unknown sagaId={}, ignoring", reply.sagaId());
            return;
        }
        if (saga.getState() != OrderSaga.State.STARTED) {
            log.info("Saga {} already advanced past STARTED (state={}), ignoring duplicate reply",
                    saga.getId(), saga.getState());
            return;
        }

        recorder.recordReply(saga.getId(), "payment.charge", reply.success(), reply, reply.failureReason());
        if (reply.success()) {
            saga.markPaid(reply.paymentId());
            log.info("Saga {} → PAID, sending notify command", saga.getId());

            NotifyUserCommand notify = new NotifyUserCommand(saga.getId(), saga.getOrderId(),
                    "Your order " + saga.getOrderId() + " has been paid.");
            streamBridge.send(NOTIFY_COMMANDS, notify);
            recorder.recordCommand(saga.getId(), "notify.user", notify);
        } else {
            // Nothing to compensate — payment was the first step. Just fail.
            saga.markFailed("payment_failed: " + reply.failureReason());
            markOrderCancelled(saga.getOrderId(), reply.failureReason());
            metrics.terminal(SagaMetrics.OUTCOME_FAILED,
                    "payment_failed:" + reply.failureReason(), saga.getCreatedAt());
            log.info("Saga {} FAILED at payment step", saga.getId());
        }
    }

    /** Called by consumer when notification-service replies. */
    @Transactional
    public void onNotifyReply(NotifyUserReply reply) {
        OrderSaga saga = sagaRepo.findById(reply.sagaId()).orElse(null);
        if (saga == null) {
            log.warn("NotifyUserReply for unknown sagaId={}, ignoring", reply.sagaId());
            return;
        }
        if (saga.getState() != OrderSaga.State.PAID) {
            log.info("Saga {} not in PAID state (state={}), ignoring duplicate reply",
                    saga.getId(), saga.getState());
            return;
        }

        recorder.recordReply(saga.getId(), "notify.user", reply.success(), reply, reply.failureReason());
        if (reply.success()) {
            saga.markNotified();
            markOrderPaid(saga.getOrderId());
            metrics.terminal(SagaMetrics.OUTCOME_COMPLETED, null, saga.getCreatedAt());
            log.info("Saga {} COMPLETED", saga.getId());
        } else {
            // Notification failed — compensate: refund the payment.
            saga.beginCompensation("notify_failed: " + reply.failureReason());
            log.warn("Saga {} → COMPENSATING, sending refund command", saga.getId());

            RefundCommand refund = new RefundCommand(saga.getId(), saga.getOrderId(),
                    saga.getPaymentId(), reply.failureReason());
            streamBridge.send(REFUND_COMMANDS, refund);
            recorder.recordCompensation(saga.getId(), "payment.refund", refund, reply.failureReason());

            // Mark order cancelled now — the refund fires and forgets.
            // In real prod, wait for RefundReply before marking terminal.
            saga.markFailed("notify_failed_refunded");
            markOrderCancelled(saga.getOrderId(), reply.failureReason());
            metrics.terminal(SagaMetrics.OUTCOME_COMPENSATED,
                    "notify_failed:" + reply.failureReason(), saga.getCreatedAt());
        }
    }

    // ─── Split-capture reply handlers (Phase 5) ───────────────────────────

    /**
     * Called when payment-service replies to an {@link AuthorizePaymentCommand}.
     * On success → saga = AUTHORIZED, orchestrator WAITS (no next step).
     * On failure → saga = FAILED, order = CANCELLED (same tail as a declined
     *              combined payment).
     */
    @Transactional
    public void onPaymentAuthorizedReply(PaymentAuthorizedReply reply) {
        OrderSaga saga = sagaRepo.findById(reply.sagaId()).orElse(null);
        if (saga == null) {
            log.warn("PaymentAuthorizedReply for unknown sagaId={}, ignoring", reply.sagaId());
            return;
        }
        if (saga.getState() != OrderSaga.State.STARTED) {
            log.info("Saga {} not in STARTED (state={}), ignoring duplicate authorized reply",
                    saga.getId(), saga.getState());
            return;
        }
        recorder.recordReply(saga.getId(), "payment.authorize", reply.success(), reply, reply.failureReason());

        if (reply.success()) {
            // Store the Payment.id so later Capture/Void commands can target it.
            saga.markAuthorized(reply.paymentId());
            metrics.authorized();
            log.info("Saga {} → AUTHORIZED (authId={}), waiting for operator capture/void",
                    saga.getId(), reply.authId());
        } else {
            saga.markFailed("authorize_failed: " + reply.failureReason());
            markOrderCancelled(saga.getOrderId(), reply.failureReason());
            metrics.terminal(SagaMetrics.OUTCOME_FAILED,
                    "authorize_failed:" + reply.failureReason(), saga.getCreatedAt());
            log.info("Saga {} FAILED at authorize step", saga.getId());
        }
    }

    /**
     * Called when payment-service replies to a {@link
     * com.example.common.saga.CapturePaymentCommand}. On success → saga = PAID,
     * NotifyUserCommand is sent (same tail as combined-flow PaymentReply).
     * On failure → saga stays AUTHORIZED (operator can retry or void).
     */
    @Transactional
    public void onPaymentCapturedReply(PaymentCapturedReply reply) {
        OrderSaga saga = sagaRepo.findById(reply.sagaId()).orElse(null);
        if (saga == null) {
            log.warn("PaymentCapturedReply for unknown sagaId={}, ignoring", reply.sagaId());
            return;
        }
        if (saga.getState() != OrderSaga.State.AUTHORIZED) {
            log.info("Saga {} not in AUTHORIZED (state={}), ignoring duplicate captured reply",
                    saga.getId(), saga.getState());
            return;
        }
        recorder.recordReply(saga.getId(), "payment.capture", reply.success(), reply, reply.failureReason());

        if (reply.success()) {
            metrics.captured();
            // How long did the operator take to decide?
            metrics.timeToCapture(saga.getUpdatedAt());
            saga.markPaid(reply.paymentId());
            log.info("Saga {} → PAID (via split-capture), sending notify command", saga.getId());

            NotifyUserCommand notify = new NotifyUserCommand(saga.getId(), saga.getOrderId(),
                    "Your order " + saga.getOrderId() + " has been paid.");
            streamBridge.send(NOTIFY_COMMANDS, notify);
            recorder.recordCommand(saga.getId(), "notify.user", notify);
        } else {
            // Capture failed → saga stays AUTHORIZED. Operator can retry.
            log.warn("Saga {} capture FAILED (saga stays AUTHORIZED): reason={}",
                    saga.getId(), reply.failureReason());
            // Deliberately NO state transition. The alert
            // (orders.saga.capture_failed is a future metric) will surface it.
        }
    }

    /**
     * Called when payment-service replies to a {@link
     * com.example.common.saga.VoidPaymentCommand}. On success → saga = VOIDED
     * (terminal), order = CANCELLED. On failure → saga stays AUTHORIZED (rare).
     */
    @Transactional
    public void onPaymentVoidedReply(PaymentVoidedReply reply) {
        OrderSaga saga = sagaRepo.findById(reply.sagaId()).orElse(null);
        if (saga == null) {
            log.warn("PaymentVoidedReply for unknown sagaId={}, ignoring", reply.sagaId());
            return;
        }
        if (saga.getState() != OrderSaga.State.AUTHORIZED) {
            log.info("Saga {} not in AUTHORIZED (state={}), ignoring duplicate voided reply",
                    saga.getId(), saga.getState());
            return;
        }
        recorder.recordReply(saga.getId(), "payment.void", reply.success(), reply, reply.failureReason());

        if (reply.success()) {
            saga.markVoided("voided_by_operator");
            markOrderCancelled(saga.getOrderId(), "voided");
            metrics.voided();
            metrics.terminal(SagaMetrics.OUTCOME_COMPENSATED,
                    "voided_by_operator", saga.getCreatedAt());
            log.info("Saga {} → VOIDED (terminal, no money moved)", saga.getId());
        } else {
            log.warn("Saga {} void FAILED (saga stays AUTHORIZED): reason={}",
                    saga.getId(), reply.failureReason());
        }
    }

    private void markOrderPaid(String orderId) {
        Order o = orderRepo.findById(orderId).orElse(null);
        if (o != null) o.markPaid();
    }

    private void markOrderCancelled(String orderId, String reason) {
        Order o = orderRepo.findById(orderId).orElse(null);
        if (o != null) o.markCancelled(reason);
    }
}
