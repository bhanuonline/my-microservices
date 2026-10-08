package com.example.orderservice.controller;

import com.example.orderservice.client.PaymentClient;
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
    private final PaymentClient paymentClient;

    public CheckoutController(ProductService productService,
                              OrderService orderService,
                              OrderSagaRepository sagaRepo,
                              PaymentClient paymentClient) {
        this.productService = productService;
        this.orderService = orderService;
        this.sagaRepo = sagaRepo;
        this.paymentClient = paymentClient;
    }

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
                PaymentClient.InitiateResponse resp = paymentClient.initiate(new PaymentClient.InitiateRequest(
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
}
