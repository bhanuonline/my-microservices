package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * News blackout config. When a scheduled market-moving event is within its
 * window, signals for affected symbols are skipped.
 *
 * Calendar is loaded from a YAML file on the classpath (or filesystem override)
 * so you can edit events without redeploying.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "news")
public class NewsProperties {

    /** Master switch. false = calendar is loaded but signals are never blocked. */
    private boolean enabled = true;

    /**
     * Minimum event severity to block: LOW / MEDIUM / HIGH.
     * MEDIUM (default) blocks MEDIUM + HIGH events, ignores LOW.
     */
    private String blockSeverity = "MEDIUM";

    /** Fallback window when the event YAML omits windowBefore/After. */
    private int defaultWindowBefore = 30;
    private int defaultWindowAfter  = 60;

    /**
     * Calendar location. Defaults to a classpath resource shipped with the app.
     * Set to an absolute filesystem path to point at an external editable file
     * (e.g. /Users/you/news-calendar.yml).
     */
    private String calendarFile = "news-calendar.yml";

    /** Auto-reload calendar file this often (seconds). 0 = only on manual reload. */
    private int reloadIntervalSeconds = 300;
}
