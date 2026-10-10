package com.example.orderservice.dto;

import java.util.UUID;

/**
 * Checkout response — tells the client enough to either show "success" (sync
 * mock) or redirect to a hosted-checkout page (Stripe/Razorpay/PayPal).
 *
 * <p>The redirectUrl is populated only for providers whose flow requires
 * sending the user to a provider-hosted page. For the mock provider it stays
 * null — the saga has already fired synchronously by the time the response
 * is sent.
 *
 * <p>Polling: clients can watch the saga transition via
 * {@code GET /admin/sagas/{sagaId}} or the payment via
 * {@code GET /api/v1/payments/by-order/{orderId}}.
 */
public record CheckoutResponse(
        String orderId,
        UUID sagaId,
        String status,
        String provider,
        String redirectUrl
) {}
