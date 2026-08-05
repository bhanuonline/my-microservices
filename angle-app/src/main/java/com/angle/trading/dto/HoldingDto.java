package com.angle.trading.dto;

public record HoldingDto(
        String symbol,
        String name,
        double qty,
        double avgCost,
        double price,
        double changePct
) {}
