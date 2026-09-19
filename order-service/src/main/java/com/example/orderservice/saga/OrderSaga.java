package com.example.orderservice.saga;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * The saga is a first-class persisted entity. That's how it survives crashes —
 * on restart, orchestrator can query DB for RUNNING sagas and resume.
 */
@Entity
@Table(name = "order_sagas")
public class OrderSaga {

    public enum State {
        STARTED,           // just created
        PAID,              // payment succeeded
        NOTIFIED,          // notify succeeded → terminal SUCCESS
        COMPENSATING,      // some step failed, running compensations
        FAILED             // terminal FAILURE
    }

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state;

    // Populated after payment step — needed by RefundCommand if we compensate.
    private String paymentId;

    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    protected OrderSaga() {}

    public OrderSaga(String orderId) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.state = State.STARTED;
        this.createdAt = Instant.now();
    }

    public void transitionTo(State next) {
        this.state = next;
        this.updatedAt = Instant.now();
    }

    public void markPaid(String paymentId) {
        this.paymentId = paymentId;
        transitionTo(State.PAID);
    }

    public void markNotified() {
        transitionTo(State.NOTIFIED);
    }

    public void beginCompensation(String reason) {
        this.failureReason = reason;
        transitionTo(State.COMPENSATING);
    }

    public void markFailed(String reason) {
        if (this.failureReason == null) this.failureReason = reason;
        transitionTo(State.FAILED);
    }

    public UUID getId() { return id; }
    public String getOrderId() { return orderId; }
    public State getState() { return state; }
    public String getPaymentId() { return paymentId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
