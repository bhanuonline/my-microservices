package com.angle.trading.marketstructure.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A candle that grabbed a liquidity level and closed back inside.
 *
 *   BUY_SIDE  sweep — candle high > level; close < level; anticipates downward reversal.
 *   SELL_SIDE sweep — candle low  < level; close > level; anticipates upward reversal.
 *
 *   index          — the sweep candle
 *   levelPrice     — the swept level's price
 *   side           — side of the level that was swept
 *   levelFormedAt  — index of the swing candle that formed the level
 */
public record LiquiditySweep(
        int index,
        Instant timestamp,
        BigDecimal levelPrice,
        LiquiditySide side,
        int levelFormedAt
) {}
