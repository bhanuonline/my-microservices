package com.statemachine.controller;

import com.statemachine.MyOrderStates;
import com.statemachine.OrderEvents;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateMachine;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private StateMachine<MyOrderStates, OrderEvents> stateMachine;
    private boolean started = false;

    @GetMapping("/state")
    public String getState() {
        startIfNeeded();
        return "Current state: " + stateMachine.getState().getId();
    }

    @PostMapping("/event/{event}")
    public String sendEvent(@PathVariable String event) {
        try {
            OrderEvents ev = OrderEvents.valueOf(event.toUpperCase());
            stateMachine.sendEvent(ev);
            return "Event '" + event + "' sent. New state: " + stateMachine.getState().getId();
        } catch (IllegalArgumentException ex) {
            return "Unknown event: " + event + ". Valid events: START, PAYMENT_OK, STOCK_RESERVED";
        }
    }

    private void startIfNeeded() {
        if (!started) {
            stateMachine.start();
            started = true;
        }
    }
}