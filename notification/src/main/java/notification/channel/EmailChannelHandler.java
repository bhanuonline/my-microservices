package notification.integration;

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
