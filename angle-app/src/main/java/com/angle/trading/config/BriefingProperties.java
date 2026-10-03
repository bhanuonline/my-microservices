package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Pre-market briefing config.
 *
 * At {@code generateCron} (default 08:30 IST weekdays) we:
 *   1. Snapshot latest global-markets quotes (Phase 1)
 *   2. Pull last 24h of news headlines (Phase 2)
 *   3. Ask Claude to write a 200-word overnight summary
 *   4. Save to briefing DB table + optionally notify
 *
 * Latest briefing shows at /briefing/today.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "briefing")
public class BriefingProperties {

    /** Master switch. false = no auto-generation. Manual endpoint still works. */
    private boolean enabled = true;

    /** When to auto-generate. Default: 08:30 IST Mon-Fri (before market opens at 09:15). */
    private String generateCron = "0 30 8 * * MON-FRI";

    /** How many days of briefings to keep in DB. */
    private int retentionDays = 90;

    /** Cap headline count sent to Claude (control prompt size + cost). */
    private int maxHeadlinesInPrompt = 25;

    /** Email delivery (leave off until SMTP is configured). */
    private boolean emailEnabled = false;
    private String  emailTo      = "";

    /** Telegram delivery (leave off until bot token is set). */
    private boolean telegramEnabled = false;
    private String  telegramBotToken = "";
    private String  telegramChatId   = "";
}
