package com.example.orderservice.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    public enum Status {
        CREATED,     // just placed
        PAID,        // payment succeeded
        CANCELLED    // payment failed → cancelled
    }

    @Id
    private String id;                       // UUID string

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    protected Order() {}

    public Order(String id, Long productId, Integer quantity, BigDecimal amount) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = Status.CREATED;
        this.createdAt = Instant.now();
    }

    // State machine — refuses illegal transitions.
    public void markPaid() {
        if (status == Status.CREATED) {
            this.status = Status.PAID;
            this.updatedAt = Instant.now();
        } else if (status == Status.PAID) {
            // Idempotent: already paid, silently accept
        } else {
            throw new IllegalStateException("Cannot mark PAID from " + status);
        }
    }

    public void markCancelled(String reason) {
        if (status == Status.CREATED) {
            this.status = Status.CANCELLED;
            this.updatedAt = Instant.now();
        } else if (status == Status.CANCELLED) {
            // Idempotent
        } else {
            throw new IllegalStateException("Cannot mark CANCELLED from " + status);
        }
    }

    public String getId() { return id; }
    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
