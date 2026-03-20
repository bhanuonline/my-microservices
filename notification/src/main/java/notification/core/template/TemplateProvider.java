package notification.core.template;

import notification.core.NotificationType;

import java.util.Optional;

public interface TemplateProvider {
    Optional<Template> findTemplate(
            NotificationType type,
            String language,
            String version,
            String variant
    );
}
