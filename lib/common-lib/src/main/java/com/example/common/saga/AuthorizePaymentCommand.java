package com.example.common.saga;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * First command of the <b>split-capture</b> payment flow — sent by the saga
 * orchestrator when the chosen provider supports two-phase payments
 * (currently Checkout.com; see
 * {@code com.example.paymentservice.provider.SplitCapturePaymentProvider}).
 *
 * <p>Reserves funds on the customer's card without actually moving them.
 * payment-service replies with {@link PaymentAuthorizedReply}. The saga
 * then waits — nothing more happens until an operator sends a
 * {@link CapturePaymentCommand} or {@link VoidPaymentCommand}.
 *
 * <p>Contrast with {@link PaymentCommand}, which does
 * authorize + capture in one shot (combined-flow providers).
 */
public record AuthorizePaymentCommand(
        UUID sagaId,
        String orderId,
        BigDecimal amount,
        String currency,
        String provider
) implements SagaCommand {}
