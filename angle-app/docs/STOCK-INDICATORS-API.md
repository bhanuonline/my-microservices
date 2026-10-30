# Stock Indicators API

Complete reference for the stock indicators endpoints. All endpoints require HTTP Basic auth (`admin:admin123`) and are served on port `9010`.

---

## Endpoints Overview

| Endpoint | Use case |
|---|---|
| `GET /api/stocks/quote` | Live price + indicators in one call |
| `GET /api/stocks/indicators` | Indicators only (no LTP call, faster) |
| `GET /api/stocks/indicators/day` | Every candle's indicators for a specific date |
| `GET /api/stocks/indicators/mtf` | Multi-timeframe analysis + trade suggestion |

---

## Common Parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `symbol` | Yes | — | Angel scrip master symbol (e.g. `HDFCBANK-EQ`, `RELIANCE-EQ`, `CRUDEOIL30OCT26FUT`) |
| `interval` | No | `ONE_DAY` | Candle timeframe (see table below) |
| `days` | No | `300` | Calendar days of history for indicator warmup |
| `asOf` | No | today | `YYYY-MM-DD` — compute indicators as of a past date |

### Supported Intervals

| Interval | Angel API | Notes |
|---|---|---|
| `FIFTEEN_MINUTE` | Native | 26 bars/day |
| `THIRTY_MINUTE` | Native | 13 bars/day |
| `ONE_HOUR` | Native | 7 bars/day (NSE), up to 14 (MCX) |
| `TWO_HOUR` | Synthesized from 1h | No extra API call — aggregated from cached 1h candles |
| `THREE_HOUR` | Synthesized from 1h | Same |
| `FOUR_HOUR` | Synthesized from 1h | Same |
| `ONE_DAY` | Native | 1 bar/day |

### Symbol Format
- NSE equity: `RELIANCE-EQ`, `HDFCBANK-EQ`, `TCS-EQ`, `INFY-EQ`
- MCX futures: `CRUDEOIL30OCT26FUT` (changes monthly — use instruments lookup)
- Find any symbol: `GET /api/instruments/lookup?symbol=...`

---

## 1. `GET /api/stocks/quote`

Returns **live price (LTP) + grouped technical indicators**.

### When to use
- Stock dashboard card showing price + analysis
- Any screen where you need both price and indicators together

### Parameters
All common parameters apply. No additional parameters.

### Example
```bash
# Daily indicators (default)
curl -u admin:admin123 "http://localhost:9010/api/stocks/quote?symbol=HDFCBANK-EQ"

# 1-hour indicators
curl -u admin:admin123 "http://localhost:9010/api/stocks/quote?symbol=HDFCBANK-EQ&interval=ONE_HOUR&days=60"

# Historical — indicators as of 28 Oct
curl -u admin:admin123 "http://localhost:9010/api/stocks/quote?symbol=HDFCBANK-EQ&asOf=2026-10-28"
```

### Response
```json
{
  "token": "1333",
  "symbol": "HDFCBANK-EQ",
  "name": "HDFC Bank",
  "exchange": "NSE",
  "instrumentType": "EQ",
  "ltp": 683.40,
  "open": 681.00,
  "high": 687.50,
  "low": 679.20,
  "previousClose": 680.00,
  "change": 3.40,
  "changePercent": 0.50,
  "indicators": {
    "momentum": {
      "rsi14": 48.34,
      "macd": { "line": 0.26, "signal": 0.12, "histogram": 0.15 }
    },
    "trend": {
      "adx14": 19.90,
      "supertrend": { "line": 680.04, "bullish": true }
    },
    "movingAverages": {
      "ema9": 684.10, "ema20": 683.80, "ema50": 683.82,
      "ema200": 691.97, "sma20": 683.67, "vwap": 700.84
    },
    "volatility": {
      "atr14": 3.03,
      "bollingerBands": { "upper": 686.49, "middle": 683.67, "lower": 680.85 }
    },
    "signals": {
      "rsi": "NEUTRAL",
      "adx": "RANGING",
      "trend": "BULLISH",
      "macd": "BULLISH",
      "bbPosition": "LOWER_HALF",
      "vwapPosition": "BELOW"
    },
    "crossovers": {
      "ema9AboveEma20": true,
      "ema20AboveEma50": false,
      "priceAboveEma200": false,
      "priceAboveVwap": false
    },
    "marketStructure": {
      "bias": "BULLISH",
      "lastEvent": "CHOCH_BULLISH",
      "nearestSwingHigh": 685.75,
      "nearestSwingLow": 680.00
    },
    "smartMoney": {
      "nearestBullishOB": { "top": 682.00, "bottom": 679.50, "direction": "BULLISH" },
      "nearestBearishOB": { "top": 696.10, "bottom": 694.50, "direction": "BEARISH" },
      "openFvgs": [
        { "top": 685.10, "bottom": 683.40, "direction": "BEARISH" }
      ],
      "buySideLiquidityAbove": 686.80,
      "sellSideLiquidityBelow": 669.30
    },
    "pivotPoints": {
      "pivot": 680.57,
      "r1": 691.84, "r2": 698.37, "r3": 709.64,
      "s1": 674.04, "s2": 662.77, "s3": 656.24,
      "basedOn": "2026-10-28"
    },
    "interval": "ONE_HOUR",
    "candleCount": 282
  }
}
```

### Note on `asOf`
When `asOf` is provided, `ltp/open/high/low` are always **live** from Angel (LTP endpoint is real-time only). Only the `indicators` block reflects the historical date.

---

## 2. `GET /api/stocks/indicators`

Returns **indicators only** — no LTP call. Faster and cheaper when price is not needed.

### When to use
- Stock screener scanning multiple stocks (e.g. RSI < 30)
- Background jobs that only need technical values
- Checking historical indicators for a past date

### Parameters
All common parameters apply.

### Example
```bash
# Daily indicators
curl -u admin:admin123 "http://localhost:9010/api/stocks/indicators?symbol=HDFCBANK-EQ"

# 1-hour indicators
curl -u admin:admin123 "http://localhost:9010/api/stocks/indicators?symbol=HDFCBANK-EQ&interval=ONE_HOUR&days=60"

# Historical — indicators as of 28 Oct
curl -u admin:admin123 "http://localhost:9010/api/stocks/indicators?symbol=HDFCBANK-EQ&interval=ONE_DAY&asOf=2026-10-28"

# Crude Oil
curl -u admin:admin123 "http://localhost:9010/api/stocks/indicators?symbol=CRUDEOIL30OCT26FUT&interval=ONE_HOUR&days=60"
```

### Response
Same structure as the `indicators` block in `/api/stocks/quote` (without price fields). See response schema above.

---

## 3. `GET /api/stocks/indicators/day`

Returns **one indicator row per candle for a specific trading date**. Most detailed endpoint.

### When to use
- Review how indicators evolved during a trading day hour by hour
- Backtest-style review of a specific date
- See intraday VWAP, SuperTrend, BB at each candle

### Parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `symbol` | Yes | — | Instrument symbol |
| `date` | Yes | — | `YYYY-MM-DD` — the specific trading day |
| `interval` | No | `ONE_HOUR` | Candle interval |
| `days` | No | `300` | Warmup history before `date` |

### Example
```bash
# HDFC Bank hourly on 28 Oct — returns 7 rows (9:15 → 15:15)
curl -u admin:admin123 "http://localhost:9010/api/stocks/indicators/day?symbol=HDFCBANK-EQ&date=2026-10-28&interval=ONE_HOUR&days=60"

# 15-minute breakdown — returns 26 rows
curl -u admin:admin123 "http://localhost:9010/api/stocks/indicators/day?symbol=HDFCBANK-EQ&date=2026-10-28&interval=FIFTEEN_MINUTE&days=60"

# Today's live data (cached for 2 min, grows as new candles form)
curl -u admin:admin123 "http://localhost:9010/api/stocks/indicators/day?symbol=HDFCBANK-EQ&date=2026-10-30&interval=ONE_HOUR&days=60"
```

### Response
Array of snapshot rows, one per candle:
```json
[
  {
    "time": "2026-10-28T03:45:00Z",
    "timeIst": "2026-10-28 09:15",
    "candle": { "open": 681.00, "high": 685.50, "low": 679.20, "close": 683.40, "volume": 125430 },
    "momentum": { "rsi14": 29.13, "macd": { "line": -5.19, "signal": -4.90, "histogram": -0.29 } },
    "trend": { "adx14": 59.00, "supertrend": { "line": 688.72, "bullish": false } },
    "movingAverages": { "ema9": 682.77, "ema20": 687.06, "ema50": 695.69, "ema200": 708.10, "sma20": 687.57, "vwap": 675.68 },
    "volatility": { "atr14": 4.69, "bollingerBands": { "upper": 697.20, "middle": 687.57, "lower": 677.93 } },
    "signals": { "rsi": "OVERSOLD", "adx": "TRENDING", "trend": "BEARISH", "macd": "BEARISH", "bbPosition": "LOWER_HALF", "vwapPosition": "ABOVE" },
    "crossovers": { "ema9AboveEma20": false, "ema20AboveEma50": false, "priceAboveEma200": false, "priceAboveVwap": true },
    "marketStructure": { "bias": "BEARISH", "lastEvent": "CHOCH_BEARISH", "nearestSwingHigh": 695.50, "nearestSwingLow": 671.20 },
    "smartMoney": { "nearestBullishOB": null, "nearestBearishOB": { "top": 694.00, "bottom": 691.20, "direction": "BEARISH" }, "openFvgs": [], "buySideLiquidityAbove": 697.00, "sellSideLiquidityBelow": 668.50 },
    "pivotPoints": { "pivot": 680.57, "r1": 691.84, "r2": 698.37, "r3": 709.64, "s1": 674.04, "s2": 662.77, "s3": 656.24, "basedOn": "2026-10-27" }
  }
]
```

### Rows per interval
| Interval | Rows (NSE) | Rows (MCX crude) |
|---|---|---|
| `FIFTEEN_MINUTE` | 26 | ~85 |
| `THIRTY_MINUTE` | 13 | ~43 |
| `ONE_HOUR` | 7 | ~14 |
| `ONE_DAY` | 1 | 1 |

### Caching
- Past dates (`date < today`): cached **24 hours** in Redis + permanently in DB
- Today (`date == today`): cached **2 minutes** (refreshes as new candles form)

---

## 4. `GET /api/stocks/indicators/mtf`

Returns **multi-timeframe analysis** — the most powerful endpoint for trading decisions.

### When to use
- Before placing a trade — check if all timeframes agree
- Determine trade direction, entry zone, stop loss, targets
- Check market bias across 15m, 30m, 1h, 2h, 3h, 4h, 1d

### Parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `symbol` | Yes | — | Instrument symbol |
| `interval` | No | `ONE_HOUR` | Highest timeframe to include (determines TF stack) |
| `days` | No | `60` | Warmup history |
| `asOf` | No | today | `YYYY-MM-DD` for historical MTF analysis |

### Timeframe Stack by Interval

| `interval` param | Timeframes included |
|---|---|
| `FIFTEEN_MINUTE` | 15m |
| `THIRTY_MINUTE` | 15m + 30m |
| `ONE_HOUR` | 15m + 30m + 1h |
| `TWO_HOUR` | 15m + 30m + 1h + 2h |
| `THREE_HOUR` | 15m + 30m + 1h + 2h + 3h |
| `FOUR_HOUR` | 15m + 30m + 1h + 2h + 3h + 4h |
| `ONE_DAY` | 15m + 30m + 1h + 2h + 3h + 4h + 1d |

> **Note:** `TWO_HOUR`, `THREE_HOUR`, `FOUR_HOUR` are synthesized by aggregating 1h candles. No extra Angel API call.

### Example
```bash
# Full stack (all 7 TFs) — recommended for swing trading
curl -u admin:admin123 "http://localhost:9010/api/stocks/indicators/mtf?symbol=HDFCBANK-EQ&interval=ONE_DAY&days=60"

# Intraday stack (15m+30m+1h+2h+3h+4h) — for intraday trading
curl -u admin:admin123 "http://localhost:9010/api/stocks/indicators/mtf?symbol=HDFCBANK-EQ&interval=FOUR_HOUR&days=60"

# 3-TF intraday (15m+30m+1h) — quick intraday check
curl -u admin:admin123 "http://localhost:9010/api/stocks/indicators/mtf?symbol=RELIANCE-EQ&interval=ONE_HOUR&days=60"

# Crude Oil — full stack
curl -u admin:admin123 "http://localhost:9010/api/stocks/indicators/mtf?symbol=CRUDEOIL30OCT26FUT&interval=ONE_DAY&days=60"

# Historical
curl -u admin:admin123 "http://localhost:9010/api/stocks/indicators/mtf?symbol=HDFCBANK-EQ&interval=ONE_DAY&days=60&asOf=2026-10-28"
```

### Response
```json
{
  "symbol": "HDFCBANK-EQ",
  "requestedInterval": "ONE_DAY",
  "timeframes": {
    "FIFTEEN_MINUTE": { "momentum": {...}, "trend": {...}, "signals": {"trend": "BULLISH"}, ... },
    "THIRTY_MINUTE":  { "momentum": {...}, "trend": {...}, "signals": {"trend": "BULLISH"}, ... },
    "ONE_HOUR":       { "momentum": {...}, "trend": {...}, "signals": {"trend": "BEARISH"}, ... },
    "TWO_HOUR":       { "momentum": {...}, "trend": {...}, "signals": {"trend": "BEARISH"}, ... },
    "THREE_HOUR":     { "momentum": {...}, "trend": {...}, "signals": {"trend": "BEARISH"}, ... },
    "FOUR_HOUR":      { "momentum": {...}, "trend": {...}, "signals": {"trend": "BEARISH"}, ... },
    "ONE_DAY":        { "momentum": {...}, "trend": {...}, "signals": {"trend": "BEARISH"}, ... }
  },
  "conclusion": {
    "primaryBias": "BEARISH",
    "alignedTimeframes": ["ONE_HOUR", "TWO_HOUR", "THREE_HOUR", "FOUR_HOUR", "ONE_DAY"],
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
    "note": "SHORT setup (STRONG confidence). Higher TFs BEARISH but lower TFs in pullback. Wait for FIFTEEN_MINUTE + THIRTY_MINUTE SuperTrend to turn BEARISH. Stop: ₹690.8. Target 1: ₹671.2 (1:1.8)."
  }
}
```

### Reading the Conclusion

| `tradeSetup` | Meaning | Action |
|---|---|---|
| `TREND_CONTINUATION` | All TFs aligned → strong signal | Enter now in `primaryBias` direction |
| `WAIT_FOR_PULLBACK_END` | Higher TFs have bias, lower TFs going against | Wait for lower TFs to flip, then enter |
| `MIXED_SIGNALS` | Higher TFs disagree | Stay out, no clear bias |
| `NO_CLEAR_TREND` | No consistent bias anywhere | Stay out |

| `confidence` | Meaning |
|---|---|
| `STRONG` | All higher timeframes agree |
| `MODERATE` | Majority of higher TFs agree |
| `WEAK` | Only the highest TF is clear |

---

## Indicators Reference

### Momentum
| Indicator | Signal values | Reading |
|---|---|---|
| `rsi14` | `OVERSOLD` / `NEUTRAL` / `OVERBOUGHT` | < 30 oversold, > 70 overbought |
| `macd.histogram` | `BULLISH` / `BEARISH` | Positive = bullish momentum |

### Trend
| Indicator | Signal values | Reading |
|---|---|---|
| `adx14` | `RANGING` / `DEVELOPING` / `TRENDING` | > 25 = trend present, < 20 = ranging |
| `supertrend.bullish` | `BULLISH` / `BEARISH` | Direction of current trend |

### Moving Averages
| Field | What it is |
|---|---|
| `ema9/20/50/200` | Exponential moving averages — dynamic support/resistance |
| `sma20` | Simple 20-period average |
| `vwap` | Session VWAP — key intraday level (price above = bullish) |

### Volatility
| Indicator | Reading |
|---|---|
| `atr14` | Average daily range in ₹ — use for stop-loss sizing (1.5 × ATR) |
| `bollingerBands.upper/lower` | Volatility envelope — price outside bands = extreme move |

### Signals
| Field | Values | Meaning |
|---|---|---|
| `bbPosition` | `ABOVE_UPPER` / `UPPER_HALF` / `LOWER_HALF` / `BELOW_LOWER` | Price location within BB |
| `vwapPosition` | `ABOVE` / `BELOW` | Price vs session VWAP |

### Crossovers
| Field | Meaning |
|---|---|
| `ema9AboveEma20` | Short-term bullish momentum |
| `ema20AboveEma50` | Medium-term bullish trend |
| `priceAboveEma200` | Long-term bull market |
| `priceAboveVwap` | Intraday bullish bias |

### Market Structure (SMC)
| Field | Meaning |
|---|---|
| `bias` | `BULLISH` / `BEARISH` — overall structural direction |
| `lastEvent` | `BOS_BULLISH` = continuation, `CHOCH_BULLISH` = reversal |
| `nearestSwingHigh` | Nearest resistance above price |
| `nearestSwingLow` | Nearest support below price |

### Smart Money (SMC)
| Field | Meaning |
|---|---|
| `nearestBullishOB` | Nearest unmitigated bullish Order Block (support zone) |
| `nearestBearishOB` | Nearest unmitigated bearish Order Block (resistance zone) |
| `openFvgs` | Up to 5 unmitigated Fair Value Gaps near price |
| `buySideLiquidityAbove` | Stop-hunt target above price |
| `sellSideLiquidityBelow` | Stop-hunt target below price |

### Pivot Points
| Field | Meaning |
|---|---|
| `pivot` | Central pivot — above = bullish bias, below = bearish |
| `r1/r2/r3` | Resistance levels — where sellers may appear |
| `s1/s2/s3` | Support levels — where buyers may appear |
| `basedOn` | Previous trading day used for calculation |

---

## Caching Behaviour

| Layer | Survives restart | TTL |
|---|---|---|
| L1 JVM (ConcurrentHashMap) | No | Same as Redis TTL |
| L2 Redis | **Yes** | Past dates: 24h, today: 2 min |
| L3 DB (day breakdown only) | **Yes** | Permanent (past dates only) |

### Cache Admin
```bash
# View all cached entries
curl -u admin:admin123 "http://localhost:9010/api/admin/cache/indicators/stats"

# Clear one symbol
curl -u admin:admin123 -X POST "http://localhost:9010/api/admin/cache/indicators/clear/HDFCBANK-EQ"

# Clear all
curl -u admin:admin123 -X POST "http://localhost:9010/api/admin/cache/indicators/clear"

# Force recompute
curl -u admin:admin123 -X POST "http://localhost:9010/api/admin/cache/indicators/refresh?symbol=HDFCBANK-EQ&interval=ONE_DAY&days=300"
```

---

## Quick Decision Guide

```
Need price + indicators?
  YES → /api/stocks/quote
  NO  → /api/stocks/indicators  (faster)

Need hourly breakdown for one day?
  → /api/stocks/indicators/day?date=YYYY-MM-DD

Need trade decision (entry/stop/target)?
  → /api/stocks/indicators/mtf?interval=ONE_DAY

Scanning multiple stocks?
  → /api/stocks/indicators  (loop over symbols)

Crude Oil?
  Step 1: GET /api/instruments/futures?underlying=CRUDEOIL  (find symbol)
  Step 2: Use {{crudeSymbol}} in any endpoint above
```
