package com.angle.trading.dto;

import java.util.List;

/**
 * Request body for POST /api/stocks/analysis/bulk.
 *
 * <p>Top-level {@code live} and {@code breakdown} apply to all symbols.
 * Each symbol can override interval, baseInterval, days, and asOf.
 *
 * <p>Example:
 * <pre>
 * {
 *   "live": true,
 *   "breakdown": false,
 *   "symbols": [
 *     { "symbol": "HDFCBANK-EQ",       "interval": "ONE_HOUR"    },
 *     { "symbol": "RELIANCE-EQ",        "interval": "ONE_HOUR"    },
 *     { "symbol": "Nifty 50",           "interval": "ONE_DAY"     },
 *     { "symbol": "CRUDEOIL30OCT26FUT", "interval": "FIVE_MINUTE",
 *       "baseInterval": "FIVE_MINUTE",  "days": 30                }
 *   ]
 * }
 * </pre>
 *
 * @param live      apply live mode to all symbols
 * @param breakdown include candle-by-candle breakdown for all symbols
 *                  (max 3 symbols when true — enforced by service layer)
 * @param symbols   list of per-symbol requests (max 20 — configurable)
 */
public record BulkAnalysisRequestDto(
        boolean               live,
        boolean               breakdown,
        List<SymbolRequestDto> symbols
) {}
