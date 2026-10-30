package com.angle.trading.dto;
import java.math.BigDecimal;
/** Fair Value Gap zone for API responses. */
public record FvgZoneDto(BigDecimal top, BigDecimal bottom, String direction) {}
