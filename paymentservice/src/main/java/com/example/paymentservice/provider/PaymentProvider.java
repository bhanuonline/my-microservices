package com.example.paymentservice.provider;

import com.example.paymentservice.model.Payment;

/**
 * Abstraction over external payment providers. One implementation per
 * provider ({@link MockPaymentProvider}, StripePaymentProvider, etc.).
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   PaymentCommand arrives on Kafka
 *       │
 *       ▼
 *   PaymentCommandProcessor
 *       - finds-or-creates Payment row (INITIATED)
 *       - picks provider by name
 *       - calls provider.initiate(payment)
 *       - persists whatever status came back
 *       - if terminal → publishes PaymentReply immediately
 *       - if INITIATED → waits for a webhook
 *
 *   Later (for async providers):
 *       Provider POSTs to /webhooks/{providerName}
 *       → WebhookController calls provider.handleWebhook(...)
 *       → that returns WebhookResult { paymentId, newStatus, reason }
 *       → PaymentCommandProcessor transitions the Payment row and
 *         publishes PaymentReply.
 * </pre>
 *
 * <h3>Why a shared interface for 3+ VERY different APIs</h3>
 * Stripe, Razorpay, and PayPal have different data shapes, different auth,
 * different webhook formats. The interface deliberately deals in {@link Payment}
 * (our domain) and {@link ProviderSession} (minimal transport object), not
 * in each provider's SDK types. Implementation code is free to use the full
 * SDK internally; the orchestrator only sees our types.
 */
public interface PaymentProvider {

    /** Short lowercase name used in {@code PaymentCommand.provider} and config. */
    String name();

    /**
     * Attempt to charge the payment. Implementations MUST be idempotent on
     * {@code payment.sagaId} — a re-sent command for the same saga must not
     * create a second provider-side session.
     *
     * <p>Synchronous providers (like the mock in the "immediate" mode) can
     * return a terminal status directly. Hosted-checkout providers (Stripe,
     * Razorpay, PayPal) return {@code INITIATED} and emit the terminal
     * status later via webhook.
     */
    ProviderSession initiate(Payment payment);

    /**
     * Refund a previously-captured payment. Called by the saga's
     * compensation path.
     */
    ProviderSession refund(Payment payment);

    /**
     * Process an inbound webhook payload. Implementations are responsible
     * for signature verification BEFORE trusting any field in {@code body}.
     *
     * @param rawBody raw request body as a byte[] — signature verification
     *                usually requires the un-parsed bytes.
     * @param headers HTTP headers (lower-cased keys).
     * @return a {@link WebhookResult}; if the payload is unparseable or
     *         fails signature verification, implementations should throw
     *         {@link WebhookVerificationException}.
     */
    WebhookResult handleWebhook(byte[] rawBody, java.util.Map<String, String> headers);
}
