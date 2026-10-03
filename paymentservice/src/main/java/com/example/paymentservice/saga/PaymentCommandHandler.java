package com.example.paymentservice.saga;

import com.example.common.saga.PaymentCommand;
import com.example.common.saga.RefundCommand;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Cloud Stream bindings. Real work delegated to {@link PaymentCommandProcessor}
 * which is @Transactional and dedupes on sagaId. Both PaymentCommand and
 * RefundCommand now emit a PaymentReply so the saga can advance.
 */
@Configuration
public class PaymentCommandHandler {

    @Bean
    public Consumer<PaymentCommand> paymentCommand(PaymentCommandProcessor processor) {
        return processor::handlePayment;
    }

    @Bean
    public Consumer<RefundCommand> refundCommand(PaymentCommandProcessor processor) {
        return processor::handleRefund;
    }
}
