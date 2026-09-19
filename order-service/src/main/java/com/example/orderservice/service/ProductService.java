package com.example.orderservice.service;

import com.example.orderservice.client.ProductClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductClient productClient;

    public ProductService(ProductClient productClient) {
        this.productClient = productClient;
    }

    @CircuitBreaker(name = "productClient", fallbackMethod = "checkFallback")
    public String checkAvailability(Long productId) {
        return productClient.checkProductAvailability(productId);
    }

    public String checkFallback(Long productId, Throwable t) {
        log.warn("product-service unavailable for id={}: {}", productId, t.getMessage());
        return "UNAVAILABLE";
    }
}
