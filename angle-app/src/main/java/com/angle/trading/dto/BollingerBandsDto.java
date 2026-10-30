package com.angle.trading.dto;
import java.math.BigDecimal;
public record BollingerBandsDto(BigDecimal upper, BigDecimal middle, BigDecimal lower) {}
