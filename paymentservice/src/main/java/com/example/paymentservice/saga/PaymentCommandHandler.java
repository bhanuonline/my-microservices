package com.example.paymentservice.saga;

import com.example.common.saga.AuthorizePaymentCommand;
import com.example.common.saga.CapturePaymentCommand;
import com.example.common.saga.PaymentCommand;
import com.example.common.saga.RefundCommand;
import com.example.common.saga.VoidPaymentCommand;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Cloud Stream bindings. Real work delegated to {@link PaymentCommandProcessor}
 * which is @Transactional and dedupes on sagaId.
 *
 * <p>Bean names match the entries in {@code spring.cloud.function.definition}
 * in application.yml. Each binding reads from a dedicated topic (see
 * {@code spring.cloud.stream.bindings.*.destination}) and the processor
 * forwards to the right PaymentService handler.
 */
@Configuration
public class PaymentCommandHandler {

    // ─── Combined flow (Phase 1) ──────────────────────────────────────────

    @Bean
    public Consumer<PaymentCommand> paymentCommand(PaymentCommandProcessor processor) {
        return processor::handlePayment;
    }

    @Bean
    public Consumer<RefundCommand> refundCommand(PaymentCommandProcessor processor) {
        return processor::handleRefund;
    }

    // ─── Split-capture flow (Phase 5) ─────────────────────────────────────

    @Bean
    public Consumer<AuthorizePaymentCommand> authorizePaymentCommand(PaymentCommandProcessor processor) {
        return processor::handleAuthorize;
    }

    @Bean
    public Consumer<CapturePaymentCommand> capturePaymentCommand(PaymentCommandProcessor processor) {
        return processor::handleCapture;
    }

    @Bean
    public Consumer<VoidPaymentCommand> voidPaymentCommand(PaymentCommandProcessor processor) {
        return processor::handleVoid;
    }
}
