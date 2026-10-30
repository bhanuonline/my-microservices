package com.angle.trading.dto;
import java.math.BigDecimal;
/** A single price level (stop or target) with basis and risk-reward ratio. */
public record TradeLevelDto(BigDecimal price, String basis, String riskReward) {}
