package notification.integration.email.impl;

import lombok.extern.slf4j.Slf4j;
import notification.integration.email.EmailGateway;
import notification.integration.email.model.EmailPayload;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@Primary
public class SmtpEmailGateway implements EmailGateway {

    @Override
    public void send(EmailPayload payload) {
        // SMTP send logic
        log.info("Sending email to " + payload.getRecipient().getEmail());
    }

    @Override
    public void sendBatch(List<EmailPayload> payloads) {
        // REAL batch logic (SMTP / SES / SendGrid)
        log.info("Sending batch email: " + payloads.size());
    }
}
