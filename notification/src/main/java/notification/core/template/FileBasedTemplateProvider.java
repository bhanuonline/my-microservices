package notification.core.template;

import notification.core.NotificationType;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
public class FileBasedTemplateProvider implements TemplateProvider {

    private static final String DEFAULT_LANG = "en";

    // Only body is stored here (simple file-based source)
    private final Map<String, String> templates = Map.of(
            "WELCOME_en", "Hello {{name}}, welcome!",
            "WELCOME_fr", "Bonjour {{name}}, bienvenue!",
            "WELCOME_hi", "नमस्ते {{name}}, आपका स्वागत है!"
    );

    @Override
    public Optional<Template> findTemplate(
            NotificationType type,
            String language,
            String version,
            String variant) {

        // 1️⃣ Try requested language
        String body = templates.get(key(type, language));

        if (body != null) {
            return Optional.of(
                    buildTemplate(type, language, version, variant, body)
            );
        }

        // 2️⃣ Fallback to default language (en)
        body = templates.get(key(type, DEFAULT_LANG));

        if (body != null) {
            return Optional.of(
                    buildTemplate(type, DEFAULT_LANG, version, variant, body)
            );
        }

        return Optional.empty();
    }

    private Template buildTemplate(
            NotificationType type,
            String language,
            String version,
            String variant,
            String body) {

        return new Template(
                type,
                language,
                version,
                variant,
                body
        );
    }

    private String key(NotificationType type, String language) {
        return type.name() + "_" + language;
    }
}
