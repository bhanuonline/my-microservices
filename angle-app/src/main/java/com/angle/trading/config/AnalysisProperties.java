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

    @Data
    public static class Nifty {
        private String dataFile;
    }

    @Data
    public static class StrategyConfig {
        private String defaultStrategy;
        private Sma sma = new Sma();
    }

    @Data
    public static class Sma {
        private int shortPeriod;
        private int longPeriod;
    }
}
