package com.example.springcore.di;

import org.springframework.stereotype.Component;

@Component
public class DiDemo {

    private final ConstructorInjectionCar constructorCar;
    private final SetterInjectionCar setterCar;
    private final FieldInjectionCar fieldCar;

    public DiDemo(ConstructorInjectionCar constructorCar, SetterInjectionCar setterCar, FieldInjectionCar fieldCar) {
        this.constructorCar = constructorCar;
        this.setterCar = setterCar;
        this.fieldCar = fieldCar;
    }

    public void demonstrate() {
        System.out.println("\n=== TOPIC 1: Dependency Injection ===");
        System.out.println("[Constructor] " + constructorCar.describe());
        System.out.println("[Setter]      " + setterCar.describe());
        System.out.println("[Field]       " + fieldCar.describe());
        System.out.println("Rule: prefer constructor injection — deps are final and always present.");
    }
}
