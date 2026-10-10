package com.example.orderservice.consumer;

import com.example.common.dlq.DlqEvent;
import com.example.common.dlq.DlqEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Catches every DLQ-bound message and persists it to {@code dlq_events} for
 * operator review via the admin API. Previously only logged; Tier 2 upgrade.
 *
 * Pattern matches both naming schemes:
 *   - error.&lt;topic&gt;.&lt;group&gt;  (Spring Cloud Stream Kafka binder)
 *   - &lt;topic&gt;.DLT              (plain spring-kafka @RetryableTopic / DefaultErrorHandler)
 */
@Component
public class DlqObserver {

    private static final Logger log = LoggerFactory.getLogger(DlqObserver.class);
    private static final int LOG_PAYLOAD_PREVIEW = 300;

    private final DlqEventRepository repo;

    public DlqObserver(DlqEventRepository repo) {
        this.repo = repo;
    }

    @KafkaListener(topicPattern = "error\\..*|.*\\.DLT", groupId = "order-service-dlq-observer")
    public void onDeadLetter(Message<byte[]> message) {
        MessageHeaders h = message.getHeaders();
        String dlqTopic = asString(h.get(KafkaHeaders.RECEIVED_TOPIC));
        String originalTopic = asString(h.get("x-original-topic"));
        Integer originalPartition = asInt(h.get("x-original-partition"));
        Long originalOffset = asLong(h.get("x-original-offset"));
        String key = asString(h.get(KafkaHeaders.RECEIVED_KEY));
        String exceptionClass = asString(h.get("x-exception-fqcn"));
        String exceptionMessage = asString(h.get("x-exception-message"));

        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);

        DlqEvent saved = repo.save(new DlqEvent(
                dlqTopic, originalTopic, originalPartition, originalOffset,
                key, payload, exceptionClass, exceptionMessage));

        String preview = payload.length() <= LOG_PAYLOAD_PREVIEW
                ? payload : payload.substring(0, LOG_PAYLOAD_PREVIEW) + "...(truncated)";
        log.error("DLQ persisted id={} dlqTopic={} originalTopic={} originalOffset={} exception={} payload={}",
                saved.getId(), dlqTopic, originalTopic, originalOffset, exceptionMessage, preview);
    }

    private static String asString(Object o) { return o == null ? null : o.toString(); }
    private static Integer asInt(Object o)   { return o instanceof Number n ? n.intValue()  : null; }
    private static Long asLong(Object o)     { return o instanceof Number n ? n.longValue() : null; }
}
