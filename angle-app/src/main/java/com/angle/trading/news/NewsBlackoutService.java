package com.angle.trading.news;

import com.angle.trading.config.NewsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * "Is there a news event blocking signals for this symbol right now?"
 *
 * Called by SignalDetector after regime/consensus/dedupe/MTF pass. Returns
 * Decision(allowed, reason) so the log makes it obvious WHY a signal was
 * skipped.
 *
 * Filters events by:
 *   1. Master enabled flag
 *   2. Severity ≥ configured block-severity
 *   3. Time window (before/after event)
 *   4. Symbol targeting (event's affectedSymbols list, or all)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsBlackoutService {

    private final NewsProperties props;
    private final NewsCalendarService calendarService;

    /** The gate. */
    public Decision check(String symbolToken) {
        if (!props.isEnabled()) return Decision.ok("news disabled");

        int minSeverity = NewsEvent.severityLevel(props.getBlockSeverity());
        Instant now = Instant.now();

        for (NewsEvent e : calendarService.all()) {
            if (e.severityLevel() < minSeverity) continue;
            if (!e.appliesTo(symbolToken)) continue;
            if (e.isActive(now, props.getDefaultWindowBefore(), props.getDefaultWindowAfter())) {
                return Decision.deny(String.format(
                        "'%s' at %s (severity %s, window %d/%d min)",
                        e.getName(),
                        e.getDateTime(),
                        e.getSeverity(),
                        e.windowBeforeOr(props.getDefaultWindowBefore()),
                        e.windowAfterOr(props.getDefaultWindowAfter())
                ));
            }
        }
        return Decision.ok("no active blackout");
    }

    public record Decision(boolean allowed, String reason) {
        static Decision ok(String r)   { return new Decision(true,  r); }
        static Decision deny(String r) { return new Decision(false, r); }
    }
}
