package com.angle.trading.dto;

import java.math.BigDecimal;

/**
 * Open Interest data with market interpretation.
 * Only available for F&O instruments (futures/options).
 * Null for equity and indices.
 *
 * interpretation:
 *   LONG_BUILDUP   — price ↑ + OI ↑ → bulls entering NEW positions → BULLISH
 *   SHORT_BUILDUP  — price ↓ + OI ↑ → bears entering NEW positions → BEARISH
 *   SHORT_COVERING — price ↑ + OI ↓ → bears EXITING → weak rally, may reverse
 *   LONG_UNWINDING — price ↓ + OI ↓ → bulls EXITING → bearish but temporary
 */
public record OpenInterestDto(
        BigDecimal current,
        BigDecimal change,
        String     changePct,
        String     interpretation,
        String     description
) {}
