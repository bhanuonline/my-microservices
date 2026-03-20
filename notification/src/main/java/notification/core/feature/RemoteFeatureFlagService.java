package notification.core.feature;

import notification.core.NotificationContext;
import org.springframework.stereotype.Component;

@Component
public class RemoteFeatureFlagService implements FeatureFlagService {


    @Override
    public boolean isEnabled(String flagKey, NotificationContext context) {
        return true;
    }
}
