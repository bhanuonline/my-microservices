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
}
