package notification.core.template;

import lombok.RequiredArgsConstructor;
import notification.core.NotificationType;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 📌 Repository = DB / CMS / API
 * 📌 No logic here — just retrieval
 */
@Component
@RequiredArgsConstructor
@Primary
public class CmsTemplateProvider implements TemplateProvider {

    private final TemplateRepository repository;

    @Override
    public Optional<Template> findTemplate(
            NotificationType type,
            String language,
            String version,
            String variant) {

        return repository.find(type, language, version, variant
        );
    }
}
