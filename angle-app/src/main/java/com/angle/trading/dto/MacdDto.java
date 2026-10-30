package com.angle.trading.dto;
import java.math.BigDecimal;
public record MacdDto(BigDecimal line, BigDecimal signal, BigDecimal histogram) {}
