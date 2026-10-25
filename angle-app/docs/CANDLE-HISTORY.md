# Candle History — How It Works

> **Audience:** developers working on angle-app who want to understand how OHLCV
> candle data flows from Angel One into MySQL, how the cache layers work, and
> how saving is triggered (automatically vs on-demand).
>
> For general system DFDs (live stream, signal pipeline, screener, auth, scheduled jobs)
> see → [FLOW-DIAGRAM.md](./FLOW-DIAGRAM.md)

---

## Table of Contents

1. [What is a Candle](#1-what-is-a-candle)
2. [Data Sources](#2-data-sources)
3. [Architecture Overview (DFD Level 0)](#3-architecture-overview)
4. [Trigger Layer — What Starts a Fetch](#4-trigger-layer)
5. [Cache Layer — L1 → L2 → L3 → Angel](#5-cache-layer)
6. [Gap-Fill Logic](#6-gap-fill-logic)
7. [Save Path — How Candles Reach MySQL](#7-save-path)
8. [MySQL Schema](#8-mysql-schema)
9. [Live Stream Path (Separate)](#9-live-stream-path)
10. [Consumers — Who Uses Candles](#10-consumers)
11. [Scheduled Jobs](#11-scheduled-jobs)
12. [Retention — Cleanup](#12-retention)
13. [Cache Providers — Configuration](#13-cache-providers)
14. [CandleHistoryJob — Planned](#14-candlehistoryjob-planned)
15. [Key Classes](#15-key-classes)

---

## 1. What is a Candle

A single OHLCV bar representing price movement over one time interval.

```java
record Candle(
    Instant   timestamp,   // when this bar closed
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long       volume
)
```

Supported intervals: `ONE_MINUTE`, `FIVE_MINUTE`, `FIFTEEN_MINUTE`,
`THIRTY_MINUTE`, `ONE_HOUR`, `ONE_DAY`.

BigDecimal is used (not double) to avoid floating-point drift in indicator math.

---

## 2. Data Sources

```
┌─────────────────────────────────────────────────────────┐
│  SOURCE 1: Angel One REST API (historical)              │
│                                                         │
│  POST /rest/secure/angelbroking/historical/v1/          │
│       getCandleData                                     │
│                                                         │
│  Returns: OHLCV[] for a date range                      │
│  Used for: all cached / stored candle data              │
│  Rate limit: be conservative — cache aggressively       │
├─────────────────────────────────────────────────────────┤
│  SOURCE 2: Angel One WebSocket (live ticks)             │
│                                                         │
│  wss://smartapisocket.angelone.in/smart-stream          │
│                                                         │
│  Returns: LTP (last traded price) every ~1 second       │
│  Used for: real-time signal detection only              │
│  NOT saved to candle_history table                      │
└─────────────────────────────────────────────────────────┘
```

---

## 3. Architecture Overview

```
╔══════════════════════════════════════════════════════════════════╗
║                      EXTERNAL                                    ║
║   Angel One REST API              Angel One WebSocket            ║
║   (historical candles)            (live ticks)                   ║
╚══════════════╤══════════════════════════╤═══════════════════════╝
               │                          │
               ▼                          ▼
╔══════════════════════════════════════════════════════════════════╗
║                      angle-app                                   ║
║                                                                  ║
║  TRIGGERS ──► MarketDataService ──► CandleCache (3 layers)      ║
║                                           │                      ║
║                               ┌───────────┼───────────┐         ║
║                               ▼           ▼           ▼         ║
║                           Caffeine     Redis       MySQL         ║
║                           (L1, 60s)  (L2, 5min)  (L3, 365d)    ║
║                                                                  ║
║  CONSUMERS: signals, screener, chart, backtest, AI picks        ║
╚══════════════════════════════════════════════════════════════════╝
```

---

## 4. Trigger Layer

Five things cause candles to be fetched (and therefore saved):

```
┌─────────────────┬──────────────────────┬─────────────────────────────┐
│ TRIGGER         │ WHEN                 │ WHAT IT FETCHES             │
├─────────────────┼──────────────────────┼─────────────────────────────┤
│ User request    │ on demand            │ whatever the page needs     │
│ /screener       │                      │ e.g. 260 daily candles      │
│ /bias           │                      │ e.g. 250 × 5m candles       │
│ /live/chart     │                      │ recent candles for 1 symbol │
├─────────────────┼──────────────────────┼─────────────────────────────┤
│ BiasWarmer      │ every 5 min          │ all instruments             │
│ (scheduled)     │ 09:00–15:00 IST      │ 5m / 15m / 1h candles      │
│                 │ Mon–Fri              │                             │
│                 │ enabled=false ⚠      │ (currently OFF in config)   │
├─────────────────┼──────────────────────┼─────────────────────────────┤
│ SignalDetector  │ every bar close      │ last 250 candles per        │
│ (live event)    │ (1m / 5m / 15m)     │ instrument (from cache)     │
├─────────────────┼──────────────────────┼─────────────────────────────┤
│ CandleHistoryJob│ 3 scheduled runs     │ see Section 14              │
│ (PLANNED)       │ pre/post market      │ daily + intraday            │
│                 │ + weekend            │                             │
├─────────────────┼──────────────────────┼─────────────────────────────┤
│ Paper trading   │ on demand            │ full day candles for        │
│ backtest        │                      │ the replayed date           │
└─────────────────┴──────────────────────┴─────────────────────────────┘
```

All triggers call the same method: `MarketDataService.getCandles()`.

---

## 5. Cache Layer

The cache has three layers. Each layer is checked in order. The first hit
returns immediately. A miss falls through to the next layer.

```
MarketDataService.getCandles(broker, exchange, token, interval, from, to)
        │
        ▼
┌───────────────────────────────────────────────────────────────┐
│  L1 — Caffeine (in-JVM)                                       │
│  TTL: 60 seconds    Size: 500 entries max                     │
│  Key: broker:exchange:token:interval:from:to                  │
│  Fast: sub-millisecond lookup                                 │
│  Lost: on app restart                                         │
└───────────────┬──────────────────────────────────────────────-┘
           HIT  │  MISS
                ▼
┌───────────────────────────────────────────────────────────────┐
│  L2 — Redis (external)                                        │
│  TTL: 5 minutes    Serialization: JSON (Jackson)              │
│  Survives: app restart (data stays in Redis)                  │
│  Shared: across multiple app instances                        │
└───────────────┬───────────────────────────────────────────────┘
           HIT  │  MISS
                ▼
┌───────────────────────────────────────────────────────────────┐
│  L3 — MySQL (candle_history table)                            │
│  Retention: 365 days    Range-aware: gap-fill enabled         │
│  Survives: Redis restart, app restart                         │
│  Durable: permanent storage                                   │
└───────────────┬───────────────────────────────────────────────┘
           HIT  │  MISS (or partial)
                ▼
┌───────────────────────────────────────────────────────────────┐
│  Angel One REST API                                           │
│  Only called when MySQL has no data for the requested range   │
│  Response is saved to MySQL (async) + returned to caller      │
└───────────────────────────────────────────────────────────────┘
```

### On a hit at any layer

| Layer hit | Action |
|-----------|--------|
| L1 (Caffeine) | Return immediately. No DB, no Redis, no Angel call. |
| L2 (Redis) | Return data. Populate L1 for next call. |
| L3 (MySQL) | Return data. Populate L2 + L1. No Angel call. |
| Angel API | Return data. Save to MySQL async. Populate L2 + L1. |

---

## 6. Gap-Fill Logic

On an L3 (MySQL) miss, the system does NOT blindly re-fetch the full date
range from Angel. Instead it checks what MySQL already has and fetches only
the missing portion.

```
Request: NIFTY 5m candles  2026-09-01 → 2026-10-25
                                │
                                ▼
         latestTimestamp() → "I have data up to 2026-10-24 15:30"
                                │
                    ┌───────────┴────────────────────┐
                    │                                │
            DB covers request              DB is partial
                    │                                │
                    ▼                                ▼
            return MySQL rows          fetch DELTA from Angel:
            (no Angel call)           2026-10-24 → 2026-10-25 only
                                                     │
                                          merge: MySQL rows + delta
                                          save delta to MySQL (async)
                                          return merged result
```

**Day 1 (first ever fetch):**
- MySQL empty → fetch all 90 days from Angel → save ~4,500 rows

**Day 2 onwards:**
- MySQL has yesterday's data → fetch only today's delta (~78 candles)
- Angel API call is tiny — saves rate limit budget

---

## 7. Save Path

Saving is **always async** — the caller never waits for the DB write.

```
Angel API response received
        │
        ├──────────────────────────────────────► return to caller
        │                                        (synchronous, immediate)
        │
        └──► MysqlCandleStore.saveAsync()
                    │
                    ▼
             CompletableFuture.supplyAsync(
               () -> saveBlocking(),
               writer   // dedicated 2-thread pool "candle-store-writer"
             )
                    │
                    ▼
             saveBlocking():
               build CandleEntity objects
               batch into groups of 500
               repo.saveAll(batch)   → INSERT
               on duplicate: retry row-by-row (skip existing)
```

**If the app crashes mid-write:** the candle is simply re-fetched from Angel
the next time it is needed. No data loss risk.

**Duplicate handling:** the `candle_history` table has a UNIQUE constraint on
`(broker, exchange, symbol_token, interval_type, ts)`. Duplicates are silently
skipped on retry.

---

## 8. MySQL Schema

```sql
CREATE TABLE candle_history (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    broker        VARCHAR(16)  NOT NULL,          -- ANGEL
    exchange      VARCHAR(8)   NOT NULL,           -- NSE / MCX / NFO
    symbol_token  VARCHAR(32)  NOT NULL,
    interval_type VARCHAR(16)  NOT NULL,           -- FIVE_MINUTE / ONE_DAY
    ts            TIMESTAMP    NOT NULL,           -- candle open time
    open          DECIMAL(15,4) NOT NULL,
    high          DECIMAL(15,4) NOT NULL,
    low           DECIMAL(15,4) NOT NULL,
    close         DECIMAL(15,4) NOT NULL,
    volume        BIGINT        NOT NULL,
    saved_at      TIMESTAMP    NOT NULL,           -- when we saved it

    UNIQUE KEY uk_candle (broker, exchange, symbol_token, interval_type, ts),
    INDEX idx_lookup  (symbol_token, interval_type, ts),    -- hot read path
    INDEX idx_saved_at (saved_at)                           -- retention purge
);
```

**Row estimate (50 instruments, 1 year):**

| Interval | Bars/day | Days | Total rows |
|----------|----------|------|------------|
| 5m       | 75       | 250  | 937,500    |
| 15m      | 25       | 250  | 312,500    |
| 1h       | 7        | 250  | 87,500     |
| 1D       | 1        | 250  | 12,500     |
| **Total**|          |      | **~1.35M** |

After 365-day retention purge, table stays bounded at ~1.4M rows.

---

## 9. Live Stream Path

Live ticks follow a completely separate path and are **NOT saved to MySQL**.

```
Angel WebSocket (tick every ~1 second)
        │
        ▼
  LiveStreamService        ← maintains WebSocket connection
        │
        ▼
  CandleAggregator         ← sweeps every 1 second
        │                    accumulates ticks into OHLCV bars
        │  when bar boundary crosses (e.g. 5-min mark):
        ▼
  CandleClosedEvent        ← internal Spring event fired
        │
        ▼
  SignalDetector           ← listens to the event
        │
        ├──► getCandles(last 250 bars)  ← from cache (usually L1/L2 hit)
        ├──► runs 8 strategies
        └──► consensus BUY/SELL → SignalEntity saved → feed updated

Live ticks → NOT in candle_history.
Historical REST → IN candle_history.
```

---

## 10. Consumers

Every consumer calls `MarketDataService.getCandles()`. The cache is
transparent to them — they always get a `List<Candle>` back.

```
MarketDataService.getCandles()
        │
        ├──► BiasWarmer           pre-warms all instruments every 5 min
        │
        ├──► SignalDetector       feeds 8 strategies on every bar close
        │      strategies: macd-crossover, rsi-mean-reversion,
        │                  bollinger-bounce, ob-retest, volume-breakout,
        │                  moving-average-crossover, sweep-fvg, ensemble
        │
        ├──► FoScreenerService    260 daily candles → ADX/RSI/EMA scoring
        │      computes: ADX(14), RSI(14), EMA50, EMA200, ATR(14)
        │      result: score 0–6 per instrument
        │
        ├──► IndicatorSeriesService  for /live/chart page
        │      computes: EMA9/20/50, VWAP, SuperTrend, RSI14, MACD
        │
        ├──► OrbService           first 15-min candles → ORB high/low
        │
        ├──► MtfConfirmationService  higher TF candles (15m, 1h)
        │
        ├──► RegimeService        ADX-based: trending vs ranging detection
        │
        ├──► AiPickService        feeds candles to Claude for AI picks
        │
        └──► PaperTradingSession  historical replay for backtesting
```

---

## 11. Scheduled Jobs

All scheduled jobs in the app that relate to candle data:

```
┌──────────────────────┬──────────────────────────┬───────────────────────────────┐
│ Class                │ Schedule                 │ What it does                  │
├──────────────────────┼──────────────────────────┼───────────────────────────────┤
│ BiasWarmer           │ every 5 min              │ Pre-warms candle cache for    │
│                      │ 09:00–15:00 IST Mon-Fri  │ all instruments (intraday).   │
│                      │ enabled=false ⚠          │ Side effect: saves to MySQL.  │
├──────────────────────┼──────────────────────────┼───────────────────────────────┤
│ CandleAggregator     │ sweep every 1 second     │ Aggregates live ticks into    │
│                      │ (live stream mode)       │ 1m/5m/15m bars. Fires event.  │
├──────────────────────┼──────────────────────────┼───────────────────────────────┤
│ MysqlRetentionSched  │ 03:00 AM daily           │ Deletes candle_history rows   │
│                      │ (cron 0 0 3 * * ?)       │ older than 365 days.          │
├──────────────────────┼──────────────────────────┼───────────────────────────────┤
│ CandleHistoryJob     │ 09:00 AM Mon-Fri         │ Pre-market: saves intraday.   │
│ (PLANNED — see §14) │ 15:40 PM Mon-Fri         │ Post-market: saves 1D candle. │
│                      │ 10:00 AM Saturday        │ Weekend: 260-day backfill.    │
└──────────────────────┴──────────────────────────┴───────────────────────────────┘
```

### Enable BiasWarmer (2-line config change)

```properties
# application.properties
bias.warmer.enabled=true
bias.warmer.warm-on-startup=true
```

---

## 12. Retention

Candle data grows ~5,000 rows/day for 50 instruments. The retention cron
keeps the table bounded.

```
Every night 03:00 AM
        │
        ▼
  MysqlRetentionScheduler.purge()
        │
        ▼
  DELETE FROM candle_history
  WHERE saved_at < NOW() - 365 days
        │
        ▼
  Logs: "MySQL retention purge — deleted N rows older than 365 days"
```

Config:
```properties
bias.cache.mysql.retention-enabled=true
bias.cache.mysql.retention-days=365
bias.cache.mysql.purge-cron=0 0 3 * * ?
```

---

## 13. Cache Providers

Switch cache provider without any Java changes — only config:

```properties
bias.cache.provider=redis-mysql    # current (active)
```

| Provider | L1 Caffeine | L2 Redis | L3 MySQL | Notes |
|----------|-------------|----------|----------|-------|
| `caffeine` | ✅ | — | — | Default. Lost on restart. |
| `noop` | — | — | — | Every call hits Angel. Dev only. |
| `redis` | — | ✅ | — | Restart-safe, no persistence. |
| `mysql` | — | — | ✅ | Durable, no Redis needed. |
| `caffeine-mysql` | ✅ | — | ✅ | No Redis needed. Recommended if no Redis. |
| `redis-mysql` | — | ✅ | ✅ | **Current.** Multi-instance safe. |
| `layered` | ✅ | ✅ | — | Fast + Redis, no MySQL persistence. |
| `layered3` | ✅ | ✅ | ✅ | Maximum speed + durability. |

**If Redis is down:** switch to `caffeine-mysql` in properties and restart.
No code changes needed.

---

## 14. CandleHistoryJob — Planned

> Status: **not yet implemented**. Design approved, pending build.

A new `@Component` with three `@Scheduled` methods that call the existing
`MarketDataService.getCandles()` on a fixed schedule — no new save mechanism,
just new triggers.

### Why it's needed

`BiasWarmer` covers intraday (5m/15m) during market hours. Nobody covers:
- Daily (1D) candles → needed by F&O Screener
- Off-hours backfill → screener loads slow on Monday if no one opened it Friday

### Schedule design

```
RUN 1 — preMarket()
  @Scheduled cron = "0 0 9 * * MON-FRI", zone = "Asia/Kolkata"

  For each enabled instrument:
    getCandles(FIVE_MINUTE,    today-5d  → today)
    getCandles(FIFTEEN_MINUTE, today-5d  → today)
    getCandles(ONE_HOUR,       today-10d → today)

  Purpose: cache is HOT before 09:15 market open.
  Angel calls: tiny (gap-fill fetches only today's delta).

─────────────────────────────────────────────────────────────

RUN 2 — postMarket()
  @Scheduled cron = "0 40 15 * * MON-FRI", zone = "Asia/Kolkata"

  For each enabled instrument:
    getCandles(ONE_DAY, today-10d → today)

  Purpose: save today's final daily candle.
  Screener has fresh 1D data for tomorrow without user visit.

─────────────────────────────────────────────────────────────

RUN 3 — weekendBackfill()
  @Scheduled cron = "0 0 10 * * SAT", zone = "Asia/Kolkata"

  For each enabled instrument:
    getCandles(ONE_DAY, today-260d → today)

  Purpose: ensure full 1-year daily history is in MySQL for all
  50 instruments. Monday screener loads instantly from MySQL.
  Angel calls: tiny (gap-fill fetches only this week's 5 bars).
```

### DFD for CandleHistoryJob

```
Saturday 10:00 AM (automatic, no user)
        │
        ▼
  CandleHistoryJob.weekendBackfill()
        │
        │  for each of 50 instruments:
        ▼
  MarketDataService.getCandles(1D, 260d)   ← same as user click
        │
        ▼
  LayeredCandleCacheWithStore.get()
        │
        ▼
  latestTimestamp() → MySQL has data until Fri 15:30
        │
        ▼
  Gap: only fetch Mon–Fri this week (5 bars) from Angel
        │
        ├──► return full merged result (260 candles)
        │
        └──► saveAsync() → candle-store-writer pool → MySQL
                           (5 new rows per instrument)

Monday morning user opens /screener
        │
        ▼
  FoScreenerService.scan()
        │
        ▼
  getCandles(1D, 260d) for 50 instruments
        │
        ▼
  L1 miss → L2 miss → L3 MySQL HIT (weekend job pre-loaded it)
        │
        ▼
  return instantly — 0 Angel API calls
```

### Implementation plan

1. Create `CandleHistoryJob.java` in `com.angle.trading.candle` package
2. Inject `MarketDataService` + `BiasInstrumentRepository`
3. Add 3 `@Scheduled` methods (preMarket, postMarket, weekendBackfill)
4. Add config properties: `candle.history-job.enabled=true`
5. Add manual trigger endpoint: `POST /api/candle/backfill`

---

## 15. Key Classes

| Class | Package | Role |
|-------|---------|------|
| `Candle` | `broker.model` | OHLCV record (the data unit) |
| `MarketDataService` | `marketdata` | Single entry point for all callers |
| `CandleCache` | `cache` | Interface — pluggable cache |
| `CandleCacheAutoConfig` | `cache` | Wires the right provider at boot |
| `LayeredCandleCacheWithStore` | `cache.impl` | L1→L2→L3 + gap-fill logic |
| `RedisCandleCache` | `cache.impl` | L2 Redis implementation |
| `CaffeineCandleCache` | `cache.impl` | L1 in-JVM implementation |
| `MysqlCandleStore` | `cache.impl` | L3 async save + range read |
| `CandleEntity` | `persistence` | JPA entity for `candle_history` |
| `CandleRepository` | `persistence` | JPA repo — range query + purge |
| `BiasWarmer` | `bias` | Scheduled cache warmer (intraday) |
| `AngelClient` | `broker.angel` | REST API calls to Angel One |
| `CandleAggregator` | `broker.angel.stream` | Tick → OHLCV bar aggregation |
| `SignalDetector` | `analysis` | Consumes bars → BUY/SELL signals |
| `FoScreenerService` | `screener` | Consumes 1D bars → ADX/RSI scores |
| `CandleHistoryJob` | `candle` | **PLANNED** — scheduled save job |

---

## Quick Reference

```
# Enable automatic intraday saving (market hours)
bias.warmer.enabled=true
bias.warmer.warm-on-startup=true

# Change cache provider (no Redis available)
bias.cache.provider=caffeine-mysql

# Change retention window
bias.cache.mysql.retention-days=365

# Check what's in cache (admin endpoint)
GET /api/cache/debug

# Force cache warm manually
POST /api/cache/warm
```
