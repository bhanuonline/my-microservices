package notification.routing;

import notification.core.NotificationContext;
import notification.core.NotificationType;

public interface ChannelHandler {
    NotificationType type();
    void handle(NotificationContext ctx);
}