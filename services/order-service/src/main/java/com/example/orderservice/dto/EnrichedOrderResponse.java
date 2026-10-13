package com.example.orderservice.dto;

import java.time.Instant;

// Extended response returned by GET /api/v1/orders/{id}/enriched.
// Combines order data + product availability — fetched in parallel.
public record EnrichedOrderResponse(
        String orderId,
        Long productId,
        Integer quantity,
        String status,
        Instant createdAt,
        String productAvailability   // fetched from product-service in parallel
) {}
