package com.angle.trading.hedge;

import java.math.BigDecimal;

public record HedgeComparisonRequest(
        String     symbolToken,
        BigDecimal entryPrice,
        int        quantity,
        BigDecimal putStrike,
        BigDecimal putPremium,
        BigDecimal callStrike,
        BigDecimal callPremium,
        BigDecimal futurePrice,
        Integer    daysToExpiry    // nullable — defaults to 30 in the analyzer
) {}
