package com.example.common.saga;

import java.util.UUID;

/**
 * Operator-triggered command that releases a prior authorization without
 * charging the customer. Only valid against sagas in {@code AUTHORIZED}
 * state. payment-service replies with {@link PaymentVoidedReply}.
 *
 * <p>Semantically different from a refund: no money ever moved, so the
 * provider typically does not charge a fee for voiding an authorization.
 * (Compare with {@link RefundCommand}, which reverses a completed capture.)
 */
public record VoidPaymentCommand(
        UUID sagaId,
        String paymentId,
        String reason
) {}
