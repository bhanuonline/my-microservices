package com.angle.trading.dto;
import java.math.BigDecimal;
public record CandleDataDto(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {}
