package com.angle.trading.dto;

import java.util.List;
import java.util.Map;

/**
 * Consolidated analysis response for a single stock symbol.
 *
 * <p>Combines live price, multi-timeframe indicators, SMC analysis,
 * trade suggestion, and market status in one response.
 *
 * <p>Which fields are populated depends on the mode:
 * <ul>
 *   <li>LIVE        — all fields including timeframeStatus and nextCandleAt</li>
 *   <li>CONSOLIDATED — price + indicators, no timeframeStatus</li>
 *   <li>HISTORICAL  — indicators only, price is null</li>
 * </ul>
 *
 * @param symbol           Angel scrip master symbol e.g. "HDFCBANK-EQ"
 * @param name             instrument name e.g. "HDFC Bank"
 * @param exchange         exchange e.g. "NSE", "MCX" (auto-detected)
 * @param mode             LIVE | CONSOLIDATED | HISTORICAL
 * @param baseInterval     smallest TF used e.g. "FIFTEEN_MINUTE"
 * @param asOf             effective analysis date in IST
 * @param status           OK | STALE | ERROR
 * @param error            error message if status=ERROR, null otherwise
 * @param stale            true when Angel API failed and cached data is returned
 * @param staleSince       IST timestamp of last successful fetch
 * @param staleReason      why data is stale e.g. "Angel API rate limited"
 * @param retryAfterSeconds suggested wait before retrying (null when not stale)
 * @param price            live price block — null in HISTORICAL mode
 * @param market           market status, session hours, nextCandleAt
 * @param timeframeStatus  per-TF completion status — only in LIVE mode
 * @param multiTimeframe   indicators for each timeframe in the MTF stack
 * @param conclusion       cross-TF bias, confidence, tradeSetup
 * @param tradeSuggestion  actionable entry/stop/target suggestion
 * @param dayBreakdown     candle-by-candle detail — only when breakdown=true
 */
public record StockAnalysisDto(

        // ── Identity ─────────────────────────────────────────────
        String         symbol,
        String         name,
        String         exchange,
        String         mode,
        String         baseInterval,
        String         asOf,

        // ── Request result status ────────────────────────────────
        AnalysisStatus status,
        String         error,

        // ── Stale data flags ─────────────────────────────────────
        boolean        stale,
        String         staleSince,
        String         staleReason,
        Integer        retryAfterSeconds,

        // ── Price (null in HISTORICAL mode) ──────────────────────
        PriceDto       price,

        // ── Market status ─────────────────────────────────────────
        MarketStatusDto market,

        // ── Live-mode only ────────────────────────────────────────
        // key = interval name e.g. "ONE_HOUR"
        Map<String, TimeframeStatusDto> timeframeStatus,

        // ── Multi-timeframe indicators ────────────────────────────
        // ordered: lowest TF → highest TF
        Map<String, IndicatorsDto> multiTimeframe,

        // ── Analysis ──────────────────────────────────────────────
        MtfConclusionDto   conclusion,
        TradeSuggestionDto tradeSuggestion,

        // ── Opening Range Breakout ────────────────────────────────
        // Populated for LIVE and CONSOLIDATED modes. null in HISTORICAL.
        OrbDto orb,

        // ── Optional day breakdown (breakdown=true only) ──────────
        DayBreakdownDto dayBreakdown,

        // ── Market closed suggestions (live=true + CLOSED only) ───
        // e.g. "Use ?asOf=2026-10-30 to analyse today's session"
        List<String> suggestions

) {
    /**
     * Creates a minimal error result for use in bulk responses
     * when a symbol fails to analyse.
     *
     * @param symbol the symbol that failed
     * @param reason the error description
     */
    public static StockAnalysisDto error(String symbol, String reason) {
        return new StockAnalysisDto(
                symbol, null, null, null, null, null,
                AnalysisStatus.ERROR, reason,
                false, null, null, null,
                null, null, null, null,
                null, null, null, null, null
        );
    }
}
