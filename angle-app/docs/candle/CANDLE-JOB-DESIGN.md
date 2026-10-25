# Candle History Job — Design Document

> **Status:** Finalized. Ready for implementation.
> **Scope:** @Scheduled job (Approach 1 of 3).
> Kafka and Spring Batch covered in separate docs once this is stable.

---

## Table of Contents

1. [Purpose](#1-purpose)
2. [Questions & Answers (Finalized)](#2-questions--answers-finalized)
3. [What Was Missing / Improvements Added](#3-what-was-missing--improvements-added)
4. [Architecture](#4-architecture)
5. [Thread Design](#5-thread-design)
6. [Angel API Constraints](#6-angel-api-constraints)
7. [Per-Exchange Trading Hours](#7-per-exchange-trading-hours)
8. [Database Tables](#8-database-tables)
9. [Configuration Reference](#9-configuration-reference)
10. [API Endpoints](#10-api-endpoints)
11. [Job Page Design](#11-job-page-design)
12. [Files to Create (Implementation Steps)](#12-files-to-create-implementation-steps)
13. [Edge Cases & Guards](#13-edge-cases--guards)

---

## 1. Purpose

Automatically save candle (OHLCV) history to `candle_history` table
without waiting for a user to open a page.

Five triggers with specific intervals:

| Trigger | Time | Intervals | Lookback |
|---|---|---|---|
| Pre-market | 09:00 AM Mon-Fri | 5m + 15m + 1h | intraday-lookback-days (30d) |
| Post-market | 15:40 PM Mon-Fri | 1D only | daily-lookback-days (730d) |
| Weekend backfill | Saturday 10:00 AM | 1D only | daily-lookback-days (730d) |
| Manual | anytime | configurable via API param | configurable via API param |
| Startup | on app ready | all (same as full run) | all lookbacks |

---

## 2. Questions & Answers (Finalized)

### Q1 — Does it start when app starts or manually?

**A:** Both — controlled by config.

```properties
candle.history-job.run-on-startup=false   # disabled at startup (recommended)
candle.history-job.run-on-startup=true    # runs once app is ready
candle.history-job.enabled=false          # disables scheduled runs (manual only)
```

Default: disabled at startup, scheduled runs enabled.
Manual trigger always available via API or Job page.

---

### Q2 — How does it get data for all segments (NSE, MCX, NFO, CDS)?

**A:** Reads from `bias_instrument` table — NOT hardcoded.

```sql
SELECT * FROM bias_instrument WHERE enabled = 1 ORDER BY priority
```

Each row has `exchange` column (NSE / MCX / NFO / CDS).
Job uses that exchange when calling Angel API.
**No code change needed to add new segments** — just add to bias_instrument.

Enable/Disable per instrument:
- Toggle on `/admin/instruments` page (already built)
- Disabled instruments are skipped automatically
- Change takes effect on next job run — no restart needed

---

### Q3 — App stops and restarts — from beginning or last saved?

**A:** Always from last saved. Gap-fill reads `MAX(ts)` from `candle_history`.

```
latestTimestamp(token, interval) → reads from candle_history table
```

Nothing cached elsewhere. Every restart resumes from exactly where it left off.
Works per token × interval pair independently.

---

### Q4 — Where is "last saved" stored?

**A:** In `candle_history` table itself.

```sql
SELECT MAX(ts) FROM candle_history
WHERE symbol_token = ? AND interval_type = ?
```

This is `CandleRepository.findLatestTimestamp()` — already implemented.
No separate tracking table. No in-memory state. Survives restarts.

---

### Q5 — Job page + API trigger?

**A:** Yes — both.

- **Job page** (`/jobs`) in sidebar: status, history, trigger buttons, progress
- **API**: `POST /api/jobs/candle/run` (all stocks), `POST /api/jobs/candle/run/{token}` (single)
- **Instrument page**: `↻` backfill button per row → calls single-stock API

---

### Q6 — How does job pick which segment?

**A:** From `bias_instrument.exchange` column — not config, not hardcoded.

---

### Q7 — What happens when new stocks/segments added later?

**A:** Fully automatic.

- Add instrument via Admin UI (`/admin/instruments`)
- Next job run picks it up automatically
- New instrument has no `candle_history` rows → full backfill triggered by gap-fill
- Old instruments unaffected

---

### Q8 — From when to save data? How many years?

**A:** Config-based, different per interval.

```properties
candle.history-job.intraday-lookback-days=30   # 5m/15m/1h → 30 days
candle.history-job.daily-lookback-days=730     # 1D → 2 trading years
```

Why different:
- 5m candles: 30 days = 2,250 bars per stock. Enough for EMA200 (needs 200 bars).
  Going 2 years = 450K rows per stock = too much.
- 1D candles: 730 days = 2 years. Needed for screener, trend analysis, AI picks.
  Only 730 rows per stock — very cheap.

Can override per API call:
```
POST /api/jobs/candle/run?lookbackDays=365
POST /api/jobs/candle/run/{token}?lookbackDays=730&interval=1D
```

---

### Q8b — Single stock trigger — job page or instrument page?

**A:** Both, different use cases.

**Job page** (`/jobs`):
- Dropdown to select stock + interval + lookback
- See job history per stock
- Best for: ad-hoc backfill, debugging

**Instrument page** (`/admin/instruments`):
- Small `↻` button per row
- Quick shortcut without navigating to jobs page
- Best for: "I just added this stock, backfill it now"

Both call the same API: `POST /api/jobs/candle/run/{token}`

---

## 3. What Was Missing / Improvements Added

These were found during re-check — not in the original design:

### 3.1 Angel API Date Range Limit ⚠️ IMPORTANT

Angel One historical API has a maximum date window per single API call.
Requests exceeding the limit either fail or return partial data silently.

```
Interval        Max window per call    Notes
──────────────────────────────────────────────────────────────────
ONE_MINUTE      30 days                75 bars/day × 30 = 2,250 bars
FIVE_MINUTE     60 days                75 bars/day × 60 = 4,500 bars
FIFTEEN_MINUTE  60 days                25 bars/day × 60 = 1,500 bars
ONE_HOUR        400 days               7  bars/day × 400 = 2,800 bars
ONE_DAY         365 days               1  bar/day  × 365 = 365 bars
```

**What happens without chunking:**
```
Want: NIFTY 1D, 730-day lookback
API call: from=2024-10-25, to=2026-10-25   ← 730 days

Angel: ❌ Error OR silently returns only last 365 days
Result: missing 1 full year of data
```

**Fix — Chunking logic (built inside CandleJobService):**
```
Want: NIFTY 1D, 730-day lookback
Limit: 365 days per call

Chunk 1: from=2024-10-25  to=2025-10-25  → 365 bars ✅
Chunk 2: from=2025-10-25  to=2026-10-25  → 365 bars ✅
Merge:   730 bars total, sorted by date
Save:    all 730 rows to candle_history
```

**Visual:**
```
WITHOUT chunking:
  [────────────────── 730 days ─────────────────────]  ❌

WITH chunking:
  [──── 365 days (chunk 1) ────][──── 365 days (chunk 2) ────]  ✅
```

**Pseudo-code:**
```java
// Inside CandleJobService — automatic, hidden from caller
List<Candle> fetchWithChunking(token, exchange, interval, from, to) {

    int maxDays = getMaxWindowDays(interval);
    // ONE_MINUTE=30, FIVE_MINUTE=60, ONE_DAY=365 etc.

    List<Candle> allCandles = new ArrayList<>();
    LocalDate chunkStart = from;

    while (chunkStart.isBefore(to)) {
        LocalDate chunkEnd = chunkStart.plusDays(maxDays);
        if (chunkEnd.isAfter(to)) chunkEnd = to;    // don't overshoot

        List<Candle> chunk = marketDataService.getCandles(
                broker, exchange, token, interval, chunkStart, chunkEnd);

        allCandles.addAll(chunk);
        chunkStart = chunkEnd.plusDays(1);   // next chunk
        Thread.sleep(apiDelayMs);            // 200ms rate-limit guard
    }

    return allCandles;   // merged, sorted chronologically
}
```

**When chunking kicks in vs not needed:**
```
Scenario                         Chunks needed
──────────────────────────────────────────────────────────
1D,  730-day lookback            2 chunks (365 + 365)
1D,  365-day lookback            1 chunk  (fits exactly)
5m,  30-day lookback             1 chunk  (fits, limit=60d)
5m,  90-day lookback             2 chunks (60 + 30)
1m,  30-day lookback             1 chunk  (fits, limit=30d)
```

**API calls for full weekend backfill (50 stocks, 1D, 730 days):**
```
50 stocks × 2 chunks = 100 API calls
With 200ms delay + 10 threads parallel:
  Total time ≈ 10-15 seconds ✅
```

Config:
```properties
candle.history-job.max-window-days-intraday=60
candle.history-job.max-window-days-daily=365
```

### 3.2 MCX Different Trading Hours ⚠️ IMPORTANT

`AngelClient.getCandles()` defaults to NSE hours (09:15–15:30) for all exchanges.
MCX (commodities) trades until 11:30 PM IST — 8 hours longer than NSE.

**Trading hours per exchange:**
```
Exchange        Open       Close     Notes
──────────────────────────────────────────────────────────
NSE / BSE       09:15      15:30     Equity + indices
NFO / BFO       09:15      15:30     F&O contracts
CDS             09:00      17:00     Currency derivatives
MCX             09:00      23:30     Crude, Gold, Silver etc.
```

**What happens without fix (current bug):**
```
Crude Oil (MCX) 5m candles for one day:

With NSE hours (wrong):  09:00 → 15:30 = 78 bars   ❌ missing evening
With MCX hours (correct): 09:00 → 23:30 = 175 bars  ✅ full day
                                          ↑ 97 extra bars from evening session
```

**Does NOT affect 1D candles:**
```
1D bar = one bar for the full day regardless of time window.
Trading hours only matter for intraday intervals (5m, 15m, 1h).
```

**Fix — per-exchange hours map in CandleJobService:**
```java
private static final Map<String, LocalTime[]> TRADING_HOURS = Map.of(
    "NSE", new LocalTime[]{LocalTime.of(9, 15), LocalTime.of(15, 30)},
    "BSE", new LocalTime[]{LocalTime.of(9, 15), LocalTime.of(15, 30)},
    "NFO", new LocalTime[]{LocalTime.of(9, 15), LocalTime.of(15, 30)},
    "BFO", new LocalTime[]{LocalTime.of(9, 15), LocalTime.of(15, 30)},
    "CDS", new LocalTime[]{LocalTime.of(9,  0), LocalTime.of(17,  0)},
    "MCX", new LocalTime[]{LocalTime.of(9,  0), LocalTime.of(23, 30)}
);

// Used when fetching intraday candles:
LocalTime[] hours = TRADING_HOURS.getOrDefault(exchange, TRADING_HOURS.get("NSE"));
angelClient.getCandlesInRange(exchange, token, interval,
    date.atTime(hours[0]),   // correct open
    date.atTime(hours[1])    // correct close
);
```

`AngelClient.getCandlesInRange()` already exists and accepts custom times.
No change to AngelClient needed — just use the right method in the job.

### 3.3 Overlap Guard (Concurrent Run Prevention)

**Problem:** Two triggers can fire at the same time:
```
09:00 AM  → @Scheduled pre-market fires → job starts (takes 15s)
09:00:05  → Admin clicks "Run All" manually
            → second run starts ❌ duplicate
Result: 2× Angel API calls, 2× DB writes, rate limit hit
```

**Fix — AtomicBoolean (atomic, no race condition):**
```java
private final AtomicBoolean running = new AtomicBoolean(false);

public JobResult run(String triggeredBy) {
    if (!running.compareAndSet(false, true)) {
        // Another run already in progress — skip
        log.warn("Job already running — {} skipped", triggeredBy);
        return JobResult.SKIPPED;
    }
    try {
        doRun();
    } finally {
        running.set(false);   // always release, even on exception
    }
}
```

**Why compareAndSet not simple if-check:**
```
WRONG (race condition):
  Thread A: if (!running) → true  ┐ both see false
  Thread B: if (!running) → true  ┘ at same time → both run ❌

RIGHT (atomic):
  Thread A: compareAndSet(false→true) → wins  → runs ✅
  Thread B: compareAndSet(false→true) → loses → skipped ✅
```

**What each trigger returns when job already running:**
```
@Scheduled  → silently skips, logs warning
Manual API  → HTTP 409 Conflict:
              { status: "SKIPPED", reason: "Job already running",
                startedAt: "2026-10-25T09:00:00" }
Startup     → silently skips, logs warning
```

Same pattern already in `BiasWarmer.java` line 126 — reused here.

### 3.4 Angel API Rate Limiting

**Problem:** 10 parallel threads → 10 simultaneous Angel calls → rate limit hit.
```
10 threads fire at same time → Angel: 429 Too Many Requests ❌
OR: JWT temporarily blocked
```

**Fix — Semaphore (shared rate limiter across all threads):**
```java
// Max 3 concurrent Angel calls regardless of thread count
private final Semaphore angelSemaphore = new Semaphore(3);

List<Candle> callAngel(...) {
    angelSemaphore.acquire();        // wait for a slot (max 3 at once)
    try {
        Thread.sleep(apiDelayMs);    // 200ms between calls
        return angelClient.getCandlesInRange(...);
    } finally {
        angelSemaphore.release();    // free the slot
    }
}
```

**Why Semaphore not just Thread.sleep():**
```
Thread.sleep only:  10 threads each sleep 200ms → still 10 calls/200ms = 50/sec ❌
Semaphore(3):       max 3 concurrent calls at any time → ~15 calls/sec ✅
Angel limit:        ~30 calls/sec → safe margin ✅
```

**Config:**
```properties
candle.history-job.max-concurrent-calls=3   # max parallel Angel calls
candle.history-job.api-delay-ms=200         # delay between each call
```

### 3.5 Per-Instrument Timeout

**Problem:** Angel API can hang — no response, no error, just silence.
```
Stock 1-9:  done ✅
Stock 10:   Angel call → ........................ no response
Job stuck at "10/50" forever ❌  Stock 11-50 never run
```

**Fix — 30-second timeout per instrument:**
```java
CompletableFuture<Void> future = CompletableFuture.runAsync(
    () -> fetchAndSave(instrument), threadPool);

try {
    future.get(30, TimeUnit.SECONDS);    // wait max 30s per stock
} catch (TimeoutException e) {
    future.cancel(true);                 // kill hanging thread
    failedCount.incrementAndGet();       // mark as failed
    log.warn("Timeout for {} — skipping", instrument.getSymbol());
    // job continues with next stock ✅
}
```

**What happens to timed-out stock:**
```
Marked FAILED in candle_job_run → visible on job page
Job continues with remaining stocks
"Retry Failed" button appears → re-runs failed stocks only
```

**Config:**
```properties
candle.history-job.timeout-per-instrument-sec=30
```

### 3.6 Cancellation Support

**Problem:** No way to stop a running job mid-way (wrong trigger, API errors, etc.)

**Fix — AtomicBoolean cancelled flag:**
```java
private final AtomicBoolean cancelled = new AtomicBoolean(false);

for (BiasInstrumentEntity instrument : instruments) {
    if (cancelled.get()) {
        log.info("Job cancelled — stopped at {}/{}", doneCount.get(), total);
        break;   // stop before next instrument
    }
    processInstrument(instrument);   // current instrument finishes cleanly
}
cancelled.set(false);   // reset for next run
```

**What gets cancelled:**
```
Current instrument (e.g. stock 23) → finishes cleanly ✅
Stocks 24-50 → never started ✅ (gap-fill handles them next run)
Already saved candles (1-23) → safe, not rolled back ✅
```

**Why not interrupt mid-fetch?**
```
Interrupting HTTP mid-call → partial candles saved → data corruption
Better: finish current stock → check flag → stop cleanly
```

**API + UI:**
```
POST /api/jobs/candle/cancel    → sets cancelled=true
Job page: [■ Cancel] enabled only while RUNNING
After stop: status = CANCELLED, lock released, new job can start
```

### 3.7 Retry Failed or Cancelled Instruments

**Problem:** 3 of 50 stocks fail → re-running all 50 wastes 47 API calls.
Also: job cancelled at 23/50 → stocks 24-50 were skipped, never fetched.

**Fix — per-instrument status tracking:**

New table `candle_job_run_detail` (one row per instrument per run):
```sql
candle_job_run_detail (
    id            BIGINT PK,
    run_id        BIGINT FK → candle_job_run.id,
    symbol_token  VARCHAR(32),
    symbol        VARCHAR(64),
    status        VARCHAR(16),   -- DONE / FAILED / SKIPPED / CANCELLED
    candles_saved INT,
    error_msg     VARCHAR(500),
    duration_ms   INT
)
```

**Retry covers both FAILED and CANCELLED/SKIPPED:**
```
POST /api/jobs/candle/retry/{runId}
  → query detail WHERE run_id=? AND status IN ('FAILED','SKIPPED','CANCELLED')
  → create new candle_job_run (trigger=RETRY, total=count)
  → run job for only those stocks
  → same gap-fill, chunking, timeout, rate limit
```

**Job page:**
```
Run #12  47/50  ⚠️ 3 FAILED   [Retry Failed →]
Run #11  23/50  🚫 CANCELLED  [Retry Remaining →]  ← stocks 24-50 skipped
Run #13   3/3   ✅ DONE        (retry of #12)
```

**Two tables now (not one):**
```
candle_job_run        ← one row per job run (summary)
candle_job_run_detail ← one row per instrument per run (detail)
```

### 3.8 Market Holiday Check

**Problem:** Pre/post-market job fires on NSE holidays → 50 wasted Angel API calls.

**Fix — reuse existing MarketCalendar (already built in BiasWarmer):**
```java
@Scheduled(cron = "${candle.history-job.pre-market-cron}", zone = "Asia/Kolkata")
public void preMarket() {
    if (!marketCalendar.isMarketOpen(LocalDate.now(ZoneId.of("Asia/Kolkata")))) {
        log.info("CandleJob preMarket SKIP — market holiday");
        return;   // skip ✅
    }
    candleJobService.run("SCHEDULED_PRE_MARKET");
}
```

**Which runs check holiday:**
```
Pre-market       ✅ YES   No intraday data on closed day
Post-market      ✅ YES   No new 1D bar to save
Weekend backfill ❌ NO    Runs regardless — fills weekly history
Manual trigger   ❌ NO    Admin chose to run — respect it
Startup          ❌ NO    May start on holiday, still backfill old data
Retry            ❌ NO    Retrying previous failures
```

**Holidays configured in application.properties (already exists):**
```properties
bias.calendar.holidays[0]=2026-01-26   # Republic Day
bias.calendar.holidays[1]=2026-10-02   # Gandhi Jayanti
```

**MCX note:** MCX has own holiday list (different from NSE).
For now: treat same as NSE (conservative). Future: per-exchange calendars.

### 3.9 Real-Time Progress Polling

**Problem:** Job page shows no live progress — user doesn't know if job is stuck or working.

**Fix — polling every 3 seconds (simpler than SSE, sufficient for 15-30s jobs):**

Status endpoint (in-memory, no DB query):
```java
// GET /api/jobs/candle/status
{
  "status":       "RUNNING",
  "runId":        12,
  "trigger":      "MANUAL",
  "startedAt":    "2026-10-25T09:00:05",
  "elapsedSec":   18,
  "totalCount":   50,
  "doneCount":    32,
  "failedCount":  1,
  "currentStock": "RELIANCE",
  "candlesSaved": 4821,
  "pctComplete":  64
}
```

In-memory state (AtomicIntegers — no DB hit per poll):
```java
private final AtomicInteger doneCount    = new AtomicInteger(0);
private final AtomicInteger failedCount  = new AtomicInteger(0);
private final AtomicInteger candlesSaved = new AtomicInteger(0);
private volatile String     currentStock = "";
```

JavaScript polling on job page:
```javascript
let pollInterval = setInterval(async () => {
    const s = await fetch('/api/jobs/candle/status').then(r => r.json());
    updateProgressBar(s);          // ████████░░ 32/50 (64%)
    if (s.status !== 'RUNNING') {
        clearInterval(pollInterval); // stop when done
        refreshHistory();
    }
}, 3000);
```

Progress bar states:
```
RUNNING:   ████████████░░░░  32/50 (64%) | Processing: RELIANCE | Elapsed: 18s
DONE:      ████████████████  50/50 ✅ | Completed 2m 34s | Saved: 8,450 candles
CANCELLED: ████████░░░░░░░░  23/50 🚫 Cancelled
FAILED:    ████████████████  47/50 ⚠️ 3 failed | [Retry Failed →]
```

### 3.10 Custom Date Range Override (API)

**Problem:** Config lookback always relative to today. Need specific historical windows.

**Fix — from/to params override config:**
```
POST /api/jobs/candle/run?from=2025-01-01&to=2025-12-31&interval=1D
→ fetches exactly Jan-Dec 2025, chunking still applies
```

Priority order:
```
1. API ?from=&to=      ← explicit dates (highest priority)
2. API ?lookbackDays=  ← override config days
3. Config property     ← default fallback
```

### 3.11 Dedicated Thread Pool (not shared with BiasWarmer)

**Problem:** Sharing thread pool with BiasWarmer causes contention.
```
BiasWarmer (09:30 AM) + manual job trigger at same time
→ 20 tasks competing for 10 threads → both slow, Angel rate limit hit
```

**Fix — each has own pool:**
```properties
bias.warmer.thread-pool-size=10          # BiasWarmer (existing)
candle.history-job.thread-pool-size=10   # Job (separate, independent)
```
Each also has own Semaphore(3) for Angel rate limiting — no shared state.

### 3.12 Crash Recovery (Stale RUNNING Detection)

**Problem:** App crashes mid-job → candle_job_run shows RUNNING forever.
```
Job starts  → DB: status = RUNNING
App crashes → DB: still RUNNING (stale) ❌
App restarts → AtomicBoolean = false (reset)
Job page shows RUNNING but app is idle — contradiction
```

**Fix — @PostConstruct stale detection on startup:**
```java
@PostConstruct
public void fixStaleRuns() {
    // Any RUNNING row older than 1 hour = app crashed
    Instant threshold = Instant.now().minus(1, ChronoUnit.HOURS);
    int fixed = jobRunRepo.markStaleAsFailed(threshold, Instant.now());
    if (fixed > 0)
        log.warn("Marked {} stale RUNNING jobs as FAILED (app restart)", fixed);
}

// In repository:
@Modifying
@Query("UPDATE CandleJobRunEntity r SET r.status='FAILED', r.endedAt=:now, " +
       "r.errorMessage='App restarted — job interrupted' " +
       "WHERE r.status='RUNNING' AND r.startedAt < :threshold")
int markStaleAsFailed(Instant threshold, Instant now);
```

**Result:**
```
App restarts → stale RUNNING → marked FAILED
Job page: shows FAILED (not stuck RUNNING)
Retry button appears → user can re-run interrupted stocks
New jobs can start immediately (running flag = false)
```

---

## 4. Architecture

```
application.properties (candle.history-job.*)
              │
              ▼
    CandleJobProperties  @ConfigurationProperties
              │
              ▼
    CandleHistoryJob  (@Component)
     │   │   │   │
     │   │   │   └── @EventListener(ApplicationReadyEvent) → startup run
     │   │   └────── @Scheduled pre-market  09:00 Mon-Fri
     │   └────────── @Scheduled post-market 15:40 Mon-Fri
     └────────────── @Scheduled weekend     Sat 10:00 AM
              │
              ▼
    CandleJobService  (core engine)
     ├── reads:  bias_instrument WHERE enabled=1
     ├── checks: MarketCalendar (holiday guard)
     ├── checks: AtomicBoolean (overlap guard)
     ├── creates: candle_job_run row (RUNNING)
     ├── main loop (sequential, checks cancel between instruments):
     │     for each instrument:
     │       ├── check cancelled flag → break if true (write SKIPPED detail rows)
     │       ├── submit to thread pool (CompletableFuture)
     │       └── wait max 30s (timeout → FAILED detail row)
     │
     ├── worker threads (parallel, Semaphore(3) limits Angel calls):
     │       ├── resolve trading hours per exchange
     │       ├── chunk lookback → multiple API calls
     │       ├── acquire Semaphore → sleep 200ms → call Angel → release
     │       ├── MysqlCandleStore.saveAsync() → candle_history
     │       ├── write candle_job_run_detail (DONE/FAILED + timing)
     │       └── update AtomicInteger counters (done/failed/saved)
     │
     └── on finish: update candle_job_run (COMPLETED/FAILED/CANCELLED)

    CandleJobController
     ├── GET  /jobs                         → job page HTML
     ├── POST /api/jobs/candle/run          → trigger all
     ├── POST /api/jobs/candle/run/{token}  → single stock
     ├── POST /api/jobs/candle/cancel       → cancel running job
     ├── POST /api/jobs/candle/retry/{id}   → retry failed instruments
     ├── GET  /api/jobs/candle/status       → live progress JSON
     └── GET  /api/jobs/candle/history      → past runs JSON
```

---

## 5. Thread Design

```
10 instruments fetched in parallel (default):

Thread pool (size=10, named "candle-job-worker-N")

Batch of 50 instruments:
  [1-10]  → all running simultaneously (~2-3 seconds each)
  [11-20] → start when slots free up
  [21-30] → ...
  [31-40] → ...
  [41-50] → ...

Total: ~10-15 seconds for 50 stocks (vs 100+ seconds sequential)


Main loop (sequential, checks cancel between instruments):
  for each instrument:
    1. Check cancelled flag → if true, break loop (stop all remaining)
    2. Submit instrument to thread pool (CompletableFuture.runAsync)
    3. Wait max 30s (timeout guard)
    4. On timeout → cancel future, mark FAILED, continue to next

Per instrument worker (inside thread pool):
  1. Resolve trading hours for exchange (NSE=09:15-15:30, MCX=09:00-23:30...)
  2. Split lookback into chunks (if > API max window)
  3. For each chunk:
       a. Acquire Semaphore(3) slot  ← rate limiter, max 3 concurrent Angel calls
       b. Sleep 200ms               ← delay between calls
       c. Call Angel API (getCandlesInRange with correct hours)
       d. Release Semaphore slot
  4. Save to candle_history async (MysqlCandleStore.saveAsync)
  5. Write candle_job_run_detail row (DONE, candles_saved, duration_ms)
  6. Increment doneCount (AtomicInteger)
  7. On error → write detail row (FAILED, error_msg), increment failedCount
```

---

## 6. Angel API Constraints

```
Interval        Max window per call   Notes
──────────────────────────────────────────────────────
ONE_MINUTE      30 days               Very large row count — use sparingly
FIVE_MINUTE     60 days               Chunk 30-day lookback into 1 call
FIFTEEN_MINUTE  60 days               Chunk 30-day lookback into 1 call
ONE_HOUR        400 days              Usually 1 call for 30-day lookback
ONE_DAY         365 days              730-day lookback = 2 calls (365+365)

Chunking logic:
  lookback=30 days,  maxWindow=60 days  → 1 call  [today-30 → today]
  lookback=730 days, maxWindow=365 days → 2 calls  [today-730 → today-365]
                                                    [today-365 → today]
  Results merged chronologically before saving.

Rate limit:
  ~30 requests/second per JWT token (Angel One limit)
  Semaphore(3) → max 3 concurrent calls at any time
  + 200ms delay per call → ~15 calls/sec → safe margin ✅
```

---

## 7. Per-Exchange Trading Hours

```
Exchange    Market hours (IST)    AngelClient call
──────────────────────────────────────────────────────────
NSE         09:15 – 15:30         getCandles() default ✅
BSE         09:15 – 15:30         getCandles() default ✅
NFO         09:15 – 15:30         getCandles() default ✅
BFO         09:15 – 15:30         getCandles() default ✅
CDS         09:00 – 17:00         getCandlesInRange(09:00, 17:00)
MCX         09:00 – 23:30         getCandlesInRange(09:00, 23:30)

For 1D candles: trading hours don't matter (full day bar).
For intraday: use correct window per exchange.
```

---

## 8. Database Tables

### candle_job_run (summary — one row per job run)

```sql
CREATE TABLE candle_job_run (
    id             BIGINT         AUTO_INCREMENT PRIMARY KEY,

    -- What ran
    job_name       VARCHAR(64)    NOT NULL,     -- INTRADAY / DAILY / WEEKEND / SINGLE / RETRY
    trigger_type   VARCHAR(16)    NOT NULL,     -- SCHEDULED / MANUAL / STARTUP / RETRY
    triggered_by   VARCHAR(64)    NOT NULL,     -- "SYSTEM" or username
    target_token   VARCHAR(32)    DEFAULT NULL, -- NULL = all, set = single stock

    -- When
    started_at     TIMESTAMP      NOT NULL,
    ended_at       TIMESTAMP      DEFAULT NULL, -- NULL = still running

    -- Status
    status         VARCHAR(16)    NOT NULL,     -- RUNNING / COMPLETED / FAILED / CANCELLED
    error_message  VARCHAR(500)   DEFAULT NULL,

    -- Progress counters
    total_count    INT            NOT NULL DEFAULT 0,
    done_count     INT            NOT NULL DEFAULT 0,
    failed_count   INT            NOT NULL DEFAULT 0,
    total_saved    INT            NOT NULL DEFAULT 0,

    -- Config snapshot at run time
    interval_type  VARCHAR(16)    DEFAULT NULL,
    lookback_days  INT            DEFAULT NULL,
    thread_count   INT            DEFAULT NULL,

    KEY idx_job_started (started_at DESC),
    KEY idx_job_status  (status),
    KEY idx_job_name    (job_name)
);
```

### candle_job_run_detail (one row per instrument per run)

```sql
CREATE TABLE candle_job_run_detail (
    id             BIGINT         AUTO_INCREMENT PRIMARY KEY,
    run_id         BIGINT         NOT NULL,     -- FK → candle_job_run.id
    symbol_token   VARCHAR(32)    NOT NULL,
    symbol         VARCHAR(64)    DEFAULT NULL,
    status         VARCHAR(16)    NOT NULL,     -- DONE / FAILED / SKIPPED / CANCELLED
    candles_saved  INT            NOT NULL DEFAULT 0,
    error_msg      VARCHAR(500)   DEFAULT NULL,
    started_at     TIMESTAMP      DEFAULT NULL, -- when this instrument started
    ended_at       TIMESTAMP      DEFAULT NULL, -- when it finished (or failed/timed out)
    duration_ms    INT            DEFAULT NULL, -- derived: ended_at - started_at

    KEY idx_detail_run    (run_id),
    KEY idx_detail_status (run_id, status),     -- fast retry query
    CONSTRAINT fk_detail_run FOREIGN KEY (run_id) REFERENCES candle_job_run(id)
);
```

**Why two tables:**
```
candle_job_run        → job page history table (50 rows max on screen)
candle_job_run_detail → per-stock drill-down + Retry Failed support
                        (50 rows per run × history = manageable)
```

---

## 9. Configuration Reference

```properties
# ── Enable/Disable ────────────────────────────────────────────────
candle.history-job.enabled=true
candle.history-job.run-on-startup=false      # true = run once on app start

# ── Schedules ─────────────────────────────────────────────────────
candle.history-job.pre-market-cron=0 0 9 * * MON-FRI
candle.history-job.post-market-cron=0 40 15 * * MON-FRI
candle.history-job.weekend-cron=0 0 10 * * SAT
candle.history-job.zone=Asia/Kolkata

# ── Lookback ──────────────────────────────────────────────────────
candle.history-job.intraday-lookback-days=30    # 5m/15m/1h
candle.history-job.daily-lookback-days=730      # 1D (2 years)

# ── Performance ───────────────────────────────────────────────────
candle.history-job.thread-pool-size=10           # parallel instruments
candle.history-job.max-concurrent-calls=3        # max simultaneous Angel API calls (Semaphore)
candle.history-job.api-delay-ms=200              # delay between each Angel call
candle.history-job.timeout-per-instrument-sec=30 # timeout per stock

# ── Angel API chunking ────────────────────────────────────────────
candle.history-job.max-window-days-intraday=60  # max days per API call
candle.history-job.max-window-days-daily=365    # max days per API call (1D)

# ── Job history retention ─────────────────────────────────────────
candle.history-job.history-retention-days=90   # keep last 90 days of job runs
```

---

## 10. API Endpoints

```
Trigger:
  POST /api/jobs/candle/run
    ?interval=5m|15m|1h|1D   (optional, default=all)
    ?lookbackDays=30           (optional, overrides config)
    ?from=2025-01-01           (optional, custom start date)
    ?to=2025-12-31             (optional, custom end date)

  POST /api/jobs/candle/run/{token}
    ?interval=1D               (optional)
    ?lookbackDays=730          (optional)

  POST /api/jobs/candle/cancel
    Cancels currently running job

  POST /api/jobs/candle/retry/{runId}
    Re-runs only failed instruments from that run

Status:
  GET /api/jobs/candle/status
    → { status, done, total, failed, currentStock, startedAt, elapsedSec }

  GET /api/jobs/candle/history
    ?limit=20 (default)
    → [ { id, jobName, status, startedAt, endedAt, done, failed, saved } ]

Page:
  GET /jobs
    → Thymeleaf page (job dashboard)
```

---

## 11. Job Page Design

```
/jobs — sidebar: "Jobs" with clock icon

┌──────────────────────────────────────────────────────────────────┐
│  Candle History Jobs                                             │
├──────────────────────────────────────────────────────────────────┤
│  CURRENT STATUS                                                  │
│  ────────────────────────────────────────────────────────────   │
│  ● IDLE     Last run: 24 Oct 2026 15:40   Duration: 2m 34s     │
│  Next scheduled: 25 Oct 2026 09:00 (pre-market)                 │
│                                                                  │
│  [▶ Run All]  [▶ Run Daily Only]  [▶ Run Intraday Only]         │
│  [■ Cancel]   (disabled when idle)                              │
├──────────────────────────────────────────────────────────────────┤
│  SINGLE STOCK                                                    │
│  ────────────────────────────────────────────────────────────   │
│  Stock: [NIFTY 50 ▼ search...]  Interval: [1D ▼]               │
│  Lookback: [730 days]   [▶ Run for this stock]                  │
├──────────────────────────────────────────────────────────────────┤
│  PROGRESS (visible when RUNNING)                                 │
│  ────────────────────────────────────────────────────────────   │
│  ████████████████░░░░░░░░░░  32/50  (64%)                       │
│  Current: TATASTEEL   Failed: 2   Saved: 4,821 candles          │
│  [■ Cancel Job]                                                  │
├──────────────────────────────────────────────────────────────────┤
│  RUN HISTORY                                                     │
│  ────────────────────────────────────────────────────────────   │
│  #   Trigger   Started          Duration  Stocks  Saved  Status │
│  12  MANUAL    24 Oct 15:40     2m 34s    50/50   4,821  ✅     │
│  11  SCHEDULE  24 Oct 09:00     1m 12s    50/50   1,250  ✅     │
│  10  STARTUP   23 Oct 08:55     3m 01s    50/50   36,500 ✅     │
│   9  MANUAL    22 Oct 11:30     0m 45s     1/1      730  ✅     │
│   8  SCHEDULE  21 Oct 15:40     2m 10s    48/50   4,819  ⚠️ 2F  │
│                                           [Retry Failed →]      │
└──────────────────────────────────────────────────────────────────┘

Status icons:
  ✅ COMPLETED  🔄 RUNNING  ❌ FAILED  ⚠️ COMPLETED with failures  🚫 CANCELLED
```

---

## 12. Files to Create (Implementation Steps)

```
Step  File                              What it does
──────────────────────────────────────────────────────────────────────
  1   CandleJobProperties.java          @ConfigurationProperties binding
  2   CandleJobRunEntity.java           JPA entity → candle_job_run table
  2b  CandleJobRunDetailEntity.java     JPA entity → candle_job_run_detail table
  3   CandleJobRunRepository.java       JPA repo (findLatest, findHistory, markStale)
  3b  CandleJobRunDetailRepository.java JPA repo (findFailed for retry)
  4   002_candle_job_run.sql            DB migration (create both tables)
  5   CandleJobService.java             Core engine:
                                         - thread pool
                                         - overlap guard (AtomicBoolean)
                                         - chunk large lookbacks
                                         - per-exchange trading hours
                                         - progress tracking (AtomicInteger)
                                         - cancel support
                                         - timeout per instrument
                                         - save to candle_job_run
  6   CandleHistoryJob.java             @Scheduled triggers + @EventListener
                                         startup + delegates to CandleJobService
  7   CandleJobController.java          REST API + /jobs page controller
  8   templates/jobs/index.html         Job dashboard (HTML + JS polling)
  9   static/css/jobs.css               Styles matching existing dark theme
 10   app-sidebar.html                  Add "Jobs" nav link
 11   admin/instruments.html            Add ↻ backfill button per row
```

---

## 13. Edge Cases & Guards

```
Scenario                          Handling
──────────────────────────────────────────────────────────────────────
Job already running, scheduled    Skip — AtomicBoolean guard, log warning
fires again
Job running, manual trigger       Return 409 Conflict with current job status
Market holiday                    Skip pre/post market runs via MarketCalendar
Angel API down                    Per-instrument timeout (30s), mark FAILED, continue
Angel rate limit hit              200ms delay between calls prevents this
New stock added (no history)      gap-fill detects empty → full backfill auto
Stock disabled mid-run            Not re-checked mid-run (checked at start)
App crash during run              candle_job_run row stays RUNNING (stale)
                                  On next start: @PostConstruct detects → mark FAILED
                                  detail rows: completed stocks = DONE, rest = missing
                                  Retry button appears → re-runs missing stocks via gap-fill
MCX 23:30 trading hours           getCandlesInRange(09:00, 23:30) used for MCX
730-day lookback > 365 API limit  Chunked into 2 calls, merged before save
Single stock run, token invalid   Returns error immediately, logged in job run
Duplicate candles                 UNIQUE constraint in candle_history handles it
Cancel requested                  Workers check AtomicBoolean after each instrument
```

---

> **Next step:** Begin implementation — Step 1 (CandleJobProperties) through Step 11.
> All design decisions above are finalized. No further discussion needed before coding.
