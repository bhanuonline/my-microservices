package com.angle.trading.hedge;

import java.math.BigDecimal;

public record HedgeJournalRequest(
        String     symbol,
        BigDecimal entryPrice,
        int        quantity,
        String     strategyName,
        String     regime,
        String     rationale,
        BigDecimal putStrike,
        BigDecimal callStrike,
        BigDecimal putPremium,
        BigDecimal maxLoss,
        BigDecimal breakeven
) {}
