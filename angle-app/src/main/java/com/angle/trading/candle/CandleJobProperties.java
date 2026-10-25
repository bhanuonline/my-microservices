package com.angle.trading.candle;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Config for CandleHistoryJob.
 *
 * application.properties prefix: candle.history-job
 *
 * Example:
 *   candle.history-job.enabled=true
 *   candle.history-job.run-on-startup=false
 *   candle.history-job.thread-pool-size=10
 *   candle.history-job.daily-lookback-days=730
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "candle.history-job")
public class CandleJobProperties {

    /** Master switch — false = no scheduled runs (manual API still works). */
    private boolean enabled = true;

    /** Run a full backfill once immediately after app startup. */
    private boolean runOnStartup = false;

    // ── Schedules (Spring cron, IST) ──────────────────────────────────
    private String preMarketCron  = "0 0 9 * * MON-FRI";
    private String postMarketCron = "0 40 15 * * MON-FRI";
    private String weekendCron    = "0 0 10 * * SAT";
    private String zone           = "Asia/Kolkata";

    // ── Lookback ──────────────────────────────────────────────────────
    /** Days of intraday (5m/15m/1h) history to keep. */
    private int intradayLookbackDays = 30;

    /** Days of daily (1D) history to keep. */
    private int dailyLookbackDays = 730;

    // ── Angel API chunking ────────────────────────────────────────────
    /** Max date window per Angel API call for intraday intervals. */
    private int maxWindowDaysIntraday = 60;

    /** Max date window per Angel API call for 1D interval. */
    private int maxWindowDaysDaily = 365;

    // ── Performance ───────────────────────────────────────────────────
    /** Parallel instrument fetches. */
    private int threadPoolSize = 10;

    /** Max simultaneous Angel API calls (Semaphore permit count). */
    private int maxConcurrentCalls = 3;

    /** Milliseconds to sleep between Angel API calls. */
    private int apiDelayMs = 200;

    /** Max seconds to wait for one instrument before marking it timed out. */
    private int timeoutPerInstrumentSec = 30;

    // ── Job history ───────────────────────────────────────────────────
    /** Keep candle_job_run rows for this many days. */
    private int historyRetentionDays = 90;
}
