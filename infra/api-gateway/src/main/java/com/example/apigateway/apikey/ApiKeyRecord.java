package com.example.apigateway.apikey;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Metadata stored per API key. NEVER contains the raw key — the Redis key
 * itself is the SHA-256 hash of the raw key.
 *
 *   Redis key:  apikey:<sha256(rawKey)>
 *   Value:      JSON of this record
 *   TTL:        expiresAt - now (or 1y if no expiry)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyRecord {

    private String id;              // public identifier, e.g. "key_01H..."
    private String prefix;          // first 10 chars of raw key (for UI display only)
    private String ownerId;
    private String name;
    private List<String> scopes;
    private String rateLimitTier;
    private Instant expiresAt;
    private boolean enabled;
    private Instant createdAt;
    private Instant lastUsedAt;

    public ApiKeyRecord() {}

    public ApiKeyRecord(String id, String prefix, String ownerId, String name,
                        List<String> scopes, String rateLimitTier,
                        Instant expiresAt, boolean enabled, Instant createdAt) {
        this.id = id;
        this.prefix = prefix;
        this.ownerId = ownerId;
        this.name = name;
        this.scopes = scopes;
        this.rateLimitTier = rateLimitTier;
        this.expiresAt = expiresAt;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes; }

    public String getRateLimitTier() { return rateLimitTier; }
    public void setRateLimitTier(String rateLimitTier) { this.rateLimitTier = rateLimitTier; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
