package com.angle.trading.broker.model;

/**
 * Option contract type.
 *
 *  CE (Call European)  — right to BUY the underlying at the strike price
 *  PE (Put European)   — right to SELL the underlying at the strike price
 *
 * Indian options are European-style: exercisable only on the expiry date.
 */
public enum OptionType {
    CE,
    PE
}
