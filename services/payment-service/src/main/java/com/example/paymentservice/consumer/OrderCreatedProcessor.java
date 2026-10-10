package com.example.paymentservice.consumer;

import com.example.common.event.OrderCreatedEvent;
import com.example.common.event.PaymentCompletedEvent;
import com.example.common.event.PaymentFailedEvent;
import com.example.common.idempotency.IdempotencyGuard;
import com.example.paymentservice.event.PaymentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderCreatedProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedProcessor.class);
    private static final String CONSUMER_NAME = "payment-service.orderCreated";

    private final PaymentEventPublisher publisher;
    private final IdempotencyGuard guard;

    public OrderCreatedProcessor(PaymentEventPublisher publisher, IdempotencyGuard guard) {
        this.publisher = publisher;
        this.guard = guard;
    }

    @Transactional
    public void handle(OrderCreatedEvent event) {
        if (!guard.claim(event.eventId(), CONSUMER_NAME)) {
            log.info("Skipping duplicate OrderCreated eventId={} orderId={}", event.eventId(), event.orderId());
            return;
        }

        log.info("Processing payment orderId={} amount={}", event.orderId(), event.amount());

        if (event.quantity() % 2 == 0) {
            publisher.publishCompleted(new PaymentCompletedEvent(
                    UUID.randomUUID(), event.orderId(), UUID.randomUUID().toString(),
                    event.amount(), Instant.now()));
        } else {
            publisher.publishFailed(new PaymentFailedEvent(
                    UUID.randomUUID(), event.orderId(), "insufficient_funds", Instant.now()));
        }
    }
}
