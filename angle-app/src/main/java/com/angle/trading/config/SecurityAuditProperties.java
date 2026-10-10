package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Login-audit log settings.
 *
 *   • {@link #enabled} — hard off switch (writes become no-ops).
 *   • {@link #retentionDays} — how long to keep rows. 0 = keep forever.
 *   • {@link #pruneCron} — Spring cron; nightly default at 03:17 IST.
 *     Off-peak so it doesn't fight with the daily report job.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security.audit")
public class SecurityAuditProperties {

    /** Master switch. false → audit writes are skipped. */
    private boolean enabled = true;

    /** Days to keep audit rows. 0 = retain forever. */
    private int retentionDays = 90;

    /** Spring cron for the nightly prune. 6-field (sec min hour dom mon dow). */
    private String pruneCron = "17 17 3 * * *";
}
