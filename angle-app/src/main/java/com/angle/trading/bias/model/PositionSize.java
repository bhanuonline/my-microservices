package com.angle.trading.bias.model;

import java.math.BigDecimal;

/**
 * Position sizing calculation.
 *
 *   capital           — your configured trading capital
 *   riskPercent       — % of capital allowed to risk per trade
 *   maxRiskAmount     — capital × riskPercent / 100
 *   perLotRiskAmount  — (entry - stop) × lotSize
 *   lotSize           — instrument's lot size (Nifty 75, Bank Nifty 30, etc.)
 *   recommendedLots   — max_risk / per_lot_risk, rounded DOWN
 *   totalRiskIfTaken  — perLotRisk × recommendedLots (actual capital at risk)
 *   warning           — non-null when the recommendation is 0 lots (trade too big)
 */
public record PositionSize(
        long       capital,
        double     riskPercent,
        BigDecimal maxRiskAmount,
        BigDecimal perLotRiskAmount,
        int        lotSize,
        int        recommendedLots,
        BigDecimal totalRiskIfTaken,
        String     warning
) {}
