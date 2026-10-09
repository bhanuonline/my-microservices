package com.example.common.saga;

import java.util.UUID;

/**
 * Operator-triggered command that converts a prior authorization into an
 * actual charge. Only valid against sagas currently in the
 * {@code AUTHORIZED} state; payment-service replies with
 * {@link PaymentCapturedReply}.
 *
 * <p>Typically sent by the admin endpoint
 * {@code POST /admin/orders/{orderId}/capture} on order-service, which looks
 * up the saga + payment and publishes this command. Future: a scheduler
 * could also publish it after a configurable delay.
 */
public record CapturePaymentCommand(
        UUID sagaId,
        String paymentId,
        String reason
) {}
