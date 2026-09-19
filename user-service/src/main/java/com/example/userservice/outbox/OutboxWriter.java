package com.example.userservice.outbox;

import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Writes events to the outbox table. Called from within the same @Transactional
 * boundary as the domain write — that's what makes the pattern work.
 *
 * DO NOT publish to Kafka here. That's the OutboxRelay's job.
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
        String json = toJson(payload);
        repo.save(new OutboxEvent(aggregateType, destinationTopic, json));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Payload should always be a POJO/record. If serialization fails,
            // we CANNOT persist the event → fail the transaction loudly.
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
