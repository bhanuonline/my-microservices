package com.example.orderservice.controller;

import com.example.orderservice.client.ProductClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final ProductClient productClient;

    public OrderController(ProductClient productClient) {
        this.productClient = productClient;
    }

    @PostMapping("/{productId}")
    public ResponseEntity<String> createOrder(@PathVariable Long productId) {
        String result = productClient.checkProductAvailability(productId);

        if ("AVAILABLE".equalsIgnoreCase(result)) {
            // Normally, you'd save the order to a DB and publish an event here
            return ResponseEntity.ok("Order created successfully for product " + productId);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Product not available");
        }
    }
}