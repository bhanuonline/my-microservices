package com.angle.trading.dto;
import java.math.BigDecimal;
public record MovingAveragesDto(BigDecimal ema9, BigDecimal ema20, BigDecimal ema50, BigDecimal ema200, BigDecimal sma20, BigDecimal vwap) {}
