package com.example.paymentservice.saga;

import com.example.common.idempotency.IdempotencyGuard;
import com.example.common.saga.PaymentCommand;
import com.example.common.saga.PaymentReply;
import com.example.common.saga.RefundCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Idempotent saga command handler. Keyed on sagaId. Payment and Refund use
 * different consumer names so a RefundCommand for a sagaId that already saw
 * a PaymentCommand still processes (different keys).
 */
@Service
public class PaymentCommandProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandProcessor.class);
    private static final String PAYMENT_CONSUMER = "payment-service.paymentCommand";
    private static final String REFUND_CONSUMER = "payment-service.refundCommand";
    private static final String REPLY_BINDING = "paymentReply-out-0";

    private final StreamBridge streamBridge;
    private final IdempotencyGuard guard;

    public PaymentCommandProcessor(StreamBridge streamBridge, IdempotencyGuard guard) {
        this.streamBridge = streamBridge;
        this.guard = guard;
    }

    @Transactional
    public void handlePayment(PaymentCommand cmd) {
        if (!guard.claim(cmd.sagaId(), PAYMENT_CONSUMER)) {
            log.info("Skipping duplicate PaymentCommand sagaId={}", cmd.sagaId());
            return;
        }
        log.info("Processing PaymentCommand sagaId={} orderId={} amount={}",
                cmd.sagaId(), cmd.orderId(), cmd.amount());

        boolean success = cmd.amount().compareTo(new BigDecimal("50")) < 0;
        PaymentReply reply = success
                ? new PaymentReply(cmd.sagaId(), cmd.orderId(), true, null, UUID.randomUUID().toString())
                : new PaymentReply(cmd.sagaId(), cmd.orderId(), false, "amount_over_limit", null);
        streamBridge.send(REPLY_BINDING, reply);
    }

    @Transactional
    public void handleRefund(RefundCommand cmd) {
        if (!guard.claim(cmd.sagaId(), REFUND_CONSUMER)) {
            log.info("Skipping duplicate RefundCommand sagaId={}", cmd.sagaId());
            return;
        }
        log.warn("Executing REFUND sagaId={} orderId={} paymentId={} reason={}",
                cmd.sagaId(), cmd.orderId(), cmd.paymentId(), cmd.reason());

        PaymentReply reply = new PaymentReply(
                cmd.sagaId(), cmd.orderId(), true, "refunded:" + cmd.reason(), cmd.paymentId());
        streamBridge.send(REPLY_BINDING, reply);
    }
}
