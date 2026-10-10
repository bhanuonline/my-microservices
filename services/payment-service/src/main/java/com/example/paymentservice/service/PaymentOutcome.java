package com.example.paymentservice.service;

import com.example.paymentservice.model.Payment;

/**
 * The small value object {@link PaymentService} returns to its callers so
 * they know whether a reply was published or whether we're waiting for a
 * webhook. Carries the (optional) hosted-checkout redirect URL for the
 * hosted-checkout flow.
 */
public record PaymentOutcome(
        Payment payment,
        boolean replyPublished,
        String redirectUrl,
        String failureReason
) {

    public static PaymentOutcome ok(Payment payment) {
        return new PaymentOutcome(payment, true, null, null);
    }

    public static PaymentOutcome failed(Payment payment, String reason) {
        return new PaymentOutcome(payment, true, null, reason);
    }

    /** Non-terminal: provider will drive us via webhook. No reply published yet. */
    public static PaymentOutcome pending(Payment payment) {
        return new PaymentOutcome(payment, false, null, null);
    }

    public static PaymentOutcome pending(Payment payment, String redirectUrl) {
        return new PaymentOutcome(payment, false, redirectUrl, null);
    }
}
