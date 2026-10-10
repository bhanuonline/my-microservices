package com.example.paymentservice.event;

import com.example.common.event.PaymentCompletedEvent;
import com.example.common.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private static final String COMPLETED_BINDING = "paymentCompleted-out-0";
    private static final String FAILED_BINDING = "paymentFailed-out-0";

    private final StreamBridge streamBridge;

    public PaymentEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void publishCompleted(PaymentCompletedEvent event) {
        log.info("Publishing PaymentCompletedEvent orderId={}", event.orderId());
        streamBridge.send(COMPLETED_BINDING, event);
    }

    public void publishFailed(PaymentFailedEvent event) {
        log.info("Publishing PaymentFailedEvent orderId={} reason={}", event.orderId(), event.reason());
        streamBridge.send(FAILED_BINDING, event);
    }
}
