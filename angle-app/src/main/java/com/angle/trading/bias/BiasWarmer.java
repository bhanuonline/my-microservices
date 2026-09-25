package com.angle.trading.bias;

import com.angle.trading.config.BiasProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pre-fetches every configured instrument on a schedule so the cache is
 * always hot. Result: a user's first click on {@code /bias} never pays
 * the cold-cache Angel cost during market hours.
 *
 * How it works:
 *   1. Cron fires (default: every 5 min, 09:00–15:00 IST, Mon–Fri).
 *   2. Loops all bias.instruments[] in parallel.
 *   3. Calls biasSheetService.build(cfg) — same code the dashboard runs.
 *   4. Cache is filled as a side effect (via CandleCache wrapping in MarketDataService).
 *
 * Failure is per-instrument: one bad instrument doesn't stop the round.
 * A whole round hanging past timeoutSeconds is cancelled and logged.
 *
 * Off-hours behaviour: cron pattern gates to market hours, so no wasted
 * Angel calls at night/weekends. The warm-up-at-startup pass runs regardless
 * — useful when you deploy at 2 AM and want the cache pre-warmed for tomorrow.
 */
@Slf4j
@Component
public class BiasWarmer {

    private final BiasProperties biasProperties;
    private final BiasSheetService biasSheetService;
    private final MarketCalendar marketCalendar;
    private final ExecutorService executor;

    /**
     * Guard against overlapping rounds. If a previous warm() is still running
     * when the next cron fires, we skip it — prevents Angel rate-limit bursts
     * and duplicated work on slow days.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ---------- observability counters (in-memory, reset on restart) ----------

    private static final int RECENT_MAX = 10;

    private final AtomicLong totalRounds        = new AtomicLong();
    private final AtomicLong successfulRounds   = new AtomicLong();
    private final AtomicLong failedRounds       = new AtomicLong();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong totalDurationMs    = new AtomicLong();
    private volatile long minDurationMs         = Long.MAX_VALUE;
    private volatile long maxDurationMs         = 0;
    private volatile Instant lastRoundAt;
    private volatile long lastRoundDurationMs;
    private volatile int lastRoundOk;
    private volatile int lastRoundFailed;
    private volatile String lastTrigger;
    private final Deque<RoundSummary> recent    = new ArrayDeque<>(RECENT_MAX);

    // ---------- circuit breaker state ----------

    /** CLOSED = normal, OPEN = skipping, HALF_OPEN = trial round in progress. */
    public enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    private volatile CircuitState circuitState = CircuitState.CLOSED;
    /** When the circuit will transition from OPEN → HALF_OPEN. null when not OPEN. */
    private volatile Instant circuitOpenUntil;
    /** Timestamp of the last transition (any state). null on boot. */
    private volatile Instant circuitLastTransitionAt;

    public BiasWarmer(BiasProperties biasProperties, BiasSheetService biasSheetService,
                       MarketCalendar marketCalendar) {
        this.biasProperties = biasProperties;
        this.biasSheetService = biasSheetService;
        this.marketCalendar = marketCalendar;
        int poolSize = Math.max(1, biasProperties.getWarmer().getThreadPoolSize());
        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "bias-warmer");
            t.setDaemon(true);
            return t;
        });
        log.info("BiasWarmer initialised — enabled={}, cron='{}' ({}), poolSize={}, warmOnStartup={}",
                biasProperties.getWarmer().isEnabled(),
                biasProperties.getWarmer().getCron(),
                biasProperties.getWarmer().getZone(),
                poolSize,
                biasProperties.getWarmer().isWarmOnStartup());
    }

    /**
     * Cron-driven warm-up. Uses SpEL to pull the cron string from properties
     * so you can change the schedule without a rebuild.
     *
     * Overlap-safe: if a previous round is still running (unlikely but possible
     * when timeoutSeconds > cron interval), this fire is skipped rather than
     * doubling up on Angel calls.
     */
    @Scheduled(cron = "#{@biasProperties.warmer.cron}", zone = "#{@biasProperties.warmer.zone}")
    public void scheduledWarm() {
        if (!biasProperties.getWarmer().isEnabled()) return;
        java.time.LocalDate today = java.time.LocalDate.now();
        if (!marketCalendar.isMarketOpen(today)) {
            log.debug("BiasWarmer[scheduled] SKIP — market closed ({}) on {}",
                    marketCalendar.closedReason(today), today);
            return;
        }
        if (!checkCircuitBeforeRound()) return;
        if (!running.compareAndSet(false, true)) {
            log.warn("BiasWarmer[scheduled] SKIP — previous round still running");
            return;
        }
        try {
            warm("scheduled");
        } finally {
            running.set(false);
        }
    }

    /**
     * One warm-up right after the app is ready (Spring's ApplicationReadyEvent
     * fires once, when all beans are constructed + the web server is up).
     *
     * The event listener runs on the framework's post-startup thread — the
     * servlet container is already accepting requests, so this does NOT delay
     * user-facing traffic. The actual per-instrument work is parallelised
     * across the warmer's own executor.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmOnStartup() {
        if (!biasProperties.getWarmer().isEnabled()) return;
        if (!biasProperties.getWarmer().isWarmOnStartup()) return;
        java.time.LocalDate today = java.time.LocalDate.now();
        if (!marketCalendar.isMarketOpen(today)) {
            log.info("BiasWarmer[startup] SKIP — market closed today ({})",
                    marketCalendar.closedReason(today));
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("BiasWarmer[startup] SKIP — another round already running");
            return;
        }
        try {
            warm("startup");
        } finally {
            running.set(false);
        }
    }

    /**
     * Public entry point — called by the manual debug endpoint.
     * Overlap-safe: returns a "skipped" result if a round is already running
     * so the admin knows to try again in a moment.
     *
     * @param force when true, bypasses the circuit breaker (admin override).
     *              Useful to test if Angel has recovered while circuit is OPEN.
     */
    public WarmResult warmNow(boolean force) {
        if (!force && !checkCircuitBeforeRound()) {
            return new WarmResult(0, 0, 0);
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("BiasWarmer[manual] SKIP — a round is already running");
            return new WarmResult(0, 0, 0);
        }
        try {
            return warm(force ? "manual:force" : "manual");
        } finally {
            running.set(false);
        }
    }

    /** Backward-compatible overload. */
    public WarmResult warmNow() {
        return warmNow(false);
    }

    /**
     * Circuit breaker gate. Returns true if a round should proceed, false if
     * the breaker is OPEN and cooldown hasn't elapsed.
     *
     * State transitions here:
     *   OPEN + cooldown elapsed → HALF_OPEN (allow one test round)
     */
    private boolean checkCircuitBeforeRound() {
        BiasProperties.Circuit cfg = biasProperties.getWarmer().getCircuit();
        if (!cfg.isEnabled()) return true;
        if (circuitState == CircuitState.CLOSED) return true;

        if (circuitState == CircuitState.OPEN) {
            if (circuitOpenUntil != null && Instant.now().isAfter(circuitOpenUntil)) {
                log.info("Circuit breaker OPEN cooldown expired — transitioning to HALF_OPEN (trial round)");
                transitionTo(CircuitState.HALF_OPEN);
                return true;
            }
            log.warn("Circuit breaker OPEN — skipping round (retry after {})", circuitOpenUntil);
            return false;
        }
        // HALF_OPEN — allow (the round in flight will decide next state)
        return true;
    }

    /** Manual reset — forces state back to CLOSED regardless. Called by admin. */
    public synchronized void resetCircuit() {
        log.info("Circuit breaker manually RESET (was {})", circuitState);
        consecutiveFailures.set(0);
        transitionTo(CircuitState.CLOSED);
        circuitOpenUntil = null;
    }

    private synchronized void transitionTo(CircuitState newState) {
        if (this.circuitState != newState) {
            log.info("Circuit breaker: {} → {}", this.circuitState, newState);
        }
        this.circuitState = newState;
        this.circuitLastTransitionAt = Instant.now();
    }

    /**
     * Called by recordRound() after every warm() completes.
     * Applies the state machine based on the round's outcome.
     */
    private void updateCircuitAfterRound(int ok, int failed) {
        BiasProperties.Circuit cfg = biasProperties.getWarmer().getCircuit();
        if (!cfg.isEnabled()) return;

        boolean roundFailed = (failed > 0 && ok == 0);   // fully-failed round only
        int consec = consecutiveFailures.get();

        if (circuitState == CircuitState.HALF_OPEN) {
            if (roundFailed) {
                // Trial round failed — back to OPEN with a fresh cooldown.
                circuitOpenUntil = Instant.now().plusSeconds(cfg.getCooldownMinutes() * 60L);
                log.warn("Circuit breaker HALF_OPEN trial FAILED — reopening until {}", circuitOpenUntil);
                transitionTo(CircuitState.OPEN);
            } else {
                log.info("Circuit breaker HALF_OPEN trial SUCCEEDED — closing");
                transitionTo(CircuitState.CLOSED);
                circuitOpenUntil = null;
            }
            return;
        }

        // Normal CLOSED-state handling
        if (roundFailed && consec >= cfg.getFailureThreshold()) {
            circuitOpenUntil = Instant.now().plusSeconds(cfg.getCooldownMinutes() * 60L);
            log.warn("Circuit breaker OPENING — {} consecutive full-failure rounds. Cooldown until {}",
                    consec, circuitOpenUntil);
            transitionTo(CircuitState.OPEN);
        }
    }

    /** True when a warm-up round is currently in progress. Exposed for /admin. */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Snapshot of live warmer metrics — surfaced by /admin/cache/warmer/stats.
     * Cheap to call, safe to poll.
     */
    public WarmerStats stats() {
        long total = totalRounds.get();
        long avg = total == 0 ? 0 : totalDurationMs.get() / total;
        List<RoundSummary> recentSnapshot;
        synchronized (recent) {
            recentSnapshot = new ArrayList<>(recent);
        }
        return new WarmerStats(
                isRunning(),
                total,
                successfulRounds.get(),
                failedRounds.get(),
                consecutiveFailures.get(),
                lastRoundAt,
                lastRoundDurationMs,
                lastRoundOk,
                lastRoundFailed,
                lastTrigger,
                avg,
                minDurationMs == Long.MAX_VALUE ? 0 : minDurationMs,
                maxDurationMs,
                recentSnapshot,
                circuitState.name(),
                circuitOpenUntil,
                circuitLastTransitionAt
        );
    }

    /**
     * Update all counters + ring buffer after a round finishes.
     * Called from {@link #warm} once for the whole round (not per stage).
     */
    private void recordRound(String trigger, long durationMs, int ok, int failed) {
        totalRounds.incrementAndGet();
        totalDurationMs.addAndGet(durationMs);
        if (failed == 0) {
            successfulRounds.incrementAndGet();
            consecutiveFailures.set(0);
        } else {
            failedRounds.incrementAndGet();
            consecutiveFailures.incrementAndGet();
        }
        if (durationMs < minDurationMs) minDurationMs = durationMs;
        if (durationMs > maxDurationMs) maxDurationMs = durationMs;
        lastRoundAt         = Instant.now();
        lastRoundDurationMs = durationMs;
        lastRoundOk         = ok;
        lastRoundFailed     = failed;
        lastTrigger         = trigger;

        synchronized (recent) {
            if (recent.size() >= RECENT_MAX) recent.removeFirst();
            recent.addLast(new RoundSummary(lastRoundAt, trigger, durationMs, ok, failed));
        }

        // Circuit-breaker state machine sees the outcome AFTER counters update.
        updateCircuitAfterRound(ok, failed);
    }

    // ---------- internals ----------

    private WarmResult warm(String trigger) {
        List<BiasProperties.Instrument> instruments = biasProperties.getInstruments();
        if (instruments.isEmpty()) {
            log.info("BiasWarmer[{}] — no instruments configured, skipping", trigger);
            return new WarmResult(0, 0, 0);
        }

        // Split into priority (stage 1) and non-priority (stage 2) based on config.
        Set<String> priorityTokens = parsePriorityTokens(biasProperties.getWarmer().getPriorityTokens());
        List<BiasProperties.Instrument> stage1 = new ArrayList<>();
        List<BiasProperties.Instrument> stage2 = new ArrayList<>();
        for (BiasProperties.Instrument ins : instruments) {
            if (priorityTokens.contains(ins.getSymbolToken())) stage1.add(ins);
            else stage2.add(ins);
        }

        long start = System.currentTimeMillis();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        // Stage 1 — priority instruments first (if any).
        if (!stage1.isEmpty()) {
            log.debug("BiasWarmer[{}] stage 1 (priority): {} instruments", trigger, stage1.size());
            runStage(trigger + ":stage1", stage1, ok, fail);
        }
        // Stage 2 — everything else.
        if (!stage2.isEmpty()) {
            log.debug("BiasWarmer[{}] stage 2: {} instruments", trigger, stage2.size());
            runStage(trigger + ":stage2", stage2, ok, fail);
        }

        long ms = System.currentTimeMillis() - start;
        log.info("BiasWarmer[{}] complete — {}/{} warmed, {} failed, {} ms (stage1={}, stage2={})",
                trigger, ok.get(), instruments.size(), fail.get(), ms, stage1.size(), stage2.size());
        recordRound(trigger, ms, ok.get(), fail.get());
        return new WarmResult(instruments.size(), ok.get(), fail.get());
    }

    /**
     * Fan out a batch of instruments in parallel, wait for all (with timeout).
     * Increments the shared ok/fail counters. Timeout is per-stage — both
     * stages get the full {@code timeoutSeconds} budget independently.
     */
    private void runStage(String stageName, List<BiasProperties.Instrument> instruments,
                           AtomicInteger ok, AtomicInteger fail) {
        int timeoutSeconds = biasProperties.getWarmer().getTimeoutSeconds();

        List<CompletableFuture<Void>> futures = instruments.stream()
                .map(instrument -> CompletableFuture.runAsync(() -> {
                    try {
                        biasSheetService.build(instrument);   // fills cache as side effect
                        ok.incrementAndGet();
                    } catch (Exception e) {
                        fail.incrementAndGet();
                        log.warn("BiasWarmer[{}] failed for {}: {}",
                                stageName, instrument.getSymbol(), e.getMessage());
                    }
                }, executor))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("BiasWarmer[{}] timed out after {}s — some instruments may not have warmed",
                    stageName, timeoutSeconds);
        }
    }

    /** Parse a comma-separated token string into a set; empty/null → empty set. */
    private static Set<String> parsePriorityTokens(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return new LinkedHashSet<>(Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    /** Return value of a warm round — surfaced by the manual trigger endpoint. */
    public record WarmResult(int total, int succeeded, int failed) {}

    /** One historical round entry — for the /stats endpoint's recent list. */
    public record RoundSummary(Instant at, String trigger, long durationMs, int ok, int failed) {}

    /**
     * Full metrics snapshot. All counters are lifetime since JVM boot
     * (not persisted). {@code recent} is a ring buffer capped at {@code RECENT_MAX}.
     *
     * Circuit fields:
     *   circuitState              — CLOSED / OPEN / HALF_OPEN
     *   circuitOpenUntil          — when OPEN cooldown ends (null when not OPEN)
     *   circuitLastTransitionAt   — timestamp of last state change (null if never transitioned)
     */
    public record WarmerStats(
            boolean currentlyRunning,
            long totalRounds,
            long successfulRounds,
            long failedRounds,
            int consecutiveFailures,
            Instant lastRoundAt,
            long lastRoundDurationMs,
            int lastRoundOk,
            int lastRoundFailed,
            String lastTrigger,
            long avgDurationMs,
            long minDurationMs,
            long maxDurationMs,
            List<RoundSummary> recentRounds,
            String circuitState,
            Instant circuitOpenUntil,
            Instant circuitLastTransitionAt
    ) {}
}
