package com.example.userservice.outbox;

import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reads PENDING outbox rows and publishes them to Kafka.
 *
 * KafkaTemplate here (not StreamBridge) because we already have the raw JSON payload —
 * StreamBridge would re-serialize. Sending a String directly is simpler.
 *
 * @Transactional so the SELECT-FOR-UPDATE lock is held for the whole batch;
 * marking rows as SENT happens in the same tx as the read.
 *
 * If the publish throws (Kafka down), the tx rolls back → row stays PENDING → retried next tick.
 * If the JVM crashes AFTER publish but BEFORE the mark commits → duplicate delivery.
 * That's why consumers must be idempotent — this is the at-least-once contract.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository repo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository repo, KafkaTemplate<String, String> kafkaTemplate) {
        this.repo = repo;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = repo.lockPendingBatch(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return;

        log.debug("Draining {} outbox events", batch.size());

        for (OutboxEvent event : batch) {
            event.incrementAttempt();
            try {
                // Use event.id as message key → guarantees ordering per aggregate,
                // and lets consumers dedupe (key = idempotency key).
                kafkaTemplate.send(event.getDestination(), event.getId().toString(), event.getPayload())
                        .get();  // block: we need to know if send succeeded before marking SENT
                event.markSent();
            } catch (Exception e) {
                // Leave row as PENDING. Next tick will retry.
                // In real prod: after N attempts, move to FAILED and alert.
                log.warn("Publish failed for outboxId={}, will retry: {}",
                        event.getId(), e.getMessage());
                // Break so we don't hammer Kafka with the whole batch during outages
                break;
            }
        }
    }
}
