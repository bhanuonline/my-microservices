package com.angle.trading.hedge;

import java.math.BigDecimal;
import java.util.List;

public record HedgeStrategyAnalysis(
        HedgeStrategyType      strategy,
        String                 displayName,
        String                 position,
        BigDecimal             netPremiumPerShare,
        BigDecimal             totalPremiumCost,
        BigDecimal             breakeven,
        BigDecimal             maxGain,              // null = unlimited
        BigDecimal             maxLoss,
        List<HedgeScenario>    scenarios,            // 7 points for table
        List<HedgeScenario>    chartScenarios,       // 25 points for payoff chart
        int                    regimeFitScore,       // 1–5
        boolean                recommended,
        String                 rationale,
        String                 hedgeNote,
        double                 probabilityOfProfit,  // 0.0–1.0 from historical returns
        int                    estimatedDaysToBreakeven,
        List<HedgeTimeDecayRow> timeDecay,           // P&L at key time points (stock at entry)
        String                 riskRewardRatio,      // e.g. "∞ : 1" or "1.3 : 1"
        double                 breakevenDistancePct, // % stock must move to break even (+up, -down)
        BigDecimal             dailyTheta            // estimated ₹/day (negative=cost, positive=income)
) {}
