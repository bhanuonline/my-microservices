package com.example.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by product-service when a stock decrement crosses the low-stock threshold.
 * Consumed by notification (admin alert) and any future "reorder" automation.
 */
public record ProductStockLowEvent(
        UUID eventId,
        Long productId,
        String productName,
        int remainingStock,
        int threshold,
        Instant occurredAt
) {}
