package notification.core.experiment;

import notification.core.recipient.Recipient;
import org.springframework.stereotype.Component;

/**
 * 📌 Deterministic
 * 📌 No user flickering
 * 📌 Zero persistence required
 */
@Component
public class TemplateVariantResolver {

    public String resolveVariant(Recipient recipient) {
        // Stable hashing → same user gets same variant
        return Math.abs(recipient.id().hashCode()) % 2 == 0
                ? "A"
                : "B";
    }
}
