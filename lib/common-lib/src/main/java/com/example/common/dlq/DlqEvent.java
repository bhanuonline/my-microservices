package com.example.common.dlq;

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

/**
 * Row-per-poison-message record. Written by {@code DlqObserver}; queried /
 * acted on by the DLQ admin controller.
 *
 * One row = one message that failed in-process retries and the binder's
 * configured max-attempts, then landed on error.&lt;topic&gt;.&lt;group&gt; (Cloud Stream)
 * or &lt;topic&gt;.DLT (plain spring-kafka).
 */
@Entity
@Table(
    name = "dlq_events",
    indexes = {
        @Index(name = "idx_dlq_status", columnList = "status"),
        @Index(name = "idx_dlq_received", columnList = "received_at")
    }
)
public class DlqEvent {

    public enum Status {
        /** Just landed; needs human review. */
        NEW,
        /** Operator published it back to the original topic. */
        REPLAYED,
        /** Operator decided to drop it; keep the row for audit. */
        ACKNOWLEDGED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dlq_topic", length = 255, nullable = false)
    private String dlqTopic;

    @Column(name = "original_topic", length = 255)
    private String originalTopic;

    @Column(name = "original_partition")
    private Integer originalPartition;

    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "message_key", length = 512)
    private String messageKey;

    @Lob
    @Column(name = "payload")
    private String payload;

    @Column(name = "exception_class", length = 255)
    private String exceptionClass;

    @Lob
    @Column(name = "exception_message")
    private String exceptionMessage;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "status_updated_at")
    private Instant statusUpdatedAt;

    @Column(name = "status_updated_by", length = 128)
    private String statusUpdatedBy;

    protected DlqEvent() {}

    public DlqEvent(String dlqTopic, String originalTopic, Integer originalPartition,
                    Long originalOffset, String messageKey, String payload,
                    String exceptionClass, String exceptionMessage) {
        this.dlqTopic = dlqTopic;
        this.originalTopic = originalTopic;
        this.originalPartition = originalPartition;
        this.originalOffset = originalOffset;
        this.messageKey = messageKey;
        this.payload = payload;
        this.exceptionClass = exceptionClass;
        this.exceptionMessage = exceptionMessage;
        this.receivedAt = Instant.now();
        this.status = Status.NEW;
    }

    public void markReplayed(String operator) {
        this.status = Status.REPLAYED;
        this.statusUpdatedAt = Instant.now();
        this.statusUpdatedBy = operator;
    }

    public void markAcknowledged(String operator) {
        this.status = Status.ACKNOWLEDGED;
        this.statusUpdatedAt = Instant.now();
        this.statusUpdatedBy = operator;
    }

    public Long getId() { return id; }
    public String getDlqTopic() { return dlqTopic; }
    public String getOriginalTopic() { return originalTopic; }
    public Integer getOriginalPartition() { return originalPartition; }
    public Long getOriginalOffset() { return originalOffset; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public String getExceptionClass() { return exceptionClass; }
    public String getExceptionMessage() { return exceptionMessage; }
    public Instant getReceivedAt() { return receivedAt; }
    public Status getStatus() { return status; }
    public Instant getStatusUpdatedAt() { return statusUpdatedAt; }
    public String getStatusUpdatedBy() { return statusUpdatedBy; }
}
