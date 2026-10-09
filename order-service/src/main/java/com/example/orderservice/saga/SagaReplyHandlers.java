package com.example.orderservice.saga;

import com.example.common.saga.NotifyUserReply;
import com.example.common.saga.PaymentAuthorizedReply;
import com.example.common.saga.PaymentCapturedReply;
import com.example.common.saga.PaymentReply;
import com.example.common.saga.PaymentVoidedReply;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Reply-side consumers for the saga. Bean names must match Cloud Stream
 * bindings in application.yml (<name>-in-0).
 *
 * <p>Combined flow (Phase 1): paymentReply, notifyUserReply.
 * <p>Split-capture flow (Phase 5): paymentAuthorizedReply,
 * paymentCapturedReply, paymentVoidedReply.
 */
@Configuration
public class SagaReplyHandlers {

    // ─── Combined flow (Phase 1) ──────────────────────────────────────────

    @Bean
    public Consumer<PaymentReply> paymentReply(OrderSagaOrchestrator orchestrator) {
        return orchestrator::onPaymentReply;
    }

    @Bean
    public Consumer<NotifyUserReply> notifyUserReply(OrderSagaOrchestrator orchestrator) {
        return orchestrator::onNotifyReply;
    }

    // ─── Split-capture flow (Phase 5) ─────────────────────────────────────

    @Bean
    public Consumer<PaymentAuthorizedReply> paymentAuthorizedReply(OrderSagaOrchestrator orchestrator) {
        return orchestrator::onPaymentAuthorizedReply;
    }

    @Bean
    public Consumer<PaymentCapturedReply> paymentCapturedReply(OrderSagaOrchestrator orchestrator) {
        return orchestrator::onPaymentCapturedReply;
    }

    @Bean
    public Consumer<PaymentVoidedReply> paymentVoidedReply(OrderSagaOrchestrator orchestrator) {
        return orchestrator::onPaymentVoidedReply;
    }
}
