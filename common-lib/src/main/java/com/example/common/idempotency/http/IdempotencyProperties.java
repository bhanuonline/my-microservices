package com.example.common.idempotency.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configure in application.yml:
 *
 *   idempotency:
 *     enabled: true
 *     ttl: 24h
 *     endpoints:
 *       - POST /api/v1/orders
 *       - POST /api/v1/payments/charge
 */
@ConfigurationProperties(prefix = "idempotency")
public class IdempotencyProperties {

    private boolean enabled = false;
    private Duration ttl = Duration.ofHours(24);
    private int maxBodyBytes = 1_048_576;              // 1 MiB cap on cached response size
    private List<String> endpoints = new ArrayList<>();  // "METHOD /path" exact match

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }

    public int getMaxBodyBytes() { return maxBodyBytes; }
    public void setMaxBodyBytes(int maxBodyBytes) { this.maxBodyBytes = maxBodyBytes; }

    public List<String> getEndpoints() { return endpoints; }
    public void setEndpoints(List<String> endpoints) { this.endpoints = endpoints; }
}
