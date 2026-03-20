package notification.integration;

import notification.core.NotificationContext;
import org.springframework.stereotype.Component;

@Component
public class MockEmailGateway implements EmailGateway {

    @Override
    public void send(NotificationContext context) {
        System.out.println(
            "📧 EMAIL SENT to " + context.getRecipient() +
            " | Message: " + context.getMessage()
        );
    }
}
