package com.example.orderservice.batch;

import com.example.orderservice.event.OrderCancelledEvent;
import com.example.orderservice.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class StaleOrderProcessor implements ItemProcessor<Order, Order> {

    private static final Logger log = LoggerFactory.getLogger(StaleOrderProcessor.class);

    private final ApplicationEventPublisher publisher;

    public StaleOrderProcessor(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public Order process(Order order) {
        log.info("Cancelling stale order: id={} createdAt={}", order.getId(), order.getCreatedAt());
        order.markCancelled("batch-timeout");
        publisher.publishEvent(new OrderCancelledEvent(order.getId(), "batch-timeout", Instant.now()));
        return order;
    }
}
