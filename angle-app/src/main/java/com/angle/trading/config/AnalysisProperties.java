package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds analysis.* keys from application.properties.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "analysis")
public class AnalysisProperties {

    private Nifty nifty = new Nifty();
    private StrategyConfig strategy = new StrategyConfig();
    private Smc smc = new Smc();
    private SuperTrendCfg superTrend = new SuperTrendCfg();

    @Data
    public static class Nifty {
        private String dataFile;
    }

    @Data
    public static class StrategyConfig {
        private String defaultStrategy;
        private Sma       sma      = new Sma();
        private Rsi       rsi      = new Rsi();
        private Macd      macd     = new Macd();
        private Ensemble  ensemble = new Ensemble();
        private VolumeBreakout volumeBreakout = new VolumeBreakout();
        private Bollinger bollinger = new Bollinger();
    }

    @Data
    public static class Sma {
        private int shortPeriod;
        private int longPeriod;
    }

    @Data
    public static class Rsi {
        private int period      = 14;
        private int oversold    = 30;
        private int overbought  = 70;
    }

    @Data
    public static class Macd {
        private int fastPeriod   = 12;
        private int slowPeriod   = 26;
        private int signalPeriod = 9;
    }

    /** Minimum number of child strategies that must agree for ensemble to trade. */
    @Data
    public static class Ensemble {
        private int minAgreement = 2;
    }

    /**
     * Volume-breakout strategy.
     *   lookback         — N candles to define the recent range (breakout above/below)
     *   volumeAvgPeriod  — M candles to compute average volume
     *   volumeMultiplier — current vol must exceed avgVol × this to confirm (1.5 = 50% above avg)
     *   riskReward       — target distance = riskReward × stop distance (2.0 = 2:1)
     */
    @Data
    public static class VolumeBreakout {
        private int    lookback         = 20;
        private int    volumeAvgPeriod  = 20;
        private double volumeMultiplier = 1.5;
        private double riskReward       = 2.0;
    }

    /**
     * Bollinger Bounce (mean-reversion) strategy.
     *   period            — Bollinger SMA period (default 20)
     *   stdMultiplier     — band width (2.0 covers ~95% of price action)
     *   stopBufferPercent — extra padding below/above reversal low/high (0.1 = 0.1%)
     */
    @Data
    public static class Bollinger {
        private int    period            = 20;
        private double stdMultiplier     = 2.0;
        private double stopBufferPercent = 0.1;
    }

    /** Smart Money Concepts settings. */
    @Data
    public static class Smc {
        private Swing swing = new Swing();
        private Sweep sweep = new Sweep();
    }

    @Data
    public static class Swing {
        /** N candles on each side must be less extreme for a pivot to confirm. */
        private int lookback = 3;
    }

    @Data
    public static class Sweep {
        /** How many candles back a sweep is still considered "recent". */
        private int windowCandles = 10;
    }

    /**
     * SuperTrend indicator config.
     *   period      — ATR period. Classic 10; use 7 for faster, 14 for slower.
     *   multiplier  — ATR band width. Classic 3.0; use 2.0 for tighter, 4.0 for wider.
     */
    @Data
    public static class SuperTrendCfg {
        private int period = 10;
        private double multiplier = 3.0;
    }
}
