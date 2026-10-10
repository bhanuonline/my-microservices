package com.example.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by product-service when an order would drive stock below zero.
 * Consumed by notification (admin alert) and, in a real system, by order-service
 * to cancel/refund the order (that compensation isn't wired yet).
 */
public record ProductStockInsufficientEvent(
        UUID eventId,
        String orderId,
        Long productId,
        String productName,
        int requested,
        int available,
        Instant occurredAt
) {}
