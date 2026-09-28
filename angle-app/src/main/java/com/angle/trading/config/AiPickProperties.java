package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Config for the AI-driven stock picker (portfolio MVP).
 *
 * The picker scans a universe of instruments (from InstrumentService), asks
 * Claude for BUY/HOLD/AVOID recommendations, and persists each pick so we
 * can measure outcomes over time.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai-picks")
public class AiPickProperties {

    /** Master switch. false = the picker is disabled (generate endpoint still callable). */
    private boolean enabled = true;

    /** Max stocks to send to Claude in one prompt. Bigger = more context but slower + costlier. */
    private int maxUniverseSize = 30;

    /** Candles to include per stock in the prompt (daily bars). */
    private int candlesPerStock = 60;

    /**
     * Only fire signals with confidence at or above this level.
     * HIGH (default) → strictest, few picks. MEDIUM → balanced. LOW → catches all.
     */
    private String minConfidence = "MEDIUM";

    /** Dedupe: skip if same symbol has an OPEN pick from the last N days. */
    private int dedupeDays = 7;

    /** Auto-EXPIRE an OPEN pick after this many days if neither target nor stop hits. */
    private int expiryDays = 90;

    /** Cron for the outcome-tracker (checks every OPEN pick against latest close). */
    private String outcomeCheckCron = "0 45 15 * * MON-FRI";   // 3:45 PM IST after close
}
