package com.example.orderservice.service;

import com.example.orderservice.client.ProductClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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

    // Applied outermost-to-innermost: Retry → CircuitBreaker → Bulkhead → actual call.
    // Fallback fires on the LAST failure after all resilience layers give up.
    @Retry(name = "productClient", fallbackMethod = "checkFallback")
    @CircuitBreaker(name = "productClient", fallbackMethod = "checkFallback")
    @Bulkhead(name = "productClient", fallbackMethod = "checkFallback")
    public String checkAvailability(Long productId) {
        return productClient.checkProductAvailability(productId);
    }

    // Single fallback shared by all 3 patterns. Signature: same params + Throwable at end.
    public String checkFallback(Long productId, Throwable t) {
        log.warn("product-service degraded for id={}: {} ({})",
                productId, t.getClass().getSimpleName(), t.getMessage());
        return "UNAVAILABLE";
    }
}
