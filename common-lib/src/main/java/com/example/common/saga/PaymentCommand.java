package com.example.common.saga;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command — issued by an orchestrator to a target service.
 * sagaId lets the target route its reply back to the correct saga instance.
 */
public record PaymentCommand(
        UUID sagaId,
        String orderId,
        BigDecimal amount
) {}
