package com.angle.trading.dto;

import java.math.BigDecimal;

public record StockQuoteDto(
        String token,
        String symbol,
        String name,
        String exchange,
        String instrumentType,
        BigDecimal ltp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePercent,
        IndicatorsDto indicators
) {}
