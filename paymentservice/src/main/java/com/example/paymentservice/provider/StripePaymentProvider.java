package com.example.paymentservice.provider;

import com.example.paymentservice.model.Payment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Stripe payment provider — PHASE 2.
 *
 * <p>Scaffolding only. The real implementation lands in a separate commit
 * once a Stripe test account + API keys are configured and {@code stripe
 * listen --forward-to localhost:8091/webhooks/stripe} is running.
 *
 * <p>What Phase 2 adds here:
 * <ul>
 *   <li>{@code com.stripe:stripe-java} SDK dependency</li>
 *   <li>{@link #initiate} creates a Stripe Checkout Session, returns the
 *       session's URL as redirectUrl and the session id as providerRef</li>
 *   <li>{@link #refund} calls Stripe's Refund API</li>
 *   <li>{@link #handleWebhook} verifies the Stripe-Signature header via
 *       {@code Webhook.constructEvent(body, sig, secret)}, then dispatches
 *       on event type (checkout.session.completed, payment_intent.payment_failed,
 *       charge.refunded, etc.)</li>
 * </ul>
 *
 * <p>Enabled only when {@code payment.stripe.enabled=true} — kept off by
 * default so a service with no Stripe keys still boots cleanly.
 */
@Component
@ConditionalOnProperty(value = "payment.stripe.enabled", havingValue = "true")
public class StripePaymentProvider implements PaymentProvider {

    public static final String NAME = "stripe";

    @Override
    public String name() { return NAME; }

    @Override
    public ProviderSession initiate(Payment payment) {
        throw new UnsupportedOperationException(
                "StripePaymentProvider.initiate() will be implemented in Phase 2. "
                + "See docs/microservices/payment-gateway.md for the plan.");
    }

    @Override
    public ProviderSession refund(Payment payment) {
        throw new UnsupportedOperationException(
                "StripePaymentProvider.refund() will be implemented in Phase 2.");
    }

    @Override
    public WebhookResult handleWebhook(byte[] rawBody, Map<String, String> headers) {
        throw new UnsupportedOperationException(
                "StripePaymentProvider.handleWebhook() will be implemented in Phase 2. "
                + "Must verify Stripe-Signature header before trusting payload.");
    }
}
