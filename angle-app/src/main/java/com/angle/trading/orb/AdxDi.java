package com.angle.trading.orb;

import java.math.BigDecimal;

/**
 * ADX + directional indicators snapshot for one candle.
 *
 *   plusDi  — +DI, buying pressure
 *   minusDi — −DI, selling pressure
 *   adx     — trend strength (0-100); higher = stronger trend regardless of direction
 *
 * Interpretation used by the ORB combined signal:
 *   ADX > 25 AND +DI > -DI  → bullish trending
 *   ADX > 25 AND -DI > +DI  → bearish trending
 *   ADX ≤ 25                → ranging, no reliable direction
 *
 * All three may be null when the series doesn't have enough bars yet
 * (needs ~2 × period candles to warm up Wilder's double smoothing).
 */
public record AdxDi(BigDecimal adx, BigDecimal plusDi, BigDecimal minusDi) {

    public static final AdxDi EMPTY = new AdxDi(null, null, null);

    public boolean hasValue() {
        return adx != null && plusDi != null && minusDi != null;
    }

    /** +DI > -DI (buying pressure dominant). */
    public boolean isBullishDi() {
        return hasValue() && plusDi.compareTo(minusDi) > 0;
    }

    /** -DI > +DI (selling pressure dominant). */
    public boolean isBearishDi() {
        return hasValue() && minusDi.compareTo(plusDi) > 0;
    }

    /** ADX above the given threshold (default 25 for "strong trend"). */
    public boolean isTrending(BigDecimal threshold) {
        return hasValue() && adx.compareTo(threshold) > 0;
    }
}
