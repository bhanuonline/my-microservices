package com.angle.trading.signal;

public record SignalRecordRequest(
        String market,
        String symbol,
        String tradeDate,
        String note,
        String overallSignal,
        int    overallSum,
        String stateJson
) {}
