package com.angle.trading.config;

import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Bias dashboard configuration.
 *
 * The dashboard aggregates indicator + structure + zone data into a single
 * daily-bias page. Everything is config-driven — swap symbols, timeframes,
 * refresh interval, and consensus thresholds without touching Java.
 *
 * Example (application.properties):
 *   bias.enabled=true
 *   bias.refresh-minutes=15
 *   bias.instruments[0].symbol=Nifty 50
 *   bias.instruments[0].broker=ANGEL
 *   bias.instruments[0].exchange=NSE
 *   bias.instruments[0].symbol-token=99926000
 *   bias.instruments[0].intraday-interval=FIVE_MINUTE
 *   bias.timeframes[0]=ONE_DAY
 *   bias.timeframes[1]=ONE_HOUR
 *   bias.timeframes[2]=FIFTEEN_MINUTE
 *   bias.timeframes[3]=FIVE_MINUTE
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "bias")
public class BiasProperties {

    private boolean enabled = true;
    private int refreshMinutes = 15;
    private int lookbackDays = 90;
    private String urlPath = "/bias";

    /** Instruments to build the sheet for. */
    private List<Instrument> instruments = new ArrayList<>();

    /** Timeframes for the multi-TF bias section (in order top → bottom). */
    private List<Interval> timeframes = List.of(
            Interval.ONE_DAY,
            Interval.ONE_HOUR,
            Interval.FIFTEEN_MINUTE,
            Interval.FIVE_MINUTE
    );

    /** Thresholds for the consolidated score. Sum of +1/0/-1 across dimensions. */
    private Scoring scoring = new Scoring();

    /** What changes should fire an alert. */
    private ChangeAlerts changeAlerts = new ChangeAlerts();

    @Data
    public static class Instrument {
        private String   symbol;              // display name, e.g. "Nifty 50"
        private String   broker = "ANGEL";
        private Exchange exchange = Exchange.NSE;
        private String   symbolToken;         // e.g. "99926000"
        private Interval intradayInterval = Interval.FIVE_MINUTE;
    }

    @Data
    public static class Scoring {
        private int strongThreshold   = 5;
        private int moderateThreshold = 3;
        private int weakThreshold     = 1;
    }

    @Data
    public static class ChangeAlerts {
        private boolean enabled = true;
        private boolean onBiasFlip = true;               // any timeframe bias flips
        private boolean onScoreJump = true;              // score changes by >= N
        private int     scoreJumpThreshold = 2;
        private boolean onNewStructuralEvent = true;     // new BOS/CHoCH detected
        private boolean onNewSweep = true;               // new liquidity sweep
        private boolean onAdxCross = true;               // ADX crosses 25 threshold
    }
}
