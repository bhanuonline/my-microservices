package com.example.common.saga;

import java.util.UUID;

public record NotifyUserCommand(
        UUID sagaId,
        String orderId,
        String message
) {}
