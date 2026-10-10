package com.example.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-side idempotency ledger. One row = "this consumer has handled this event".
 *
 * Primary key is (eventId, consumer) via a composite string key assembled in
 * {@link IdempotencyGuard} — a single eventId can be claimed independently by
 * different consumers (e.g. two services consuming the same topic), while a
 * replay to the same consumer collides and gets rejected.
 *
 * The dedup trick works ONLY if the insert is a true INSERT (not a merge). See
 * IdempotencyGuard for the entity-manager persist() dance that makes this real.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    /** composite key: eventId + "|" + consumer, SHA-stable via nameUUIDFromBytes */
    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String consumer;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(UUID id, String consumer) {
        this.id = id;
        this.consumer = consumer;
        this.processedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getConsumer() { return consumer; }
    public Instant getProcessedAt() { return processedAt; }
}
