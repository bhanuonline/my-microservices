package com.angle.trading.journal;
import java.math.BigDecimal;
public record TradeStatsDto(long totalTrades, long openTrades, long closedTrades,
        long wins, long losses, String winRate, BigDecimal totalProfitPoints,
        long target1Hits, long target2Hits, long stopHits, long manualCloses) {}
