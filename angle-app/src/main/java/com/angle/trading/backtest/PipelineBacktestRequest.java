package com.angle.trading.backtest;

import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;

import java.time.LocalDate;

/**
 * Input to {@link PipelineBacktestService}.
 *
 * Sizing:
 *   RISK_BASED   → position = (capital × riskPct) / stopDistance (default)
 *   FIXED_LOTS   → position = lotSize × fixedLots (ignores capital/risk)
 */
public record PipelineBacktestRequest(
        String     symbolToken,
        String     symbol,
        Exchange   exchange,
        Interval   interval,
        LocalDate  fromDate,
        LocalDate  toDate,
        int        minAgreement,
        String     trailingMode,
        double     trailingPercent,
        double     trailingActivation,
        int        expiryBars,
        double     capital,
        double     riskPercent,
        // ── position sizing (new) ──
        int        lotSize,            // units per lot (Nifty=75, Reliance=250, cash=1)
        String     sizingMode,         // RISK_BASED | FIXED_LOTS
        int        fixedLots           // when sizingMode=FIXED_LOTS, use this many lots per trade
) {}
