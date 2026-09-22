package com.angle.trading.bias.model;

/**
 * Advance/Decline breadth snapshot.
 *
 *   label         — human-readable ("Nifty 50 breadth")
 *   universeSize  — total constituents attempted
 *   advances      — number of stocks up vs prev close
 *   declines      — number of stocks down vs prev close
 *   unchanged     — number of stocks flat
 *   ratio         — advances / declines (Double so it can be NaN/Infinite; UI handles)
 *   regime        — "STRONG_BULLISH" / "BULLISH" / "NEUTRAL" / "BEARISH" / "STRONG_BEARISH" / null
 *   interpretation — human-readable signal
 *
 * Divergence check (against main index) is applied at the score level in
 * BiasSheetService, not here — this record just captures the raw breadth.
 */
public record BreadthSection(
        String label,
        int    universeSize,
        int    advances,
        int    declines,
        int    unchanged,
        Double ratio,
        String regime,
        String interpretation
) {}
