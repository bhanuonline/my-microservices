package com.example.orderservice.consumer;

import com.example.common.event.PaymentCompletedEvent;
import com.example.common.event.PaymentFailedEvent;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotent consumer.
 *
 * The dedup trick:
 *   INSERT INTO processed_events (eventId, ...)
 *   If the event was already processed, this throws DataIntegrityViolationException
 *   because eventId is the primary key. Catch it → skip. No double-processing.
 *
 * The whole handler runs in ONE @Transactional. If the state update fails, the
 * processed_events insert also rolls back, so the message will be retried.
 */
@Service
public class PaymentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProcessor.class);
    private static final String CONSUMER_NAME = "order-service.paymentEvents";

    private final OrderRepository orderRepo;
    private final ProcessedEventRepository processedRepo;

    public PaymentEventProcessor(OrderRepository orderRepo, ProcessedEventRepository processedRepo) {
        this.orderRepo = orderRepo;
        this.processedRepo = processedRepo;
    }

    @Transactional
    public void handleCompleted(PaymentCompletedEvent event) {
        if (!claimEvent(event.eventId())) {
            log.info("Skipping duplicate PaymentCompleted eventId={}", event.eventId());
            return;
        }

        Order order = orderRepo.findById(event.orderId()).orElse(null);
        if (order == null) {
            // Order not found — possibly this event arrived before the order was persisted,
            // OR the order was deleted. In real systems, either: (a) requeue with backoff,
            // or (b) send to DLQ. For learning, log loudly.
            log.error("PaymentCompleted for unknown orderId={}", event.orderId());
            return;
        }
        order.markPaid();
        log.info("Order {} marked PAID", order.getId());
    }

    @Transactional
    public void handleFailed(PaymentFailedEvent event) {
        if (!claimEvent(event.eventId())) {
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

    /**
     * Returns true if this event is new (claim succeeded). False if already processed.
     */
    private boolean claimEvent(UUID eventId) {
        try {
            processedRepo.saveAndFlush(new ProcessedEvent(eventId, CONSUMER_NAME));
            return true;
        } catch (DataIntegrityViolationException dup) {
            return false;
        }
    }
}
