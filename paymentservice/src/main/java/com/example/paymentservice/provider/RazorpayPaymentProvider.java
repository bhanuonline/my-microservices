package com.example.paymentservice.provider;

import com.example.paymentservice.model.Payment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Razorpay payment provider — PHASE 3.
 *
 * <p>Scaffolding only. Phase 3 adds:
 * <ul>
 *   <li>{@code com.razorpay:razorpay-java} SDK dependency</li>
 *   <li>{@link #initiate} creates a Razorpay Order + Checkout URL</li>
 *   <li>{@link #handleWebhook} verifies the X-Razorpay-Signature HMAC-SHA256
 *       header against RAZORPAY_WEBHOOK_SECRET, then dispatches on event
 *       (order.paid, payment.failed, refund.processed)</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(value = "payment.razorpay.enabled", havingValue = "true")
public class RazorpayPaymentProvider implements PaymentProvider {

    public static final String NAME = "razorpay";

    @Override
    public String name() { return NAME; }

    @Override
    public ProviderSession initiate(Payment payment) {
        throw new UnsupportedOperationException(
                "RazorpayPaymentProvider.initiate() will be implemented in Phase 3.");
    }

    @Override
    public ProviderSession refund(Payment payment) {
        throw new UnsupportedOperationException(
                "RazorpayPaymentProvider.refund() will be implemented in Phase 3.");
    }

    @Override
    public WebhookResult handleWebhook(byte[] rawBody, Map<String, String> headers) {
        throw new UnsupportedOperationException(
                "RazorpayPaymentProvider.handleWebhook() will be implemented in Phase 3.");
    }
}
