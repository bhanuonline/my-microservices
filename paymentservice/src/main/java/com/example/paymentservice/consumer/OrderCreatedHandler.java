package com.example.paymentservice.consumer;

import com.example.common.event.OrderCreatedEvent;
import com.example.common.event.PaymentCompletedEvent;
import com.example.common.event.PaymentFailedEvent;
import com.example.paymentservice.event.PaymentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Simulated payment processor.
 * Rule for the demo: even quantities succeed, odd quantities "fail".
 * (Lets you exercise both branches with predictable inputs.)
 */
@Configuration
public class OrderCreatedHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedHandler.class);

    @Autowired
    private PaymentEventPublisher publisher;

    @Bean
    public Consumer<OrderCreatedEvent> orderCreated() {
        return event -> {
            log.info("Processing payment for orderId={} amount={}", event.orderId(), event.amount());

            if (event.quantity() % 2 == 0) {
                publisher.publishCompleted(new PaymentCompletedEvent(
                        UUID.randomUUID(),
                        event.orderId(),
                        UUID.randomUUID().toString(),
                        event.amount(),
                        Instant.now()
                ));
            } else {
                publisher.publishFailed(new PaymentFailedEvent(
                        UUID.randomUUID(),
                        event.orderId(),
                        "insufficient_funds",
                        Instant.now()
                ));
            }
        };
    }
}
