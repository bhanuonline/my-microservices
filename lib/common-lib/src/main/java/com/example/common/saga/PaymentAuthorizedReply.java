package com.example.common.saga;

import java.util.UUID;

/**
 * Response to {@link AuthorizePaymentCommand} from payment-service.
 *
 * <p>On {@code success=true}, {@code paymentId} is payment-service's internal
 * Payment row id (used in subsequent CapturePaymentCommand /
 * VoidPaymentCommand), and {@code authId} is the provider-side authorization
 * reference (e.g. Checkout.com's {@code pay_...} id). The saga transitions
 * to {@code AUTHORIZED} and waits.
 *
 * <p>On {@code success=false}, {@code failureReason} explains why (card
 * declined, insufficient funds, etc.) and the saga transitions to
 * {@code FAILED}.
 */
public record PaymentAuthorizedReply(
        UUID sagaId,
        String orderId,
        String paymentId,
        String authId,
        boolean success,
        String failureReason
) implements SagaReply {}
