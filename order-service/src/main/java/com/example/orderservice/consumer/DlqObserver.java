package com.example.orderservice.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Watches DLQ topics and logs anything that lands there.
 *
 * Why raw @KafkaListener (not Cloud Stream)?
 *   - DLQs across many topics — we just want to observe, not process business events.
 *   - Cloud Stream's binding model expects one type per binding; DLQ payloads
 *     are raw bytes with useful headers. Simpler to use @KafkaListener directly.
 *
 * In production you'd probably:
 *   - Save to a `dlq_events` table with acknowledged=false.
 *   - Trigger a PagerDuty alert.
 *   - Provide an admin UI to inspect + replay.
 */
@Component
public class DlqObserver {

    private static final Logger log = LoggerFactory.getLogger(DlqObserver.class);

    // topicPattern subscribes to anything ending in .DLT — one listener catches them all.
    @KafkaListener(topicPattern = ".*\\.DLT", groupId = "order-service-dlq-observer")
    public void onDeadLetter(Message<byte[]> message) {
        // Cloud Stream stamps DLQ messages with these headers.
        Object originalTopic = message.getHeaders().get("x-original-topic");
        Object exceptionMessage = message.getHeaders().get("x-exception-message");
        Object originalOffset = message.getHeaders().get("x-original-offset");

        String payloadPreview = new String(message.getPayload(),
                java.nio.charset.StandardCharsets.UTF_8);
        if (payloadPreview.length() > 300) {
            payloadPreview = payloadPreview.substring(0, 300) + "...(truncated)";
        }

        log.error("DLQ received | originalTopic={} originalOffset={} exception={} payload={}",
                originalTopic, originalOffset, exceptionMessage, payloadPreview);
    }
}
