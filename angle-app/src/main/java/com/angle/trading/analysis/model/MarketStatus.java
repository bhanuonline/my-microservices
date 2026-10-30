package com.angle.trading.analysis.model;

import java.time.ZonedDateTime;

/**
 * Current market status for a given exchange at a given time.
 *
 * @param open            true if market is open right now
 * @param sessionOpen     today's session open time (may differ on partial MCX holidays)
 * @param sessionClose    today's session close time
 * @param statusMessage   human-readable status (e.g. "NSE open until 15:30 IST")
 * @param nextCandleAt    when the next candle bar closes (IST)
 * @param nextMarketOpen  when the market next opens — null if currently open
 * @param sessionProgress e.g. "2h 15m into session (34% complete)"
 * @param sessionNote     extra context on partial MCX holidays
 */
public record MarketStatus(
        boolean      open,
        String       sessionOpen,
        String       sessionClose,
        String       statusMessage,
        ZonedDateTime nextCandleAt,
        ZonedDateTime nextMarketOpen,
        String       sessionProgress,
        String       sessionNote
) {}
