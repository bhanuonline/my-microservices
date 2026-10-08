package com.example.common.saga;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command — issued by an orchestrator to a target service.
 * sagaId lets the target route its reply back to the correct saga instance.
 *
 * <h3>provider</h3>
 * Optional. Names the payment provider the orchestrator wants used
 * ({@code "mock"}, {@code "stripe"}, {@code "razorpay"}, {@code "paypal"}).
 * Null is interpreted by payment-service as "use the configured default"
 * (see {@code payment.default-provider} in payment-service's application.yml).
 *
 * <p>Added as a nullable field so pre-existing messages still on Kafka remain
 * deserializable — Jackson maps missing JSON fields to {@code null}.
 */
public record PaymentCommand(
        UUID sagaId,
        String orderId,
        BigDecimal amount,
        String provider
) {
    /** Backwards-compatible constructor — defaults provider to null → use configured default. */
    public PaymentCommand(UUID sagaId, String orderId, BigDecimal amount) {
        this(sagaId, orderId, amount, null);
    }
}
