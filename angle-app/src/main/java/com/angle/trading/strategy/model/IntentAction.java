package com.angle.trading.strategy.model;

/**
 * What a strategy wants the trader to do at a specific candle.
 *
 *   ENTER_LONG   — open a long position
 *   ENTER_SHORT  — open a short position
 *   EXIT         — close whatever position is open (any direction)
 *   HOLD         — do nothing this candle
 *
 * Replaces the old {@code Signal} enum (BUY/SELL/HOLD). "Enter/Exit"
 * language is clearer once stops and targets enter the picture — a "BUY"
 * with no target is really "enter long and figure it out later".
 */
public enum IntentAction {
    ENTER_LONG,
    ENTER_SHORT,
    EXIT,
    HOLD
}
