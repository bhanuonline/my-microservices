package com.example.paymentservice.provider;

import com.example.paymentservice.model.Payment.Status;

/**
 * Returned by {@link PaymentProvider#handleWebhook}. Tells the controller
 * which {@code Payment} the webhook referenced and the new status to apply.
 *
 * @param providerRef    the provider-side identifier the webhook was about
 *                       (used to look up the matching Payment row).
 * @param newStatus      status to transition the Payment to.
 * @param failureReason  populated when newStatus is DECLINED or FAILED.
 * @param replayable     true if the webhook is safe to ignore (e.g., we've
 *                       already processed this provider-ref — duplicate
 *                       delivery). Controller returns 200 but takes no action.
 */
public record WebhookResult(
        String providerRef,
        Status newStatus,
        String failureReason,
        boolean replayable
) {

    public static WebhookResult captured(String providerRef) {
        return new WebhookResult(providerRef, Status.CAPTURED, null, false);
    }

    public static WebhookResult declined(String providerRef, String reason) {
        return new WebhookResult(providerRef, Status.DECLINED, reason, false);
    }

    public static WebhookResult refunded(String providerRef) {
        return new WebhookResult(providerRef, Status.REFUNDED, null, false);
    }

    public static WebhookResult ignored(String providerRef) {
        return new WebhookResult(providerRef, null, null, true);
    }
}
