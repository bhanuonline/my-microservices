package com.example.springcore.events;

import org.springframework.context.ApplicationEvent;

public class OrderPlacedEvent extends ApplicationEvent {

    private final String orderId;

    public OrderPlacedEvent(Object source, String orderId) {
        super(source);
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
