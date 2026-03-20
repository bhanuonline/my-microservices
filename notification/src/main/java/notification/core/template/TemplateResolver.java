package notification.core.template;


import lombok.RequiredArgsConstructor;
import notification.core.NotificationType;
import notification.core.exception.TemplateNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TemplateResolver {

    private static final String DEFAULT_LANGUAGE = "en";

    private final TemplateProvider provider;

    public Template resolve(
            NotificationType type,
            String language,
            String version,
            String variant) {

        // 1️⃣ Try requested language
        return provider.findTemplate(type, language, version, variant)
                // 2️⃣ Fallback to English
                .or(() ->
                        provider.findTemplate(
                                type,
                                DEFAULT_LANGUAGE,
                                version,
                                variant
                        )
                )
                .orElseThrow(() ->
                        new TemplateNotFoundException(
                                type, language,version,variant
                        )
                );
    }
}
