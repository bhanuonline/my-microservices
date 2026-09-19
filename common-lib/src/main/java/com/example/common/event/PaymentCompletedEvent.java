package com.example.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by payment-service when a payment succeeds.
 * eventId is the idempotency key — consumers dedupe on it.
 */
public record PaymentCompletedEvent(
        UUID eventId,
        String orderId,
        String paymentId,
        BigDecimal amount,
        Instant completedAt
) {}
