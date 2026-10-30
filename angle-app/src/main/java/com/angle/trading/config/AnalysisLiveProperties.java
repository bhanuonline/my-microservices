package com.angle.trading.config;

import com.angle.trading.broker.model.Interval;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the live stock analysis API.
 *
 * <pre>
 * analysis.live.enabled        = true              master switch
 * analysis.live.base-interval  = FIFTEEN_MINUTE    smallest TF in MTF stack
 * analysis.live.max-symbols    = 20                max symbols in bulk POST
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "analysis.live")
public class AnalysisLiveProperties {

    /** Master switch — set false to disable /api/stocks/analysis endpoints. */
    private boolean enabled = true;

    /**
     * Smallest timeframe in the MTF stack.
     * Controls how often live mode calls Angel API (one call per candle close).
     * FIFTEEN_MINUTE = Angel called 26 times/day per symbol.
     * FIVE_MINUTE    = Angel called 75 times/day per symbol (higher rate limit risk).
     */
    private Interval baseInterval = Interval.FIFTEEN_MINUTE;

    /** Maximum symbols allowed in a single POST /api/stocks/analysis/bulk request. */
    private int maxSymbols = 20;
}
