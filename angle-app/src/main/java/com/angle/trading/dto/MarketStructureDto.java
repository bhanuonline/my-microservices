package com.angle.trading.dto;
import java.math.BigDecimal;
/**
 * Current market structure context.
 *   bias             — BULLISH | BEARISH (from last structure event)
 *   lastEvent        — BOS_BULLISH | BOS_BEARISH | CHOCH_BULLISH | CHOCH_BEARISH
 *   nearestSwingHigh — nearest unbroken swing high above current price (resistance)
 *   nearestSwingLow  — nearest unbroken swing low below current price (support)
 */
public record MarketStructureDto(
        String bias,
        String lastEvent,
        BigDecimal nearestSwingHigh,
        BigDecimal nearestSwingLow
) {}
