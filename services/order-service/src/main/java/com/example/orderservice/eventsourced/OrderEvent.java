package com.example.orderservice.eventsourced;

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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One row per state change on an Order aggregate. Append-only; UPDATEs/DELETEs
 * are forbidden by convention. The unique constraint on (order_id, version)
 * enforces optimistic concurrency: two concurrent appends at the same version
 * can't both commit.
 */
@Entity
@Table(
    name = "order_events",
    uniqueConstraints = @UniqueConstraint(name = "uk_order_events_version", columnNames = {"order_id", "version"}),
    indexes = @Index(name = "idx_order_events_order", columnList = "order_id, seq")
)
public class OrderEvent {

    public enum Type {
        /** Aggregate created. */
        OrderCreated,
        /** Payment authorized / reserved. */
        PaymentReserved,
        /** Payment confirmed and charged. */
        PaymentCompleted,
        /** Payment failed; aggregate stays in PENDING or transitions to CANCELLED. */
        PaymentFailed,
        /** Order cancelled by user or system. */
        OrderCancelled,
        /** Order fulfilled / shipped. */
        OrderFulfilled
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seq;

    @Column(name = "order_id", length = 64, nullable = false)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 32, nullable = false)
    private Type eventType;

    /** JSON payload — denormalised but stable per event type. */
    @Lob
    @Column(nullable = false)
    private String payload;

    /** Aggregate version after this event is applied. Starts at 1 for OrderCreated. */
    @Column(nullable = false)
    private int version;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected OrderEvent() {}

    public OrderEvent(String orderId, Type eventType, String payload, int version) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.payload = payload;
        this.version = version;
        this.occurredAt = Instant.now();
    }

    public Long getSeq() { return seq; }
    public String getOrderId() { return orderId; }
    public Type getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public int getVersion() { return version; }
    public Instant getOccurredAt() { return occurredAt; }
}
