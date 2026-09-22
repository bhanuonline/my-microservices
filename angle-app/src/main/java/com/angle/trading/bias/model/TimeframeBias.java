package com.angle.trading.bias.model;

import com.angle.trading.broker.model.Interval;
import com.angle.trading.marketstructure.model.Direction;

/**
 * Section 2 of the bias sheet — one row per timeframe.
 *
 *   interval           — 1D / 1H / 15M / 5M
 *   bias               — BULLISH / BEARISH / null (undetermined)
 *   reason             — short human-readable why (e.g. "bullish BOS at 24500")
 *   analystConfidence  — 0.0..1.0 from Analyst endpoint at this timeframe
 */
public record TimeframeBias(
        Interval interval,
        Direction bias,
        String reason,
        Double analystConfidence
) {}
