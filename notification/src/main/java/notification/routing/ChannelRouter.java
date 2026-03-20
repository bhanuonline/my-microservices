package notification.routing;

import notification.core.NotificationType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ChannelRouter {

    private final Map<NotificationType, ChannelHandler> handlers;

    public ChannelRouter(List<ChannelHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(ChannelHandler::type, h -> h));
    }

    public ChannelHandler route(NotificationType type) {
        return handlers.get(type);
    }
}
