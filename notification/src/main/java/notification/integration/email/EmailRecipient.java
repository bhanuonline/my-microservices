package notification.integration.email;

import lombok.AllArgsConstructor;
import lombok.Getter;
import notification.core.recipient.Recipient;

@Getter
@AllArgsConstructor
public class EmailRecipient implements Recipient {
    private final String email;
    private final String language;

    @Override
    public String id() {
        return email;
    }

    @Override
    public String language() {
        return language;
    }
}
