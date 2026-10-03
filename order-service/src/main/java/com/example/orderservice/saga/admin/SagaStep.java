package com.example.orderservice.saga.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit row per command sent or reply received by {@link com.example.orderservice.saga.OrderSagaOrchestrator}.
 * Powers the saga admin UI — operators see the exact command sequence and
 * reply payloads for any saga, including stuck ones.
 */
@Entity
@Table(
    name = "saga_steps",
    indexes = @Index(name = "idx_saga_steps_saga", columnList = "saga_id, occurred_at")
)
public class SagaStep {

    public enum Direction { EMIT_COMMAND, RECEIVE_REPLY, COMPENSATION }
    public enum Outcome   { PENDING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "step_name", length = 64, nullable = false)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Outcome outcome;

    @Lob
    private String payload;

    @Column(name = "failure_reason", length = 1024)
    private String failureReason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected SagaStep() {}

    public SagaStep(UUID sagaId, String stepName, Direction direction, Outcome outcome,
                    String payload, String failureReason) {
        this.sagaId = sagaId;
        this.stepName = stepName;
        this.direction = direction;
        this.outcome = outcome;
        this.payload = payload;
        this.failureReason = failureReason;
        this.occurredAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getSagaId() { return sagaId; }
    public String getStepName() { return stepName; }
    public Direction getDirection() { return direction; }
    public Outcome getOutcome() { return outcome; }
    public String getPayload() { return payload; }
    public String getFailureReason() { return failureReason; }
    public Instant getOccurredAt() { return occurredAt; }
}
