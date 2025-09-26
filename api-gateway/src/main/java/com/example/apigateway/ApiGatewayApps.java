package com.example.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class ApiGatewayApps {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApps.class, args);
        System.out.println("Spring Boot Version: " + SpringBootVersion.getVersion());
    }
}
