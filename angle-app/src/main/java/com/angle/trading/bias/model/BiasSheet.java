package com.angle.trading.bias.model;

import java.time.Instant;
import java.util.List;

/**
 * The full daily-bias sheet for ONE instrument at ONE point in time.
 *
 * Assembled by BiasSheetService from many sources. Everything here is a
 * snapshot — never mutates. Rebuilt on every refresh.
 *
 * Fields nullable when the underlying data isn't available (e.g. options
 * chain not yet built, VIX not fetched). UI shows "—" for nulls.
 */
public record BiasSheet(
        Instant  asOf,
        String   symbol,             // "Nifty 50"
        String   symbolToken,        // "99926000"
        String   exchange,           // "NSE"

        MarketContextSection  marketContext,
        List<TimeframeBias>   multiTfBias,
        TrendFilters          trendFilters,
        StructureSection      structure,
        ZonesSection          zones,
        ConsolidatedScore     consolidated
) {}
