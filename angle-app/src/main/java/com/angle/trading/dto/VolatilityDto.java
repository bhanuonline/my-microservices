package com.angle.trading.dto;
import java.math.BigDecimal;
public record VolatilityDto(BigDecimal atr14, BollingerBandsDto bollingerBands) {}
