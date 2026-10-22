package com.example.springcore.aop;

import org.springframework.stereotype.Component;

@Component
public class AopDemo {

    private final BankService bankService;

    public AopDemo(BankService bankService) {
        this.bankService = bankService;
    }

    public void demonstrate() {
        System.out.println("\n=== TOPIC 8: AOP (Aspect-Oriented Programming) ===");
        System.out.println("LoggingAspect intercepts BankService without touching its code:");
        bankService.transfer("Alice", "Bob", 1000);
        System.out.println("---");
        bankService.getBalance("Alice");
    }
}
