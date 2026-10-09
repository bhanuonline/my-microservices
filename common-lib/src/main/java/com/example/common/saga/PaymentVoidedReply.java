package com.example.common.saga;

import java.util.UUID;

/**
 * Response to {@link VoidPaymentCommand} from payment-service.
 *
 * <p>On {@code success=true}, the authorization is released (customer's
 * card is no longer held). The saga transitions to {@code VOIDED} (new
 * terminal), the order is cancelled. No refund fires — nothing was
 * captured.
 *
 * <p>On {@code success=false}, the saga stays {@code AUTHORIZED} —
 * operator must investigate (usually: the auth already expired at the
 * provider, which is effectively the same end state).
 */
public record PaymentVoidedReply(
        UUID sagaId,
        String orderId,
        String paymentId,
        boolean success,
        String failureReason
) implements SagaReply {}
