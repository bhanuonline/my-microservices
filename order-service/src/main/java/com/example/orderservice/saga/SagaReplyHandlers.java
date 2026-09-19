package com.example.orderservice.saga;

import com.example.common.saga.NotifyUserReply;
import com.example.common.saga.PaymentReply;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Reply-side consumers for the saga.
 * Bean names must match Cloud Stream bindings:
 *   paymentReply-in-0, notifyUserReply-in-0
 */
@Configuration
public class SagaReplyHandlers {

    @Bean
    public Consumer<PaymentReply> paymentReply(OrderSagaOrchestrator orchestrator) {
        return orchestrator::onPaymentReply;
    }

    @Bean
    public Consumer<NotifyUserReply> notifyUserReply(OrderSagaOrchestrator orchestrator) {
        return orchestrator::onNotifyReply;
    }
}
