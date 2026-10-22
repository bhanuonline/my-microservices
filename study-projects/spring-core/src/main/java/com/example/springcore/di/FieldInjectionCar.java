package com.example.springcore.di;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

// Field injection — Spring sets the field directly via reflection
// Avoid: can't make dep final, can't test without Spring context
@Component
public class FieldInjectionCar {

    @Autowired
    @Qualifier("petrolEngine")
    private Engine engine;

    public String describe() {
        return "FieldCar uses: " + engine.getType() + " engine";
    }
}
