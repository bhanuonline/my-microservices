package com.angle.trading.hedge;

import java.math.BigDecimal;
import java.util.List;

public record IndicatorSnapshot(
        BigDecimal    rsi,
        int           rsiScore,
        String        rsiSignal,
        BigDecimal    adx,
        int           adxScore,
        String        adxSignal,
        BigDecimal    ema21,
        BigDecimal    currentPrice,
        int           ema21Score,
        String        ema21Signal,
        int           totalScore,
        MarketRegime  regime,
        List<String>  reasons
) {}
