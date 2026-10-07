package com.example.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AlgoliaApps {
    public static void main(String[] args) {
        SpringApplication.run(AlgoliaApps.class, args);
        System.out.println("Spring Boot Version: " + SpringBootVersion.getVersion());
    }
}
