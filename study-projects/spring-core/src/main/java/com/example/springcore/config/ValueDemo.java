package com.example.springcore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ValueDemo {

    // @Value reads from application.properties (or env vars / config server)
    @Value("${app.name}")
    private String appName;

    @Value("${app.version}")
    private String version;

    @Value("${app.max-connections}")
    private int maxConnections;

    private final DatabaseConfig dbConfig;

    public ValueDemo(DatabaseConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public void demonstrate() {
        System.out.println("\n=== TOPIC 5: @Configuration & @Value ===");
        System.out.println("[@Value] app.name        = " + appName);
        System.out.println("[@Value] app.version     = " + version);
        System.out.println("[@Value] max-connections = " + maxConnections);
        System.out.println("[@Bean]  DatabaseConfig  = " + dbConfig);
    }
}
