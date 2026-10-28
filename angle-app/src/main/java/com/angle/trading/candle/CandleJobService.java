package com.angle.trading.candle;

import com.angle.trading.bias.MarketCalendar;
import com.angle.trading.broker.angel.AngelClient;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.cache.CandleStore;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.persistence.BiasInstrumentEntity;
import com.angle.trading.persistence.BiasInstrumentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core engine for candle history backfill.
 *
 * Features:
 *   - Thread pool for parallel instrument fetching
 *   - Semaphore to limit concurrent Angel API calls (rate limiting)
 *   - Chunking for large date ranges (Angel API limit per call)
 *   - Per-exchange correct trading hours (MCX till 23:30)
 *   - Overlap guard (AtomicBoolean — only one run at a time)
 *   - Cancellation support (AtomicBoolean cancelled)
 *   - Per-instrument timeout (30s default)
 *   - Progress tracking (AtomicIntegers — polled by status endpoint)
 *   - Crash recovery (@PostConstruct fixes stale RUNNING rows)
 *   - Retry of FAILED / SKIPPED / CANCELLED instruments
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleJobService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Per-exchange intraday trading window (open, close). 1D ignores these. */
    private static final Map<String, LocalTime[]> TRADING_HOURS = Map.of(
        "NSE", new LocalTime[]{LocalTime.of(9, 15), LocalTime.of(15, 30)},
        "BSE", new LocalTime[]{LocalTime.of(9, 15), LocalTime.of(15, 30)},
        "NFO", new LocalTime[]{LocalTime.of(9, 15), LocalTime.of(15, 30)},
        "BFO", new LocalTime[]{LocalTime.of(9, 15), LocalTime.of(15, 30)},
        "CDS", new LocalTime[]{LocalTime.of(9,  0), LocalTime.of(17,  0)},
        "MCX", new LocalTime[]{LocalTime.of(9,  0), LocalTime.of(23, 30)}
    );

    private final CandleJobProperties          props;
    private final BiasInstrumentRepository     instrumentRepo;
    private final MarketDataService            marketDataService;
    private final AngelClient                  angelClient;
    private final CandleStore                  candleStore;
    private final MarketCalendar               marketCalendar;
    private final CandleJobRunRepository       runRepo;
    private final CandleJobRunDetailRepository detailRepo;

    // ── Shared state ──────────────────────────────────────────────────
    private final AtomicBoolean running   = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    // In-memory progress (polled by status endpoint — no DB hit)
    private final AtomicInteger doneCount    = new AtomicInteger(0);
    private final AtomicInteger failedCount  = new AtomicInteger(0);
    private final AtomicInteger candlesSaved = new AtomicInteger(0);
    private volatile String     currentStock = "";
    private volatile int        totalCount   = 0;
    private volatile Instant    jobStartedAt = null;
    private volatile Long       currentRunId = null;
    private volatile String     jobStatus    = "IDLE";

    private ExecutorService threadPool;
    private ExecutorService jobExecutor;   // single-thread — one job at a time
    private Semaphore       angelSemaphore;

    @PostConstruct
    public void init() {
        // Only initialise non-DB resources here — @PostConstruct has no transaction
        threadPool     = Executors.newFixedThreadPool(props.getThreadPoolSize(),
                r -> new Thread(r, "candle-job-worker"));
        jobExecutor    = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "candle-job-runner"));
        angelSemaphore = new Semaphore(props.getMaxConcurrentCalls());
        log.info("CandleJobService init — threadPool={}, semaphore={}",
                props.getThreadPoolSize(), props.getMaxConcurrentCalls());
    }

    /** Crash recovery — runs after full Spring context is ready (transaction available). */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void fixStaleRuns() {
        try {
            Instant threshold = Instant.now().minus(1, ChronoUnit.HOURS);
            int fixed = runRepo.markStaleAsFailed(threshold, Instant.now());
            if (fixed > 0) {
                log.warn("CandleJob: marked {} stale RUNNING runs as FAILED (app restart)", fixed);
            }
            log.info("CandleJobService ready — stale-run check complete");
        } catch (Exception e) {
            log.warn("CandleJob stale-run check failed (tables may not exist yet): {}", e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────

    /** Run for all enabled instruments. Returns run ID or -1 if skipped. */
    public long runAll(String triggerType, String triggeredBy,
                       String intervalFilter, Integer lookbackOverride,
                       LocalDate fromOverride, LocalDate toOverride) {
        if (!running.compareAndSet(false, true)) {
            log.warn("CandleJob already running — {} skipped", triggeredBy);
            return -1L;
        }
        cancelled.set(false);
        List<BiasInstrumentEntity> instruments =
                instrumentRepo.findByEnabledTrueOrderByPriorityAscIdAsc();
        return execute(instruments, triggerType, triggeredBy, null,
                intervalFilter, lookbackOverride, fromOverride, toOverride);
    }

    /** Run for one instrument by token. */
    public long runSingle(String token, String triggeredBy,
                          String intervalFilter, Integer lookbackOverride,
                          LocalDate fromOverride, LocalDate toOverride) {
        if (!running.compareAndSet(false, true)) {
            log.warn("CandleJob already running — single-stock run {} skipped", token);
            return -1L;
        }
        cancelled.set(false);
        List<BiasInstrumentEntity> instruments =
                instrumentRepo.findBySymbolToken(token)
                        .map(List::of)
                        .orElse(List.of());
        if (instruments.isEmpty()) {
            running.set(false);
            log.warn("CandleJob: token {} not found in bias_instrument", token);
            return -1L;
        }
        return execute(instruments, "MANUAL", triggeredBy, token,
                intervalFilter, lookbackOverride, fromOverride, toOverride);
    }

    /** Retry FAILED/SKIPPED/CANCELLED instruments from a previous run. */
    public long retry(Long runId, String triggeredBy) {
        if (!running.compareAndSet(false, true)) {
            log.warn("CandleJob already running — retry {} skipped", runId);
            return -1L;
        }
        cancelled.set(false);
        List<CandleJobRunDetailEntity> retryable = detailRepo.findRetryable(runId);
        if (retryable.isEmpty()) {
            running.set(false);
            log.info("CandleJob retry {}: nothing to retry", runId);
            return -1L;
        }
        // Build instrument list from retryable detail rows
        List<BiasInstrumentEntity> instruments = retryable.stream()
                .map(d -> instrumentRepo.findBySymbolToken(d.getSymbolToken()).orElse(null))
                .filter(i -> i != null)
                .toList();
        return execute(instruments, "RETRY", triggeredBy, null, null, null, null, null);
    }

    /** Cancel the currently running job. */
    public void cancel() {
        if (running.get()) {
            cancelled.set(true);
            log.info("CandleJob cancel requested");
        }
    }

    /** Current status snapshot for the polling endpoint. */
    public JobStatus status() {
        return new JobStatus(
                jobStatus, currentRunId, jobStartedAt,
                totalCount, doneCount.get(), failedCount.get(),
                candlesSaved.get(), currentStock
        );
    }

    public boolean isRunning() { return running.get(); }

    // ── Core execution ────────────────────────────────────────────────

    private long execute(List<BiasInstrumentEntity> instruments,
                         String triggerType, String triggeredBy, String targetToken,
                         String intervalFilter, Integer lookbackOverride,
                         LocalDate fromOverride, LocalDate toOverride) {

        // ── 1. Reset in-memory progress counters ──────────────────────
        doneCount.set(0);
        failedCount.set(0);
        candlesSaved.set(0);
        currentStock = "";
        totalCount   = instruments.size();
        jobStartedAt = Instant.now();
        jobStatus    = "RUNNING";

        // ── 2. Persist run record NOW so caller gets the ID immediately ─
        CandleJobRunEntity run = new CandleJobRunEntity();
        run.setJobName(targetToken != null ? "SINGLE" : triggerType);
        run.setTriggerType(triggerType);
        run.setTriggeredBy(triggeredBy);
        run.setTargetToken(targetToken);
        run.setStartedAt(jobStartedAt);
        run.setStatus("RUNNING");
        run.setTotalCount(totalCount);
        run.setIntervalType(intervalFilter);
        run.setLookbackDays(lookbackOverride);
        run.setThreadCount(props.getThreadPoolSize());
        // Store the candle date range in the summary record
        LocalDate toDate   = toOverride   != null ? toOverride   : LocalDate.now(IST);
        LocalDate fromDate = fromOverride != null ? fromOverride
                : toDate.minusDays(lookbackOverride != null ? lookbackOverride
                        : (intervalFilter != null && intervalFilter.equalsIgnoreCase("1D")
                                ? props.getDailyLookbackDays() : props.getIntradayLookbackDays()));
        run.setCandleFrom(fromDate);
        run.setCandleTo(toDate);
        run = runRepo.save(run);
        currentRunId = run.getId();
        final long runId    = run.getId();
        final CandleJobRunEntity savedRun = run;

        log.info("CandleJob[{}] queued — trigger={} instruments={} interval={} lookback={}",
                runId, triggerType, instruments.size(), intervalFilter, lookbackOverride);

        // ── 3. Run actual work on background thread — HTTP returns immediately ─
        jobExecutor.submit(() -> doRun(savedRun, instruments, intervalFilter,
                lookbackOverride, fromOverride, toOverride));

        return runId;   // caller gets ID instantly, job running in background
    }

    private void doRun(CandleJobRunEntity run,
                       List<BiasInstrumentEntity> instruments,
                       String intervalFilter, Integer lookbackOverride,
                       LocalDate fromOverride, LocalDate toOverride) {

        // Determine intervals to fetch
        List<Interval> intervals = resolveIntervals(intervalFilter);

        try {
            // Main loop — sequential, cancel check between instruments
            for (BiasInstrumentEntity instr : instruments) {
                if (cancelled.get()) {
                    log.info("CandleJob[{}] cancelled at {}/{}", run.getId(), doneCount.get(), totalCount);
                    // Write SKIPPED detail rows for remaining instruments
                    saveDetailSkipped(run.getId(), instruments, instr);
                    break;
                }
                currentStock = instr.getSymbol();
                processInstrument(run.getId(), instr, intervals, lookbackOverride,
                        fromOverride, toOverride);
            }
        } finally {
            finalise(run);
        }
    }

    private void processInstrument(long runId, BiasInstrumentEntity instr,
                                   List<Interval> intervals,
                                   Integer lookbackOverride,
                                   LocalDate fromOverride, LocalDate toOverride) {
        Instant instrStart = Instant.now();
        int savedForInstr  = 0;
        String errorMsg    = null;
        String detailStatus;

        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(
                () -> fetchAllIntervals(instr, intervals, lookbackOverride, fromOverride, toOverride),
                threadPool);

        try {
            savedForInstr = future.get(props.getTimeoutPerInstrumentSec(), TimeUnit.SECONDS);
            candlesSaved.addAndGet(savedForInstr);
            doneCount.incrementAndGet();
            detailStatus = "DONE";
            log.debug("CandleJob instrument={} saved={}", instr.getSymbol(), savedForInstr);
        } catch (TimeoutException e) {
            future.cancel(true);
            failedCount.incrementAndGet();
            errorMsg = "Timed out after " + props.getTimeoutPerInstrumentSec() + "s";
            detailStatus = "FAILED";
            log.warn("CandleJob timeout for {} after {}s", instr.getSymbol(), props.getTimeoutPerInstrumentSec());
        } catch (Exception e) {
            failedCount.incrementAndGet();
            errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            if (errorMsg != null && errorMsg.length() > 490) errorMsg = errorMsg.substring(0, 490);
            detailStatus = "FAILED";
            log.warn("CandleJob error for {}: {}", instr.getSymbol(), errorMsg);
        }

        // Compute actual candle date range used for this instrument
        LocalDate toDate   = toOverride   != null ? toOverride   : LocalDate.now(IST);
        LocalDate fromDate = fromOverride != null ? fromOverride
                : toDate.minusDays(lookbackOverride != null ? lookbackOverride
                        : props.getDailyLookbackDays());

        // Write detail row
        saveDetail(runId, instr, detailStatus, savedForInstr, errorMsg, instrStart, fromDate, toDate);
    }

    private int fetchAllIntervals(BiasInstrumentEntity instr, List<Interval> intervals,
                                  Integer lookbackOverride, LocalDate fromOverride, LocalDate toOverride) {
        int total = 0;
        Exchange exchange;
        try {
            exchange = Exchange.valueOf(instr.getExchange());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown exchange {} for {}, skipping", instr.getExchange(), instr.getSymbol());
            return 0;
        }

        for (Interval interval : intervals) {
            try {
                LocalDate to   = toOverride   != null ? toOverride   : LocalDate.now(IST);
                LocalDate from = fromOverride  != null ? fromOverride
                                : to.minusDays(lookbackOverride != null ? lookbackOverride
                                                : effectiveLookback(interval));

                List<Candle> candles = fetchWithChunking(instr, exchange, interval, from, to);
                if (!candles.isEmpty()) {
                    candleStore.saveAsync(instr.getBroker(), instr.getExchange(),
                            instr.getSymbolToken(), interval.name(), candles);
                    total += candles.size();
                }
            } catch (Exception e) {
                log.warn("CandleJob fetch failed for {}:{}: {}", instr.getSymbol(), interval, e.getMessage());
            }
        }
        return total;
    }

    /** Split large date ranges into chunks that fit Angel API limits. */
    private List<Candle> fetchWithChunking(BiasInstrumentEntity instr, Exchange exchange,
                                            Interval interval, LocalDate from, LocalDate to) throws InterruptedException {
        int maxDays = interval == Interval.ONE_DAY
                ? props.getMaxWindowDaysDaily()
                : props.getMaxWindowDaysIntraday();

        List<Candle> all = new ArrayList<>();
        LocalDate chunkStart = from;

        while (!chunkStart.isAfter(to)) {
            LocalDate chunkEnd = chunkStart.plusDays(maxDays - 1);
            if (chunkEnd.isAfter(to)) chunkEnd = to;

            LocalDate finalChunkEnd = chunkEnd;
            List<Candle> chunk = callAngel(instr, exchange, interval, chunkStart, finalChunkEnd);
            all.addAll(chunk);

            chunkStart = chunkEnd.plusDays(1);
        }
        return all;
    }

    /** Rate-limited Angel API call (Semaphore + delay). */
    private List<Candle> callAngel(BiasInstrumentEntity instr, Exchange exchange,
                                    Interval interval, LocalDate from, LocalDate to) throws InterruptedException {
        angelSemaphore.acquire();
        try {
            Thread.sleep(props.getApiDelayMs());

            // All intervals use marketDataService → gap-fill (MySQL → Angel only if missing).
            // This prevents re-fetching from Angel when data is already saved in MySQL.
            // MCX/CDS trading hours are handled by the broker client internally.
            return marketDataService.getCandles(
                    instr.getBroker(), exchange, instr.getSymbolToken(), interval, from, to);
        } finally {
            angelSemaphore.release();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private int effectiveLookback(Interval interval) {
        return interval == Interval.ONE_DAY
                ? props.getDailyLookbackDays()
                : props.getIntradayLookbackDays();
    }

    private List<Interval> resolveIntervals(String filter) {
        if (filter == null || filter.isBlank() || filter.equalsIgnoreCase("ALL")) {
            return List.of(Interval.FIVE_MINUTE, Interval.FIFTEEN_MINUTE,
                           Interval.ONE_HOUR, Interval.ONE_DAY);
        }
        return switch (filter.toUpperCase()) {
            case "1D", "ONE_DAY"           -> List.of(Interval.ONE_DAY);
            case "1H", "ONE_HOUR"          -> List.of(Interval.ONE_HOUR);
            case "15M","FIFTEEN_MINUTE"    -> List.of(Interval.FIFTEEN_MINUTE);
            case "5M", "FIVE_MINUTE"       -> List.of(Interval.FIVE_MINUTE);
            case "INTRADAY"                -> List.of(Interval.FIVE_MINUTE,
                                                       Interval.FIFTEEN_MINUTE, Interval.ONE_HOUR);
            default -> List.of(Interval.FIVE_MINUTE, Interval.FIFTEEN_MINUTE,
                               Interval.ONE_HOUR, Interval.ONE_DAY);
        };
    }

    @Transactional
    protected void finalise(CandleJobRunEntity run) {
        String finalStatus = cancelled.get() ? "CANCELLED"
                : failedCount.get() > 0 && doneCount.get() == 0 ? "FAILED"
                : "COMPLETED";
        run.setStatus(finalStatus);
        run.setEndedAt(Instant.now());
        run.setDoneCount(doneCount.get());
        run.setFailedCount(failedCount.get());
        run.setTotalSaved(candlesSaved.get());
        runRepo.save(run);

        jobStatus    = finalStatus;
        currentStock = "";
        running.set(false);
        cancelled.set(false);

        log.info("CandleJob[{}] {} — done={} failed={} saved={} elapsed={}s",
                run.getId(), finalStatus, doneCount.get(), failedCount.get(), candlesSaved.get(),
                ChronoUnit.SECONDS.between(run.getStartedAt(), run.getEndedAt()));
    }

    private void saveDetail(long runId, BiasInstrumentEntity instr,
                            String status, int saved, String errorMsg,
                            Instant startedAt, LocalDate candleFrom, LocalDate candleTo) {
        CandleJobRunDetailEntity d = new CandleJobRunDetailEntity();
        d.setRunId(runId);
        d.setSymbolToken(instr.getSymbolToken());
        d.setSymbol(instr.getSymbol());
        d.setStatus(status);
        d.setCandlesSaved(saved);
        d.setErrorMsg(errorMsg);
        d.setStartedAt(startedAt);
        d.setEndedAt(Instant.now());
        d.setDurationMs((int) ChronoUnit.MILLIS.between(startedAt, d.getEndedAt()));
        d.setCandleFrom(candleFrom);
        d.setCandleTo(candleTo);
        detailRepo.save(d);
    }

    private void saveDetailSkipped(long runId, List<BiasInstrumentEntity> all,
                                   BiasInstrumentEntity cancelledAt) {
        boolean reached = false;
        for (BiasInstrumentEntity instr : all) {
            if (instr.getSymbolToken().equals(cancelledAt.getSymbolToken())) {
                reached = true;
                continue;
            }
            if (!reached) continue;  // already processed
            CandleJobRunDetailEntity d = new CandleJobRunDetailEntity();
            d.setRunId(runId);
            d.setSymbolToken(instr.getSymbolToken());
            d.setSymbol(instr.getSymbol());
            d.setStatus("SKIPPED");
            d.setCandlesSaved(0);
            d.setStartedAt(Instant.now());
            d.setEndedAt(Instant.now());
            d.setDurationMs(0);
            detailRepo.save(d);
        }
    }

    // ── Status DTO ────────────────────────────────────────────────────

    public record JobStatus(
            String  status,
            Long    runId,
            Instant startedAt,
            int     totalCount,
            int     doneCount,
            int     failedCount,
            int     candlesSaved,
            String  currentStock
    ) {
        public int pctComplete() {
            return totalCount == 0 ? 0 : (int) (doneCount * 100L / totalCount);
        }

        public long elapsedSec() {
            return startedAt == null ? 0 : ChronoUnit.SECONDS.between(startedAt, Instant.now());
        }
    }
}
