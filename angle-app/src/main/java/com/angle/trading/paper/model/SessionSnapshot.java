package com.angle.trading.paper.model;

import com.angle.trading.strategy.model.Trade;
import com.angle.trading.strategy.model.TradeIntent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Immutable snapshot of a session's state — returned by GET /api/paper/sessions/{id}.
 *
 *   candleCount — how many candles have been ingested so far
 *   lastIntent  — the most recent decision from the strategy (may be HOLD)
 *   openPosition — the currently-open paper position (null if flat)
 *   trades      — completed trades so far, chronological
 *   netPnl      — sum of trade P&Ls booked so far (does NOT include unrealised P&L on the open position)
 */
public record SessionSnapshot(
        String sessionId,
        String strategyName,
        String sourceName,
        SessionStatus status,
        Instant startedAt,
        Instant lastCandleAt,
        int candleCount,
        int totalTrades,
        int winners,
        int losers,
        BigDecimal netPnl,
        TradeIntent lastIntent,
        PaperPosition openPosition,
        List<Trade> trades
) {}
