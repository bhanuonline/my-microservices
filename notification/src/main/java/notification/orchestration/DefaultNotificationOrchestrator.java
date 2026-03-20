package notification.orchestration;

import lombok.RequiredArgsConstructor;
import notification.core.NotificationContext;
import notification.routing.ChannelRouter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultNotificationOrchestrator
        implements NotificationOrchestrator {

    private final ChannelRouter router;

    @Override
    public void orchestrate(NotificationContext context) {
        router.route(context.getType()).handle(context);
    }
}
