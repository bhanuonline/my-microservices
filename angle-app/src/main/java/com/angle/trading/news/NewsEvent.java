package com.angle.trading.news;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * One calendar entry.
 *
 * All times are IST ({@code Asia/Kolkata}). The YAML parser needs mutable
 * fields with default constructors so this is a Lombok bean, not a record.
 *
 * Fields:
 *   name             free-text label ("RBI Monetary Policy")
 *   dateTime         "yyyy-MM-dd HH:mm" in IST
 *   severity         LOW / MEDIUM / HIGH — filtered by news.block-severity
 *   windowBefore     minutes to block BEFORE the event (null = config default)
 *   windowAfter      minutes to block AFTER  the event (null = config default)
 *   affectedSymbols  empty/null = block ALL symbols; else only these tokens
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsEvent {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private String        name;
    private LocalDateTime dateTime;         // IST
    private String        severity = "MEDIUM";
    private Integer       windowBefore;     // may be null → use default
    private Integer       windowAfter;      // may be null → use default
    private List<String>  affectedSymbols;  // may be null/empty → all

    /** Convert IST wall-clock to absolute instant for time-window checks. */
    public Instant instant() {
        return dateTime.atZone(IST).toInstant();
    }

    public int windowBeforeOr(int fallback) {
        return windowBefore == null ? fallback : windowBefore;
    }

    public int windowAfterOr(int fallback) {
        return windowAfter == null ? fallback : windowAfter;
    }

    /** True when {@code now} is inside the effective blackout window. */
    public boolean isActive(Instant now, int fallbackBefore, int fallbackAfter) {
        Instant e = instant();
        Instant start = e.minusSeconds(windowBeforeOr(fallbackBefore) * 60L);
        Instant end   = e.plusSeconds(windowAfterOr(fallbackAfter)  * 60L);
        return !now.isBefore(start) && !now.isAfter(end);
    }

    /** True when this event applies to the given symbol token. */
    public boolean appliesTo(String symbolToken) {
        return affectedSymbols == null || affectedSymbols.isEmpty()
                || affectedSymbols.contains(symbolToken);
    }

    /** Numeric severity for threshold comparison. Unknown values map to MEDIUM. */
    public int severityLevel() {
        return switch (severity == null ? "MEDIUM" : severity.toUpperCase()) {
            case "LOW"  -> 1;
            case "HIGH" -> 3;
            default     -> 2;   // MEDIUM
        };
    }

    public static int severityLevel(String s) {
        return switch (s == null ? "MEDIUM" : s.toUpperCase()) {
            case "LOW"  -> 1;
            case "HIGH" -> 3;
            default     -> 2;
        };
    }
}
