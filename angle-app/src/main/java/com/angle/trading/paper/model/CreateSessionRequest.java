package com.angle.trading.paper.model;

import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;

/**
 * Request body for POST /api/paper/sessions.
 *
 * Two supported source types:
 *
 *   "replay-nifty-csv"  — replays the bundled Nifty CSV at candlesPerSecond
 *     Required: strategyName
 *     Optional: candlesPerSecond (default 50)
 *
 *   "angel-live"        — polls Angel SmartAPI for the current instrument
 *     Required: strategyName, symbolToken
 *     Optional: exchange (default NSE), interval (default ONE_MINUTE),
 *               warmupCandles (default 100), pollIntervalSeconds (default 30)
 *
 * Fields not applicable to the chosen sourceType are ignored.
 */
public record CreateSessionRequest(
        String strategyName,
        String sourceType,

        // replay-nifty-csv
        Integer candlesPerSecond,

        // angel-live
        String symbolToken,
        Exchange exchange,
        Interval interval,
        Integer warmupCandles,
        Integer pollIntervalSeconds
) {}
