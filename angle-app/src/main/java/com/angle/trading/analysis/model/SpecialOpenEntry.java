package com.angle.trading.analysis.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * A special trading session that overrides the normal weekend/holiday block.
 * Used for Diwali Muhurat Trading (Sunday evening session).
 *
 * @param date        the date of the special session
 * @param name        human-readable name
 * @param exchanges   list of exchange names this applies to (e.g. ["NSE","MCX"])
 * @param marketOpen  session start time in IST
 * @param marketClose session end time in IST
 * @param enabled     false = treat as normal closed day
 */
public record SpecialOpenEntry(
        LocalDate   date,
        String      name,
        List<String> exchanges,
        LocalTime   marketOpen,
        LocalTime   marketClose,
        boolean     enabled
) {}
