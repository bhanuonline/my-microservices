package notification.integration.sms;

import lombok.AllArgsConstructor;
import lombok.Getter;
import notification.core.recipient.Recipient;

@Getter
@AllArgsConstructor
public class SmsRecipient implements Recipient {
    private final String phoneNumber;
    private final String language;

    @Override
    public String id() {
        return phoneNumber;
    }

    @Override
    public String language() {
        return language;
    }
}
