package com.angle.trading.dto;

import java.util.List;

/**
 * Response wrapper for POST /api/stocks/analysis/bulk.
 *
 * <p>All symbols are processed in parallel. If one symbol fails,
 * the others still return — check each result's {@code status} field.
 *
 * <p>Example:
 * <pre>
 * {
 *   "requested": 4,
 *   "successful": 3,
 *   "failed": 1,
 *   "processingTimeMs": 1240,
 *   "results": [
 *     { "symbol": "HDFCBANK-EQ", "status": "OK",    ... },
 *     { "symbol": "INVALID-EQ",  "status": "ERROR",  "error": "Symbol not found" },
 *     { "symbol": "RELIANCE-EQ", "status": "OK",    ... },
 *     { "symbol": "Nifty 50",    "status": "STALE",  "staleSince": "10:00 IST", ... }
 *   ]
 * }
 * </pre>
 *
 * @param requested        total symbols in the request
 * @param successful       symbols with status OK or STALE
 * @param failed           symbols with status ERROR
 * @param processingTimeMs total time taken in milliseconds
 * @param results          one entry per symbol, in request order
 */
public record BulkAnalysisResponseDto(
        int                     requested,
        int                     successful,
        int                     failed,
        long                    processingTimeMs,
        List<StockAnalysisDto>  results
) {
    /**
     * Builds a BulkAnalysisResponseDto from a list of results and elapsed time.
     *
     * @param results         list of per-symbol analysis results
     * @param processingTimeMs time taken to process all symbols in parallel
     */
    public static BulkAnalysisResponseDto of(List<StockAnalysisDto> results,
                                              long processingTimeMs) {
        int failed     = (int) results.stream()
                .filter(r -> r.status() == AnalysisStatus.ERROR).count();
        int successful = results.size() - failed;
        return new BulkAnalysisResponseDto(
                results.size(), successful, failed, processingTimeMs, results);
    }
}
