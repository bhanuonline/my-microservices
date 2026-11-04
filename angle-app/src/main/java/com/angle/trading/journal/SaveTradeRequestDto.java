package com.angle.trading.journal;
import com.angle.trading.dto.StockAnalysisDto;
import java.math.BigDecimal;
public record SaveTradeRequestDto(String symbol, String exchange, String intervalStack, BigDecimal entryPrice, StockAnalysisDto analysis) {}
