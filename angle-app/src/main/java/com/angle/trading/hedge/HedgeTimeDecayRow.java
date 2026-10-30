package com.angle.trading.hedge;

import java.math.BigDecimal;

public record HedgeTimeDecayRow(
        String     label,            // "Today (30d)", "20 days left", etc.
        int        daysRemaining,
        BigDecimal estimatedNetPnl   // stock assumed to stay at entry price
) {}
