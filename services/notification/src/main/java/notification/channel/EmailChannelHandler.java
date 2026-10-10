package notification.channel;

import lombok.RequiredArgsConstructor;
import notification.builder.BuildPipeline;
import notification.core.NotificationContext;
import notification.core.NotificationType;
import notification.dispatch.OutboundDispatcher;
import notification.routing.ChannelHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailChannelHandler implements ChannelHandler {

    private final BuildPipeline pipeline;
    private final OutboundDispatcher dispatcher;

    @Override
    public NotificationType type() {
        return NotificationType.EMAIL;
    }

    @Override
    public void handle(NotificationContext context) {
        pipeline.run(context);
        dispatcher.dispatch(context);
    }
}
