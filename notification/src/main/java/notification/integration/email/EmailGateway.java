package notification.integration.email;

import notification.core.NotificationContext;
import notification.integration.email.model.EmailPayload;

import java.util.List;

public interface EmailGateway {
    void send(EmailPayload payload);

    /**
     * 📌 Why default?
     * Not all providers support batch
     * Keeps backward compatibility
     * Strategy stays clean
     * @param payloads
     */
    default void sendBatch(List<EmailPayload> payloads) {
        // fallback if gateway doesn't support batch
        payloads.forEach(this::send);
    }
}
