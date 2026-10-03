package com.example.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by product-service on every create / update / delete of a Product.
 * Consumed by:
 *   - product-query  (Elasticsearch read model — ProductDoc upsert / delete)
 *   - analytics, cache invalidators, downstream BFFs, …
 *
 * eventId: idempotency key — consumers claim via IdempotencyGuard.
 * deleted = true  → consumers remove the doc from their projections.
 * price   = BigDecimal — never float / double for money.
 *
 * category/brand (Phase 4): nullable keyword fields the Elasticsearch read
 * model aggregates on for faceted navigation (sidebar filters). Older events
 * written before Phase 4 won't carry them — consumers must tolerate null.
 */
public record ProductUpdatedEvent(
        UUID eventId,
        Long productId,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String category,
        String brand,
        boolean deleted,
        Instant updatedAt
) {}
