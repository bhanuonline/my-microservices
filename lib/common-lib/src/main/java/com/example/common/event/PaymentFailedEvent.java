package com.example.common.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        String orderId,
        String reason,
        Instant failedAt
) {}
