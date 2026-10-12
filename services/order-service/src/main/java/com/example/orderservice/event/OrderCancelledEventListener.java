package com.example.orderservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class OrderCancelledEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledEventListener.class);

    // @Async("eventExecutor"): listener runs on the eventExecutor thread pool,
    // not on the batch thread — so the batch job moves on immediately after publishEvent().
    @Async("eventExecutor")
    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.warn("[{}] ORDER CANCELLED — id={} reason={} at={}",
                Thread.currentThread().getName(),
                event.orderId(), event.reason(), event.cancelledAt());
        // Extend here: send a notification command to Kafka, update analytics, etc.
    }
}
