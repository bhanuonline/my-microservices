package notification.core.template;

import notification.core.NotificationType;

import java.util.Optional;

public interface TemplateRepository {

    Optional<Template> find(
            NotificationType type,
            String language,
            String version,
            String variant
    );
}
