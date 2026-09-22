package com.example.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// name = eureka service name (must match product-service's spring.application.name)
// NO url= parameter → Feign asks Eureka to resolve it via load balancer
@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}/availability")
    String checkProductAvailability(@PathVariable("id") Long id);
}