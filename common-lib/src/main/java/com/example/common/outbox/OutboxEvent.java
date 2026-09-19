package com.example.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic outbox row. Every service that emits events writes to its own table
 * (named "outbox_events" by convention). Poller drains it into Kafka.
 *
 * Design notes:
 *  - id is a UUID → becomes the eventId consumers dedupe on. Idempotency key.
 *  - status transitions: PENDING → SENT (or PENDING → FAILED after N attempts).
 *  - aggregateType = "user" / "order" / etc. Lets the poller route to different topics.
 *  - destination = full Kafka topic name → poller can be dumb.
 *  - payload = JSON blob of the actual event.
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status_created", columnList = "status, createdAt")
})
public class OutboxEvent {

    public enum Status { PENDING, SENT, FAILED }

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false, length = 200)
    private String destination;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    @Column(nullable = false)
    private int attemptCount;

    protected OutboxEvent() {}

    public OutboxEvent(String aggregateType, String destination, String payload) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.destination = destination;
        this.payload = payload;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
        this.attemptCount = 0;
    }

    public void markSent() {
        this.status = Status.SENT;
        this.sentAt = Instant.now();
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public String getDestination() { return destination; }
    public String getPayload() { return payload; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public int getAttemptCount() { return attemptCount; }
}
