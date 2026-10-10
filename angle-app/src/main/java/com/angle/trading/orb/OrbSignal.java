package com.angle.trading.orb;

/**
 * Opening Range Breakout verdict for a single (instrument, timeframe) cell.
 *
 * Lifecycle (per cell, per trading day):
 *   FORMING    → still building the opening range (within the first N minutes of session)
 *   WAITING    → OR locked, no breakout yet (inside the range)
 *   BUY        → price broke above OR_HIGH after the OR window closed
 *   SHORT      → price broke below OR_LOW  after the OR window closed
 *   NO_TRADE   → session ended without breakout (OR never resolved)
 *
 * BUY / SHORT are terminal for the day — the first breakout wins and the
 * cell stays locked until the next session reset (classic ORB behavior).
 */
public enum OrbSignal {
    FORMING,
    WAITING,
    BUY,
    SHORT,
    NO_TRADE
}
