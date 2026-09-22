package com.angle.trading.bias.model;

import java.math.BigDecimal;

/**
 * India VIX snapshot.
 *
 *   value     — current VIX level (null if fetch failed)
 *   change    — points move vs previous close
 *   changePct — % move vs previous close
 *   regime    — "CALM" (<13) / "NORMAL" (13-18) / "ELEVATED" (>18)
 */
public record VixSection(
        BigDecimal value,
        BigDecimal change,
        BigDecimal changePct,
        String     regime
) {}
