package com.example.common.saga;

import java.util.UUID;

/**
 * Marker interface for every message sent FROM a downstream service BACK TO
 * the saga orchestrator. Symmetric to {@link SagaCommand}.
 *
 * <p>Every reply carries at least {@link #sagaId()} (so the orchestrator
 * knows which saga this is about) and {@link #success()} (so the
 * orchestrator can branch to the next step vs. the failure path without
 * each reply handler having to re-define the convention).
 *
 * <p>Same design rationale as {@link SagaCommand} — sealed interface for
 * compile-time contract + catalogue, records retained for brevity.
 */
public sealed interface SagaReply permits
        PaymentReply,
        NotifyUserReply,
        PaymentAuthorizedReply,
        PaymentCapturedReply,
        PaymentVoidedReply {

    UUID sagaId();

    boolean success();
}
