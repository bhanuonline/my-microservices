package com.angle.trading.bias.model;

import com.angle.trading.strategy.model.IntentAction;

import java.math.BigDecimal;

/**
 * Actionable trade plan built from the consolidated bias.
 *
 * Rendered as the "Trade Plan" card on the bias dashboard when the
 * recommendation is LONG-ONLY or SHORT-ONLY. Skipped for NO-TRADE.
 *
 *   action         — ENTER_LONG / ENTER_SHORT
 *   entry          — suggested entry price (usually current close)
 *   stop / target  — from the ensemble strategy
 *   riskPoints     — |entry - stop|
 *   rewardPoints   — |target - entry|
 *   riskRewardRatio — reward / risk (higher = better)
 *   positionSize   — how many lots to trade, given your risk budget
 *   optionSuggestion — which option strike to trade (if applicable)
 *   rationale      — human-readable "why"
 */
public record TradePlan(
        IntentAction     action,
        BigDecimal       entry,
        BigDecimal       stop,
        BigDecimal       target,
        BigDecimal       riskPoints,
        BigDecimal       rewardPoints,
        BigDecimal       riskRewardRatio,
        PositionSize     positionSize,
        OptionSuggestion optionSuggestion,
        String           rationale
) {}
