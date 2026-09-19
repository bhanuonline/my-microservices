package com.example.paymentservice.saga;

import com.example.common.saga.PaymentCommand;
import com.example.common.saga.PaymentReply;
import com.example.common.saga.RefundCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Consumes saga commands, replies with success/failure.
 * Same "even qty = success" rule as OrderCreatedHandler, but here we know
 * only the amount — so we succeed if amount is a "nice" number (whole $10 multiple).
 * Just enough demo logic to exercise both branches.
 */
@Configuration
public class PaymentCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandHandler.class);
    private static final String REPLY_BINDING = "paymentReply-out-0";

    @Bean
    public Consumer<PaymentCommand> paymentCommand(StreamBridge streamBridge) {
        return cmd -> {
            log.info("Processing PaymentCommand sagaId={} orderId={} amount={}",
                    cmd.sagaId(), cmd.orderId(), cmd.amount());

            // Demo rule: succeed if amount < $50, fail otherwise. Predictable.
            boolean success = cmd.amount().compareTo(new java.math.BigDecimal("50")) < 0;

            PaymentReply reply;
            if (success) {
                reply = new PaymentReply(cmd.sagaId(), cmd.orderId(), true,
                        null, UUID.randomUUID().toString());
            } else {
                reply = new PaymentReply(cmd.sagaId(), cmd.orderId(), false,
                        "amount_over_limit", null);
            }
            streamBridge.send(REPLY_BINDING, reply);
        };
    }

    @Bean
    public Consumer<RefundCommand> refundCommand() {
        return cmd -> {
            log.warn("Executing REFUND sagaId={} orderId={} paymentId={} reason={}",
                    cmd.sagaId(), cmd.orderId(), cmd.paymentId(), cmd.reason());
            // Real code: call payment gateway refund API, persist refund record.
        };
    }
}
