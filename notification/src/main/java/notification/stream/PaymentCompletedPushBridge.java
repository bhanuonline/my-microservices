package notification.stream;

import com.example.common.event.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka → SSE bridge. When payment.completed arrives, push a notification
 * to any browser subscribed to {@code /notifications/stream/{orderId}}.
 *
 * We key by orderId (not userId) because PaymentCompletedEvent doesn't carry
 * userId today. In production the event would include userId and we'd push
 * to the user's subscription.
 */
@Component
public class PaymentCompletedPushBridge {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompletedPushBridge.class);

    private final NotificationStreamController stream;

    public PaymentCompletedPushBridge(NotificationStreamController stream) {
        this.stream = stream;
    }

    @KafkaListener(topics = "payment.completed", groupId = "notification-sse-bridge",
            containerFactory = "paymentCompletedListenerFactory")
    public void onCompleted(PaymentCompletedEvent event) {
        log.info("pushing payment.completed via SSE orderId={}", event.orderId());
        stream.push(event.orderId(), "payment-completed", Map.of(
                "orderId", event.orderId(),
                "paymentId", event.paymentId(),
                "amount", event.amount(),
                "completedAt", event.completedAt().toString()
        ));
    }
}
