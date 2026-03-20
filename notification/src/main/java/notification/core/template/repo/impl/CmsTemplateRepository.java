package notification.core.template.repo.impl;

import notification.core.NotificationType;
import notification.core.template.Template;
import notification.core.template.TemplateRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CmsTemplateRepository implements TemplateRepository {

    @Override
    public Optional<Template> find(
            NotificationType type,
            String language,
            String version,
            String variant
    ) {
        // Simulate CMS fetch
        if ("fr".equals(language)) {
            return Optional.of(new Template(
                    type, language, version, variant,
                    "Bonjour {{name}}, bienvenue!"
            ));
        }
        return Optional.empty();
    }
}
