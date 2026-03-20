package notification.core.template;

import lombok.RequiredArgsConstructor;
import notification.core.NotificationType;
import notification.core.template.repo.impl.CmsTemplateRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@RequiredArgsConstructor
@Primary
public class CachedTemplateRepository implements TemplateRepository {

    private final CmsTemplateRepository cmsRepo;
    private final ConcurrentHashMap<String, Template> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<Template> find(
            NotificationType type, String language, String version, String variant
    ) {
        String key = String.join("|", type.name(), language, version, variant);

        return Optional.ofNullable(
                cache.computeIfAbsent(key, k ->
                        cmsRepo.find(type, language, version, variant).orElse(null)
                )
        );
    }
}
