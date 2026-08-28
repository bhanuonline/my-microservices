package com.angle.trading.strategy.model;

import java.math.BigDecimal;

/**
 * A per-candle instruction from a strategy: what to do, at what price,
 * with what stop and target.
 *
 *   action     — ENTER_LONG / ENTER_SHORT / EXIT / HOLD
 *   entry      — planned entry price. Null → fall back to candle close.
 *   stop       — stop-loss level. Null → no automatic stop (exit only via next EXIT signal).
 *   target     — profit target. Null → no automatic target.
 *   rationale  — human-readable "why" (shows up in the backtest trade log).
 *
 * Prefer the factory methods {@link #enterLong}, {@link #enterShort},
 * {@link #exit}, {@link #hold} — they document the intent at the call site
 * and prevent invalid combinations (e.g. HOLD with an entry price).
 */
public record TradeIntent(
        IntentAction action,
        BigDecimal entry,
        BigDecimal stop,
        BigDecimal target,
        String rationale
) {

    private static final TradeIntent HOLD_INSTANCE =
            new TradeIntent(IntentAction.HOLD, null, null, null, null);

    public static TradeIntent hold() {
        return HOLD_INSTANCE;
    }

    public static TradeIntent enterLong(BigDecimal entry, BigDecimal stop,
                                        BigDecimal target, String rationale) {
        return new TradeIntent(IntentAction.ENTER_LONG, entry, stop, target, rationale);
    }

    public static TradeIntent enterShort(BigDecimal entry, BigDecimal stop,
                                         BigDecimal target, String rationale) {
        return new TradeIntent(IntentAction.ENTER_SHORT, entry, stop, target, rationale);
    }

    public static TradeIntent exit(String rationale) {
        return new TradeIntent(IntentAction.EXIT, null, null, null, rationale);
    }
}
