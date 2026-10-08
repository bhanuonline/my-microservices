package com.example.paymentservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistent payment record. Replaces the previous in-memory + random-success
 * logic with a proper state machine + idempotent uniqueness.
 *
 * <h3>State machine</h3>
 * <pre>
 *   INITIATED ─────────► AUTHORIZED ─────────► CAPTURED  (success)
 *        │                                           │
 *        │                                           ▼
 *        │                                       REFUNDED  (compensation)
 *        │
 *        ├─► DECLINED  (provider rejected: card declined, over-limit, fraud)
 *        │
 *        └─► FAILED    (system error: network, timeout, bug)
 * </pre>
 *
 * Only (DECLINED, FAILED, REFUNDED) are terminal from the saga's perspective.
 *
 * <h3>Idempotency</h3>
 * Unique index on (sagaId, provider) means a re-sent {@code PaymentCommand}
 * for the same saga + provider finds the existing row and returns its state
 * — the orchestrator's resume path works without double-charging.
 */
@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payments_saga_provider",
                columnNames = {"saga_id", "provider"}
        ),
        indexes = {
                @Index(name = "ix_payments_order_id", columnList = "order_id"),
                @Index(name = "ix_payments_provider_ref", columnList = "provider_ref")
        }
)
public class Payment {

    public enum Status {
        /** Row created, waiting for provider confirmation (webhook or sync reply). */
        INITIATED,
        /** Provider has authorized the charge but not yet captured (e.g. Stripe PaymentIntent "requires_capture"). */
        AUTHORIZED,
        /** Money has moved. Terminal success state from the saga's perspective. */
        CAPTURED,
        /** Compensation complete. Follows CAPTURED only. */
        REFUNDED,
        /** Provider declined — card declined, insufficient funds, fraud block, etc. Terminal. */
        DECLINED,
        /** System error — network, timeout, unexpected provider response. Terminal. */
        FAILED
    }

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String provider;

    /** Provider-side identifier (Stripe session_id, Razorpay order_id, etc.). Null for mock. */
    @Column(name = "provider_ref", length = 255)
    private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Payment() {}

    public Payment(UUID sagaId, String orderId, BigDecimal amount, String currency, String provider) {
        this.id = UUID.randomUUID().toString();
        this.sagaId = sagaId;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.provider = provider;
        this.status = Status.INITIATED;
        this.createdAt = Instant.now();
    }

    // ─── state transitions ────────────────────────────────────────────────

    /**
     * Record the provider's reference (session id / intent id) without changing
     * status. Used by hosted-checkout providers (Stripe, Razorpay) when
     * {@code initiate()} returns a redirect URL — the Payment stays INITIATED
     * until the webhook arrives.
     */
    public void linkToProvider(String providerRef) {
        if (this.status != Status.INITIATED) {
            throw new IllegalStateException("Payment " + id + ": linkToProvider only valid in INITIATED, was " + status);
        }
        this.providerRef = providerRef;
        this.updatedAt = Instant.now();
    }

    public void markAuthorized(String providerRef) {
        requireFrom(Status.INITIATED);
        this.providerRef = providerRef;
        transitionTo(Status.AUTHORIZED);
    }

    public void markCaptured(String providerRef) {
        requireFromAny(Status.INITIATED, Status.AUTHORIZED);
        if (providerRef != null) this.providerRef = providerRef;
        transitionTo(Status.CAPTURED);
    }

    public void markRefunded() {
        requireFrom(Status.CAPTURED);
        transitionTo(Status.REFUNDED);
    }

    public void markDeclined(String reason) {
        requireFromAny(Status.INITIATED, Status.AUTHORIZED);
        this.failureReason = reason;
        transitionTo(Status.DECLINED);
    }

    public void markFailed(String reason) {
        if (isTerminal()) return;
        this.failureReason = reason;
        transitionTo(Status.FAILED);
    }

    public boolean isTerminal() {
        return status == Status.CAPTURED
                || status == Status.REFUNDED
                || status == Status.DECLINED
                || status == Status.FAILED;
    }

    private void transitionTo(Status next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }

    private void requireFrom(Status required) {
        if (this.status != required) {
            throw new IllegalStateException("Payment " + id + ": cannot transition from " + status + " to new state (expected " + required + ")");
        }
    }

    private void requireFromAny(Status... allowed) {
        for (Status s : allowed) {
            if (this.status == s) return;
        }
        throw new IllegalStateException("Payment " + id + ": state " + status + " not allowed for this transition");
    }

    // ─── getters ──────────────────────────────────────────────────────────

    public String getId() { return id; }
    public UUID getSagaId() { return sagaId; }
    public String getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getProvider() { return provider; }
    public String getProviderRef() { return providerRef; }
    public Status getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
