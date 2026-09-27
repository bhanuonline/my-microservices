package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Config for the consensus signal lifecycle (detector + monitor + feed).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "signals")
public class SignalsProperties {

    private Detector detector = new Detector();
    private Monitor  monitor  = new Monitor();
    private Feed     feed     = new Feed();

    /** SignalDetector — fires on candle-close events. */
    @Data
    public static class Detector {
        private boolean enabled = true;
        /** Minimum strategies that must agree for a signal to save. */
        private int minAgreement = 3;
        /** Which intervals to detect on (from CandleClosedEvent). CSV. */
        private String intervals = "FIVE_MINUTE,FIFTEEN_MINUTE";
        /** Skip if a same-direction signal on the same symbol fired in this many minutes. */
        private int dedupeMinutes = 15;

        /**
         * After saving a CONSENSUS signal, also call the AI (Claude) and save a
         * second row with source=AI. Adds ~$0.02/signal API cost. Set false to
         * disable AI-confirmer without redeploying.
         */
        private boolean aiConfirm = true;
    }

    /** LiveSignalMonitor — watches ticks to close open signals when target/stop hits. */
    @Data
    public static class Monitor {
        private boolean enabled = true;
        /** Auto-EXPIRE any OPEN signal older than this. */
        private int expiryHours = 4;
        /** How often the sweeper checks for stale signals. */
        private int expirySweepMinutes = 15;
    }

    /** Feed page defaults. */
    @Data
    public static class Feed {
        private int defaultMaxAgeHours = 24;
        private int autoRefreshSeconds = 30;
    }
}
