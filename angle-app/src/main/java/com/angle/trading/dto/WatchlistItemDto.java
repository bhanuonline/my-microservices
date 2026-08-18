package com.angle.trading.dto;

public record WatchlistItemDto(
        String symbol,
        String name,
        double price,
        double changePct
) {}
