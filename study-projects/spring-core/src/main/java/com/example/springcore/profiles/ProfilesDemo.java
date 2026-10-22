package com.example.springcore.profiles;

import org.springframework.stereotype.Component;

@Component
public class ProfilesDemo {

    private final DataSource dataSource;

    public ProfilesDemo(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void demonstrate() {
        System.out.println("\n=== TOPIC 6: @Profile ===");
        System.out.println("Active DataSource: " + dataSource.getUrl());
        System.out.println("Switch profile: spring.profiles.active=prod in application.properties");
    }
}
