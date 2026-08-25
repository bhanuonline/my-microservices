package com.angle.trading.broker.model;

/**
 * Exchange segments supported by Indian brokers.
 *
 *  NSE — NSE equity + indices           (e.g. RELIANCE, Nifty 50 index)
 *  BSE — BSE equity                     (e.g. SENSEX-listed stocks)
 *  NFO — NSE F&O                        (Nifty/BankNifty futures + options, stock F&O)
 *  BFO — BSE F&O                        (Sensex/BankEx options)
 *  MCX — Commodities                    (gold, silver, crude oil)
 *  CDS — Currency derivatives           (USDINR, EURINR)
 *
 * Broker adapters map this enum to whatever string their API expects
 * (Angel uses these exact names, other brokers may differ).
 */
public enum Exchange {
    NSE,
    BSE,
    NFO,
    BFO,
    MCX,
    CDS
}
