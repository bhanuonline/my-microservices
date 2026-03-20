package notification.core;

import notification.core.artifact.ArtifactStore;
import notification.integration.email.model.EmailPayload;

class EmailContext extends NotificationContext {
    private EmailPayload payload;

    EmailContext(NotificationType type, NotificationTarget target, String message, ArtifactStore artifacts) {
        super(type, target, message, artifacts);
    }
}
