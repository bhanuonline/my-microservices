package com.example.paymentservice.provider;

import com.example.paymentservice.model.Payment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * PayPal payment provider — PHASE 4.
 *
 * <p>Scaffolding only. PayPal's model is different from Stripe/Razorpay:
 * OAuth per request (short-lived access tokens), different webhook signing
 * (certificate-based rather than HMAC). Phase 4 adds:
 * <ul>
 *   <li>{@code com.paypal.sdk:checkout-sdk} SDK dependency</li>
 *   <li>OAuth token caching</li>
 *   <li>{@link #initiate} creates a PayPal Order with CAPTURE intent</li>
 *   <li>{@link #handleWebhook} verifies via POST to
 *       {@code /v1/notifications/verify-webhook-signature} (no local HMAC;
 *       PayPal verifies for us)</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(value = "payment.paypal.enabled", havingValue = "true")
public class PayPalPaymentProvider implements PaymentProvider {

    public static final String NAME = "paypal";

    @Override
    public String name() { return NAME; }

    @Override
    public ProviderSession initiate(Payment payment) {
        throw new UnsupportedOperationException(
                "PayPalPaymentProvider.initiate() will be implemented in Phase 4.");
    }

    @Override
    public ProviderSession refund(Payment payment) {
        throw new UnsupportedOperationException(
                "PayPalPaymentProvider.refund() will be implemented in Phase 4.");
    }

    @Override
    public WebhookResult handleWebhook(byte[] rawBody, Map<String, String> headers) {
        throw new UnsupportedOperationException(
                "PayPalPaymentProvider.handleWebhook() will be implemented in Phase 4.");
    }
}
