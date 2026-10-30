package com.angle.trading.dto;
import java.math.BigDecimal;
/** A price zone to enter (from–to range). */
public record TradeZoneDto(BigDecimal from, BigDecimal to, String basis) {}
