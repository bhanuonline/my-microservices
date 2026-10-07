package com.example.backoffice;

import com.example.backoffice.config.BackendProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Operator back-office. Server-rendered Thymeleaf admin console that frames
 * every existing admin endpoint under one navigation tree. No DB; reads live
 * from backing services via their REST APIs.
 */
@SpringBootApplication
@EnableConfigurationProperties(BackendProperties.class)
public class BackofficeUiApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackofficeUiApplication.class, args);
    }
}
