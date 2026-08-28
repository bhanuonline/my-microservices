package com.angle.trading.marketstructure.model;

/**
 * Which side of the book the liquidity sits on.
 *
 *   BUY_SIDE  (BSL) — stops of short-sellers, resting above swing highs. Taken by upward moves.
 *   SELL_SIDE (SSL) — stops of buyers,       resting below swing lows.  Taken by downward moves.
 */
public enum LiquiditySide {
    BUY_SIDE,
    SELL_SIDE
}
