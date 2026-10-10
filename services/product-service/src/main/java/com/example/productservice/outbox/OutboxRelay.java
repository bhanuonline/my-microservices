package com.example.productservice.outbox;

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
 * Publishes outbox batches to Kafka inside a Kafka transaction so consumers
 * with isolation.level=read_committed see the whole batch atomically.
 * See order-service OutboxRelay for the full design notes.
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
            batch.forEach(OutboxEvent::markSent);
            log.debug("Published outbox batch size={}", batch.size());
        } catch (Exception e) {
            log.warn("Outbox batch publish failed size={}: {}", batch.size(), e.getMessage());
        }
    }
}
