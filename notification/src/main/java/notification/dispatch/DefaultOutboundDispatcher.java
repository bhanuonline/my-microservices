package notification.dispatch;

import lombok.RequiredArgsConstructor;
import notification.core.NotificationContext;
import notification.integration.email.EmailGateway;
import notification.integration.email.artifact.EmailPayloadArtifact;
import notification.integration.email.model.EmailPayload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultOutboundDispatcher
        implements OutboundDispatcher {

    private final EmailGateway emailGateway;

    @Override
    public void dispatch(NotificationContext context) {
        //EmailPayload payload = (EmailPayload) context.getMetadata().get("EMAIL_PAYLOAD");
        //emailGateway.send(payload);

        EmailPayloadArtifact artifact =
                context.getArtifacts().get(EmailPayloadArtifact.class);

        artifact.getPayloads().forEach(emailGateway::send);
    }
}
