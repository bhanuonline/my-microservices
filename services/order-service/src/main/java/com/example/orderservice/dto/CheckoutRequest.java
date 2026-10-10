package com.example.orderservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Checkout request — carries the usual order fields plus:
 *   <ul>
 *     <li>{@code provider}: which payment provider to route the saga through.
 *         Allowed values match {@link
 *         com.example.paymentservice.provider.PaymentProvider#name} across
 *         enabled impls. {@code null} → payment-service picks its configured
 *         default.</li>
 *     <li>{@code returnUrl} / {@code cancelUrl}: hosted-checkout providers
 *         need to know where to send the user after the payment page.
 *         Ignored by sync providers like the mock.</li>
 *   </ul>
 */
public record CheckoutRequest(
        @NotNull @Positive Long productId,
        @NotNull @Positive Integer quantity,
        @Pattern(regexp = "mock|stripe|razorpay|paypal", message = "provider must be one of: mock, stripe, razorpay, paypal")
        String provider,
        String returnUrl,
        String cancelUrl
) {}
