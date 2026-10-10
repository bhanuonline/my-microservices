package com.example.orderservice.saga.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Thin fire-and-forget writer for the saga audit log. Call sites hand it the
 * event; recorder serializes and persists. Writes run in a REQUIRES_NEW tx
 * so that a failure to record never aborts the business tx that triggered it.
 */
@Component
public class SagaRecorder {

    private static final Logger log = LoggerFactory.getLogger(SagaRecorder.class);

    private final SagaStepRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public SagaRecorder(SagaStepRepository repo) { this.repo = repo; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCommand(UUID sagaId, String stepName, Object payload) {
        safeSave(new SagaStep(sagaId, stepName, SagaStep.Direction.EMIT_COMMAND,
                SagaStep.Outcome.PENDING, toJson(payload), null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordReply(UUID sagaId, String stepName, boolean success, Object payload, String failureReason) {
        safeSave(new SagaStep(sagaId, stepName, SagaStep.Direction.RECEIVE_REPLY,
                success ? SagaStep.Outcome.SUCCEEDED : SagaStep.Outcome.FAILED,
                toJson(payload), failureReason));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCompensation(UUID sagaId, String stepName, Object payload, String reason) {
        safeSave(new SagaStep(sagaId, stepName, SagaStep.Direction.COMPENSATION,
                SagaStep.Outcome.PENDING, toJson(payload), reason));
    }

    private void safeSave(SagaStep step) {
        try {
            repo.save(step);
        } catch (RuntimeException e) {
            // Never fail the parent tx on an audit write — surface at WARN and move on.
            log.warn("saga audit write failed sagaId={} step={}: {}",
                    step.getSagaId(), step.getStepName(), e.getMessage());
        }
    }

    private String toJson(Object v) {
        if (v == null) return null;
        try { return mapper.writeValueAsString(v); }
        catch (JsonProcessingException e) { return "{\"error\":\"serialize-failed\"}"; }
    }
}
