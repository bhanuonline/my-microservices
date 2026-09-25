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

    /** India VIX symbol on Angel + thresholds. */
    private Vix vix = new Vix();

    /** Bank Nifty (or any other index) run in parallel for divergence checks. */
    private Correlated correlated = new Correlated();

    /** Advance/Decline breadth — number of stocks up vs down. */
    private Breadth breadth = new Breadth();

    /** Ticker strip fetch performance. */
    private Ticker ticker = new Ticker();

    /** Background cache warmer — pre-fetches instruments on a schedule. */
    private Warmer warmer = new Warmer();

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
        private boolean onDivergence = true;             // Nifty vs Bank Nifty diverge
        private boolean onVixSpike = true;               // VIX crosses spike threshold
        private boolean onBreadthFlip = true;            // A/D ratio flips bull ↔ bear
    }

    /**
     * India VIX config.
     * Angel exposes India VIX as symbol token 99919011 on NSE.
     * (Verify against your scrip master; some brokers use different tokens.)
     */
    @Data
    public static class Vix {
        private boolean enabled = true;
        private String  symbolToken = "99919011";
        private String  exchange    = "NSE";
        /** VIX above this = "elevated volatility" — worth alerting on. */
        private double  spikeThreshold = 18.0;
        /** VIX below this = "very calm" — trending strategies favoured. */
        private double  calmThreshold  = 13.0;
    }

    /**
     * A parallel instrument to cross-check the main instrument's move.
     * For Nifty, this is typically Bank Nifty (token 99926009).
     * Divergence between the two = warning of fake move.
     */
    @Data
    public static class Correlated {
        private boolean enabled = true;
        private String  symbol       = "Bank Nifty";
        private String  broker       = "ANGEL";
        private String  exchange     = "NSE";
        private String  symbolToken  = "99926009";
        /** Divergence percent-of-price above which we flag it. */
        private double  divergencePercentThreshold = 0.15;
    }

    /**
     * Advance/Decline breadth. Fetches LTP for each configured stock token,
     * compares with previous close, counts advances vs declines.
     *
     * Angel batch quote endpoint accepts up to 50 tokens per call — Nifty 50
     * fits in one call. For larger baskets (Nifty 200 etc.) the fetcher would
     * need to page.
     *
     * Populate constituentTokens with the tokens of your breadth universe.
     * If empty, the breadth section is skipped entirely.
     */
    @Data
    public static class Breadth {
        private boolean enabled = true;
        private String  broker   = "ANGEL";
        private String  exchange = "NSE";
        /** Human-readable label shown on dashboard (e.g. "Nifty 50 breadth"). */
        private String  label    = "Nifty 50 breadth";
        /** NSE stock tokens making up the breadth universe. */
        private List<String> constituentTokens = new ArrayList<>();
        /** Advances / declines ratio at or above this = broad bullish. */
        private double bullishRatio = 2.0;
        /** Advances / declines ratio at or below this = broad bearish. */
        private double bearishRatio = 0.5;
    }

    /**
     * Ticker strip fetch performance.
     *
     *   parallelEnabled — true = fetch all instruments concurrently (fast, ~2s);
     *                     false = sequential (old behaviour, ~20s; kept for debug).
     *   threadPoolSize  — how many instruments may be fetched at the same time.
     *                     Increase for more instruments; too high may trigger Angel rate limits.
     *   timeoutSeconds  — max wait per instrument before giving up on it.
     *                     Missing instruments render with "—" values.
     */
    @Data
    public static class Ticker {
        private boolean parallelEnabled = true;
        private int     threadPoolSize  = 10;
        private int     timeoutSeconds  = 30;
    }

    /**
     * Background warmer — pre-fetches every configured instrument on a schedule
     * so the user's first request always hits a warm cache.
     *
     *   enabled         — master switch
     *   cron            — Spring cron (in {@code zone}). Default: every 5 min, market hours, Mon-Fri
     *   zone            — IANA time zone for the cron. Default: Asia/Kolkata (IST)
     *   warmOnStartup   — also fire one warm-up right after the app is ready
     *   parallelEnabled — fan out fetches across all instruments concurrently
     *   threadPoolSize  — thread count for parallel warm-up (≥ instrument count for max parallelism)
     *   timeoutSeconds  — kill a warm-up round if it hangs longer than this
     *   priorityTokens  — comma-separated symbol tokens to warm FIRST (stage 1).
     *                     Guarantees your primary instruments are hot even if
     *                     the round times out before stage 2 finishes. Empty
     *                     = single-stage (all instruments in one batch).
     *                     Example: bias.warmer.priority-tokens=99926000,99926009
     */
    @Data
    public static class Warmer {
        private boolean enabled         = true;
        private String  cron            = "0 */5 9-15 * * MON-FRI";
        private String  zone            = "Asia/Kolkata";
        private boolean warmOnStartup   = true;
        private boolean parallelEnabled = true;
        private int     threadPoolSize  = 10;
        private int     timeoutSeconds  = 60;
        private String  priorityTokens  = "";
        private Circuit circuit         = new Circuit();
    }

    /**
     * Circuit breaker for the warmer.
     *
     * When {@code failureThreshold} consecutive rounds fail (0 instruments
     * succeeded), the breaker OPENS — subsequent scheduled fires are skipped
     * for {@code cooldownMinutes}. After cooldown a single HALF_OPEN test round
     * runs: success → CLOSED, failure → OPEN again.
     *
     * Manual warms via {@code POST /admin/cache/candles/warm?force=true} bypass
     * the breaker. {@code POST /admin/warmer/circuit/reset} forces CLOSED.
     */
    @Data
    public static class Circuit {
        private boolean enabled          = true;
        private int     failureThreshold = 3;
        private int     cooldownMinutes  = 15;
    }
}
