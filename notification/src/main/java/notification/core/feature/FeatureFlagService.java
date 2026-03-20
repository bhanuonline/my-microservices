package notification.core.feature;

import notification.core.NotificationContext;

public interface FeatureFlagService {
    boolean isEnabled(String flagKey, NotificationContext context);
}
