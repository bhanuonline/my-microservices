package com.example.springcore.di;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

// Setter injection — useful when the dependency is optional
@Component
public class SetterInjectionCar {


    private Engine engine;

    @Autowired
    public void setEngine(@Qualifier("dieselEngine") Engine engine) {
        this.engine = engine;
    }

    public String describe() {
        return "SetterCar uses: " + engine.getType() + " engine";
    }
}
