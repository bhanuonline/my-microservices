package com.angle.trading.paper.model;

import com.angle.trading.strategy.model.IntentAction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An open position inside a paper-trading session.
 *
 *   direction — ENTER_LONG or ENTER_SHORT
 *   entryTime — when the entry candle timestamped
 *   entry     — fill price
 *   stop      — stop-loss level (nullable)
 *   target    — profit target (nullable)
 *   rationale — free text from the strategy that opened this
 */
public record PaperPosition(
        IntentAction direction,
        Instant entryTime,
        BigDecimal entry,
        BigDecimal stop,
        BigDecimal target,
        String rationale
) {}
