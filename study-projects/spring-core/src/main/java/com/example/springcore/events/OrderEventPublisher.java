package com.example.springcore.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private final ApplicationEventPublisher publisher;

    public OrderEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void placeOrder(String orderId) {
        System.out.println("[Events] Publishing OrderPlacedEvent for: " + orderId);
        publisher.publishEvent(new OrderPlacedEvent(this, orderId));
    }
}
