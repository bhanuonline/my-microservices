package com.example.springcore.lifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class LifecycleBean {

    public LifecycleBean() {
        System.out.println("[Lifecycle] 1. Constructor called");
    }

    @PostConstruct
    // runs after Spring injects all dependencies into this bean
    public void init() {
        System.out.println("[Lifecycle] 2. @PostConstruct — bean is ready to use");
    }

    @PreDestroy
    // runs just before Spring removes this bean from the context (on shutdown)
    public void cleanup() {
        System.out.println("[Lifecycle] 3. @PreDestroy — cleaning up resources");
    }
}
