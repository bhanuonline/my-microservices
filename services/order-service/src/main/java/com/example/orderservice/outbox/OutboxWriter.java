package com.example.orderservice.outbox;

import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Writes events to the outbox table inside the same @Transactional as the
 * domain write. If the tx rolls back, the event vanishes with it — no dual-write.
 * The OutboxRelay publishes to Kafka later.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repo;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxEventRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    public void write(String aggregateType, String destinationTopic, Object payload) {
        try {
            repo.save(new OutboxEvent(aggregateType, destinationTopic, objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
