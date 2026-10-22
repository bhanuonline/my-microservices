package com.example.springcore.scopes;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ScopesDemo {

    // ApplicationContext lets us manually call getBean() to show scope behavior
    private final ApplicationContext ctx;

    public ScopesDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public void demonstrate() {
        System.out.println("\n=== TOPIC 2: Bean Scopes ===");

        SingletonBean s1 = ctx.getBean(SingletonBean.class);
        SingletonBean s2 = ctx.getBean(SingletonBean.class);
        System.out.println("[Singleton] call 1: " + s1.getId());
        System.out.println("[Singleton] call 2: " + s2.getId());
        System.out.println("[Singleton] same instance? " + (s1 == s2)); // true

        PrototypeBean p1 = ctx.getBean(PrototypeBean.class);
        PrototypeBean p2 = ctx.getBean(PrototypeBean.class);
        System.out.println("[Prototype] call 1: " + p1.getId());
        System.out.println("[Prototype] call 2: " + p2.getId());
        System.out.println("[Prototype] same instance? " + (p1 == p2)); // false
    }
}
