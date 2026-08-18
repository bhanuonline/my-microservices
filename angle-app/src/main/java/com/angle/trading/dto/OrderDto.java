package com.angle.trading.dto;

public record OrderDto(
        String id,
        String symbol,
        String side,
        double qty,
        double price,
        String status,
        String date
) {}
