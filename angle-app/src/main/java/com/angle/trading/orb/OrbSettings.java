package com.angle.trading.orb;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Runtime-mutable settings for the ORB engine, exposed on the dashboard config
 * card so you can tune behavior without restarting.
 *
 * Values seeded from {@code application.properties} (prefix {@code orb}) and
 * can be overridden at runtime via {@code POST /api/orb/config}. Changes take
 * effect on the next tick / candle event — no restart needed.
 *
 * Example application.properties:
 *   orb.adx-cadence=TF_BOUNDARY
 *   orb.orb-cadence=TF_BOUNDARY
 *   orb.adx-threshold=25
 *   orb.combination-mode=REQUIRE_BOTH
 */
@Component
@ConfigurationProperties(prefix = "orb")
@Getter
@Setter
public class OrbSettings {

    /** How often each cell recomputes its ADX. */
    public enum Cadence {
        /** Recompute only when this cell's own timeframe candle closes (standard, matches Angel). */
        TF_BOUNDARY,
        /** Recompute every 1-minute close using the in-progress TF bar as the current close. */
        EVERY_1M
    }

    /** How ORB + ADX combine into the final signal. */
    public enum CombinationMode {
        /** BUY/SHORT only when ORB AND ADX+DI both agree (strict — fewer signals). */
        REQUIRE_BOTH,
        /** ORB verdict alone — ADX/DI shown but doesn't gate the signal. */
        ORB_ONLY,
        /** Signal from +DI/-DI/ADX alone — ignore ORB position vs OR. */
        ADX_ONLY
    }

    private Cadence adxCadence = Cadence.TF_BOUNDARY;
    private Cadence orbCadence = Cadence.TF_BOUNDARY;
    private BigDecimal adxThreshold = new BigDecimal("25");
    private CombinationMode combinationMode = CombinationMode.REQUIRE_BOTH;

    @PostConstruct
    void logAtStart() {
        org.slf4j.LoggerFactory.getLogger(OrbSettings.class)
            .info("OrbSettings initialised — adxCadence={}, orbCadence={}, adxThreshold={}, combinationMode={}",
                    adxCadence, orbCadence, adxThreshold, combinationMode);
    }
}
