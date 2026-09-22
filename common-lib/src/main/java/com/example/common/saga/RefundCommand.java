package com.example.common.saga;

import java.util.UUID;

/**
 * COMPENSATING command — sent by orchestrator when a later step failed
 * and we need to undo the payment charge.
 */
public record RefundCommand(
        UUID sagaId,
        String orderId,
        String paymentId,
        String reason
) {}
