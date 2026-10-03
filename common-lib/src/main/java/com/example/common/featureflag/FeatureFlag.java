package com.example.common.featureflag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row = one flag. Rules JSON encodes the rollout policy:
 *
 *   {
 *     "percentage": 25,                       // 0..100 — sticky by principal hash
 *     "tenantWhitelist": ["acme","beta"],     // tenants always on
 *     "userWhitelist":   ["u-123","u-456"]    // specific users always on
 *   }
 *
 * All three are optional; `enabled=false` short-circuits everything.
 */
@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

    @Id
    @Column(name = "flag_key", length = 128)
    private String flagKey;

    @Column(length = 1024)
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Lob
    @Column(name = "rules_json")
    private String rulesJson;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    protected FeatureFlag() {}

    public FeatureFlag(String flagKey, String description, boolean enabled, String rulesJson) {
        this.flagKey = flagKey;
        this.description = description;
        this.enabled = enabled;
        this.rulesJson = rulesJson;
        this.updatedAt = Instant.now();
    }

    public void update(boolean enabled, String rulesJson, String updatedBy) {
        this.enabled = enabled;
        this.rulesJson = rulesJson;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    public String getFlagKey()    { return flagKey; }
    public String getDescription() { return description; }
    public boolean isEnabled()    { return enabled; }
    public String getRulesJson()  { return rulesJson; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy()  { return updatedBy; }

    public void setDescription(String d) { this.description = d; }
}
