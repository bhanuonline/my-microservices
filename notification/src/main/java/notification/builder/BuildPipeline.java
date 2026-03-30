package notification.builder;

import notification.builder.BuildStep;
import notification.core.NotificationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BuildPipeline {
    private List<BuildStep> steps;
    public void run(NotificationContext ctx) {
        steps.forEach(step -> step.execute(ctx));
    }
}