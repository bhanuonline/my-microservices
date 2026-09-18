package com.angle.trading.bias.model;

import java.math.BigDecimal;

/**
 * Snapshot of a correlated instrument (e.g. Bank Nifty alongside Nifty).
 *
 *   symbol            — display name
 *   currentPrice      — last close
 *   dayChangePercent  — today's % move
 *   mainChangePercent — the main instrument's % move today
 *   divergencePercent — abs difference between the two %s
 *   diverging         — true if divergence exceeds configured threshold
 *   directionAgreement — true if both moving same direction (both up or both down)
 *
 * Signal use:
 *   - Both up + no divergence → real bullish move
 *   - Nifty up, Bank Nifty flat/down → NARROW rally, fade risk
 *   - Both down + no divergence → real selloff
 *   - Divergent moves → wait for confirmation, don't trade
 */
public record CorrelatedSection(
        String     symbol,
        BigDecimal currentPrice,
        BigDecimal dayChangePercent,
        BigDecimal mainChangePercent,
        BigDecimal divergencePercent,
        boolean    diverging,
        boolean    directionAgreement,
        String     signal              // human-readable interpretation
) {}
