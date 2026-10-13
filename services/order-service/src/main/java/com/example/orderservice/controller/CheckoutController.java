package com.example.orderservice.controller;

import com.example.orderservice.client.PaymentClient;
import com.example.orderservice.service.PaymentService;
import com.example.orderservice.dto.CheckoutRequest;
import com.example.orderservice.dto.CheckoutResponse;
import com.example.orderservice.exception.ProductUnavailableException;
import com.example.orderservice.model.Order;
import com.example.orderservice.saga.OrderSaga;
import com.example.orderservice.saga.OrderSagaRepository;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.service.ProductService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Checkout entry point. Creates an order + starts a saga, routing the
 * payment through the client-chosen provider.
 *
 * <h3>Mock (synchronous)</h3>
 * Saga's Kafka PaymentCommand drives everything. Response.redirectUrl=null.
 *
 * <h3>Stripe / Razorpay / PayPal (hosted checkout, async)</h3>
 * Saga's Kafka PaymentCommand still fires. In parallel, this controller
 * makes a synchronous Feign call to payment-service's /initiate endpoint
 * so it can echo the redirect URL back to the client in one round-trip.
 * payment-service's find-or-create guard makes both orderings safe.
 */
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);
    private static final String MOCK_PROVIDER = "mock";

    private final ProductService productService;
    private final OrderService orderService;
    private final OrderSagaRepository sagaRepo;
    private final PaymentService paymentService;
    private final Executor executor;

    public CheckoutController(ProductService productService,
                              OrderService orderService,
                              OrderSagaRepository sagaRepo,
                              PaymentService paymentService,
                              @Qualifier("eventExecutor") Executor executor) {
        this.productService = productService;
        this.orderService = orderService;
        this.sagaRepo = sagaRepo;
        this.paymentService = paymentService;
        this.executor = executor;
    }

    // -------------------------------------------------------------------------
    // APPROACH 1 (original) — Sequential
    // -------------------------------------------------------------------------
    // checkAvailability() runs → then checkout proceeds
    // Simple, easy to read. Fine when there's only one pre-check.
    // -------------------------------------------------------------------------
    @PostMapping
    public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest req) {
        String availability = productService.checkAvailability(req.productId());
        if (!"AVAILABLE".equalsIgnoreCase(availability)) {
            throw new ProductUnavailableException(req.productId());
        }

        Order order = orderService.create(req.productId(), req.quantity(), req.provider());

        OrderSaga saga = sagaRepo.findByOrderId(order.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Saga not found for freshly-created orderId=" + order.getId()));

        String redirectUrl = null;
        if (req.provider() != null && !MOCK_PROVIDER.equalsIgnoreCase(req.provider())) {
            // Hosted-checkout: sync call to payment-service to get the redirect URL.
            // The saga's Kafka PaymentCommand also fires; payment-service's
            // find-or-create guard converges both paths to the same Payment row.
            try {
                PaymentClient.InitiateResponse resp = paymentService.initiate(new PaymentClient.InitiateRequest(
                        saga.getId(),
                        order.getId(),
                        order.getAmount(),
                        req.provider(),
                        req.returnUrl(),
                        req.cancelUrl()));
                redirectUrl = resp.redirectUrl();
                log.info("Checkout initiated: orderId={} sagaId={} provider={} ref={}",
                        order.getId(), saga.getId(), req.provider(), resp.providerRef());
            } catch (RuntimeException e) {
                // Payment-service unreachable or Stripe error. The order+saga are
                // still created; client can poll /api/v1/payments/by-order to retry.
                log.error("Payment initiate failed; client will need to poll for redirect: orderId={} provider={}",
                        order.getId(), req.provider(), e);
            }
        } else {
            log.info("Checkout initiated (mock): orderId={} sagaId={}", order.getId(), saga.getId());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(new CheckoutResponse(
                order.getId(),
                saga.getId(),
                order.getStatus().name(),
                req.provider(),
                redirectUrl));
    }

    // -------------------------------------------------------------------------
    // APPROACH 2 — Parallel pre-validation with CompletableFuture
    // -------------------------------------------------------------------------
    // When you have TWO independent pre-checks (availability + fraud/credit),
    // run them simultaneously — total wait = max(t1, t2) instead of t1 + t2.
    //
    // Sequential:  [availability 200ms] → [fraud 150ms] = 350ms total
    // Parallel:    [availability 200ms]
    //              [fraud        150ms] = 200ms total  (50ms saved per request)
    //
    // Use /api/v1/checkout/parallel to test this path.
    // -------------------------------------------------------------------------
    @PostMapping("/parallel")
    public ResponseEntity<CheckoutResponse> checkoutParallel(@Valid @RequestBody CheckoutRequest req) {

        // Fire both checks simultaneously on the eventExecutor thread pool
        CompletableFuture<String> availabilityFuture = CompletableFuture
                .supplyAsync(() -> productService.checkAvailability(req.productId()), executor);

        CompletableFuture<Boolean> fraudFuture = CompletableFuture
                .supplyAsync(() -> runFraudCheck(req.productId()), executor);

        // .join() blocks until both complete — if either throws, join() rethrows
        CompletableFuture.allOf(availabilityFuture, fraudFuture).join();

        String availability = availabilityFuture.join();
        boolean fraudPassed  = fraudFuture.join();

        if (!"AVAILABLE".equalsIgnoreCase(availability)) {
            throw new ProductUnavailableException(req.productId());
        }
        if (!fraudPassed) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(null);
        }

        Order order = orderService.create(req.productId(), req.quantity(), req.provider());
        OrderSaga saga = sagaRepo.findByOrderId(order.getId())
                .orElseThrow(() -> new IllegalStateException("Saga not found for orderId=" + order.getId()));

        log.info("Parallel checkout done: orderId={} sagaId={}", order.getId(), saga.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CheckoutResponse(
                order.getId(), saga.getId(), order.getStatus().name(), req.provider(), null));
    }

    // Placeholder — replace with real fraud/credit service call
    private boolean runFraudCheck(Long productId) {
        return true;
    }
}
