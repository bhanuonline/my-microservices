package com.example.common.saga;

import java.util.UUID;

public record PaymentReply(
        UUID sagaId,
        String orderId,
        boolean success,
        String failureReason,
        String paymentId   // populated on success — needed for later refund
) {}
