package com.example.orderservice.dto;

import java.time.Instant;

public record OrderResponse(
        String orderId,
        Long productId,
        Integer quantity,
        String status,
        Instant createdAt
) {}
