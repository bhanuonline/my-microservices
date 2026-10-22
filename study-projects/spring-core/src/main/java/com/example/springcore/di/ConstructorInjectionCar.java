package com.example.springcore.di;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

// PREFERRED style — engine is final, can't be null, easy to test
@Component
public class ConstructorInjectionCar {

    private final Engine engine;

    public ConstructorInjectionCar(Engine engine) {
        this.engine = engine;
    }

    public String describe() {
        return "ConstructorCar uses: " + engine.getType() + " engine";
    }
}
