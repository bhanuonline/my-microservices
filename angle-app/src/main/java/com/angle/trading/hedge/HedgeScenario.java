package com.angle.trading.hedge;

import java.math.BigDecimal;

public record HedgeScenario(
        String     priceLabel,       // "-20%", "-10%", ...
        BigDecimal priceAtExpiry,
        BigDecimal stockPnl,
        BigDecimal optionPayoff,
        BigDecimal futurePnl,
        BigDecimal netPnl
) {}
