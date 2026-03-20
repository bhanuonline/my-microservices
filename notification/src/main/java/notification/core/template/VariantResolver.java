package notification.core.template;


import notification.core.recipient.Recipient;
import org.springframework.stereotype.Component;

@Component
public class VariantResolver {

    public String resolveVariant(Recipient recipient) {
        // Simple deterministic split
        return recipient.id().hashCode() % 2 == 0 ? "A" : "B";
    }
}
