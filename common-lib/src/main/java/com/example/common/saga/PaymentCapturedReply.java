package com.example.common.saga;

import java.util.UUID;

/**
 * Response to {@link CapturePaymentCommand} from payment-service.
 *
 * <p>On {@code success=true}, the Payment row is now {@code CAPTURED} and
 * the saga joins the standard notify flow (same tail as combined-flow
 * {@link PaymentReply} with success=true).
 *
 * <p>On {@code success=false}, the Payment row STAYS in {@code AUTHORIZED}
 * (no money moved). The saga does NOT transition — operator can retry the
 * capture or decide to void. A capture-failed metric increments so the
 * situation is visible in dashboards and alerts.
 */
public record PaymentCapturedReply(
        UUID sagaId,
        String orderId,
        String paymentId,
        boolean success,
        String failureReason
) {}
