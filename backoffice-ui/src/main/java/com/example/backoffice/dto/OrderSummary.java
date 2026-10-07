package com.example.backoffice.dto;

import java.time.Instant;

/** List-row view of an order. Mapped from either ES OrderDoc or gateway. */
public record OrderSummary(
        String id,
        String status,
        String amount,
        Long productId,
        Instant createdAt
) {}
