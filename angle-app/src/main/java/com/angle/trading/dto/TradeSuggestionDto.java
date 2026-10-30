package com.angle.trading.dto;

/**
 * Actionable trade suggestion derived from MTF analysis.
 *
 *   direction  — LONG | SHORT | NO_TRADE
 *   timing     — NOW (all TFs aligned, enter immediately)
 *              | WAIT (higher TFs have bias, lower TFs in pullback — wait for alignment)
 *              | NO_TRADE (no clear trend)
 *   waitFor    — human-readable condition to watch (null when timing = NOW)
 *   entryZone  — price zone to look for entry (Order Block or nearest level)
 *   stopLoss   — where to place stop with basis
 *   target1    — first target (nearest swing level)
 *   target2    — second target (liquidity level, may be null)
 *   note       — one-line plain English summary
 *
 * DISCLAIMER: This is a technical suggestion only, not financial advice.
 */
public record TradeSuggestionDto(
        String        direction,
        String        timing,
        String        waitFor,
        TradeZoneDto  entryZone,
        TradeLevelDto stopLoss,
        TradeLevelDto target1,
        TradeLevelDto target2,
        String        note
) {}
