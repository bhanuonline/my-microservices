package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Controls the Heikin Ashi analysis feature.
 *
 * analysis.ha.enabled = true   → HA can be requested via ?ha=true param
 * analysis.ha.enabled = false  → HA is always disabled, ?ha=true is ignored
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "analysis.ha")
public class AnalysisHaProperties {
    /** Master switch. Set false to disable HA entirely without code change. */
    private boolean enabled = true;
}
