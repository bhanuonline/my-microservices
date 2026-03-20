package notification.builder;

import notification.core.NotificationContext;

public interface BuildStep {
    void execute(NotificationContext context);
}
