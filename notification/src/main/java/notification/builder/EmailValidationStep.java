package notification.builder;

import notification.core.NotificationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmailValidationStep implements BuildStep {

    @Override
    public void execute(NotificationContext ctx) {

        List<String> emails = ctx.getTarget().getEmails();
        if (emails == null || emails.isEmpty()) {
            throw new IllegalArgumentException("At least one email is required");
        }
    }
}
