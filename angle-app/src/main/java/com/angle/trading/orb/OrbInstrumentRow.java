package com.angle.trading.orb;

import java.util.List;

/**
 * One row in the ORB dashboard — one instrument with its per-timeframe cells.
 *
 * The UI renders this as a horizontal strip: NIFTY 50 | [5m] [15m] [30m] ...
 */
public record OrbInstrumentRow(
        String symbolToken,
        String name,              // "Nifty 50", "Bank Nifty", "Crude Oil"
        String exchange,          // "NSE", "MCX"
        String sessionOpen,       // "09:15"
        String sessionClose,      // "15:30" or "23:30"
        List<OrbCell> cells       // ordered by timeframe ascending
) {}
