package notification.builder;

import notification.core.NotificationContext;
import org.springframework.stereotype.Component;

@Component
public class ValidationStep implements BuildStep {

    @Override
    public void execute(NotificationContext context) {
        if (context.getTarget().getRecipients() == null || context.getMessage() == null) {
            throw new IllegalArgumentException("Invalid request");
        }
    }
}
