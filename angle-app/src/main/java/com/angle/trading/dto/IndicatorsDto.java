package com.angle.trading.dto;

public record IndicatorsDto(
        MomentumDto        momentum,
        TrendDto           trend,
        MovingAveragesDto  movingAverages,
        VolatilityDto      volatility,
        SignalsDto         signals,
        CrossoversDto      crossovers,
        MarketStructureDto marketStructure,
        SmartMoneyDto      smartMoney,
        PivotPointsDto     pivotPoints,
        String interval,
        int    candleCount,
        /** IST timestamp of the last candle used e.g. "2026-10-28 15:15 IST" */
        String latestCandleAt
) {}
