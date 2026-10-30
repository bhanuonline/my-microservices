package com.angle.trading.dto;

import java.time.Instant;

public record IndicatorSnapshotDto(
        Instant time,
        String timeIst,
        CandleDataDto      candle,
        MomentumDto        momentum,
        TrendDto           trend,
        MovingAveragesDto  movingAverages,
        VolatilityDto      volatility,
        SignalsDto         signals,
        CrossoversDto      crossovers,
        MarketStructureDto marketStructure,
        SmartMoneyDto      smartMoney,
        PivotPointsDto     pivotPoints,
        OrbDto             orb
) {}
