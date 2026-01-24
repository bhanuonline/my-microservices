package com.example.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "productClient", url = "http://localhost:8081")
public interface ProductClient {

    @GetMapping("/products/{id}")
    String checkProductAvailability(@PathVariable("id") Long id);
}