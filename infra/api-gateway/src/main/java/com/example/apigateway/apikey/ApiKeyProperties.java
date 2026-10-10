package com.example.apigateway.apikey;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.apikey")
public class ApiKeyProperties {

    private boolean enabled = false;
    private String headerName = "X-Api-Key";
    private String keyPrefix = "sk_live_";
    private boolean fallThroughToJwt = true;
    private Duration cacheTtl = Duration.ofSeconds(60);
    private List<String> protectedPaths = List.of();
    private List<String> excludedPaths = List.of("/actuator/**", "/fallback/**");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getHeaderName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public boolean isFallThroughToJwt() { return fallThroughToJwt; }
    public void setFallThroughToJwt(boolean fallThroughToJwt) { this.fallThroughToJwt = fallThroughToJwt; }

    public Duration getCacheTtl() { return cacheTtl; }
    public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }

    public List<String> getProtectedPaths() { return protectedPaths; }
    public void setProtectedPaths(List<String> protectedPaths) { this.protectedPaths = protectedPaths; }

    public List<String> getExcludedPaths() { return excludedPaths; }
    public void setExcludedPaths(List<String> excludedPaths) { this.excludedPaths = excludedPaths; }
}
