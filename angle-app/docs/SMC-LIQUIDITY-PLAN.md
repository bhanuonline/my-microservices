# SMC + Liquidity — Requirements & Build Agenda

Draft plan before writing any code. Read this end-to-end, then we'll pick
what to build first.

---

## 1. What we're building (in plain English)

**Smart Money Concepts (SMC)** is a trading style based on the idea that
institutional traders (banks, hedge funds, market makers) leave predictable
footprints on the chart. Retail traders can spot those footprints and trade
alongside them.

The two big themes:

- **Market structure** — is the market making higher highs / lower lows?
  When does that pattern break? When does it change character?
- **Liquidity** — where are the stop-loss orders piled up? Institutional
  traders push price into those pools, trigger the stops, then reverse.
  Retail traders can spot the "liquidity sweep" and reverse with them.

Once we can detect those patterns from candle data, we can build strategies
that trade them — the same plug-in `Strategy` interface we already have.

---

## 2. Vocabulary — the terms we'll implement

We need to detect and label each of these on any candle series.

### Market structure

| Term | Definition | What we compute |
|------|-----------|-----------------|
| **Swing High (SH)** | A candle whose high is higher than N candles on each side | Boolean flag per candle |
| **Swing Low (SL)** | A candle whose low is lower than N candles on each side | Boolean flag per candle |
| **BOS** (Break of Structure) | Close beyond the previous swing high (bullish BOS) or swing low (bearish BOS) — continuation | Event with direction |
| **CHoCH** (Change of Character) | First BOS in the opposite direction of the prior trend — trend reversal | Event with direction |
| **HH / HL / LH / LL** | Higher High / Higher Low / Lower High / Lower Low classification of each new swing | Label per swing |

### Liquidity

| Term | Definition | What we compute |
|------|-----------|-----------------|
| **BSL** (Buy-Side Liquidity) | Stops above swing highs and equal highs — where shorts have stops | Price level list |
| **SSL** (Sell-Side Liquidity) | Stops below swing lows and equal lows — where longs have stops | Price level list |
| **Equal Highs / Equal Lows** | Two or more swing points within a tiny tolerance | Grouped level with count |
| **Liquidity Sweep** | Price wicks above BSL (or below SSL) then closes back inside | Event flagging the level and direction |
| **Inducement** | A minor liquidity pool taken *before* the real reversal | Optional — advanced |

### Institutional footprints

| Term | Definition | What we compute |
|------|-----------|-----------------|
| **Order Block (OB)** | Last opposing candle before a strong displacement | Zone (open→close range) with direction |
| **Fair Value Gap (FVG)** | 3-candle imbalance where candle 1's wick doesn't overlap candle 3's wick | Zone (top, bottom) |
| **Breaker Block** | An order block that failed and price broke through it | Zone with "broken" flag |
| **Mitigated / Unmitigated** | Whether price has revisited the zone yet | Boolean per zone |

### Optional (later phases)

- **Premium / Discount zones** — mark the top half of the last leg (premium) vs bottom half (discount)
- **Session liquidity** — Asian range high/low, London range, NY range
- **Displacement** — measure of impulsive candles for confirmation

---

## 3. Data we already have vs what's missing

### ✅ Have

- `Candle` — OHLCV with timestamp, `BigDecimal` prices
- `Interval` — timeframe enum
- `Strategy` interface — where SMC strategies will live
- `Indicator` interface — reusable computations
- Broker/CSV data pipeline — feed for any timeframe

### ❌ Missing

- Swing detection utility
- Structure state machine (tracks HH/HL/LH/LL and emits BOS/CHoCH events)
- Zone types (`OrderBlock`, `FairValueGap`, `LiquidityLevel`) — need new value objects
- Multi-timeframe alignment (SMC is heavily HTF-biased — e.g. trade M15 in the direction of H4 structure)
- Backtester extensions — current backtester tracks P&L; SMC strategies need to also track zone touches, sweeps, etc.

---

## 4. Building blocks — in dependency order

Each layer feeds the next. Build bottom-up.

```
┌─────────────────────────────────────────────────────┐
│  Layer 5: Strategies (SMC + Liquidity trading rules)│
├─────────────────────────────────────────────────────┤
│  Layer 4: Structure + zone state machine            │
├─────────────────────────────────────────────────────┤
│  Layer 3: Zone detectors (OB, FVG, sweep)           │
├─────────────────────────────────────────────────────┤
│  Layer 2: Structure primitives (BOS, CHoCH)         │
├─────────────────────────────────────────────────────┤
│  Layer 1: Swing detection                           │
├─────────────────────────────────────────────────────┤
│  Layer 0: Candle (exists) + timeframe (exists)     │
└─────────────────────────────────────────────────────┘
```

### Layer 1 — Swing detection

**What:** Given `List<Candle>`, return list of swing points.
**Parameters:** `lookback` (how many candles on each side must be lower/higher).
**Output:** `List<SwingPoint(index, timestamp, price, type=HIGH|LOW)`.
**Complexity:** small; O(n × lookback).

### Layer 2 — Structure primitives

**What:** Walk swing points chronologically, classify each as HH/HL/LH/LL.
Emit BOS/CHoCH events when structure breaks/reverses.
**Output:** `List<StructureEvent(index, type=BOS|CHoCH, direction=BULLISH|BEARISH)`.
**Depends on:** Layer 1.

### Layer 3 — Zone detectors

**Order Block:** For each BOS, walk back to the last opposing candle. Record its OHLC range as an OB.
**Fair Value Gap:** For every 3 consecutive candles, check if candle 1's high < candle 3's low (bullish FVG) or candle 1's low > candle 3's high (bearish FVG).
**Liquidity Sweep:** Detect wicks that pierce a known BSL/SSL level and close back inside within N candles.
**Output:** `List<Zone(top, bottom, formedAtIndex, direction, kind=OB|FVG|SWEEP)`.

### Layer 4 — Structure + zone state machine

Combines events into a **running state**:
- Current trend bias (bullish / bearish / ranging)
- Active (unmitigated) OBs and FVGs
- Untouched liquidity levels
- Broken structure history

This state is what strategies actually query on each candle:
> "Is HTF trend bullish AND has price just tapped an unmitigated bullish OB in the discount zone?"

### Layer 5 — Strategies

Two initial strategies to prove the plumbing works:

**Strategy A — OB Retest:**
- Wait for a BOS
- Identify the OB that caused it
- Wait for price to return to that OB
- Enter in the direction of the BOS
- Stop below OB, target next swing high/low

**Strategy B — Liquidity Sweep + FVG:**
- Wait for a liquidity sweep (price grabs SSL and closes back up)
- Look for an FVG on the resulting move
- Enter when price retests the FVG
- Stop below the sweep low, target next BSL

---

## 5. Design decisions we need to make now

Answer these before coding — each choice ripples through the design.

### 5.1 Swing lookback: fixed or configurable?

- **Fixed at 3** (fractal-style) — simple, catches minor swings
- **Configurable** (e.g. `smc.swing.lookback=5`) — flexible, more code paths
- **Recommendation:** configurable, default 3

### 5.2 Multi-timeframe: same request or two requests?

SMC almost always uses higher-timeframe bias (e.g. H4) with lower-timeframe entries (M15). Options:

- **Two candle series in the strategy** — pass HTF + LTF `List<Candle>`
- **Compute HTF on-the-fly** — resample LTF candles to HTF inside the strategy
- **Recommendation:** two series parameter for now; resample later if it's annoying

### 5.3 Zone lifecycle: mutable or immutable?

An `OrderBlock` becomes "mitigated" once price returns to it. Two ways:

- **Mutable** — `orderBlock.setMitigated(true)` when detected
- **Immutable with a state machine** — return new OB with new state
- **Recommendation:** mutable for now (simpler); refactor to events + immutable state if it grows

### 5.4 Where does state live?

- **Per-candle recomputation** — pass full candle history every call, recompute
- **Streaming state** — maintain a `StructureContext` that updates on each new candle
- **Recommendation:** per-candle recomputation for backtesting (matches current pattern); streaming for live later

### 5.5 Backtester upgrades needed?

Current backtester just tracks P&L. SMC strategies produce **more signals** than just BUY/SELL:
- "Waiting for retest"
- "Entered at OB"
- "Stopped out at swing low"
- "Target hit at BSL"

Two options:
- **Extend `Signal` enum** — add ENTER_LONG, ENTER_SHORT, EXIT_STOP, EXIT_TARGET
- **Return a richer object** — `TradeIntent(action, priceLevel, stop, target, rationale)`
- **Recommendation:** richer object — needed for realistic stops/targets

### 5.6 Configuration surface

`application.properties` additions we'll likely need:

```properties
smc.swing.lookback=3
smc.liquidity.equal-highs.tolerance-pct=0.05
smc.fvg.min-size-pct=0.10
smc.zone.max-age-candles=200
smc.htf.default-interval=ONE_HOUR
```

---

## 6. Agenda — phased build plan

Suggested order, each phase ships something testable end-to-end.

### Phase 1 — Foundations (Layer 1 + 2)
**Deliverable:** `/api/analysis/structure?...` returns swings + BOS/CHoCH events for any candle series.
- `marketstructure/model/SwingPoint.java` (record)
- `marketstructure/model/StructureEvent.java` (record)
- `marketstructure/SwingDetector.java`
- `marketstructure/StructureAnalyzer.java`
- `controller/AnalysisController.java` — new `/structure` endpoint
- Tests on the local Nifty CSV
**Estimate:** ~200 lines of code

### Phase 2 — Zone detectors (Layer 3)
**Deliverable:** `/api/analysis/zones?...` returns all detected OBs, FVGs, sweeps.
- `marketstructure/model/Zone.java`, `OrderBlock.java`, `FairValueGap.java`, `LiquidityLevel.java`
- `marketstructure/OrderBlockDetector.java`
- `marketstructure/FvgDetector.java`
- `marketstructure/LiquidityDetector.java`
- `/zones` endpoint
**Estimate:** ~300 lines

### Phase 3 — Combined state (Layer 4)
**Deliverable:** `/api/analysis/context?...` returns "current market state" — trend bias, active zones, liquidity map.
- `marketstructure/MarketContext.java` — the state carrier
- `marketstructure/ContextBuilder.java` — assembles context from Layers 1-3
- `/context` endpoint
**Estimate:** ~200 lines

### Phase 4 — First SMC strategy (Layer 5)
**Deliverable:** `Backtester` runs `ObRetestStrategy`, produces trades with stops/targets.
- Extend `Signal` or introduce `TradeIntent`
- `strategy/impl/ObRetestStrategy.java`
- Backtester changes to honour stops/targets
- Add strategy name to config
**Estimate:** ~250 lines

### Phase 5 — Liquidity strategy
**Deliverable:** Second strategy that trades sweep + FVG entries.
- `strategy/impl/LiquiditySweepFvgStrategy.java`
- Multi-timeframe candle loading (HTF bias)
**Estimate:** ~250 lines

### Phase 6 — Frontend / visualisation (optional)
**Deliverable:** Simple candlestick chart in the React frontend that overlays detected zones + labels.
- Uses existing `/candles` + new `/zones` endpoints
- Lightweight-charts or similar library
**Estimate:** 1 day, non-Java

---

## 7. What we're NOT building (scope guard)

To avoid scope creep, we explicitly **skip** these for now:

- ❌ Order execution — signals only, no live orders
- ❌ Inducement / IPDA / SMT divergences (advanced ICT concepts)
- ❌ Volume profile / order flow (needs tick data, not candles)
- ❌ Options-specific SMC (options don't reflect equity SMC cleanly)
- ❌ ML pattern classification — pure rule-based only
- ❌ Alerts / notifications
- ❌ UI editing of parameters — properties file only

Add any of these to a follow-up phase if needed.

---

## 8. Success criteria

We know Phase 1 is done when:

- `curl /api/analysis/structure?...` returns a list of swings and events
- The response for the local Nifty CSV **visually matches** what you'd draw on TradingView with the same lookback (spot-check 3-5 examples)
- Unit tests cover: single swing, back-to-back swings, BOS, CHoCH, no swings

Same idea for each subsequent phase — bar for "done" is
**"the output matches what a human would mark on the chart."**

---

## 9. Open questions for you

Before we start Phase 1, decide:

1. **Which timeframe do you want to trade?** (M15, H1, H4, D1?)
   Drives what data we backtest on.

2. **What's the target instrument?** (Nifty index? Nifty futures? A single stock like Reliance?)
   Options aren't a great fit for pure SMC — the premium series behaves differently.

3. **Any specific SMC teacher's rules you want to follow?** (ICT, Photon Trading, TDG?)
   Vocabulary varies subtly. Pick one style to keep the code consistent.

4. **Live paper trading later, or backtest-only for now?**
   Live needs the streaming state machine (5.4 above).

5. **Swing lookback default — 3 or 5?**
   3 catches every micro swing; 5 filters to more significant ones.

---

## Next step

Read this, tell me:
- Anything unclear or wrong in the vocabulary?
- Which of the 5 open questions in section 9?
- Any of section 7 you'd actually like included?
- Green light on Phase 1?

Once you sign off, we start with Phase 1 (Swing + Structure detection) — that's the smallest independently-useful deliverable and unblocks everything else.
