package com.example.springcore.events;

import org.springframework.stereotype.Component;

@Component
public class EventsDemo {

    private final OrderEventPublisher publisher;

    public EventsDemo(OrderEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void demonstrate() {
        System.out.println("\n=== TOPIC 7: Spring Events ===");
        System.out.println("Publisher fires event → Listener reacts — no direct coupling between them:");
        publisher.placeOrder("ORD-001");
    }
}
