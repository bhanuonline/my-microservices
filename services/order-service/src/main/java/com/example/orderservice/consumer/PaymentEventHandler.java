package com.example.orderservice.consumer;

import com.example.common.event.PaymentCompletedEvent;
import com.example.common.event.PaymentFailedEvent;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Consumer;

@Configuration
public class PaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);

    // Handlers must be @Bean methods returning Consumer<T>. Real work is delegated
    // to @Transactional service methods below to give us DB transactions per event.

    @Bean
    public Consumer<PaymentCompletedEvent> paymentCompleted(PaymentEventProcessor processor) {
        return processor::handleCompleted;
    }

    @Bean
    public Consumer<PaymentFailedEvent> paymentFailed(PaymentEventProcessor processor) {
        return processor::handleFailed;
    }
}
