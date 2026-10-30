package com.angle.trading.dto;

/**
 * Current market status for the instrument's exchange.
 *
 * <p>When status=OPEN: nextCandleAt and sessionProgress are populated.
 * When status=CLOSED: nextMarketOpen and lastSession are populated.
 *
 * @param status          "OPEN" or "CLOSED"
 * @param open            session open time e.g. "09:15" (null when closed)
 * @param close           session close time e.g. "15:30" (null when closed)
 * @param message         human-readable status message
 * @param nextCandleAt    ISO timestamp of when the next candle bar closes
 * @param nextMarketOpen  ISO timestamp of when market next opens (null when open)
 * @param sessionProgress e.g. "2h 15m into session (34% complete)"
 * @param sessionNote     extra context e.g. on MCX partial holidays
 * @param lastSession     last session analysis — populated when market is CLOSED
 */
public record MarketStatusDto(
        String         status,
        String         open,
        String         close,
        String         message,
        String         nextCandleAt,
        String         nextMarketOpen,
        String         sessionProgress,
        String         sessionNote,
        LastSessionDto lastSession
) {}
