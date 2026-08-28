package com.angle.trading.marketstructure.model;

/**
 * The two market-structure event types we detect at Phase 1.
 *
 *   BOS   — Break of Structure. A close beyond the previous swing IN the
 *           same direction as the current trend. Trend continuation.
 *
 *   CHOCH — Change of Character. The FIRST close beyond a swing in the
 *           OPPOSITE direction of the current trend. Trend reversal.
 */
public enum StructureEventType {
    BOS,
    CHOCH
}
