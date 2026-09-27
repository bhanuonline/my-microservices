package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Multi-timeframe confirmation gate.
 *
 * When a signal fires on a lower timeframe (e.g. FIVE_MINUTE), also check
 * higher timeframes agree in direction before allowing the signal.
 *
 * Example: 5-min BUY fires → verify 15-min trend is UP and 1-hour trend is UP.
 *   • Both agree           → signal fires
 *   • One disagrees        → signal skipped (in ALL mode)
 *   • Depends on mode      → see agreementMode
 *
 * Direction is determined per higher-TF by the {@code method} setting:
 *   EMA_STACK       — EMA fast > EMA slow → UP, else DOWN
 *   PRICE_VS_EMA    — close > EMA(period) → UP, else DOWN
 *   SUPERTREND      — SuperTrend bullish  → UP, else DOWN
 *
 * All checks use only CLOSED candles at check time — no forward-look bias.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mtf")
public class MtfProperties {

    /** Master switch. false = MTF check is bypassed, signal fires as before. */
    private boolean enabled = true;

    /**
     * Which higher timeframes to check, CSV of {@code Interval} enum names.
     * Must be STRICTLY higher than the signal's own timeframe — signals on 5M
     * with 5M listed here still work (5M is skipped as it's the same TF).
     *
     * Recommended: 15M signal → check 30M and 1H
     *              5M signal  → check 15M and 1H
     */
    private String higherTimeframes = "FIFTEEN_MINUTE,ONE_HOUR";

    /**
     * How many higher-TFs must agree:
     *   ALL       — every listed TF must match (strictest, fewest signals)
     *   MAJORITY  — more than half must match (balanced)
     *   ANY       — at least one must match (loosest)
     */
    private String agreementMode = "ALL";

    /**
     * Direction-detection method on each higher-TF.
     *   EMA_STACK       — emaFast > emaSlow
     *   PRICE_VS_EMA    — close > EMA(emaSlow)
     *   SUPERTREND      — SuperTrend bullish flag
     */
    private String method = "EMA_STACK";

    /** EMA periods used by EMA_STACK and PRICE_VS_EMA. */
    private int emaFast = 9;
    private int emaSlow = 20;

    /** SuperTrend params — used only when method=SUPERTREND. */
    private int    superTrendPeriod = 10;
    private double superTrendMultiplier = 3.0;

    /** Cache TTL for higher-TF direction lookups (per symbol+TF). */
    private int cacheMinutes = 5;
}
