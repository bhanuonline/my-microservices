package com.example.orderservice.event;

import java.time.Instant;

// Plain POJO event (Spring 4.2+: no need to extend ApplicationEvent).
// Published via ApplicationEventPublisher; received by @EventListener methods.
public record OrderCancelledEvent(
        String orderId,
        String reason,
        Instant cancelledAt
) {}
