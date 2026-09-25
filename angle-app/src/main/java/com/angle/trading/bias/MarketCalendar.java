package com.angle.trading.bias;

import com.angle.trading.config.CalendarProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Answers "is the market open today?" for gating scheduled jobs.
 *
 * Rules (in order):
 *   1. Saturday / Sunday → closed
 *   2. Date appears in {@link CalendarProperties#getHolidays()} → closed
 *   3. Otherwise → open
 *
 * When {@code bias.calendar.enabled=false}, only rule 1 applies.
 *
 * Holidays list is loaded once at startup from properties; changes require
 * app restart. That's fine — exchanges publish the year's holidays once
 * annually.
 */
@Slf4j
@Service
public class MarketCalendar {

    private final boolean enabled;
    private final Set<LocalDate> holidays;

    public MarketCalendar(CalendarProperties props) {
        this.enabled  = props.isEnabled();
        this.holidays = new HashSet<>(props.getHolidays());
        log.info("MarketCalendar initialised — enabled={}, {} holidays configured",
                enabled, holidays.size());
    }

    /** True if the given date is a normal trading day (not weekend, not holiday). */
    public boolean isMarketOpen(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        if (enabled && holidays.contains(date)) return false;
        return true;
    }

    /** Human-readable reason a date is closed — for log messages. Null if open. */
    public String closedReason(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return "weekend (" + dow + ")";
        if (enabled && holidays.contains(date)) return "market holiday";
        return null;
    }

    /** Count of configured holidays — surfaced by /admin endpoints if desired. */
    public int holidayCount() {
        return holidays.size();
    }
}
