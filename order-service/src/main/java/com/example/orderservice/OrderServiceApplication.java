package com.example.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.example.orderservice",
        "com.example.common.idempotency",
        "com.example.common.dlq",
        "com.example.common.featureflag"
})
@EnableFeignClients
@EnableScheduling
@EntityScan(basePackages = {
        "com.example.orderservice",
        "com.example.common.outbox",
        "com.example.common.idempotency",
        "com.example.common.dlq",
        "com.example.common.featureflag"
})
@EnableJpaRepositories(basePackages = {
        "com.example.orderservice",
        "com.example.common.outbox",
        "com.example.common.idempotency",
        "com.example.common.dlq",
        "com.example.common.featureflag"
})
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}