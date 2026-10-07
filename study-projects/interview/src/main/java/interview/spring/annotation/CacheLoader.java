package interview.spring.annotation;

import interview.spring.annotation.service.CacheService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CacheLoader {

    private final CacheService cacheService;

    public CacheLoader(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * This ensures the cache is loaded only after the service dependency is injected.
     */
    @PostConstruct
    public void loadCacheOnStartup() {
        cacheService.load();
        log.info("Cache loaded after bean creation.");
    }
}