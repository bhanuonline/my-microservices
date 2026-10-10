package com.example.paymentservice.saga;

import com.example.common.idempotency.IdempotencyGuard;
import com.example.common.saga.AuthorizePaymentCommand;
import com.example.common.saga.CapturePaymentCommand;
import com.example.common.saga.PaymentCommand;
import com.example.common.saga.RefundCommand;
import com.example.common.saga.VoidPaymentCommand;
import com.example.paymentservice.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Thin Kafka-driven entry point. Keeps the saga-level dedup guard but
 * delegates all business logic to {@link PaymentService}, which the webhook
 * controller also calls. One source of truth for the state machine.
 *
 * <h3>Idempotency keys</h3>
 * {@link IdempotencyGuard} de-dupes on {@code (sagaId, consumerName)}.
 * Each handler uses a <b>distinct</b> consumer name so an
 * {@code AuthorizePaymentCommand} and a later {@code CapturePaymentCommand}
 * for the same sagaId don't collide — they're different operations on the
 * same saga, both must be processed.
 */
@Service
public class PaymentCommandProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandProcessor.class);
    private static final String PAYMENT_CONSUMER   = "payment-service.paymentCommand";
    private static final String REFUND_CONSUMER    = "payment-service.refundCommand";
    private static final String AUTHORIZE_CONSUMER = "payment-service.authorizePaymentCommand";
    private static final String CAPTURE_CONSUMER   = "payment-service.capturePaymentCommand";
    private static final String VOID_CONSUMER      = "payment-service.voidPaymentCommand";

    private final PaymentService paymentService;
    private final IdempotencyGuard guard;

    public PaymentCommandProcessor(PaymentService paymentService, IdempotencyGuard guard) {
        this.paymentService = paymentService;
        this.guard = guard;
    }

    // ─── Combined-flow (Phase 1) ──────────────────────────────────────────

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

    // ─── Split-capture flow (Phase 5) ─────────────────────────────────────

    public void handleAuthorize(AuthorizePaymentCommand cmd) {
        if (!guard.claim(cmd.sagaId(), AUTHORIZE_CONSUMER)) {
            log.info("Skipping duplicate AuthorizePaymentCommand sagaId={}", cmd.sagaId());
            return;
        }
        log.info("Processing AuthorizePaymentCommand sagaId={} orderId={} amount={} provider={}",
                cmd.sagaId(), cmd.orderId(), cmd.amount(), cmd.provider());
        paymentService.handleAuthorize(cmd);
    }

    public void handleCapture(CapturePaymentCommand cmd) {
        if (!guard.claim(cmd.sagaId(), CAPTURE_CONSUMER)) {
            log.info("Skipping duplicate CapturePaymentCommand sagaId={}", cmd.sagaId());
            return;
        }
        log.info("Processing CapturePaymentCommand sagaId={} paymentId={} reason={}",
                cmd.sagaId(), cmd.paymentId(), cmd.reason());
        paymentService.handleCapture(cmd);
    }

    public void handleVoid(VoidPaymentCommand cmd) {
        if (!guard.claim(cmd.sagaId(), VOID_CONSUMER)) {
            log.info("Skipping duplicate VoidPaymentCommand sagaId={}", cmd.sagaId());
            return;
        }
        log.warn("Processing VoidPaymentCommand sagaId={} paymentId={} reason={}",
                cmd.sagaId(), cmd.paymentId(), cmd.reason());
        paymentService.handleVoid(cmd);
    }
}
