package com.example.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scan rules for common-lib cross-cutting entities (recurring footgun):
//
//   - com.example.common.featureflag → scanned by FeatureFlagAutoConfiguration.
//     DO NOT list it here, or @EnableJpaRepositories registers the repo twice.
//
//   - com.example.common.idempotency has TWO children:
//       .ProcessedEvent         (consumer-side dedup — WE need it via IdempotencyGuard)
//       .http.IdempotencyRecord (HTTP Idempotency-Key filter, scanned by its autoconfig)
//     Listing the parent package recursively picks up BOTH; the HTTP one then
//     clashes with IdempotencyAutoConfiguration. Fix: scan the parent but
//     explicitly exclude the .http subpackage from the JPA repo scan.
@SpringBootApplication(scanBasePackages = {
        "com.example.orderservice",
        "com.example.common.idempotency",
        "com.example.common.dlq"
})
@EnableFeignClients
@EnableScheduling
// EntityScan can harmlessly include IdempotencyRecord — JPA entities only need
// to be scanned once per EntityManagerFactory, duplicate scan = no-op.
@EntityScan(basePackages = {
        "com.example.orderservice",
        "com.example.common.outbox",
        "com.example.common.idempotency",
        "com.example.common.dlq"
})
// @EnableJpaRepositories CANNOT double-register: that triggers BeanDefinitionOverrideException.
// IdempotencyAutoConfiguration already does @EnableJpaRepositories on .idempotency.http,
// so we exclude that subpackage here.
@EnableJpaRepositories(basePackages = {
        "com.example.orderservice",
        "com.example.common.outbox",
        "com.example.common.idempotency",
        "com.example.common.dlq"
},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.example\\.common\\.idempotency\\.http\\..*"))
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}