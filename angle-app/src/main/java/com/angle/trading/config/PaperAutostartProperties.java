package com.angle.trading.config;

import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sessions to auto-create when the app boots.
 *
 * Example (application.properties):
 *
 *   paper.autostart.enabled=true
 *   paper.autostart.sessions[0].strategy-name=ensemble
 *   paper.autostart.sessions[0].source-type=angel-live
 *   paper.autostart.sessions[0].symbol-token=99926000
 *   paper.autostart.sessions[0].exchange=NSE
 *   paper.autostart.sessions[0].interval=ONE_MINUTE
 *   paper.autostart.sessions[0].warmup-candles=100
 *   paper.autostart.sessions[0].poll-interval-seconds=30
 *
 *   paper.autostart.sessions[1].strategy-name=ob-retest
 *   paper.autostart.sessions[1].source-type=angel-live
 *   paper.autostart.sessions[1].symbol-token=99926009
 *   ...
 *
 * Each session gets created via the same code path as a manual POST — same
 * validation, same lifecycle.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "paper.autostart")
public class PaperAutostartProperties {

    private boolean enabled = false;
    private List<Session> sessions = new ArrayList<>();

    @Data
    public static class Session {
        private String   strategyName;
        private String   sourceType;

        // angel-live specifics
        private String   symbolToken;
        private Exchange exchange;
        private Interval interval;
        private Integer  warmupCandles;
        private Integer  pollIntervalSeconds;

        // replay specifics
        private Integer  candlesPerSecond;
    }
}
