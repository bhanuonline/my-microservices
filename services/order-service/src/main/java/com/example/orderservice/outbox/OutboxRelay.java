package com.example.orderservice.outbox;

import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls outbox_events and publishes PENDING rows to Kafka inside a Kafka transaction.
 *
 * Why KafkaTemplate.executeInTransaction?
 *   Without it, each send() is auto-committed individually. If the JVM dies after
 *   send #3 of a batch of 10, 3 are visible to consumers but 7 are not — and
 *   the DB rows are still PENDING, so they'll be re-sent on the next tick,
 *   generating duplicates. The consumer dedup handles the duplicates, but it's
 *   wasted work and noisy.
 *
 *   With executeInTransaction: either ALL 10 sends commit atomically or NONE do.
 *   Consumers with isolation.level=read_committed see the whole batch or nothing.
 *
 * Does this give us EXACTLY-ONCE across the DB AND Kafka?
 *   No. The outer @Transactional (JPA) and the Kafka transaction are independent.
 *   If the Kafka tx commits but the JVM dies before the DB commit updates
 *   outbox_events.status=SENT, the next poll re-sends. That's at-least-once from
 *   DB's perspective → consumer dedup still required. This is the normal outbox
 *   contract; chaining tx managers is deprecated in Spring Kafka 3.x.
 */
/*
 * Gated by outbox.polling-enabled — set to false under the docker-cdc profile
 * so Debezium owns outbox publishing and we don't double-publish.
 */
@Component
@ConditionalOnProperty(prefix = "outbox", name = "polling-enabled", havingValue = "true", matchIfMissing = true)
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
    @Transactional("transactionManager")  // explicit — kafkaTransactionManager also exists now
    public void drain() {
        List<OutboxEvent> batch = repo.lockPendingBatch(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return;

        try {
            kafkaTemplate.executeInTransaction(ops -> {
                for (OutboxEvent event : batch) {
                    event.incrementAttempt();
                    ops.send(event.getDestination(),
                            event.getId().toString(),
                            event.getPayload());
                }
                return null;
            });
            // Kafka transaction committed → mark all as SENT (will be flushed by the outer tx).
            batch.forEach(OutboxEvent::markSent);
            log.debug("Published outbox batch size={}", batch.size());
        } catch (Exception e) {
            // Kafka tx aborted → rows stay PENDING, next tick retries. attemptCount already incremented.
            log.warn("Outbox batch publish failed size={}: {}", batch.size(), e.getMessage());
        }
    }
}
