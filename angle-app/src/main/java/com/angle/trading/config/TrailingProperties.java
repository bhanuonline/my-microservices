package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Trailing stop config. Runs on every tick alongside LiveSignalMonitor's
 * hard target/stop check.
 *
 * Modes (pick ONE via {@code mode}):
 *
 *   NONE       Disabled. Behaves like classic fixed stop.
 *
 *   FIXED      Stop trails by a FIXED distance (in price points) behind
 *              the high-water mark. Once price moves N points past entry,
 *              stop moves to (peak − trailDistance).
 *              Simple. Doesn't scale with instrument price.
 *
 *   PERCENT    Stop trails by a PERCENTAGE of the high-water mark.
 *              Scales naturally across instruments (Nifty ~24k vs Reliance ~2.9k).
 *              Recommended default.
 *
 *   MILESTONE  Multi-step ladder. Once price hits Nth level of the
 *              entry→target distance, stop jumps to Mth level.
 *              Example: at 50% of the move, stop moves to entry (breakeven).
 *              Locks in profit in discrete steps.
 *
 *   ATR        Stop trails by K × ATR. Adapts to instrument's own
 *              volatility. Best for choppy or gappy instruments.
 *              (K = atrMultiplier)
 *
 * Trailing NEVER moves the stop in the losing direction — only tightens.
 * If price reverses, the ratcheted stop closes the signal (as HIT_STOP)
 * but you keep whatever profit the trail locked in.
 *
 * Activation: trailing only kicks in after price is {@code activationPercent}
 * of the entry→target distance in your favor. Before that, the original
 * fixed stop is used. This avoids getting stopped out on entry-bar noise.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "trailing")
public class TrailingProperties {

    /** Master switch. false = trailing disabled everywhere (classic fixed stops). */
    private boolean enabled = true;

    /** FIXED / PERCENT / MILESTONE / ATR / NONE */
    private String mode = "PERCENT";

    /**
     * How far into the trade before trailing engages, as % of entry→target distance.
     * 0.0 = trail from the first tick past entry.
     * 0.3 = wait until price is 30% of the way to target.
     * 0.5 = wait until halfway (safest — avoids noise stops on entry bar).
     */
    private double activationPercent = 0.30;

    /** For mode=FIXED — trail this many price points behind the peak. */
    private double fixedDistance = 30.0;

    /** For mode=PERCENT — trail this % behind the peak (0.4 = 0.4%). */
    private double percentDistance = 0.40;

    /** For mode=ATR — trail K × ATR behind the peak. */
    private double atrMultiplier = 2.0;

    /** For mode=ATR — ATR period. */
    private int atrPeriod = 14;

    /**
     * For mode=MILESTONE — ratchet ladder. CSV of "moveAt:stopAt" pairs.
     * Each pair means: when price has moved moveAt% of entry→target,
     *                  jump stop to stopAt% of that distance.
     *
     * Default: "50:0,75:33,90:66" means
     *   At 50% of the move → stop moves to entry (breakeven, 0% gained)
     *   At 75% of the move → stop moves to 33% of the entry→target
     *   At 90% of the move → stop moves to 66% (locks in most of the gain)
     */
    private String milestoneLadder = "50:0,75:33,90:66";
}
