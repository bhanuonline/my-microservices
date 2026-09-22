package com.example.orderservice.controller;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.exception.ProductUnavailableException;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.service.ProductService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final ProductService productService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;

    public OrderController(ProductService productService,
                           OrderService orderService,
                           OrderRepository orderRepository) {
        this.productService = productService;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        MDC.put("productId", String.valueOf(req.productId()));

        String availability = productService.checkAvailability(req.productId());
        if (!"AVAILABLE".equalsIgnoreCase(availability)) {
            throw new ProductUnavailableException(req.productId());
        }

        Order order = orderService.create(req.productId(), req.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(order));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable String id) {
        return orderRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    private OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getProductId(), o.getQuantity(),
                o.getStatus().name(), o.getCreatedAt());
    }
}
