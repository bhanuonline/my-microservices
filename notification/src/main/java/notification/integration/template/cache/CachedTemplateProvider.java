package notification.integration.template.cache;

import notification.core.NotificationType;
import notification.core.template.CmsTemplateProvider;
import notification.core.template.Template;
import notification.core.template.TemplateProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CachedTemplateProvider
        implements TemplateProvider {

    private final TemplateProvider delegate;
    private final Map<String, Template> cache =
            new ConcurrentHashMap<>();

    public CachedTemplateProvider(
            CmsTemplateProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<Template> findTemplate(
            NotificationType type,
            String language,
            String version,
            String variant) {

        String key = key(type, language, version, variant);

        return Optional.ofNullable(
                cache.computeIfAbsent(
                        key,
                        k -> delegate
                                .findTemplate(type, language, version, variant)
                                .orElse(null)
                )
        );
    }

    private String key(NotificationType type,String language,
                       String version,
                       String variant) {
        return type + "|" + language + "|" + version + "|" + variant;
    }

    // 🔁 Refresh hook (scheduled / webhook)
    public void evictAll() {
        cache.clear();
    }
}
