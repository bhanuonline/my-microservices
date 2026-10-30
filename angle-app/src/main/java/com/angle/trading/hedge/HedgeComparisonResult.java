package com.angle.trading.hedge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record HedgeComparisonResult(
        String                      symbol,
        BigDecimal                  entryPrice,
        int                         quantity,
        int                         lotSize,
        double                      hedgeRatio,
        IndicatorSnapshot           indicatorSnapshot,
        List<HedgeStrategyAnalysis> analyses,
        HedgeStrategyType           recommended,
        String                      recommendedRationale,
        LocalDateTime               generatedAt,
        double                      hv30,                // annualised HV, e.g. 0.18 = 18%
        String                      hv30Assessment,      // CHEAP / FAIR / EXPENSIVE
        List<LocalDate>             expiryDates,         // next 3 NSE monthly expiries
        BigDecimal                  zeroCostCallStrike,  // symmetric OTM estimate
        int                         daysToExpiry
) {}
