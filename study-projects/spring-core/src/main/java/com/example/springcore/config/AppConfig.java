package com.example.springcore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// @Configuration marks this as a source of bean definitions
// Spring calls @Bean methods and registers the return value as a managed bean
@Configuration
public class AppConfig {

    @Bean
    public DatabaseConfig databaseConfig() {
        return new DatabaseConfig("localhost", 5432, "study_db");
    }
}
