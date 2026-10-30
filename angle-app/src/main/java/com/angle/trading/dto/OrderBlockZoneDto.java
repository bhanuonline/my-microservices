package com.angle.trading.dto;
import java.math.BigDecimal;
/** Simplified Order Block zone for API responses. */
public record OrderBlockZoneDto(BigDecimal top, BigDecimal bottom, String direction) {}
