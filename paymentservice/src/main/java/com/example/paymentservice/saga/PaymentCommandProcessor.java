package com.example.paymentservice.saga;

import com.example.common.idempotency.IdempotencyGuard;
import com.example.common.saga.PaymentCommand;
import com.example.common.saga.RefundCommand;
import com.example.paymentservice.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Thin Kafka-driven entry point. Keeps the saga-level dedup guard but
 * delegates all business logic to {@link PaymentService}, which the webhook
 * controller also calls. One source of truth for the state machine.
 */
@Service
public class PaymentCommandProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandProcessor.class);
    private static final String PAYMENT_CONSUMER = "payment-service.paymentCommand";
    private static final String REFUND_CONSUMER = "payment-service.refundCommand";

    private final PaymentService paymentService;
    private final IdempotencyGuard guard;

    public PaymentCommandProcessor(PaymentService paymentService, IdempotencyGuard guard) {
        this.paymentService = paymentService;
        this.guard = guard;
    }

    public void handlePayment(PaymentCommand cmd) {
        if (!guard.claim(cmd.sagaId(), PAYMENT_CONSUMER)) {
            log.info("Skipping duplicate PaymentCommand sagaId={}", cmd.sagaId());
            return;
        }
        log.info("Processing PaymentCommand sagaId={} orderId={} amount={} provider={}",
                cmd.sagaId(), cmd.orderId(), cmd.amount(), cmd.provider());
        paymentService.handlePayment(cmd);
    }

    public void handleRefund(RefundCommand cmd) {
        if (!guard.claim(cmd.sagaId(), REFUND_CONSUMER)) {
            log.info("Skipping duplicate RefundCommand sagaId={}", cmd.sagaId());
            return;
        }
        log.warn("Executing REFUND sagaId={} orderId={} paymentId={} reason={}",
                cmd.sagaId(), cmd.orderId(), cmd.paymentId(), cmd.reason());
        paymentService.handleRefund(cmd);
    }
}
