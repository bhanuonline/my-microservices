package com.example.orderservice.controller;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.EnrichedOrderResponse;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.exception.ProductUnavailableException;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.service.ProductService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final ProductService productService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final Executor executor;

    public OrderController(ProductService productService,
                           OrderService orderService,
                           OrderRepository orderRepository,
                           @Qualifier("eventExecutor") Executor executor) {
        this.productService = productService;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.executor = executor;
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

    // -------------------------------------------------------------------------
    // APPROACH 1 (original) — simple, single DB call
    // -------------------------------------------------------------------------
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable String id) {
        return orderRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    // -------------------------------------------------------------------------
    // APPROACH 2 — Parallel enrichment with CompletableFuture
    // -------------------------------------------------------------------------
    // Fetches order (DB) + product availability (Feign → product-service) simultaneously.
    //
    // Sequential:  [DB 20ms] → [Feign 200ms] = 220ms total
    // Parallel:    [DB 20ms]
    //              [Feign 200ms]              = 200ms total  (overlap saves time)
    //
    // Scale this to 3+ sources (product details, payment status, shipping ETA)
    // and the savings grow — all independent calls run at the same time.
    // -------------------------------------------------------------------------
    @GetMapping("/{id}/enriched")
    public EnrichedOrderResponse getOrderEnriched(@PathVariable String id) {

        // Task 1: fetch order from DB
        CompletableFuture<Order> orderFuture = CompletableFuture
                .supplyAsync(() -> orderRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id)),
                        executor);

        // Task 2: fetch product availability — uses productId from orderFuture
        // thenApplyAsync: run the next step async on the same executor once orderFuture completes
        CompletableFuture<String> availabilityFuture = orderFuture
                .thenApplyAsync(order -> productService.checkAvailability(order.getProductId()),
                        executor);

        // join() — block current thread until both futures complete
        // If either throws, join() wraps and rethrows as CompletionException
        Order order        = orderFuture.join();
        String availability = availabilityFuture.join();

        log.info("Enriched fetch: orderId={} productAvailability={}", id, availability);

        return new EnrichedOrderResponse(
                order.getId(), order.getProductId(), order.getQuantity(),
                order.getStatus().name(), order.getCreatedAt(), availability);
    }

    private OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getProductId(), o.getQuantity(),
                o.getStatus().name(), o.getCreatedAt());
    }
}
