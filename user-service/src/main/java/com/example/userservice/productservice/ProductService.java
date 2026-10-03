package com.example.userservice.productservice;

import com.example.userservice.dto.Product;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Calls product-service over HTTP via a load-balanced RestTemplate.
 *
 * Order of annotations (outer → inner):
 *   CircuitBreaker → Retry → Bulkhead
 *
 * - Retry inside the breaker so the breaker only sees the final outcome.
 * - Bulkhead inside Retry so one caller can't exhaust the slot pool through
 *   retries; retries re-acquire a slot each attempt.
 * - On breaker-open / bulkhead-full / retry-exhausted → fallback returns an
 *   empty list so the caller degrades gracefully instead of 5xx-ing.
 */
@Slf4j
@Service
public class ProductService {

    private static final String INSTANCE = "productClient";

    @Autowired
    private RestTemplate restTemplate;

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "getAllProductsFallback")
    @Retry(name = INSTANCE)
    @Bulkhead(name = INSTANCE)
    public List<Product> getAllProducts() {
        ResponseEntity<List<Product>> productList =
                restTemplate.exchange(
                        "http://PRODUCT-SERVICE/products",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<Product>>() {
                        }
                );
        log.info("product list : {}", productList);
        return productList.getBody();
    }

    /** Signature must match the guarded method + trailing Throwable. */
    @SuppressWarnings("unused")
    private List<Product> getAllProductsFallback(Throwable t) {
        log.warn("product-service unavailable, returning empty list ({}: {})",
                t.getClass().getSimpleName(), t.getMessage());
        return Collections.emptyList();
    }
}
