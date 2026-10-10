package notification.core;

import lombok.Data;
import java.util.Map;

@Data
public class NotificationContext {
    private NotificationType type;
    private String recipient;
    private String message;
    private Map<String, Object> metadata;
}