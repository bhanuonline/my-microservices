package com.angle.trading.journal;
import java.math.BigDecimal; import java.time.Instant;
public record TradeJournalDto(Long id, String symbol, String exchange, String direction,
        BigDecimal entryPrice, Instant entryTime, BigDecimal entryZoneFrom, BigDecimal entryZoneTo,
        BigDecimal stopLoss, BigDecimal target1, String target1Rr, BigDecimal target2, String target2Rr,
        String status, BigDecimal closePrice, Instant closeTime, String closeReason,
        BigDecimal profitPoints, BigDecimal profitPercent,
        String primaryBias, String confidence, String tradeSetup, String intervalStack,
        BigDecimal rsi14, BigDecimal adx14, String supertrend,
        String orb15m, String orb30m, String orb60m, String notes, Instant createdAt) {}
