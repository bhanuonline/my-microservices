package com.angle.trading.analysis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory counters for gate rejections today, used by the pipeline dashboard.
 *
 * Reset at midnight IST so numbers on the dashboard always mean "today so far."
 * Not persisted — a restart zeros them. That's intentional; long-term stats
 * live in {@code bias_signal} (via {@code SignalService}).
 *
 * Each SignalDetector gate calls the matching {@code incXxx()} whenever it
 * rejects a candidate signal. The passed counter increments on save.
 */
@Slf4j
@Service
public class PipelineStats {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AtomicLong attempted = new AtomicLong();
    private final AtomicLong regime    = new AtomicLong();
    private final AtomicLong mtf       = new AtomicLong();
    private final AtomicLong news      = new AtomicLong();
    private final AtomicLong dedupe    = new AtomicLong();
    private final AtomicLong passed    = new AtomicLong();

    private volatile LocalDate day = LocalDate.now(IST);

    public void incAttempted() { attempted.incrementAndGet(); }
    public void incRegime()    { regime.incrementAndGet(); }
    public void incMtf()       { mtf.incrementAndGet(); }
    public void incNews()      { news.incrementAndGet(); }
    public void incDedupe()    { dedupe.incrementAndGet(); }
    public void incPassed()    { passed.incrementAndGet(); }

    public Snapshot snapshot() {
        return new Snapshot(
                day.toString(),
                attempted.get(),
                regime.get(),
                mtf.get(),
                news.get(),
                dedupe.get(),
                passed.get()
        );
    }

    /** Midnight reset in IST. Runs a few seconds after 00:00 to avoid clock-skew edge. */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    public void resetDaily() {
        Snapshot last = snapshot();
        attempted.set(0); regime.set(0); mtf.set(0); news.set(0);
        dedupe.set(0);    passed.set(0);
        day = LocalDate.now(IST);
        log.info("PipelineStats: daily reset. Previous day: {}", last);
    }

    public record Snapshot(
            String  day,
            long    attempted,
            long    regimeBlocked,
            long    mtfBlocked,
            long    newsBlocked,
            long    dedupeBlocked,
            long    passed
    ) {}
}
