package notification.core;

import lombok.Builder;
import lombok.Getter;
import notification.core.recipient.Recipient;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class NotificationTarget {

    /**
     * Email recipients
     */
    private final List<String> emails;

    /**
     * SMS recipients (E.164 format)
     */
    private final List<String> phoneNumbers;

    private final List<Recipient> recipients;

    /**
     * Push notification tokens
     */
    private final List<String> deviceTokens;

    /**
     * Extensible attributes (locale, timezone, etc.)
     */
    private final Map<String, String> attributes;
}
