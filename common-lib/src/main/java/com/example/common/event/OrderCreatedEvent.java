package com.example.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by order-service when an order is created.
 * Consumed by payment-service (and any future consumers — analytics, inventory, etc).
 *
 * Contract note: BigDecimal for money — never `double` for currency.
 * Rounding errors in floating-point are a real bug in payment systems.
 */
public record OrderCreatedEvent(
        String orderId,
        Long productId,
        Integer quantity,
        BigDecimal amount,
        Instant createdAt
) {}
