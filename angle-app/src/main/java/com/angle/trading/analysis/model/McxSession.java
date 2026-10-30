package com.angle.trading.analysis.model;

/**
 * Describes which MCX trading session is affected on a partial holiday.
 *
 * MCX has two sessions:
 *   Morning: 09:00 – 17:00 IST
 *   Evening: 17:00 – 23:30 IST
 *
 * On most holidays, only the morning session is closed and
 * evening trading resumes at 17:00. A few holidays (Republic Day,
 * Good Friday, Gandhi Jayanti, Christmas) close both sessions.
 */
public enum McxSession {

    /** Both morning and evening sessions are closed. */
    FULL_CLOSED,

    /** Morning (09:00–17:00) closed. Evening (17:00–23:30) open. */
    MORNING_ONLY_CLOSED,

    /** Morning (09:00–17:00) open. Evening (17:00–23:30) closed. */
    EVENING_ONLY_CLOSED
}
