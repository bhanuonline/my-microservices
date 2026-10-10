package com.example.orderservice.consumer;

import com.example.common.event.PaymentCompletedEvent;
import com.example.common.event.PaymentFailedEvent;
import com.example.common.idempotency.IdempotencyGuard;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer. {@link IdempotencyGuard} runs the dedup insert in its OWN
 * transaction, so a replay cleanly returns false without poisoning this one.
 */
@Service
public class PaymentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProcessor.class);
    private static final String CONSUMER_NAME = "order-service.paymentEvents";

    private final OrderRepository orderRepo;
    private final IdempotencyGuard guard;

    public PaymentEventProcessor(OrderRepository orderRepo, IdempotencyGuard guard) {
        this.orderRepo = orderRepo;
        this.guard = guard;
    }

    @Transactional
    public void handleCompleted(PaymentCompletedEvent event) {
        if (!guard.claim(event.eventId(), CONSUMER_NAME)) {
            log.info("Skipping duplicate PaymentCompleted eventId={}", event.eventId());
            return;
        }
        Order order = orderRepo.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.error("PaymentCompleted for unknown orderId={}", event.orderId());
            return;
        }
        order.markPaid();
        log.info("Order {} marked PAID", order.getId());
    }

    @Transactional
    public void handleFailed(PaymentFailedEvent event) {
        if (!guard.claim(event.eventId(), CONSUMER_NAME)) {
            log.info("Skipping duplicate PaymentFailed eventId={}", event.eventId());
            return;
        }
        Order order = orderRepo.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.error("PaymentFailed for unknown orderId={}", event.orderId());
            return;
        }
        order.markCancelled(event.reason());
        log.info("Order {} CANCELLED reason={}", order.getId(), event.reason());
    }
}
