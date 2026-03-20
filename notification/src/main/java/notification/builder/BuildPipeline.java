package notification.builder;

import lombok.RequiredArgsConstructor;
import notification.core.NotificationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BuildPipeline {

    private final List<BuildStep> steps;

    public void run(NotificationContext context) {
        steps.forEach(step -> step.execute(context));
    }
}
