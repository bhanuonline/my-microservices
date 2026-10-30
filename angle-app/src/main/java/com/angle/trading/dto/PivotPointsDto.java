package com.angle.trading.dto;

import java.math.BigDecimal;

/**
 * Classic Pivot Points — calculated from the previous trading day's High/Low/Close.
 *
 *   P  = (PrevHigh + PrevLow + PrevClose) / 3
 *   R1 = 2P - PrevLow          S1 = 2P - PrevHigh
 *   R2 = P + (PrevHigh-PrevLow) S2 = P - (PrevHigh-PrevLow)
 *   R3 = PrevHigh + 2(P-PrevLow) S3 = PrevLow - 2(PrevHigh-P)
 *
 *   basedOn — the previous trading day's date used for calculation (YYYY-MM-DD)
 *
 * Usage:
 *   Price above pivot  → bullish bias for the day
 *   Price below pivot  → bearish bias
 *   R1/R2/R3           → resistance levels (where sellers may appear)
 *   S1/S2/S3           → support levels (where buyers may appear)
 */
public record PivotPointsDto(
        BigDecimal pivot,
        BigDecimal r1, BigDecimal r2, BigDecimal r3,
        BigDecimal s1, BigDecimal s2, BigDecimal s3,
        String basedOn
) {}
