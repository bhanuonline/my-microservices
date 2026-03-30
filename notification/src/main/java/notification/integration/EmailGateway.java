package notification.integration;

import notification.core.NotificationContext;

public interface EmailGateway {
    void send(NotificationContext context);
}
