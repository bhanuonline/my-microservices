package com.example.orderservice.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout entry point. Creates an order + starts a saga, routing the
 * payment through the client-chosen provider.
 *
 * <h3>Why a separate endpoint from {@code POST /api/v1/orders}</h3>
 * {@code /orders} is the raw "create an order" API — stays as it is.
 * {@code /checkout} wraps it with a provider choice + the fields hosted-
 * checkout providers need ({@code returnUrl}, {@code cancelUrl}). Keeping
 * them split means existing clients don't need to care about payment
 * providers.
 *
 * <h3>Response shape</h3>
 * For {@code provider=mock}, the saga runs synchronously and the response
 * returns 201 with {@code redirectUrl=null}. Clients can poll
 * {@code GET /admin/sagas/{sagaId}} to see the state transition.
 *
 * <p>For hosted-checkout providers (Stripe/Razorpay/PayPal — PHASE 2+),
 * the response will carry a non-null {@code redirectUrl} that clients
 * must open in a browser. The saga will transition to NOTIFIED only after
 * the user completes payment and payment-service receives the webhook.
 */
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    private final ProductService productService;
    private final OrderService orderService;
    private final OrderSagaRepository sagaRepo;

    public CheckoutController(ProductService productService,
                              OrderService orderService,
                              OrderSagaRepository sagaRepo) {
        this.productService = productService;
        this.orderService = orderService;
        this.sagaRepo = sagaRepo;
    }

    @PostMapping
    public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest req) {
        String availability = productService.checkAvailability(req.productId());
        if (!"AVAILABLE".equalsIgnoreCase(availability)) {
            throw new ProductUnavailableException(req.productId());
        }

        Order order = orderService.create(req.productId(), req.quantity(), req.provider());

        // The saga row is created inside OrderSagaOrchestrator.start().
        // Fetch it so we can echo the sagaId back to the client; the saga is
        // keyed by orderId.
        OrderSaga saga = sagaRepo.findByOrderId(order.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Saga not found for freshly-created orderId=" + order.getId()));

        log.info("Checkout initiated: orderId={} sagaId={} provider={}",
                order.getId(), saga.getId(), req.provider());

        // Phase 1: redirectUrl is always null (mock is synchronous).
        // Phase 2: Stripe/Razorpay will populate this by calling payment-service
        //          synchronously to initiate the session and get the hosted URL.
        return ResponseEntity.status(HttpStatus.CREATED).body(new CheckoutResponse(
                order.getId(),
                saga.getId(),
                order.getStatus().name(),
                req.provider(),
                null));
    }
}
