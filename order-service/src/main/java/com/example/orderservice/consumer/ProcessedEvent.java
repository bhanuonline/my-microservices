package com.example.orderservice.consumer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-side idempotency ledger.
 * We INSERT into this table BEFORE handling an event.
 * Unique primary key (eventId) → duplicate INSERT throws → we know it's a replay → skip.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false, length = 100)
    private String consumer;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(UUID eventId, String consumer) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.processedAt = Instant.now();
    }

    public UUID getEventId() { return eventId; }
}
