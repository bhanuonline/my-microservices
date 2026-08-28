package com.angle.trading.marketstructure.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A price level where stop orders are likely piled up.
 *
 * Every swing point produces one liquidity level:
 *   swing high → BUY_SIDE liquidity  (short-sellers' stops)
 *   swing low  → SELL_SIDE liquidity (buyers' stops)
 *
 *   price          — the level itself
 *   side           — BUY_SIDE or SELL_SIDE
 *   formedAtIndex  — the swing candle
 *   swept          — true once a later candle has pierced (wick + close-back-inside)
 *   sweptAtIndex   — first candle that swept the level (null if untouched)
 */
public record LiquidityLevel(
        Instant timestamp,
        BigDecimal price,
        LiquiditySide side,
        int formedAtIndex,
        boolean swept,
        Integer sweptAtIndex
) {}
