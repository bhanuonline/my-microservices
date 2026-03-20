package notification.integration.email.model;

import lombok.Builder;
import lombok.Data;
import notification.integration.email.EmailRecipient;

@Data
@Builder
public class EmailPayload {
    private EmailRecipient  recipient;
    private String subject;
    private String body;
}
