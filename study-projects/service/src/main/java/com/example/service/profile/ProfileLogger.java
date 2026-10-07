package com.example.service.profile;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProfileLogger implements CommandLineRunner {

    private final Environment env;

    public ProfileLogger(Environment env) {
        this.env = env;
    }

    @Override
    public void run(String... args) {
        System.out.println("👉 Active Spring Profiles: " + String.join(", ", env.getActiveProfiles()));
    }
}