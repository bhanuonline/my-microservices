package com.angle.trading.analysis.model;

import java.time.LocalDate;

/**
 * A single holiday parsed from market-holidays.yml.
 *
 * @param date     the holiday date
 * @param name     human-readable name (e.g. "Republic Day")
 * @param enabled  false = ignored (treat as normal trading day)
 * @param session  MCX-only — which session is affected. Null for NSE/BSE.
 */
public record HolidayEntry(
        LocalDate date,
        String    name,
        boolean   enabled,
        McxSession session
) {}
