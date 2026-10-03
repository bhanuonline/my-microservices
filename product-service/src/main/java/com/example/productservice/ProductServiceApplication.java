package com.example.productservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.example.productservice", "com.example.common.idempotency"})
@EnableCaching
@EnableJpaAuditing
@EnableScheduling
@EntityScan(basePackages = {"com.example.productservice", "com.example.common.outbox", "com.example.common.idempotency"})
@EnableJpaRepositories(basePackages = {"com.example.productservice", "com.example.common.outbox", "com.example.common.idempotency"})
public class ProductServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProductServiceApplication.class, args);
		System.out.println("Spring Boot Version: " + SpringBootVersion.getVersion());
	}

}
