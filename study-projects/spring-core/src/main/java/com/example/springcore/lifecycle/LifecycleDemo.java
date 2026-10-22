package com.example.springcore.lifecycle;

import org.springframework.stereotype.Component;

@Component
public class LifecycleDemo {

    public void demonstrate() {
        System.out.println("\n=== TOPIC 3: Bean Lifecycle ===");
        System.out.println("Order: Constructor → (deps injected) → @PostConstruct → [use] → @PreDestroy");
        System.out.println("(LifecycleBean already fired Constructor + @PostConstruct above at startup)");
        System.out.println("Watch for @PreDestroy when the app shuts down.");
    }
}
