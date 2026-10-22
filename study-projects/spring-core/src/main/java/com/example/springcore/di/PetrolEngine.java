package com.example.springcore.di;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class PetrolEngine implements Engine {

    @Override
    public String getType() {
        return "Petrol";
    }
}
