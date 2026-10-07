package com.example.orderservice.saga;

import com.example.common.saga.NotifyUserCommand;
import com.example.common.saga.NotifyUserReply;
import com.example.common.saga.PaymentCommand;
import com.example.common.saga.PaymentReply;
import com.example.common.saga.RefundCommand;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.saga.admin.SagaRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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

    private final OrderSagaRepository sagaRepo;
    private final OrderRepository orderRepo;
    private final StreamBridge streamBridge;
    private final SagaRecorder recorder;
    private final SagaMetrics metrics;

    public OrderSagaOrchestrator(OrderSagaRepository sagaRepo,
                                 OrderRepository orderRepo,
                                 StreamBridge streamBridge,
                                 SagaRecorder recorder,
                                 SagaMetrics metrics) {
        this.sagaRepo = sagaRepo;
        this.orderRepo = orderRepo;
        this.streamBridge = streamBridge;
        this.recorder = recorder;
        this.metrics = metrics;
    }

    /** Kicks off the saga. Called from OrderService.create right after the Order row is saved. */
    @Transactional
    public void start(String orderId, BigDecimal amount) {
        OrderSaga saga = sagaRepo.save(new OrderSaga(orderId));
        metrics.started();
        log.info("Saga {} STARTED for orderId={}", saga.getId(), orderId);

        // First command: try to charge payment.
        PaymentCommand cmd = new PaymentCommand(saga.getId(), orderId, amount);
        streamBridge.send(PAYMENT_COMMANDS, cmd);
        recorder.recordCommand(saga.getId(), "payment.charge", cmd);
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

    private void markOrderPaid(String orderId) {
        Order o = orderRepo.findById(orderId).orElse(null);
        if (o != null) o.markPaid();
    }

    private void markOrderCancelled(String orderId, String reason) {
        Order o = orderRepo.findById(orderId).orElse(null);
        if (o != null) o.markCancelled(reason);
    }
}
