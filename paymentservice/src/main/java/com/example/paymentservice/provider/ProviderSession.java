package com.example.paymentservice.provider;

import com.example.paymentservice.model.Payment.Status;

/**
 * The thin value object a {@link PaymentProvider} returns from {@code initiate}
 * or {@code refund}. The orchestrator uses it to update the {@link
 * com.example.paymentservice.model.Payment} row and decide whether to publish
 * a PaymentReply now (terminal) or wait for a webhook ({@code INITIATED}).
 *
 * @param providerRef     provider-side identifier (Stripe session id, Razorpay
 *                        order id, etc.). Null only when the provider has no
 *                        reference (e.g., a mock in sync mode).
 * @param redirectUrl     where the user should be redirected to pay. Null for
 *                        providers that don't use a hosted page (sync mock,
 *                        server-side-only flows).
 * @param status          outcome: INITIATED means "wait for webhook";
 *                        AUTHORIZED / CAPTURED means synchronous success;
 *                        DECLINED means synchronous rejection; FAILED means
 *                        system error.
 * @param failureReason   short machine-readable reason when status is
 *                        DECLINED or FAILED; null otherwise.
 */
public record ProviderSession(
        String providerRef,
        String redirectUrl,
        Status status,
        String failureReason
) {

    public static ProviderSession pending(String providerRef, String redirectUrl) {
        return new ProviderSession(providerRef, redirectUrl, Status.INITIATED, null);
    }

    public static ProviderSession captured(String providerRef) {
        return new ProviderSession(providerRef, null, Status.CAPTURED, null);
    }

    public static ProviderSession declined(String reason) {
        return new ProviderSession(null, null, Status.DECLINED, reason);
    }

    public static ProviderSession failed(String reason) {
        return new ProviderSession(null, null, Status.FAILED, reason);
    }

    public static ProviderSession refunded(String providerRef) {
        return new ProviderSession(providerRef, null, Status.REFUNDED, null);
    }

    /** Used by split-capture providers from {@code authorize()}. */
    public static ProviderSession authorized(String providerRef) {
        return new ProviderSession(providerRef, null, Status.AUTHORIZED, null);
    }

    /** Used by split-capture providers from {@code voidPayment()}. */
    public static ProviderSession voided(String providerRef) {
        return new ProviderSession(providerRef, null, Status.VOIDED, null);
    }
}
