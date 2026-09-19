package com.example.orderservice.controller;

import com.example.orderservice.service.ProductService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final ProductService productService;

    public OrderController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/{productId}")
    public ResponseEntity<String> createOrder(@PathVariable Long productId) {
        String result = productService.checkAvailability(productId);
        MDC.put("productId", String.valueOf(productId));
        if ("AVAILABLE".equalsIgnoreCase(result)) {
            return ResponseEntity.ok("Order created successfully for product " + productId);
        } else if ("UNAVAILABLE".equals(result)) {
            // Circuit breaker fallback kicked in — degraded response
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Product service is down. Order not created; please retry later.");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Product not available");
        }
    }

}