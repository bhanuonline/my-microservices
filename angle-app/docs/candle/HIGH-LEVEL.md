# Candle History — High-Level Technical Document

> **Audience:** Anyone who wants to understand WHAT the candle history system
> does, WHY it was designed this way, and HOW the pieces fit together —
> without needing to read Java code.
>
> For implementation details (code, algorithms, config properties) →
> see [LOW-LEVEL.md](./LOW-LEVEL.md)

---

## 1. What Problem Does This Solve?

The angle-app needs OHLCV (Open, High, Low, Close, Volume) price data to:
- Compute indicators (RSI, MACD, ADX, EMA)
- Detect trading signals
- Score stocks in the F&O screener
- Replay history for backtesting

This data comes from Angel One's API. The problem: **every API call is slow,
has a rate limit, and costs network time.** If 10 different pages all call
Angel One independently for the same NIFTY 5m candles, that's 10 wasted calls.

**The solution:** a smart 3-layer cache that remembers candle data between
requests, across restarts, and fills only the gaps — not the full history
every time.

---

## 2. The Three Questions It Answers

```
Q1: Do we already have this data in memory?        → L1 Caffeine (60s)
Q2: Do we have it in Redis from a recent fetch?    → L2 Redis   (5 min)
Q3: Do we have it saved in the database?           → L3 MySQL   (365 days)
Q4: None of the above → fetch from Angel One API   → save + return
```

Only one Angel One API call is ever made per data range. After that, it's
served from one of the three cache layers.

---

## 3. System Boundaries

```
┌─────────────────────────────────────────────────────────────────┐
│  INSIDE angle-app                                               │
│                                                                 │
│  ┌───────────┐    ┌────────────────┐    ┌───────────────────┐  │
│  │ Consumers │───►│MarketDataService│───►│   CandleCache     │  │
│  │           │    │ (single entry  │    │   (3 layers)      │  │
│  │ 10 pages  │    │  point)        │    │                   │  │
│  │ 8 signals │    └────────────────┘    └─────────┬─────────┘  │
│  │ screener  │                                    │             │
│  │ backtest  │                          L1 Caffeine             │
│  └───────────┘                          L2 Redis                │
│                                         L3 MySQL                │
└────────────────────────────────────────────┬────────────────────┘
                                             │ only on miss
                                             ▼
                                    Angel One REST API
                                    (external, rate-limited)
```

---

## 4. Key Design Decisions

### 4.1 Why 3 layers?

Each layer has a different trade-off:

| Layer | Speed | Durability | Cost |
|-------|-------|-----------|------|
| Caffeine (L1) | Fastest — nanoseconds, in-JVM | Lost on restart | Free |
| Redis (L2) | Fast — milliseconds, network | Survives restart | Memory |
| MySQL (L3) | Slower — 5–10ms, disk | Permanent, 365 days | Disk |

Using all three gives: speed of L1 for hot data, durability of MySQL for
cold data, and Redis as a middle ground that survives app restarts.

### 4.2 Why async save?

When candles are fetched from Angel One, they are saved to MySQL on a
**background thread** — the caller gets the data immediately and never
waits for the database write.

```
Angel response received
   │
   ├──► return to caller (immediate)        ← user doesn't wait
   └──► background thread saves to MySQL    ← happens in parallel
```

Trade-off: if the app crashes between fetch and save, those candles are
not persisted. Acceptable because Angel One can be called again — no
data is truly lost.

### 4.3 Why gap-fill instead of full re-fetch?

On day 2, MySQL already has yesterday's candles. A naive approach would
re-fetch the entire 90-day window. Gap-fill instead:

1. Checks what's already in MySQL
2. Finds the newest timestamp we have
3. Fetches ONLY the missing portion from Angel

**Result:** after day 1, each morning's fetch is ~80 candles (today's bars)
instead of 4,500 (full history). This is ~56× fewer API calls.

### 4.4 Why is MarketDataService the single entry point?

All 10+ consumers (dashboard, screener, signals, chart, backtest...) call
one method: `MarketDataService.getCandles()`. This means:

- Cache is shared across all consumers
- Switching the cache provider (Redis vs MySQL vs Caffeine) requires
  zero changes to consumers
- Rate limiting, logging, and error handling happen in one place

---

## 5. Data Flow — Step by Step

### First ever request (cold start)

```
Day 1, first user opens /screener

  Request: NIFTY daily candles, last 260 days
      │
      ▼ L1 MISS (Caffeine empty)
      ▼ L2 MISS (Redis empty)
      ▼ L3 MISS (MySQL empty)
      ▼
  Angel One API called
  Returns 260 daily candles
      │
      ├──► User gets data (takes ~1–2 seconds)
      └──► Background: 260 rows saved to MySQL
```

### Same request next day

```
Day 2, same user opens /screener

  Request: NIFTY daily candles, last 260 days
      │
      ▼ L1 MISS (Caffeine expired — 60s TTL)
      ▼ L2 MISS (Redis expired — 5 min TTL)
      ▼ L3 HIT  (MySQL has 259 days of data)
          │
          ├── latestTimestamp() → "I have up to yesterday"
          ├── gap = only today (1 bar)
          ├── fetch just today from Angel (tiny call)
          ├── merge: 259 MySQL rows + 1 Angel row
          └──► User gets data (takes ~10ms from MySQL + tiny Angel call)
```

### Subsequent requests same session

```
Same user clicks refresh (within 60 seconds)

  Request: NIFTY daily candles, last 260 days
      │
      ▼ L1 HIT (Caffeine still warm)
      └──► Returns in < 1ms, no DB, no Redis, no Angel
```

---

## 6. Two Types of Candle Sources

The system has two completely separate paths for candles:

```
PATH 1: Historical (REST API)                PATH 2: Live (WebSocket)
─────────────────────────────                ─────────────────────────
Source: Angel One REST API                   Source: Angel One WebSocket
Used for: all cached/stored data             Used for: real-time only
Saved to: MySQL candle_history ✅            Saved to: NOT saved ❌
Consumers: everything                        Consumers: SignalDetector only
Trigger: on demand or scheduled              Trigger: continuous stream
```

Live ticks are aggregated into bars in memory (CandleAggregator) and used
immediately for signal detection. They are NOT saved to the database because:
- They would duplicate the REST API data
- REST API provides complete, clean OHLCV data
- Live bars may be incomplete (partial candle mid-period)

---

## 7. What Triggers a Save?

Candles are saved automatically whenever they are fetched from Angel One.
No manual save action is needed.

### Currently active triggers

```
1. User visits a page
   e.g. /screener → fetches 260 daily candles → saves to MySQL

2. SignalDetector (on every bar close)
   Fetches last 250 candles for each instrument
   These come from cache (L1/L2) — MySQL only on first fetch

3. BiasWarmer — CURRENTLY DISABLED
   Would automatically warm cache every 5 min during market hours
   Enable: bias.warmer.enabled=true
```

### Planned triggers (CandleHistoryJob)

```
4. Pre-market (9:00 AM Mon-Fri)
   → saves intraday candles before market opens

5. Post-market (3:40 PM Mon-Fri)
   → saves today's final daily candle

6. Weekend backfill (Saturday 10:00 AM)
   → saves 260-day daily history for all 50 stocks
   → ensures screener loads fast Monday morning
```

---

## 8. Storage — MySQL Table

All historical candles are stored in one table: `candle_history`.

```
One row = one candle bar

Key fields:
  broker        → who provided this data (ANGEL)
  exchange      → which market (NSE / MCX / NFO)
  symbol_token  → Angel One's internal ID for the stock
  interval_type → bar size (FIVE_MINUTE / ONE_DAY / etc.)
  ts            → when this bar opened
  open/high/low/close/volume → the OHLCV data
  saved_at      → when we stored it (used for retention cleanup)
```

**Unique constraint** on `(broker, exchange, symbol_token, interval_type, ts)`
means the same candle can never be stored twice — safe to re-fetch and re-save.

**Estimated size** for 50 instruments, 1 year of data:

```
5m candles:   ~937,000 rows
15m candles:  ~312,000 rows
1h candles:   ~87,000 rows
1D candles:   ~12,500 rows
─────────────────────────
Total:        ~1.35 million rows  (~300 MB)
```

---

## 9. Retention — Automatic Cleanup

Old candles are automatically deleted every night at 3:00 AM:

```
Every night 03:00 AM
  → DELETE rows where saved_at < today - 365 days
  → table stays bounded at ~1.4 million rows
  → logs how many rows were deleted
```

Configurable: `bias.cache.mysql.retention-days=365`

---

## 10. Cache Providers — Which to Use When

The cache backend is configurable. Change it with one property:
`bias.cache.provider=<value>`

```
Current: redis-mysql

┌────────────────┬────────────────────────────────────────────────┐
│ Provider       │ Best for                                       │
├────────────────┼────────────────────────────────────────────────┤
│ caffeine       │ Local dev, no Docker needed. Loses data on     │
│                │ restart. Single instance only.                 │
├────────────────┼────────────────────────────────────────────────┤
│ caffeine-mysql │ No Redis. Fast L1 + durable L3. Good for      │
│                │ single-machine deployments.                    │
├────────────────┼────────────────────────────────────────────────┤
│ redis-mysql    │ CURRENT. Multi-instance safe. L2 Redis +       │
│ (active)       │ L3 MySQL. Best for production.                 │
├────────────────┼────────────────────────────────────────────────┤
│ layered3       │ Maximum performance. L1+L2+L3. Highest memory  │
│                │ usage.                                         │
├────────────────┼────────────────────────────────────────────────┤
│ noop           │ Development only. Every call hits Angel.       │
│                │ Never use in production.                       │
└────────────────┴────────────────────────────────────────────────┘
```

**Redis went down?** Switch to `caffeine-mysql` in properties and restart.
No code changes needed.

---

## 11. Consumers Summary

Ten different parts of the app use candle data. All call the same
`MarketDataService.getCandles()` method:

```
Consumer              Interval    Lookback    Purpose
────────────────────────────────────────────────────────────────
BiasWarmer            5m/15m/1h   5–20 days   Pre-warms cache
SignalDetector        5m          ~250 bars   8-strategy consensus
FoScreenerService     1D          260 days    ADX/RSI/EMA scoring
IndicatorSeries       5m          recent      Live chart lines
OrbService            5m          today       ORB high/low
MtfConfirmation       15m/1h      recent      Multi-TF confirmation
RegimeService         1D          recent      Trend/range detection
AiPickService         1D          recent      Claude AI analysis
PaperTradingSession   5m          1 day       Backtest replay
BiasSheetService      5m/15m/1h   5–20 days   Bias dashboard
```

---

## 12. Summary — Key Points to Remember

```
✅  One entry point: MarketDataService.getCandles()
✅  3 cache layers: Caffeine (60s) → Redis (5min) → MySQL (365days)
✅  Gap-fill: only fetches what's missing, not full history
✅  Async save: caller never waits for DB write
✅  Duplicate-safe: UNIQUE constraint prevents double saves
✅  Retention: auto-cleanup at 3 AM, keeps 1 year
✅  Configurable: switch cache provider without code changes
✅  Live ticks: separate path, NOT saved to candle_history
❌  BiasWarmer: currently disabled (enable in config)
⏳  CandleHistoryJob: planned, not yet implemented
```

---

> **Next:** [LOW-LEVEL.md](./LOW-LEVEL.md) — implementation details,
> code walkthrough, configuration reference, troubleshooting
