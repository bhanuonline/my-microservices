package notification.orchestration;

import notification.core.NotificationContext;

public interface NotificationOrchestrator {
    void orchestrate(NotificationContext context);
}
