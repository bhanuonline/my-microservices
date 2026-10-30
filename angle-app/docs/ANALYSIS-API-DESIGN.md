# Stock Analysis API — Design Document

## Overview

Two endpoints covering all use cases:

| Endpoint | Purpose |
|---|---|
| `GET /api/stocks/analysis` | Single symbol — all modes (live, historical, breakdown) |
| `POST /api/stocks/analysis/bulk` | Multiple symbols — trading watchlist, each with own params |

- Live market analysis with progressive multi-timeframe updates
- Historical point-in-time analysis
- Day breakdown (candle-by-candle detail)
- Multi-timeframe conclusion + actionable trade suggestion
- Bulk analysis for trading watchlists (different interval per symbol)

---

## Logging Strategy

Every significant event is logged at the right level — no noise, no silence.

```
Level   Where                         What is logged
──────  ────────────────────────────  ──────────────────────────────────────────────
INFO    StockAnalysisController       Request received: symbol, mode, params
INFO    StockAnalysisService          Mode detected: LIVE / CONSOLIDATED / HISTORICAL
INFO    MarketCalendarService         Market status: OPEN / CLOSED, nextCandleAt
INFO    StockAnalysisService          Angel LTP fetched: symbol, ltp, change
WARN    StockAnalysisService          Market closed — serving intelligent message
WARN    StockAnalysisService          Angel rate limited — serving stale cache
WARN    StockAnalysisService          Symbol not found in scrip master: {symbol}
WARN    StockAnalysisService          Indicator computation failed for {symbol}: {reason}
WARN    MarketCalendarService         Holiday file not found — weekends only enforced
DEBUG   StockAnalysisService          Cache HIT: {symbol}:{interval}:{date}
DEBUG   StockAnalysisService          Cache MISS — fetching from Angel: {symbol}
DEBUG   StockAnalysisService          Candles fetched: {symbol} {count} candles
DEBUG   StockAnalysisService          Indicators computed: {symbol} {interval} {ms}ms
DEBUG   StockAnalysisController       Bulk request: {count} symbols, parallel execution
ERROR   StockAnalysisController       Unexpected error for {symbol}: {message}
```

**Log format example:**
```
INFO  [nio-9010-exec-1] StockAnalysisService : [LIVE] HDFCBANK-EQ NSE — market OPEN, nextCandle=10:30 IST
DEBUG [nio-9010-exec-1] StockAnalysisService : Cache HIT indicators|HDFCBANK-EQ|ONE_HOUR|60|2026-10-30
WARN  [nio-9010-exec-2] StockAnalysisService : Angel rate limited for RELIANCE-EQ — serving stale cache (staleSince=10:00)
INFO  [nio-9010-exec-3] StockAnalysisService : [BULK] 4 symbols processed in 1240ms (parallel)
```

---

## Error Handling Strategy

### HTTP Status Codes

| Scenario | HTTP Status | Response |
|---|---|---|
| Success | 200 | Full analysis response |
| Market closed (live=true) | 200 | Intelligent message + suggestions (NOT an error) |
| Angel rate limited | 200 | Stale data + `stale: true` flag (NOT an error) |
| Symbol not found | 404 | `{ "error": "Symbol INVALID-EQ not found in scrip master" }` |
| Invalid params | 400 | `{ "error": "Invalid interval: TWO_MINUTE. Supported: FIVE_MINUTE, FIFTEEN_MINUTE, ..." }` |
| Candle fetch failed | 200 | Partial response — indicators null + warning in response |
| Per-symbol error (bulk) | 200 | That symbol has `status: ERROR`, others return normally |
| Unexpected server error | 500 | `{ "error": "Unexpected error — please retry" }` (no stack trace) |

### Error Response Shape

```json
// 404 — symbol not found
{
  "error": "Symbol INVALID-EQ not found in scrip master",
  "suggestions": [
    "Check symbol format: HDFCBANK-EQ, RELIANCE-EQ, Nifty 50",
    "Use GET /api/instruments/lookup?symbol=HDFC to find correct symbol"
  ]
}

// 400 — invalid param
{
  "error": "Invalid baseInterval: TWO_MINUTE",
  "validValues": ["FIVE_MINUTE", "FIFTEEN_MINUTE"]
}

// Bulk — per-symbol error, others succeed
[
  { "symbol": "HDFCBANK-EQ", "status": "OK",    "price": {...}, ... },
  { "symbol": "INVALID-EQ",  "status": "ERROR",  "error": "Symbol not found in scrip master" },
  { "symbol": "RELIANCE-EQ", "status": "OK",    "price": {...}, ... }
]
```

### Graceful Degradation

```
Angel LTP fails      → price: null, indicators still computed ✓
Angel candles fail   → stale:true, last cached indicators returned ✓
SMC analysis fails   → marketStructure: null, rest of response OK ✓
One TF fails in MTF  → that TF omitted, others still present ✓
One symbol fails in bulk → error in that symbol, others return ✓
```

No single failure kills the whole response.

---

## Agreed Design Points

| # | Point | Decision |
|---|---|---|
| 1 | Outside market hours | Return intelligent message + suggestions + last session data |
| 2 | newlyCompleted | Stateless — time-based `justCompleted` calculation, no server state |
| 3 | Holiday/weekend | YAML config file with `enabled` flag + `special_open` section for Muhurat |
| 4 | Stale cache in live mode | History=24h cache, Today=dynamic TTL (expires at next candle close) |
| 5 | Base interval | `FIFTEEN_MINUTE` default, configurable via property + `?baseInterval=` param |
| 6 | Exchange session hours | Auto-detect from scrip master exchSeg, override via `?marketOpen=&marketClose=` |
| 7 | Day breakdown | Optional via `?breakdown=true`, max 3 symbols in bulk |
| 8 | Rate limit fallback | Bucket4j (2 req/sec) + stale cache fallback with `stale: true` flag |
| 9 | nextCandleAt end-of-day | If next candle > marketClose → jump to next trading day open |
| 10 | Bulk max symbols | Max 20 symbols — configurable via `analysis.live.max-symbols` |
| 11 | Bulk response wrapper | `{ requested, successful, failed, processingTimeMs, results[] }` |
| 12 | Bulk status per symbol | Each result has `status: OK \| ERROR \| STALE` |
| 13 | MCX partial holidays | MORNING_ONLY_CLOSED → marketOpen=17:00 after 5pm, CLOSED before |
| 14 | Live toggle OFF | `clearTimeout` stops ghost refreshes in UI |
| 15 | Loading state | Spinner on price + shimmer on MTF table during refresh |
| 16 | 2027 holidays fallback | Missing year → WARN log + treat all weekdays as trading days |
| 17 | Sidebar navigation | Add `/live/analysis` and `/live/watchlist` links to sidebar |
| 18 | Authentication | ROLE_USER — covered by existing `/api/**` Spring Security chain |
| 19 | Market closed + last session | Closed response includes last session conclusion + price |
| 20 | Diwali Muhurat | `special_open` section in YAML overrides weekend/holiday block |

---

## API Specification

---

### 1. Single Symbol

```
GET /api/stocks/analysis
```

#### Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `symbol` | String | required | Angel scrip symbol. Exchange auto-detected from scrip master |
| `live` | boolean | `false` | Enable live progressive mode with `nextCandleAt` and `timeframeStatus` |
| `baseInterval` | Interval | config default | Lowest TF in MTF stack. `FIVE_MINUTE` or `FIFTEEN_MINUTE` |
| `interval` | Interval | `ONE_DAY` | Highest TF in MTF stack |
| `days` | int | `60` | Calendar days of warmup history |
| `asOf` | LocalDate | today | Historical point-in-time. Ignored when `live=true` |
| `breakdown` | boolean | `false` | Include candle-by-candle `dayBreakdown` section |
| `marketOpen` | String | auto | Override session open time `HH:mm` e.g. `09:00` |
| `marketClose` | String | auto | Override session close time `HH:mm` e.g. `23:30` |

---

### 2. Bulk — Multiple Symbols (Trading Watchlist)

```
POST /api/stocks/analysis/bulk
```

#### Why POST not GET?
- Each symbol can have **different interval** (HDFC on 1h, Crude on 5m, Nifty on 1d)
- No URL length limit — supports unlimited symbols
- Extensible — add per-symbol params in future without breaking API
- Standard REST practice for complex inputs

#### Request Body

```json
{
  "live": true,
  "breakdown": false,
  "symbols": [
    {
      "symbol": "HDFCBANK-EQ",
      "interval": "ONE_HOUR",
      "baseInterval": "FIFTEEN_MINUTE",
      "days": 60
    },
    {
      "symbol": "RELIANCE-EQ",
      "interval": "ONE_HOUR",
      "days": 60
    },
    {
      "symbol": "Nifty 50",
      "interval": "ONE_DAY",
      "days": 60
    },
    {
      "symbol": "CRUDEOIL30OCT26FUT",
      "interval": "FIVE_MINUTE",
      "baseInterval": "FIVE_MINUTE",
      "days": 30
    }
  ]
}
```

#### Request Body Fields

| Field | Level | Default | Description |
|---|---|---|---|
| `live` | top-level | `false` | Apply to all symbols |
| `breakdown` | top-level | `false` | Apply to all symbols |
| `symbols` | top-level | required | Array of symbol requests |
| `symbol` | per-symbol | required | Angel scrip symbol |
| `interval` | per-symbol | `ONE_DAY` | Highest TF for this symbol |
| `baseInterval` | per-symbol | config | Lowest TF for this symbol |
| `days` | per-symbol | `60` | Warmup history for this symbol |
| `marketOpen` | per-symbol | auto | Override session open |
| `marketClose` | per-symbol | auto | Override session close |
| `asOf` | per-symbol | today | Historical date (if not live) |

#### Response

Array — one analysis object per symbol, processed **in parallel**:

```json
[
  {
    "symbol": "HDFCBANK-EQ",
    "exchange": "NSE",
    "mode": "LIVE",
    "price": { "ltp": 683.40, "change": 3.40, "changePercent": 0.50 },
    "market": { "status": "OPEN", "nextCandleAt": "2026-10-30T10:30:00+05:30" },
    "conclusion": { "primaryBias": "BEARISH", "confidence": "STRONG", "tradeSetup": "WAIT_FOR_PULLBACK_END" },
    "tradeSuggestion": { "direction": "SHORT", "timing": "WAIT", ... },
    "multiTimeframe": { ... },
    "timeframeStatus": { ... }
  },
  {
    "symbol": "RELIANCE-EQ",
    "exchange": "NSE",
    "mode": "LIVE",
    ...
  },
  {
    "symbol": "CRUDEOIL30OCT26FUT",
    "exchange": "MCX",
    "mode": "LIVE",
    "market": { "status": "OPEN", "open": "09:00", "close": "23:30", ... },
    ...
  }
]
```

#### Bulk Processing — Parallel Execution

```
POST /api/stocks/analysis/bulk (4 symbols)
         │
         ▼
┌─────────────────────────────────┐
│ Split into 4 parallel tasks     │
│                                 │
│  Task 1: HDFCBANK-EQ (NSE, 1h) │──→ CompletableFuture
│  Task 2: RELIANCE-EQ (NSE, 1h) │──→ CompletableFuture
│  Task 3: Nifty 50    (NSE, 1d) │──→ CompletableFuture
│  Task 4: CRUDEOIL    (MCX, 5m) │──→ CompletableFuture
└─────────────────────────────────┘
         │
         ▼ (all run simultaneously)
┌─────────────────────────────────┐
│  Bucket4j rate limiter          │
│  spaces Angel calls: 2 req/sec  │
│  Candle cache serves most data  │
└─────────────────────────────────┘
         │
         ▼
[Collect all results → return array]
```

#### Rate Limit Handling in Bulk

```
4 symbols × 1 Angel call each (today's candles) = 4 Angel calls
Bucket4j spaces them: 2 sec total wait

After first bulk call — all candles cached:
Next bulk call = 0 Angel calls → response in < 500ms
```

#### Error Handling per Symbol

If one symbol fails (not found, rate limited), others still return:

```json
[
  { "symbol": "HDFCBANK-EQ", "status": "OK", "price": {...}, ... },
  { "symbol": "INVALID-EQ",  "status": "ERROR", "error": "Symbol not found in scrip master" },
  { "symbol": "RELIANCE-EQ", "status": "OK", "price": {...}, ... }
]
```

No full failure — partial results always returned.

### Mode Auto-Detection

```
live=true                     → LIVE mode
live=false, no asOf           → CONSOLIDATED (today, with live price)
live=false, asOf=2026-10-28   → HISTORICAL (no live price)
breakdown=true (any mode)     → adds dayBreakdown section
```

### Session Hours by Exchange (auto-detected)

| Exchange | Open | Close |
|---|---|---|
| NSE | 09:15 | 15:30 |
| BSE | 09:15 | 15:30 |
| NFO | 09:15 | 15:30 |
| MCX | 09:00 | 23:30 |
| CDS | 09:00 | 17:00 |
| BFO | 09:15 | 15:30 |

---

## Use Cases & Curl Examples

### Single Symbol (GET)

```bash
# 1. LIVE — progressive analysis (call every baseInterval)
curl -u admin:admin123 "http://localhost:9010/api/stocks/analysis?symbol=HDFCBANK-EQ&live=true"

# 2. LIVE with day breakdown
curl -u admin:admin123 "http://localhost:9010/api/stocks/analysis?symbol=HDFCBANK-EQ&live=true&breakdown=true"

# 3. LIVE with 5-min base (override config)
curl -u admin:admin123 "http://localhost:9010/api/stocks/analysis?symbol=HDFCBANK-EQ&live=true&baseInterval=FIVE_MINUTE"

# 4. LIVE for MCX Crude Oil (auto-detects MCX hours)
curl -u admin:admin123 "http://localhost:9010/api/stocks/analysis?symbol=CRUDEOIL30OCT26FUT&live=true"

# 5. CONSOLIDATED — full analysis right now
curl -u admin:admin123 "http://localhost:9010/api/stocks/analysis?symbol=HDFCBANK-EQ"

# 6. HISTORICAL — as of a past date
curl -u admin:admin123 "http://localhost:9010/api/stocks/analysis?symbol=HDFCBANK-EQ&asOf=2026-10-28"

# 7. HISTORICAL with day breakdown
curl -u admin:admin123 "http://localhost:9010/api/stocks/analysis?symbol=HDFCBANK-EQ&asOf=2026-10-28&breakdown=true&interval=ONE_HOUR"

# 8. INTRADAY STACK only (15m+30m+1h)
curl -u admin:admin123 "http://localhost:9010/api/stocks/analysis?symbol=HDFCBANK-EQ&live=true&interval=ONE_HOUR"
```

### Bulk — Trading Watchlist (POST)

```bash
# Full trading watchlist — each symbol with own interval
curl -u admin:admin123 -X POST "http://localhost:9010/api/stocks/analysis/bulk" \
  -H "Content-Type: application/json" \
  -d '{
    "live": true,
    "breakdown": false,
    "symbols": [
      { "symbol": "HDFCBANK-EQ",        "interval": "ONE_HOUR",  "days": 60 },
      { "symbol": "RELIANCE-EQ",         "interval": "ONE_HOUR",  "days": 60 },
      { "symbol": "Nifty 50",            "interval": "ONE_DAY",   "days": 60 },
      { "symbol": "CRUDEOIL30OCT26FUT",  "interval": "FIVE_MINUTE", "baseInterval": "FIVE_MINUTE", "days": 30 }
    ]
  }'

# Historical bulk — analyse past date for all symbols
curl -u admin:admin123 -X POST "http://localhost:9010/api/stocks/analysis/bulk" \
  -H "Content-Type: application/json" \
  -d '{
    "live": false,
    "symbols": [
      { "symbol": "HDFCBANK-EQ", "interval": "ONE_HOUR", "asOf": "2026-10-28" },
      { "symbol": "RELIANCE-EQ", "interval": "ONE_HOUR", "asOf": "2026-10-28" }
    ]
  }'

# Bulk with breakdown (day detail for each symbol)
curl -u admin:admin123 -X POST "http://localhost:9010/api/stocks/analysis/bulk" \
  -H "Content-Type: application/json" \
  -d '{
    "live": true,
    "breakdown": true,
    "symbols": [
      { "symbol": "HDFCBANK-EQ", "interval": "FIFTEEN_MINUTE" },
      { "symbol": "RELIANCE-EQ", "interval": "FIFTEEN_MINUTE" }
    ]
  }'
```

---

## MTF Stack by Base + Highest Interval

```
baseInterval=FIFTEEN_MINUTE (default):
  interval=ONE_HOUR   → [15m, 30m, 1h]
  interval=FOUR_HOUR  → [15m, 30m, 1h, 2h, 3h, 4h]
  interval=ONE_DAY    → [15m, 30m, 1h, 2h, 3h, 4h, 1d]

baseInterval=FIVE_MINUTE:
  interval=ONE_HOUR   → [5m, 15m, 30m, 1h]
  interval=FOUR_HOUR  → [5m, 15m, 30m, 1h, 2h, 3h, 4h]
  interval=ONE_DAY    → [5m, 15m, 30m, 1h, 2h, 3h, 4h, 1d]
```

---

## Response Shape

```json
{
  "symbol": "HDFCBANK-EQ",
  "name": "HDFC Bank",
  "exchange": "NSE",
  "mode": "LIVE",
  "baseInterval": "FIFTEEN_MINUTE",
  "asOf": "2026-10-30T10:15:00+05:30",

  "stale": false,
  "staleSince": null,
  "staleReason": null,
  "retryAfterSeconds": null,

  "price": {
    "ltp": 683.40,
    "open": 681.00,
    "high": 687.50,
    "low": 679.20,
    "previousClose": 680.00,
    "change": 3.40,
    "changePercent": 0.50
  },

  "market": {
    "status": "OPEN",
    "open": "09:15",
    "close": "15:30",
    "sessionProgress": "1h 0m into session (15% complete)",
    "nextCandleAt": "2026-10-30T10:30:00+05:30"
  },

  "timeframeStatus": {
    "FIFTEEN_MINUTE": {
      "completeBars": 4,
      "justCompleted": false,
      "latestCandleAt": "10:00 IST",
      "nextBarAt": "10:30 IST"
    },
    "ONE_HOUR": {
      "completeBars": 1,
      "justCompleted": true,
      "latestCandleAt": "10:15 IST",
      "nextBarAt": "11:15 IST"
    },
    "TWO_HOUR": {
      "completeBars": 0,
      "justCompleted": false,
      "latestCandleAt": null,
      "nextBarAt": "11:15 IST"
    }
  },

  "multiTimeframe": {
    "FIFTEEN_MINUTE": {
      "momentum": { "rsi14": 48.34, "macd": { "line": 0.26, "signal": 0.12, "histogram": 0.15 } },
      "trend": { "adx14": 19.90, "supertrend": { "line": 680.04, "bullish": true } },
      "movingAverages": { "ema9": 684.10, "ema20": 683.80, "ema50": 683.82, "ema200": 691.97, "sma20": 683.67, "vwap": 700.84 },
      "volatility": { "atr14": 3.03, "bollingerBands": { "upper": 686.49, "middle": 683.67, "lower": 680.85 } },
      "signals": { "rsi": "NEUTRAL", "adx": "RANGING", "trend": "BULLISH", "macd": "BULLISH", "bbPosition": "LOWER_HALF", "vwapPosition": "BELOW" },
      "crossovers": { "ema9AboveEma20": true, "ema20AboveEma50": false, "priceAboveEma200": false, "priceAboveVwap": false },
      "marketStructure": { "bias": "BULLISH", "lastEvent": "CHOCH_BULLISH", "nearestSwingHigh": 685.75, "nearestSwingLow": 680.00 },
      "smartMoney": { "nearestBullishOB": null, "nearestBearishOB": { "top": 696.10, "bottom": 694.50, "direction": "BEARISH" }, "openFvgs": [], "buySideLiquidityAbove": 686.80, "sellSideLiquidityBelow": 669.30 },
      "pivotPoints": { "pivot": 680.57, "r1": 691.84, "r2": 698.37, "r3": 709.64, "s1": 674.04, "s2": 662.77, "s3": 656.24, "basedOn": "2026-10-29" }
    },
    "ONE_HOUR": { "..." : "same structure" },
    "TWO_HOUR": { "..." : "same structure" },
    "ONE_DAY":  { "..." : "same structure" }
  },

  "conclusion": {
    "primaryBias": "BEARISH",
    "alignedTimeframes": ["ONE_HOUR", "TWO_HOUR", "FOUR_HOUR", "ONE_DAY"],
    "conflictingTimeframes": ["FIFTEEN_MINUTE", "THIRTY_MINUTE"],
    "confidence": "STRONG",
    "tradeSetup": "WAIT_FOR_PULLBACK_END"
  },

  "tradeSuggestion": {
    "direction": "SHORT",
    "timing": "WAIT",
    "waitFor": "Wait for FIFTEEN_MINUTE + THIRTY_MINUTE SuperTrend to turn BEARISH (pullback ending)",
    "entryZone": { "from": 681.50, "to": 685.00, "basis": "BEARISH_ORDER_BLOCK" },
    "stopLoss": { "price": 690.80, "basis": "ABOVE_NEAREST_SWING_HIGH", "riskReward": null },
    "target1": { "price": 671.20, "basis": "NEAREST_SWING_LOW", "riskReward": "1:1.8" },
    "target2": { "price": 668.50, "basis": "SELL_SIDE_LIQUIDITY", "riskReward": "1:2.3" },
    "note": "SHORT setup (STRONG confidence). Higher TFs BEARISH but lower TFs in pullback. Wait for 15m+30m SuperTrend to turn BEARISH. Stop: ₹690.8. T1: ₹671.2 (1:1.8)."
  },

  "dayBreakdown": {
    "date": "2026-10-30",
    "interval": "FIFTEEN_MINUTE",
    "totalBars": 4,
    "expectedBars": 26,
    "sessionProgress": "4 of 26 bars complete (15% of day)",
    "rows": [
      {
        "timeIst": "09:15",
        "candle": { "open": 681.00, "high": 682.50, "low": 680.20, "close": 681.80, "volume": 45230 },
        "momentum": { "rsi14": 52.10, "macd": { "line": 0.10, "signal": 0.05, "histogram": 0.05 } },
        "signals": { "rsi": "NEUTRAL", "trend": "BULLISH" },
        "crossovers": { "ema9AboveEma20": true, "priceAboveVwap": true }
      }
    ]
  }
}
```

### Market Closed Response (live=true outside hours)

```json
{
  "symbol": "HDFCBANK-EQ",
  "mode": "LIVE",
  "market": {
    "status": "CLOSED",
    "message": "Live mode is ON but market is currently closed. NSE opens at 09:15 IST on 2026-10-31.",
    "nextMarketOpen": "2026-10-31T09:15:00+05:30"
  },
  "suggestions": [
    "Use ?asOf=2026-10-30 to analyse today's completed session",
    "Use ?asOf=2026-10-30&breakdown=true&interval=ONE_HOUR for today's hourly breakdown",
    "Remove ?live=true for consolidated historical analysis"
  ]
}
```

---

## Flow Charts

### Overall Request Flow

```
Client Request
      │
      ▼
┌─────────────────────┐
│  Parse params        │
│  Detect mode         │
│  Detect exchange     │
└────────┬────────────┘
         │
         ▼
   ┌─────────────┐
   │ live=true ? │
   └──┬──────────┘
      │ YES                          NO
      ▼                              ▼
┌──────────────┐             ┌──────────────────┐
│ Market open? │             │  asOf provided?  │
└──┬───────────┘             └──┬───────────────┘
   │ YES        NO              │ YES        NO
   ▼            ▼               ▼             ▼
[LIVE]    [CLOSED msg]    [HISTORICAL]  [CONSOLIDATED]
   │                           │              │
   └──────────┬────────────────┘              │
              ▼                               ▼
    ┌─────────────────┐            ┌─────────────────┐
    │  Fetch candles   │           │  Fetch candles   │
    │  (smart cache)   │           │  (normal cache)  │
    └────────┬─────────┘           └────────┬─────────┘
             │                              │
             └──────────┬───────────────────┘
                        ▼
             ┌─────────────────────┐
             │ Rate limited (403)? │
             └──┬──────────────────┘
                │ YES                   NO
                ▼                       ▼
         [Return stale          ┌──────────────────┐
          cache + stale:true]   │ Compute MTF       │
                                │ indicators for    │
                                │ each TF           │
                                └────────┬──────────┘
                                         │
                                         ▼
                                ┌──────────────────┐
                                │ SMC Analysis      │
                                │ (structure, OB,   │
                                │  FVG, liquidity)  │
                                └────────┬──────────┘
                                         │
                                         ▼
                                ┌──────────────────┐
                                │ Build conclusion  │
                                │ + tradeSuggestion │
                                └────────┬──────────┘
                                         │
                                         ▼
                                ┌──────────────────┐
                                │ breakdown=true?  │
                                └──┬───────────────┘
                                   │ YES        NO
                                   ▼             ▼
                           [Add dayBreakdown] [Skip]
                                   │             │
                                   └──────┬──────┘
                                          ▼
                                   [Return Response]
```

---

### Live Mode Candle Cache Flow

```
Client calls live=true at 10:03am (15m base interval)
                │
                ▼
┌───────────────────────────────────┐
│ Compute dynamic TTL:              │
│  elapsed = 10:03 - 09:15 = 48m   │
│  completeBars = floor(48/15) = 3  │
│  nextCandle = 09:15 + 4×15 = 10:15│
│  TTL = 10:15 - 10:03 = 12 min    │
└──────────────────┬────────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ Fetch historical      │
        │ candles (before today)│
        │ → MySQL/Redis 24h     │
        │   cache — NO Angel    │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ Fetch today's candles │
        │ → Redis TTL=12min?   │
        └──┬──────────────────┘
           │ HIT (within 12min)   MISS (expired)
           ▼                       ▼
      [Use cache]           [Fetch from Angel]
           │                       │
           │                [Cache with 12min TTL]
           └───────┬───────────────┘
                   ▼
          [Combine + compute]
                   │
                   ▼
         [Return response]
```

---

### `justCompleted` Calculation (Point 2 — Stateless)

```
Current time: 10:15 IST
Market open:  09:15 IST
Elapsed:      60 minutes
Base interval: 15 minutes

For each TF:
┌──────────┬────────┬──────────────────────────────────────┬───────────────┐
│ TF       │ TF Min │ elapsed % tfMin < baseMin             │ justCompleted │
├──────────┼────────┼──────────────────────────────────────┼───────────────┤
│ 15m      │ 15     │ 60 % 15 = 0   → 0 < 15  → TRUE       │ true          │
│ 30m      │ 30     │ 60 % 30 = 0   → 0 < 15  → TRUE       │ true          │
│ 1h       │ 60     │ 60 % 60 = 0   → 0 < 15  → TRUE       │ true          │
│ 2h       │ 120    │ 60 % 120 = 60 → 60 < 15 → FALSE      │ false         │
│ 4h       │ 240    │ 60 % 240 = 60 → 60 < 15 → FALSE      │ false         │
│ 1d       │ 390    │ 60 % 390 = 60 → 60 < 15 → FALSE      │ false         │
└──────────┴────────┴──────────────────────────────────────┴───────────────┘

At 10:15 IST → 15m, 30m, 1h ALL just completed → strong signal check!
```

---

### Progressive TF Completion During NSE Day

```
Time     Elapsed  15m  30m   1h   2h   3h   4h
09:30     15m      ✓
09:45     30m      ✓    ✓
10:00     45m      ✓
10:15     60m      ✓    ✓    ✓              ← 1h conclusion added
10:30     75m      ✓
10:45     90m      ✓    ✓
11:00    105m      ✓
11:15    120m      ✓    ✓    ✓    ✓        ← 2h conclusion added
11:30    135m      ✓
11:45    150m      ✓    ✓
12:00    165m      ✓
12:15    180m      ✓    ✓    ✓         ✓   ← 3h conclusion added
12:30    195m      ✓
13:00    225m      ✓    ✓    ✓
13:15    240m      ✓    ✓    ✓    ✓    ✓   ✓ ← 4h + 2h conclusion added
15:15    360m      ✓    ✓    ✓    ✓        ← 1d builds all day
15:30  CLOSE → 1d bar closes → full daily conclusion
```

---

## Caching Strategy Summary

```
┌──────────────────────────────────────────────────────────────────┐
│                        CANDLE CACHE                              │
│                                                                  │
│  Historical (before today)                                       │
│  ┌─────────────────┐   ┌─────────────────┐   ┌──────────────┐  │
│  │   L1 JVM        │ → │   L2 Redis      │ → │  L3 MySQL    │  │
│  │   (fast)        │   │   24h TTL       │   │  permanent   │  │
│  └─────────────────┘   └─────────────────┘   └──────────────┘  │
│                                                                  │
│  Today's candles (live mode)                                     │
│  ┌─────────────────┐   ┌─────────────────────────────────────┐  │
│  │   L1 JVM        │ → │   L2 Redis                          │  │
│  │   (fast)        │   │   Dynamic TTL = time to next candle  │  │
│  └─────────────────┘   └─────────────────────────────────────┘  │
│                                                                  │
│        INDICATOR CACHE                                           │
│  ┌─────────────────┐   ┌─────────────────┐   ┌──────────────┐  │
│  │   L1 JVM        │ → │   L2 Redis      │ → │  L3 MySQL    │  │
│  │                 │   │   Past: 24h     │   │  day only    │  │
│  │                 │   │   Today: 2min   │   │  permanent   │  │
│  └─────────────────┘   └─────────────────┘   └──────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Improvement Details

### 1. nextCandleAt — End of Day Edge Case

```
Problem: At 15:15 NSE → now + 15min = 15:30 = market CLOSE, no candle there
Solution: if (now + baseInterval) > marketClose → nextCandleAt = next trading day open

Friday 15:15 NSE   → nextCandleAt = Monday 09:15 (skip weekend)
Pre-holiday 15:15  → nextCandleAt = day-after-holiday 09:15
MCX 23:15          → nextCandleAt = next trading day 09:00
```

### 2. Bulk Max Symbols

```properties
analysis.live.max-symbols=20
```
```json
// > 20 symbols → 400
{ "error": "Too many symbols. Max: 20. You sent: 45.",
  "suggestion": "Split into multiple bulk requests" }
```

### 3. Bulk Response Wrapper

```json
{
  "requested": 4,
  "successful": 3,
  "failed": 1,
  "processingTimeMs": 1240,
  "results": [ { "symbol": "HDFCBANK-EQ", "status": "OK", ... }, ... ]
}
```

### 4. Bulk Breakdown Size Cap

```
breakdown=true + symbols > 3 → 400:
{ "error": "breakdown=true limited to 3 symbols in bulk. You sent: 4." }
```

### 5. Diwali Muhurat — special_open in YAML

```yaml
special_open:
  - date: 2026-11-08
    name: Diwali Muhurat Trading
    exchanges: [NSE, MCX]
    marketOpen:  "18:00"
    marketClose: "19:15"
    enabled: true
    note: "Sunday override. Timings announced by exchange."
```
`MarketCalendarService` checks `special_open` BEFORE the weekend block.

### 6. MCX Partial Holiday Session Logic

```
MORNING_ONLY_CLOSED (e.g. Holi):
  Before 17:00 → status=CLOSED, message="Morning session closed. Evening opens at 17:00 IST"
  After  17:00 → status=OPEN, marketOpen=17:00, marketClose=23:30

EVENING_ONLY_CLOSED (e.g. New Year):
  Before 17:00 → status=OPEN, marketOpen=09:00, marketClose=17:00
  After  17:00 → status=CLOSED, message="Evening session closed."
```

### 7. Live Toggle OFF — clearTimeout

```javascript
let liveTimer = null;

function startLive(symbol, params) {
  liveTimer = setTimeout(() => startLive(symbol, params), delay);
}

function stopLive() {
  if (liveTimer) { clearTimeout(liveTimer); liveTimer = null; }
}

// Watchlist — one timer per symbol
const liveTimers = {};
function stopAllLive() {
  Object.values(liveTimers).forEach(clearTimeout);
  Object.keys(liveTimers).forEach(k => delete liveTimers[k]);
}
```

### 8. Loading State

```javascript
function showLoading() {
  document.getElementById('priceTicker').classList.add('loading');
  document.getElementById('mtfTable').classList.add('shimmer');
}
function hideLoading() {
  document.getElementById('priceTicker').classList.remove('loading');
  document.getElementById('mtfTable').classList.remove('shimmer');
}
```
CSS: grey overlay on price ticker, shimmer animation on MTF table rows during refresh.

### 9. 2027 Holiday Fallback

```java
// MarketCalendarService
List<HolidayEntry> getHolidays(int year) {
  List<HolidayEntry> list = holidays.get(year);
  if (list == null) {
    log.warn("No holiday list for {} — weekends only enforced", year);
    return Collections.emptyList();
  }
  return list;
}
```

### 10. Bulk Status Field per Symbol

Each result item always has `status`:
```json
{ "symbol": "HDFCBANK-EQ", "status": "OK",    "price": {...}, ... }
{ "symbol": "INVALID-EQ",  "status": "ERROR",  "error": "Symbol not found" }
{ "symbol": "RELIANCE-EQ", "status": "STALE",  "staleSince": "10:00", ... }
```

### 11. Sidebar Links

Two new entries in `app-sidebar.html`:
```html
<a href="/live/analysis"  data-nav="analysis">  Analysis  </a>
<a href="/live/watchlist" data-nav="watchlist"> Watchlist </a>
```

### 12. Authentication

No change needed. `/api/**` Spring Security chain already requires `ROLE_USER`.
Both new endpoints fall under `/api/**` — automatically protected.

### 13. Market Closed — Include Last Session

```json
{
  "market": { "status": "CLOSED", "message": "NSE closed. Opens 09:15 IST on 2026-11-02." },
  "suggestions": ["Use ?asOf=2026-10-30 to analyse today's session"],
  "lastSession": {
    "asOf": "2026-10-30",
    "closedAt": "15:30 IST",
    "price": { "ltp": 683.40, "change": 3.40, "changePercent": 0.50 },
    "conclusion": { "primaryBias": "BEARISH", "confidence": "STRONG", "tradeSetup": "WAIT_FOR_PULLBACK_END" },
    "tradeSuggestion": { "direction": "SHORT", "timing": "WAIT", "entryZone": {...}, "stopLoss": {...} }
  }
}
```

---

## Configuration

```properties
# application.properties

# ── Live Analysis ────────────────────────────────────────────
analysis.live.enabled=true
analysis.live.base-interval=FIFTEEN_MINUTE   # FIVE_MINUTE or FIFTEEN_MINUTE
analysis.live.max-symbols=20                 # max symbols in bulk POST

# ── Indicator Cache ──────────────────────────────────────────
indicators.cache.enabled=true
indicators.cache.ttl-past=PT24H
indicators.cache.ttl-today=PT2M
indicators.cache.redis-prefix=ind
```

```yaml
# src/main/resources/market-holidays.yml

nse:
  2026:
    - date: 2026-01-26
      name: Republic Day
      enabled: true
    # ... (full list — see market-holidays.yml)

mcx:
  2026:
    - date: 2026-01-26
      name: Republic Day
      enabled: true
      session: FULL_CLOSED    # FULL_CLOSED | MORNING_ONLY_CLOSED | EVENING_ONLY_CLOSED
    # ... (full list — see market-holidays.yml)

special_open:                 # overrides weekend/holiday block
  - date: 2026-11-08
    name: Diwali Muhurat Trading
    exchanges: [NSE, MCX]
    marketOpen:  "18:00"
    marketClose: "19:15"
    enabled: true
```

---

## UI Design

### Page: `/live` — Stock Analysis Dashboard

```
┌─────────────────────────────────────────────────────────────────────┐
│  AngleApp — Stock Analysis                          🟢 MARKET OPEN  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Symbol: [HDFCBANK-EQ ▾]   Interval: [ONE_DAY ▾]   [▶ LIVE]       │
│                                                                     │
├──────────────────┬──────────────────────────────────────────────────┤
│  LIVE PRICE      │  TRADE SUGGESTION                                │
│                  │                                                  │
│  ₹683.40         │  🔴 SHORT  •  WAIT                               │
│  ▲ +3.40 (0.50%) │                                                  │
│                  │  Entry Zone:  ₹681.50 – ₹685.00                 │
│  H: 687.50       │  Stop Loss:   ₹690.80  (SWING HIGH)             │
│  L: 679.20       │  Target 1:    ₹671.20  (R:R 1:1.8)              │
│  O: 681.00       │  Target 2:    ₹668.50  (R:R 1:2.3)              │
│                  │                                                  │
│  Next candle:    │  Wait for: 15m + 30m SuperTrend → BEARISH       │
│  10:30 IST       │                                                  │
├──────────────────┴──────────────────────────────────────────────────┤
│  MULTI-TIMEFRAME ANALYSIS                                           │
│                                                                     │
│  TF        RSI    ADX    Trend      MACD    Price>VWAP  Completed  │
│  ────────  ─────  ─────  ─────────  ──────  ──────────  ─────────  │
│  15m       48.3   19.9   🟢 BULL    🟢 BULL  ❌ below    ✓ 4 bars  │
│  30m       48.5   33.7   🟢 BULL    🟢 BULL  ❌ below    ✓ 2 bars  │
│  1h        44.1   52.1   🔴 BEAR    🟢 BULL  ❌ below  ⚡ JUST NOW  │
│  2h        42.8   48.3   🔴 BEAR    🔴 BEAR  ❌ below    ✓ 0 bars  │
│  3h        41.5   47.1   🔴 BEAR    🔴 BEAR  ❌ below    ✓ 0 bars  │
│  4h        39.8   46.0   🔴 BEAR    🔴 BEAR  ❌ below    ✓ 0 bars  │
│  1d        39.9   17.9   🔴 BEAR    🔴 BEAR  ❌ below    ✓ builds  │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  CONCLUSION                                                         │
│                                                                     │
│  Primary Bias:  🔴 BEARISH          Confidence: 🟡 STRONG           │
│  Setup:         ⏳ WAIT FOR PULLBACK END                            │
│                                                                     │
│  Aligned:    1h, 2h, 3h, 4h, 1d  ✓                                 │
│  Conflicting: 15m, 30m  ← pullback in progress                     │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  PIVOT POINTS (based on 2026-10-29)                                 │
│                                                                     │
│  R3: 709.64   R2: 698.37   R1: 691.84   P: 680.57                  │
│  S1: 674.04   S2: 662.77   S3: 656.24                              │
│                                      ▲ LTP 683.40 (above pivot)    │
├─────────────────────────────────────────────────────────────────────┤
│  SMART MONEY                                                        │
│                                                                     │
│  Bearish OB:  694.50 – 696.10  (resistance zone)                   │
│  Bullish OB:  null             (no support OB near price)           │
│  Buy-side Liq above:  ₹686.80  (stop-hunt target)                  │
│  Sell-side Liq below: ₹669.30  (stop-hunt target)                  │
│                                                                     │
│  Open FVGs:  685.10–683.40 (BEARISH)                               │
│              691.50–691.10 (BEARISH)                               │
├─────────────────────────────────────────────────────────────────────┤
│  DAY BREAKDOWN  [breakdown=true]                     4/26 bars done │
│                                                                     │
│  Time   Open    High    Low     Close   RSI    Trend  VWAP pos      │
│  09:15  681.00  682.50  680.20  681.80  52.1   BULL   ABOVE         │
│  09:30  681.80  683.20  681.00  682.50  50.3   BULL   ABOVE         │
│  09:45  682.50  684.10  682.00  683.40  48.8   BULL   BELOW         │
│  10:00  683.40  685.50  683.00  684.20  48.3   BULL   BELOW         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### UI API Calls (GET vs POST)

The UI has **two modes** — single symbol and watchlist. Each uses the right call:

```javascript
// ── Single Symbol Page ──────────────────────────────────
// Uses GET — simple, bookmarkable, no body needed
async function loadSingleSymbol(symbol, params) {
  const url = `/api/stocks/analysis?symbol=${symbol}&live=${params.live}&interval=${params.interval}`;
  const res = await fetch(url);
  const data = await res.json();
  renderAnalysis(data);

  // Auto-refresh at next candle (no polling waste)
  if (data.market?.status === 'OPEN' && params.live) {
    const nextCall = new Date(data.market.nextCandleAt);
    const delay = nextCall - new Date();
    setTimeout(() => loadSingleSymbol(symbol, params), delay);
  }
}

// ── Watchlist Page ───────────────────────────────────────
// Uses POST — each symbol can have own interval
async function loadWatchlist(watchlist) {
  const res = await fetch('/api/stocks/analysis/bulk', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      live: true,
      symbols: watchlist  // [{ symbol, interval, days }, ...]
    })
  });
  const data = await res.json();  // array of analysis objects
  renderWatchlist(data);

  // Refresh at soonest nextCandleAt across all symbols
  const soonest = data
    .filter(d => d.market?.nextCandleAt)
    .map(d => new Date(d.market.nextCandleAt))
    .sort()[0];
  if (soonest) setTimeout(() => loadWatchlist(watchlist), soonest - new Date());
}
```

### UI Pages

Two separate pages:

| URL | Page | API used |
|---|---|---|
| `/live/analysis` | Single symbol deep analysis | `GET /api/stocks/analysis` |
| `/live/watchlist` | Multi-symbol watchlist | `POST /api/stocks/analysis/bulk` |

### Page: `/live/watchlist` — Multi-Symbol Watchlist

```
┌─────────────────────────────────────────────────────────────────────┐
│  AngleApp — Watchlist                               🟢 MARKET OPEN  │
├─────────────────────────────────────────────────────────────────────┤
│  [+ Add Symbol]   [▶ LIVE — all]   Next refresh: 10:30 IST          │
├──────────┬────────┬────────┬──────────┬───────────┬────────────────┤
│ Symbol   │ LTP    │ Change │ Bias     │ Setup     │ Trade          │
├──────────┼────────┼────────┼──────────┼───────────┼────────────────┤
│ HDFC     │ 683.40 │ +0.50% │ 🔴 BEAR  │ ⏳ WAIT   │ SHORT ₹681–685 │
│ (1h)     │        │        │ STRONG   │ 15m pulls │ SL: 690  T:671 │
├──────────┼────────┼────────┼──────────┼───────────┼────────────────┤
│ RELIANCE │1174.20 │ +1.44% │ 🟢 BULL  │ ✅ NOW    │ LONG ₹1170–1175│
│ (1h)     │        │        │ MODERATE │ all align │ SL:1162  T:1190│
├──────────┼────────┼────────┼──────────┼───────────┼────────────────┤
│ Nifty 50 │24650.0 │ -0.20% │ 🔴 BEAR  │ ⏳ WAIT   │ SHORT zone soon│
│ (1d)     │        │        │ STRONG   │ 15m pulls │ SL:24800 T:2430│
├──────────┼────────┼────────┼──────────┼───────────┼────────────────┤
│ CRUDE    │ 6120.0 │ +0.80% │ 🟢 BULL  │ ✅ NOW    │ LONG ₹6080–6100│
│ (5m)     │        │        │ STRONG   │ all align │ SL:6050  T:6200│
└──────────┴────────┴────────┴──────────┴───────────┴────────────────┘
  Click any row → opens full single-symbol analysis page
```

**Watchlist row color coding:**
- 🟢 Green row — all TFs bullish, high confidence
- 🔴 Red row — all TFs bearish, high confidence
- 🟡 Yellow row — mixed signals, wait

### UI Components Needed

**Single Symbol Page (`/live/analysis`):**

| Component | Description |
|---|---|
| **Symbol Search** | Autocomplete input (calls `/api/instruments/lookup`) |
| **Market Status Badge** | 🟢 OPEN / 🔴 CLOSED, next open time, session progress % |
| **Price Ticker** | LTP, change, H/L/O, next candle countdown timer |
| **Trade Card** | Direction badge, timing, entry zone, stop, T1/T2 with R:R |
| **MTF Table** | One row per TF — RSI, ADX, trend badge, MACD, `⚡ JUST NOW` |
| **Conclusion Block** | Primary bias, confidence pill, setup label, aligned/conflicting TFs |
| **Pivot Bar** | Visual horizontal bar: S3–S2–S1–P–R1–R2–R3 with LTP marker |
| **Smart Money Block** | OB zones, FVGs list, buy/sell liquidity levels |
| **Day Breakdown Table** | Scrollable — one row per candle, key indicators per row |
| **Live Toggle** | ON/OFF — auto-refreshes at exact `nextCandleAt` (no polling) |

**Watchlist Page (`/live/watchlist`):**

| Component | Description |
|---|---|
| **Add Symbol Modal** | Symbol search + interval selector + Add button |
| **Watchlist Table** | One row per symbol: LTP, change%, bias, confidence, setup, trade |
| **Row Color Coding** | Green=bullish, Red=bearish, Yellow=mixed |
| **Live Toggle** | ON/OFF — refreshes entire watchlist at soonest `nextCandleAt` |
| **Click-through** | Click any row → opens full single-symbol analysis page |
| **Remove Symbol** | Delete button per row |

### UI Detail Levels

Two pages — two levels of detail:

```
/live/watchlist  (summary — many symbols at once)
  Each row shows:
    Symbol | LTP | Change% | Bias | Confidence | Setup | Trade Direction
  → Click any row → opens /live/analysis for full detail

/live/analysis  (full detail — one symbol)
  Shows everything:
    ✓ Price block       (LTP, open, high, low, change, changePercent)
    ✓ Market status     (OPEN/CLOSED, session progress, next candle countdown)
    ✓ MTF table         (all TFs — RSI, ADX, trend, MACD, crossovers, justCompleted badge)
    ✓ Conclusion        (primaryBias, confidence, tradeSetup, aligned/conflicting TFs)
    ✓ Trade suggestion  (direction, timing, entry zone, stop, T1, T2, R:R)
    ✓ Pivot points      (P, R1-R3, S1-S3 visual bar with LTP marker)
    ✓ Smart money       (OB zones, FVGs, liquidity levels)
    ✓ Market structure  (bias, lastEvent, swing high/low)
    ✓ Day breakdown     (candle-by-candle table — only when breakdown=true)
```

---

### Auto-Refresh Strategy

**Smart refresh — NOT polling. Sleeps until exact candle close.**

```
❌ Bad (polling every 5s):
   10:00:00 → fetch (no new data)
   10:00:05 → fetch (no new data)
   10:00:10 → fetch (no new data)
   ... 180 wasted calls per 15 min window ...
   10:15:00 → fetch (new candle!)

✅ Good (event-driven via nextCandleAt):
   10:00:00 → fetch → nextCandleAt = 10:15:00
   UI sleeps exactly 15 minutes
   10:15:00 → fetch (new candle!) → nextCandleAt = 10:30:00
   UI sleeps exactly 15 minutes
   ...
   Zero wasted calls.
```

---

### What Updates on Refresh (Partial DOM — No Page Reload)

```
Full page reload  ❌ — causes flash, scroll reset, bad UX
Full page refresh ❌ — same problem

Partial DOM update ✅ — smooth, only changed sections update

Sections that UPDATE on each refresh:
  ✓ Price ticker         — LTP, change, H/L/O
  ✓ Market status bar    — session progress %, next candle timer
  ✓ MTF table rows       — indicator values, justCompleted ⚡ badges
  ✓ Conclusion block     — bias, confidence, tradeSetup
  ✓ Trade suggestion     — entry zone, stop, targets
  ✓ Day breakdown table  — new row appended when candle closes
  ✓ timeframeStatus      — completeBars count per TF

Sections that NEVER change on refresh (no update needed):
  ✗ Page header / sidebar
  ✗ Symbol search bar
  ✗ Pivot points         — changes only at day open (basedOn date)
  ✗ Page layout / styles
```

---

### Technology — fetch() API

**NOT jQuery ajax. NOT HTMX. Pure modern JavaScript fetch().**

Why fetch():

| | fetch() | Ajax/jQuery | HTMX |
|---|---|---|---|
| Modern standard | ✅ | ❌ Old | ✅ |
| No library needed | ✅ | ❌ Needs jQuery | ✅ |
| Fine-grained DOM control | ✅ | ✅ | ❌ Swaps HTML blocks |
| JSON response handling | ✅ Perfect | ✅ | ❌ Expects HTML |
| Update individual cells | ✅ | ✅ | ❌ |
| Add table rows dynamically | ✅ | ✅ | ❌ |

HTMX is great for simple forms — but here we need fine-grained control:
updating individual table cells, changing badge colors, appending candle rows.
fetch() + DOM manipulation is the right tool.

---

### Auto-Refresh Logic (UI)

```javascript
// ── Single Symbol Page (/live/analysis) ─────────────────────────
// Uses GET — sleeps until nextCandleAt, then fetches and updates DOM

async function startLive(symbol, params) {
  const url = `/api/stocks/analysis?symbol=${encodeURIComponent(symbol)}&live=true&interval=${params.interval}`;
  const data = await fetch(url).then(r => r.json());

  // Update ONLY specific DOM elements — no page reload, no flash
  document.getElementById('ltp').textContent          = `₹${data.price?.ltp ?? '—'}`;
  document.getElementById('change').textContent       = `${data.price?.changePercent ?? ''}%`;
  document.getElementById('bias').textContent         = data.conclusion?.primaryBias ?? '—';
  document.getElementById('confidence').textContent   = data.conclusion?.confidence ?? '—';
  document.getElementById('setup').textContent        = data.conclusion?.tradeSetup ?? '—';
  document.getElementById('nextCandle').textContent   = data.market?.nextCandleAt ?? '—';
  document.getElementById('sessionPct').textContent   = data.market?.sessionProgress ?? '—';

  // Update MTF table rows
  renderMtfTable(data.multiTimeframe, data.timeframeStatus);

  // Update trade suggestion card
  renderTradeSuggestion(data.tradeSuggestion);

  // Append new breakdown row if candle just closed
  if (data.dayBreakdown?.rows) renderBreakdownRows(data.dayBreakdown.rows);

  // Schedule next refresh at EXACT candle close — no polling waste
  if (data.market?.status === 'OPEN' && data.market?.nextCandleAt) {
    const delay = new Date(data.market.nextCandleAt) - new Date();
    setTimeout(() => startLive(symbol, params), Math.max(delay, 1000));
  }
}

// ── Watchlist Page (/live/watchlist) ────────────────────────────
// Uses POST — each symbol has own interval, refresh at soonest nextCandleAt

async function refreshWatchlist(watchlist) {
  const data = await fetch('/api/stocks/analysis/bulk', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ live: true, symbols: watchlist })
  }).then(r => r.json());

  // Update each watchlist row individually
  data.forEach(item => renderWatchlistRow(item));

  // Sleep until soonest nextCandleAt across all symbols
  const times = data
    .filter(d => d.market?.nextCandleAt)
    .map(d => new Date(d.market.nextCandleAt));
  if (times.length > 0) {
    const soonest = new Date(Math.min(...times));
    const delay = Math.max(soonest - new Date(), 1000);
    setTimeout(() => refreshWatchlist(watchlist), delay);
  }
}
```

No constant polling — UI sleeps until exact `nextCandleAt` then fetches once.
Zero wasted calls between candle closes.

---

## Files to Create / Modify

### New Files
| File | Purpose |
|---|---|
| `dto/StockAnalysisDto.java` | Main consolidated response DTO (single symbol) |
| `dto/BulkAnalysisRequestDto.java` | POST body for bulk endpoint |
| `dto/SymbolRequestDto.java` | Per-symbol params inside bulk request |
| `dto/MarketStatusDto.java` | Market open/closed info + nextCandleAt |
| `dto/TimeframeStatusDto.java` | completeBars, justCompleted, nextBarAt per TF |
| `dto/LiveBlockDto.java` | Live-mode specific fields |
| `service/StockAnalysisService.java` | Core analysis logic (mode detection, fetch, compute) |
| `service/MarketCalendarService.java` | Market hours, holiday check, nextCandleAt calculation |
| `controller/StockAnalysisController.java` | GET single + POST bulk endpoints |
| `resources/market-holidays.yml` | NSE + MCX holiday list (user provides) |
| `templates/live/analysis.html` | Single symbol deep analysis UI |
| `templates/live/watchlist.html` | Multi-symbol watchlist UI |
| `static/css/analysis.css` | Shared UI styles for both pages |
| `static/js/analysis.js` | Auto-refresh + GET fetch for single symbol |
| `static/js/watchlist.js` | Auto-refresh + POST fetch for watchlist |

### Modified Files
| File | Change |
|---|---|
| `config/IndicatorCacheProperties.java` | Add `analysis.live.*` properties |
| `application.properties` | Add live analysis config keys |
| `postman/angle-app.postman_collection.json` | Add analysis API requests |
| `docs/STOCK-INDICATORS-API.md` | Add link to this doc |

---

## Implementation Order

```
Phase 1 — Foundation
  1.  MarketCalendarService
        - YAML loading (holiday, special_open, enabled flag)
        - Session hours per exchange (NSE/MCX/CDS)
        - MCX partial session (MORNING_ONLY_CLOSED, EVENING_ONLY_CLOSED)
        - nextCandleAt with end-of-day + holiday + weekend fix
        - special_open override (Muhurat)
        - 2027 fallback (WARN + empty list)
  2.  StockAnalysisService skeleton (mode detection only)

Phase 2 — Core DTOs
  3.  StockAnalysisDto
  4.  BulkAnalysisRequestDto + SymbolRequestDto
  5.  BulkAnalysisResponseDto (wrapper: requested/successful/failed/ms)
  6.  MarketStatusDto (status, open, close, nextCandleAt, lastSession)
  7.  TimeframeStatusDto (completeBars, justCompleted, nextBarAt)
  8.  status field (OK | ERROR | STALE) on all result items

Phase 3 — Single Symbol API
  9.  StockAnalysisController GET endpoint
  10. CONSOLIDATED mode (today, with live price)
  11. HISTORICAL mode (asOf param)
        Test: GET /api/stocks/analysis?symbol=HDFCBANK-EQ
        Test: GET /api/stocks/analysis?symbol=HDFCBANK-EQ&asOf=2026-10-28

Phase 4 — Live Mode
  12. Dynamic TTL (today's candles expire at next candle close)
  13. justCompleted time-based calculation
  14. Market closed response + lastSession data included
  15. nextCandleAt end-of-day edge case
  16. MCX partial session status (before/after 17:00)
        Test: GET /api/stocks/analysis?symbol=HDFCBANK-EQ&live=true
        Test: GET /api/stocks/analysis?symbol=CRUDEOIL30OCT26FUT&live=true

Phase 5 — Bulk Endpoint
  17. POST /api/stocks/analysis/bulk
  18. Max symbols validation (analysis.live.max-symbols=20)
  19. CompletableFuture parallel execution
  20. Per-symbol error (status: OK|ERROR|STALE)
  21. Bulk wrapper response
  22. Breakdown size cap (max 3 symbols when breakdown=true)
        Test: POST /api/stocks/analysis/bulk (4 symbols, mixed exchanges)

Phase 6 — Day Breakdown
  23. breakdown=true wiring (reuse computeDayBreakdown())
  24. Size cap enforcement
        Test: ?breakdown=true single + bulk

Phase 7 — UI Single Symbol (/live/analysis)
  25. analysis.html page
  26. Price ticker + market status bar + session progress
  27. MTF table with ⚡ justCompleted badge
  28. Trade suggestion card (entry/stop/targets/R:R)
  29. Pivot bar + SMC block + market structure
  30. Day breakdown table
  31. Loading state (spinner + shimmer on MTF table)
  32. Auto-refresh JS with clearTimeout on live OFF
  33. Sidebar link for /live/analysis

Phase 8 — UI Watchlist (/live/watchlist)
  34. watchlist.html page
  35. Summary table (LTP, change, bias, confidence, setup, trade)
  36. Add/remove symbol modal
  37. Row color coding (green/red/yellow)
  38. Click-through to /live/analysis
  39. Bulk auto-refresh JS with clearTimeout
  40. Sidebar link for /live/watchlist

Phase 9 — Polish
  41. Postman collection (bulk POST examples)
  42. Update STOCK-INDICATORS-API.md
  43. Version bump
```

---

---

## When to Use Which Endpoint

```
Single stock deep analysis before trade?
  → GET /api/stocks/analysis?symbol=HDFCBANK-EQ&live=true

Morning — check full watchlist (HDFC, Reliance, Nifty, Crude)?
  → POST /api/stocks/analysis/bulk  (each with own interval)

Review what happened on a past date?
  → GET /api/stocks/analysis?symbol=HDFCBANK-EQ&asOf=2026-10-28&breakdown=true

Scan 10 stocks for RSI < 30 (screener)?
  → POST /api/stocks/analysis/bulk  (all with ONE_DAY interval)

Intraday — just watch Nifty every 15 min?
  → GET /api/stocks/analysis?symbol=Nifty 50&live=true&interval=ONE_HOUR
```

---

*Document version: 3.0 — updated 2026-10-30.*
*Changes: All 20 design points agreed. 13 improvements added (nextCandleAt edge case, bulk max symbols, bulk wrapper, breakdown cap, Muhurat special_open, MCX partial session, clearTimeout, loading state, 2027 fallback, status field, sidebar links, auth confirmed, last session in closed response). Holiday list received and market-holidays.yml created. Ready for implementation.*
