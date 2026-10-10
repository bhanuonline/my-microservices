package com.example.apigateway.responsecache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "gateway.response-cache")
public class ResponseCacheProperties {

    private boolean enabled = false;
    private Duration defaultTtl = Duration.ofHours(1);
    private int maxCachedBytes = 512 * 1024;                        // 512 KB
    private List<String> cacheableMethods = List.of("GET", "HEAD");
    private List<String> cacheableContentTypes = List.of(
            "application/json",
            "application/xml",
            "text/plain",
            "text/html"
    );
    private List<String> excludedPaths = List.of(
            "/actuator/**",
            "/fallback/**",
            "/admin/**"
    );
    private List<String> stripHeaders = List.of(
            "Set-Cookie",
            "Authorization",
            "Date"
    );
    private boolean staleWarning = true;

    /** Per-route TTL overrides. Key = route id. */
    private Map<String, RouteOverride> routes = new HashMap<>();

    public static class RouteOverride {
        private Duration ttl;

        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getDefaultTtl() { return defaultTtl; }
    public void setDefaultTtl(Duration defaultTtl) { this.defaultTtl = defaultTtl; }

    public int getMaxCachedBytes() { return maxCachedBytes; }
    public void setMaxCachedBytes(int maxCachedBytes) { this.maxCachedBytes = maxCachedBytes; }

    public List<String> getCacheableMethods() { return cacheableMethods; }
    public void setCacheableMethods(List<String> cacheableMethods) { this.cacheableMethods = cacheableMethods; }

    public List<String> getCacheableContentTypes() { return cacheableContentTypes; }
    public void setCacheableContentTypes(List<String> cacheableContentTypes) { this.cacheableContentTypes = cacheableContentTypes; }

    public List<String> getExcludedPaths() { return excludedPaths; }
    public void setExcludedPaths(List<String> excludedPaths) { this.excludedPaths = excludedPaths; }

    public List<String> getStripHeaders() { return stripHeaders; }
    public void setStripHeaders(List<String> stripHeaders) { this.stripHeaders = stripHeaders; }

    public boolean isStaleWarning() { return staleWarning; }
    public void setStaleWarning(boolean staleWarning) { this.staleWarning = staleWarning; }

    public Map<String, RouteOverride> getRoutes() { return routes; }
    public void setRoutes(Map<String, RouteOverride> routes) { this.routes = routes; }

    public Duration ttlFor(String routeId) {
        RouteOverride override = routes.get(routeId);
        if (override != null && override.getTtl() != null) return override.getTtl();
        return defaultTtl;
    }
}
