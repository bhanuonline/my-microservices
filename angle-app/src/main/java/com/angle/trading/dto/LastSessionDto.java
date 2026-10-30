package com.angle.trading.dto;

/**
 * Summary of the most recent completed trading session.
 * Included in the market status when the market is CLOSED,
 * so users can see analysis without making an extra API call.
 *
 * @param asOf             date of the last session e.g. "2026-10-30"
 * @param closedAt         session close time e.g. "15:30 IST"
 * @param price            last known price (LTP at close)
 * @param conclusion       MTF conclusion from the last session
 * @param tradeSuggestion  trade suggestion from the last session
 */
public record LastSessionDto(
        String             asOf,
        String             closedAt,
        PriceDto           price,
        MtfConclusionDto   conclusion,
        TradeSuggestionDto tradeSuggestion
) {}
