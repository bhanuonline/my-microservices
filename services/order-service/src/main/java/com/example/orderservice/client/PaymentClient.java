package com.example.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Side-channel to payment-service for hosted-checkout providers. The saga's
 * Kafka PaymentCommand still fires — this HTTP call just gives CheckoutController
 * synchronous access to the Stripe/Razorpay/PayPal redirect URL so it can return
 * it to the client in one round-trip.
 *
 * <p>payment-service's {@code PaymentService.handlePayment} is find-or-create
 * on (sagaId, provider) and guards against double-initiation, so it's safe
 * whether the Kafka processor or this HTTP call gets there first.
 */
@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/api/v1/payments/initiate")
    InitiateResponse initiate(@RequestBody InitiateRequest req);

    record InitiateRequest(
            UUID sagaId,
            String orderId,
            BigDecimal amount,
            String provider,
            String returnUrl,
            String cancelUrl
    ) {}

    record InitiateResponse(
            String paymentId,
            String providerRef,
            String redirectUrl,
            String status
    ) {}
}
