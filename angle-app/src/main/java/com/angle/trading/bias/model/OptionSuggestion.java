package com.angle.trading.bias.model;

import com.angle.trading.broker.model.OptionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Suggested option contract to trade based on bias direction + confidence + VIX + expiry.
 *
 * Rule-based, not chain-priced — we suggest the STRIKE, not the LTP.
 * User verifies actual option premium in Angel app before placing.
 *
 *   type         — CE (buy call for long bias) or PE (buy put for short bias)
 *   strike       — chosen strike, rounded to strike interval
 *   underlying   — e.g. "NIFTY", "BANKNIFTY"
 *   spotPrice    — underlying's current price when suggestion was made
 *   atmStrike    — reference ATM strike (spot rounded to strike interval)
 *   distanceFromAtm — points OTM (+) or ITM (-); 0 = ATM
 *   suggestedExpiry — chosen expiry (near preferred-days-to-expiry from config)
 *   daysToExpiry — days from today to the chosen expiry
 *   symbol       — full Angel symbol if lookup succeeded (e.g. "NIFTY25SEP2624700CE")
 *   symbolToken  — Angel numeric token (nullable)
 *   rationale    — why this strike (confidence + VIX + expiry math)
 */
public record OptionSuggestion(
        OptionType type,
        BigDecimal strike,
        String     underlying,
        BigDecimal spotPrice,
        BigDecimal atmStrike,
        int        distanceFromAtm,
        LocalDate  suggestedExpiry,
        int        daysToExpiry,
        String     symbol,
        String     symbolToken,
        String     rationale
) {}
