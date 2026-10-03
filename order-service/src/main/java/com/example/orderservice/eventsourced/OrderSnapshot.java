package com.example.orderservice.eventsourced;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Periodic snapshot of aggregate state so loads don't replay every event.
 * One row per orderId — latest wins (we overwrite as we take new snapshots).
 */
@Entity
@Table(name = "order_snapshots")
public class OrderSnapshot {

    @Id
    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(nullable = false)
    private int version;

    @Lob
    @Column(nullable = false)
    private String state;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    protected OrderSnapshot() {}

    public OrderSnapshot(String orderId, int version, String state) {
        this.orderId = orderId;
        this.version = version;
        this.state = state;
        this.takenAt = Instant.now();
    }

    public String getOrderId() { return orderId; }
    public int getVersion() { return version; }
    public String getState() { return state; }
    public Instant getTakenAt() { return takenAt; }
}
