package com.example.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by order-service when an order is created.
 * Consumed by payment-service, product-service (stock decrement), analytics, etc.
 *
 * eventId: idempotency key — consumers insert into processed_events; duplicate = skip.
 * BigDecimal for money — never `double` for currency.
 */
public record OrderCreatedEvent(
        UUID eventId,
        String orderId,
        Long productId,
        Integer quantity,
        BigDecimal amount,
        Instant createdAt
) {}
