package com.angle.trading.paper.model;

import com.angle.trading.strategy.model.IntentAction;

import java.math.BigDecimal;

/**
 * Body for POST /api/paper/sessions/{id}/enter
 *
 *   direction  — ENTER_LONG or ENTER_SHORT (required)
 *   entry      — fill price (required)
 *   stop       — stop-loss level (optional — auto-exit still fires if set)
 *   target     — profit target  (optional — auto-exit still fires if set)
 *   rationale  — free-text note (optional)
 */
public record ManualEntryRequest(
        IntentAction direction,
        BigDecimal entry,
        BigDecimal stop,
        BigDecimal target,
        String rationale
) {}
