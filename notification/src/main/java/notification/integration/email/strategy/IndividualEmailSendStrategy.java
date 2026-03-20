package notification.integration.email.strategy;

import lombok.RequiredArgsConstructor;
import notification.dispatch.strategy.IndividualSendStrategy;
import notification.dispatch.strategy.SendStrategy;
import notification.integration.email.EmailGateway;
import notification.integration.email.model.EmailPayload;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class IndividualEmailSendStrategy
        implements SendStrategy<EmailPayload>, IndividualSendStrategy {

    private final EmailGateway emailGateway;

    @Override
    public void send(List<EmailPayload> payloads) {
        for (EmailPayload payload : payloads) {
            emailGateway.send(payload);
        }
    }
}