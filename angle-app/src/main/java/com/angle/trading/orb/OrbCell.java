package com.angle.trading.orb;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Snapshot of ORB state for one (instrument, timeframe) pair.
 *
 * Immutable record — produced by OrbService.snapshot() for the UI.
 * Mutable internal state lives in OrbService's state map.
 *
 * @param minutes            timeframe length (5, 15, 30, 60, 75, 125)
 * @param signal             current verdict (FORMING / WAITING / BUY / SHORT / NO_TRADE)
 * @param orHigh             locked opening-range high (null while FORMING)
 * @param orLow              locked opening-range low  (null while FORMING)
 * @param breakoutPrice      the tick price that triggered BUY/SHORT (null otherwise)
 * @param breakoutAt         epoch-millis when the breakout fired (null otherwise)
 * @param orFormedAt         epoch-millis when the OR window closed (null while FORMING)
 * @param orClosesAtEpochMs  epoch-millis when the OR window will close (hint shown in FORMING)
 */
public record OrbCell(
        int minutes,
        OrbSignal signal,              // raw ORB verdict (position vs OR)
        OrbSignal combinedSignal,      // ORB AND ADX+DI agree (what the UI headline uses)
        BigDecimal orHigh,
        BigDecimal orLow,
        BigDecimal breakoutPrice,
        Instant breakoutAt,
        Instant orFormedAt,
        Instant orClosesAtEpochMs,
        BigDecimal ltp,                // last seen tick price (null if no ticks yet)
        Instant ltpAt,                 // when the last tick arrived
        BigDecimal adx,                // trend strength 0-100 (null = not enough data)
        BigDecimal plusDi,             // +DI
        BigDecimal minusDi             // -DI
) {}
