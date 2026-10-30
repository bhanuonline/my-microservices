package com.angle.trading.dto;
import java.math.BigDecimal;
import java.util.List;
/**
 * Smart Money Concepts (SMC) context.
 *   nearestBullishOB       — nearest unmitigated bullish OB below price (potential support)
 *   nearestBearishOB       — nearest unmitigated bearish OB above price (potential resistance)
 *   openFvgs               — up to 5 unmitigated Fair Value Gaps closest to price
 *   buySideLiquidityAbove  — nearest unswept buy-side liquidity above price (stop-hunt target)
 *   sellSideLiquidityBelow — nearest unswept sell-side liquidity below price
 */
public record SmartMoneyDto(
        OrderBlockZoneDto nearestBullishOB,
        OrderBlockZoneDto nearestBearishOB,
        List<FvgZoneDto>  openFvgs,
        BigDecimal        buySideLiquidityAbove,
        BigDecimal        sellSideLiquidityBelow
) {}
