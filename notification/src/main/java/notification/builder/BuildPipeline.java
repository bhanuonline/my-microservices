package notification.builder;

import notification.core.NotificationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BuildPipeline {

    // Constructor-injected list of all BuildStep beans in the context.
    // If none are @Component-annotated (currently the case), Spring injects an empty list.
    private final List<BuildStep> steps;

    public BuildPipeline(List<BuildStep> steps) {
        this.steps = steps;
    }

    public void run(NotificationContext ctx) {
        steps.forEach(step -> step.execute(ctx));
    }
}
