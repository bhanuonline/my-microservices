package notification.dispatch;

import notification.core.NotificationContext;

public interface OutboundDispatcher {
    void dispatch(NotificationContext context);
}
