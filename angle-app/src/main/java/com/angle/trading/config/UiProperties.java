package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * UI version selector.
 *
 * ui.version=v1 (default)  → use existing templates (templates/dashboard/...)
 * ui.version=v2            → use new templates (templates/v2/dashboard/...)
 *
 * Switch by editing application.properties and restarting. Instant rollback
 * if V2 breaks — set back to v1 and restart.
 *
 * Controllers inject this bean and call {@link #prefix()} before returning a
 * view name:
 *
 *     return uiProperties.prefix() + "dashboard/welcome";
 *
 * V1 prefix is empty string → view name unchanged → existing templates used.
 * V2 prefix is "v2/" → Thymeleaf resolves templates/v2/dashboard/welcome.html.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ui")
public class UiProperties {

    /** Active UI version. "v1" or "v2". Case-insensitive. */
    private String version = "v1";

    /**
     * View-name prefix for the active version.
     * v1 → "" (points to existing templates/*)
     * v2 → "v2/" (points to templates/v2/*)
     */
    public String prefix() {
        return "v2".equalsIgnoreCase(version) ? "v2/" : "";
    }

    /** Convenience for templates — exposes the version as a bean property. */
    public String getActive() {
        return "v2".equalsIgnoreCase(version) ? "v2" : "v1";
    }
}
