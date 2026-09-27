package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Regime detector + time-of-day filter config for the signal pipeline.
 *
 * Each sub-filter has its own on/off flag so you can dial in exactly which
 * checks apply. When a filter is disabled it always PASSES — no impact on
 * signals. When enabled, a signal must pass its check or it is skipped.
 *
 * All thresholds are here so you can tune from application.properties
 * without recompiling.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "regime")
public class RegimeProperties {

    /** Master switch — false = all regime checks bypassed, signals fire as before. */
    private boolean enabled = true;

    private Adx  adx  = new Adx();
    private Vix  vix  = new Vix();
    private Time time = new Time();

    /**
     * ADX-based trend/range classifier.
     * ADX < weakThreshold          → RANGE-BOUND market (chop)
     * weakThreshold ≤ ADX < strong → MIXED market
     * ADX ≥ strongThreshold        → TRENDING market
     *
     * When enabled, signals fire in the modes listed in {@code allowedRegimes}.
     */
    @Data
    public static class Adx {
        private boolean enabled = true;
        private int     period = 14;
        private double  weakThreshold   = 20.0;
        private double  strongThreshold = 30.0;
        /** CSV of regimes where signals are allowed. Options: RANGE,MIXED,TREND. */
        private String  allowedRegimes = "MIXED,TREND";
    }

    /**
     * India VIX gate. High VIX = choppy, unpredictable markets — most technical
     * signals fail. Skip signals when VIX exceeds threshold.
     */
    @Data
    public static class Vix {
        private boolean enabled  = true;
        /** Skip signals when VIX is at or above this level. */
        private double  maxAllowed = 22.0;
    }

    /**
     * Time-of-day filter (all in Asia/Kolkata timezone).
     *
     * DEFAULT SAFE ZONES (from historical Nifty data):
     *   09:30 – 12:00   Golden hour + mid-morning
     *   14:00 – 15:00   Afternoon momentum
     *
     * DEFAULT SKIP ZONES:
     *   09:15 – 09:30   Opening auction volatility
     *   12:00 – 14:00   Lunch chop, thin volume
     *   15:00 – 15:30   Closing volatility
     *
     * You can extend this via allowedWindows (CSV of "HH:mm-HH:mm").
     */
    @Data
    public static class Time {
        private boolean enabled = true;
        /**
         * CSV of allowed time windows in "HH:mm-HH:mm" format (24h, IST).
         * If empty, no time filter is applied (equivalent to disabled=true).
         */
        private String allowedWindows = "09:30-12:00,14:00-15:00";
    }
}
