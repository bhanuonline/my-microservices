package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Daily analysis report configuration.
 *
 * The report is a Markdown file written to {@code outputDir/YYYY-MM-DD.md},
 * capturing every session and trade for that trading day.
 *
 *   enabled          — master switch
 *   outputDir        — where to write (default "reports/" relative to app dir)
 *   autoWriteAtClose — if true, a scheduled task writes the report at market close IST
 *   closeCron        — cron for the auto-write (IST); default 15:35 Mon–Fri
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "reports")
public class ReportsProperties {

    private boolean enabled           = true;
    private String  outputDir         = "reports";
    private boolean autoWriteAtClose  = true;
    /** Cron in Asia/Kolkata: seconds minute hour day-of-month month day-of-week */
    private String  closeCron         = "0 35 15 * * MON-FRI";
}
