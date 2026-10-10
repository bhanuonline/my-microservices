package com.example.shop;

import com.example.shop.config.BackendProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Server-rendered storefront (Thymeleaf + HTMX + Bootstrap). Talks to:
 *   - product-query  (ES)             listing + facets + search
 *   - product-service (via gateway)   PDP + Redis-cached point lookups
 *   - order-service   (via gateway)   checkout POST with Idempotency-Key
 *
 * No DB of its own — the shopping cart lives in HttpSession. Horizontal
 * scale needs Spring Session + Redis later.
 */
@SpringBootApplication
@EnableConfigurationProperties(BackendProperties.class)
public class ShopUiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopUiApplication.class, args);
    }
}
