package notification.core.template;

import lombok.AllArgsConstructor;
import lombok.Getter;
import notification.core.NotificationType;

/**
 * 📌 Immutable
 * 📌 Version + Variant are first-class
 */
@Getter
@AllArgsConstructor
public class Template {

    private final NotificationType type;
    private final String language;
    private final String version;   // v1, v2
    private final String variant;   // A, B
    private final String body;
}
