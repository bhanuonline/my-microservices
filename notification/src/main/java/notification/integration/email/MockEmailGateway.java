package notification.integration.email;

import notification.core.NotificationContext;
import notification.integration.email.model.EmailPayload;
import org.springframework.stereotype.Component;

@Component
public class MockEmailGateway implements EmailGateway {

    @Override
    public void send(EmailPayload payload) {
        System.out.println(
            "📧 EMAIL SENT to " + payload.getRecipient() +
            " | Message: " + payload.getBody()
        );
    }
}
