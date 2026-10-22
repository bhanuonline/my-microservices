package com.example.springcore.events;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    // Spring calls this automatically when an OrderPlacedEvent is published
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        System.out.println("[Events] Listener got event → order=" + event.getOrderId()
                + " → sending confirmation email");
    }
}
