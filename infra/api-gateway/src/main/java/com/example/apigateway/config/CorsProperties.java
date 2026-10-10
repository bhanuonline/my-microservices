package com.example.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gateway.cors")
public class CorsProperties {

    private boolean enabled = false;

    /** Explicit list — cannot use "*" when allowCredentials=true (browsers reject). */
    private List<String> allowedOrigins = List.of("http://localhost:5173");

    private List<String> allowedMethods = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");

    private List<String> allowedHeaders = List.of(
            "Authorization", "Content-Type",
            "X-Api-Key", "X-Correlation-Id", "Idempotency-Key");

    /**
     * Response headers the browser JS is allowed to read. Without exposing them
     * here, custom X-* headers are stripped by the browser before fetch() sees them.
     */
    private List<String> exposedHeaders = List.of(
            "X-Correlation-Id",
            "X-Idempotent-Replay",
            "X-Cache",
            "X-Cache-Age",
            "X-Cache-Original-Url",
            "X-Fallback-Reason",
            "X-RateLimit-Remaining",
            "X-RateLimit-Burst-Capacity",
            "X-RateLimit-Replenish-Rate",
            "X-Idempotency-Bypassed",
            "Retry-After",
            "Warning"
    );

    private boolean allowCredentials = true;
    private long maxAge = 3600;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public List<String> getAllowedMethods() { return allowedMethods; }
    public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }

    public List<String> getAllowedHeaders() { return allowedHeaders; }
    public void setAllowedHeaders(List<String> allowedHeaders) { this.allowedHeaders = allowedHeaders; }

    public List<String> getExposedHeaders() { return exposedHeaders; }
    public void setExposedHeaders(List<String> exposedHeaders) { this.exposedHeaders = exposedHeaders; }

    public boolean isAllowCredentials() { return allowCredentials; }
    public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }

    public long getMaxAge() { return maxAge; }
    public void setMaxAge(long maxAge) { this.maxAge = maxAge; }
}
