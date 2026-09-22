package com.example.common.saga;

import java.util.UUID;

public record NotifyUserReply(
        UUID sagaId,
        String orderId,
        boolean success,
        String failureReason
) {}
