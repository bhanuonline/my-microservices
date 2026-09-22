package com.angle.trading.paper.model;

import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Request body for POST /api/paper/sessions.
 *
 * Three supported source types:
 *
 *   "replay-nifty-csv"          — replays the bundled Nifty CSV at candlesPerSecond
 *     Required: strategyName
 *     Optional: candlesPerSecond (default 50)
 *
 *   "angel-live"                — polls Angel SmartAPI for the current instrument
 *     Required: strategyName, symbolToken
 *     Optional: exchange (default NSE), interval (default ONE_MINUTE),
 *               warmupCandles (default 100), pollIntervalSeconds (default 30)
 *
 *   "angel-historical-replay"   — fetches ANY Angel instrument for a date range
 *                                 and replays it at candlesPerSecond speed
 *     Required: strategyName, symbolToken, from, to
 *     Optional: exchange (default NSE), interval (default ONE_DAY),
 *               candlesPerSecond (default 50)
 *
 * Fields not applicable to the chosen sourceType are ignored.
 */
public record CreateSessionRequest(
        String strategyName,
        String sourceType,

        // replay-nifty-csv AND angel-historical-replay
        Integer candlesPerSecond,

        // angel-live AND angel-historical-replay
        String symbolToken,
        Exchange exchange,
        Interval interval,

        // angel-live only
        Integer warmupCandles,
        Integer pollIntervalSeconds,

        // angel-historical-replay only
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate from,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate to
) {}
