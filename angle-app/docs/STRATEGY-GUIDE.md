# Strategy Guide

Two parts:
1. The 8 strategies currently shipped + how consensus voting works.
2. How to add a new strategy.

---

## The 8 shipped strategies

### 1. `moving-average-crossover`

EMA9 crosses EMA20. BUY when fast crosses above slow with uptrend
confirmation; SELL on bearish cross.

- Target: previous swing high / low × fib extension.
- Stop: below crossover bar's low / above its high.
- Best in trending markets.

### 2. `rsi-mean-reversion`

RSI < 30 → BUY (oversold reversal). RSI > 70 → SELL (overbought reversal).

- Needs: 2 consecutive bars confirming the extreme.
- Target: midline (RSI=50) in price terms.
- Stop: last swing extreme.
- Best in range-bound markets.

### 3. `macd-crossover`

MACD line crosses signal line.

- MACD above zero + bullish cross = BUY.
- MACD below zero + bearish cross = SELL.
- Target: 2× stop distance.
- Stop: recent swing.

### 4. `ob-retest` (SMC)

Price retests an Order Block (institutional candle before a strong move).

- Detect OB: last opposite candle before 3+ consecutive directional
  candles.
- Entry: on retest of OB zone.
- Stop: beyond the OB wick.
- Target: next liquidity pool.

### 5. `sweep-fvg` (SMC)

Price sweeps liquidity (takes out a swing high/low) then returns to a Fair
Value Gap left during the sweep.

- Detect sweep: wick breaks swing, body closes back.
- Detect FVG: 3-bar imbalance left during the sweep bar.
- Entry: on return to FVG.
- Stop: beyond sweep wick.
- Target: previous consolidation range.

### 6. `volume-breakout`

Price breaks N-bar range AND volume is M× the 20-bar average.

- Default: 20-bar range, 1.5× volume multiplier.
- Target: 2× range width.
- Stop: back inside range.
- Best when volume confirms the breakout; filters false breakouts.

### 7. `bollinger-bounce`

Price pierces Bollinger Band, reversal candle inside the band = mean-
reversion signal.

- Period 20, 2 std-dev.
- Entry: close back inside band after piercing.
- Target: middle band.
- Stop: reversal wick ± buffer %.

### 8. `ensemble`

Not a direct signaler. Runs all above and surfaces consensus.
Excluded from the consensus vote itself (would double-count).

---

## Consensus voting

```
At each candle close, SignalMarkerService.consensusMarkers() runs:

  perStrategy = { name → List<TradeIntent> }
  for each strategy in CONSENSUS_MEMBERS:   // 7 strategies, ensemble excluded
    perStrategy[name] = strategy.evaluate(candles)

  for each bar i:
    longs  = strategies that voted ENTER_LONG at bar i
    shorts = strategies that voted ENTER_SHORT at bar i

    if longs.size >= minAgreement:
      emit marker with action=LONG, agreedStrategies=longs
    elif shorts.size >= minAgreement:
      emit marker with action=SHORT, agreedStrategies=shorts

    Entry/stop/target are aggregated from all agreeing strategies:
      entry  = average of their entries
      stop   = WIDEST of their stops (safest)
      target = NEAREST of their targets (most conservative)
```

- Default `signals.detector.min-agreement=3`.
- Raise to 4 or 5 for fewer, higher-quality signals.
- Each strategy produces either `ENTER_LONG`, `ENTER_SHORT`, or null per bar.

---

## Downstream filters

After consensus fires, further gates can block the signal:

| Filter | Service | Check |
|---|---|---|
| Dedupe | SignalService | Same symbol+action within last N min? |
| Regime | RegimeService | Time-of-day + VIX + ADX regime allowed? |
| MTF | MtfConfirmationService | Higher TFs agree on direction? |
| News | NewsBlackoutService | Inside RBI/FOMC/earnings window? |

Only signals that pass ALL enabled gates reach `signalService.save()`.

---

## Trailing stops

After entry, `LiveSignalMonitor` applies trailing on every tick:

1. Update high-water mark (peak for BUY, trough for SELL).
2. Compute progress toward target.
3. If progress ≥ `trailing.activation-percent`, compute candidate stop:
   - FIXED → peak ± N pts
   - PERCENT → peak × (1 ± N%)
   - MILESTONE → ladder lookup
   - ATR → peak ± K × ATR14
4. Only apply if new stop is BETTER than current (ratchet).
5. Never trail past current price (would auto-close).

---

## Adding a new strategy

### 1. Create the class

```java
package com.angle.trading.strategy;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.strategy.model.IntentAction;
import com.angle.trading.strategy.model.TradeIntent;

import java.util.ArrayList;
import java.util.List;

public class MyStrategy implements Strategy {

    @Override
    public String name() {
        return "my-strategy";
    }

    @Override
    public List<TradeIntent> evaluate(List<Candle> candles) {
        List<TradeIntent> out = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            // Use ONLY candles[0..i] for your analysis — no look-ahead!
            if (i < someLookback) { out.add(null); continue; }

            // Your logic
            boolean bullish = ...;
            if (bullish) {
                out.add(new TradeIntent(
                        IntentAction.ENTER_LONG,
                        candles.get(i).close(),       // entry
                        candles.get(i).low(),         // stop
                        candles.get(i).close().multiply(BigDecimal.valueOf(1.01)),  // target
                        name(),
                        "my-strategy rationale"));
            } else {
                out.add(null);
            }
        }
        return out;
    }
}
```

### 2. Register in `StrategyRegistry`

Add to the `@PostConstruct` registration list, or expose as a `@Component`.

### 3. Add to consensus (optional)

Edit `SignalMarkerService.CONSENSUS_MEMBERS` to include your strategy's
name. It will participate in the next consensus vote.

### 4. Backtest

```
POST /api/backtest/pipeline?symbolToken=...&minAgreement=3
```

Run before and after adding your strategy. Compare win rate, expectancy,
max drawdown.

### 5. Add tests

See `strategy/MyStrategyTest.java`. Test with fixed candle sequences:
- Known-bullish setup → expect ENTER_LONG at specific bar.
- Known-bearish setup → expect ENTER_SHORT.
- Choppy data → mostly null.

### 6. Document

- Add a section to this file.
- Add defaults to `application.properties` if your strategy has knobs.
- Mention in `FEATURES.md` under "8 strategies" (bump to 9).

---

## Hedge strategies

Implemented in `com.angle.trading.hedge`. These are **position-hedging**
strategies for an existing long-stock holding — distinct from the intraday
signal strategies above.

### Indicator inputs (daily candles)

| Indicator | Period | Role |
|---|---|---|
| RSI | 14 | Momentum strength |
| ADX | 14 | Trend strength (directional or ranging) |
| EMA | 21 | Trend direction (is price above/below?) |
| HV30 | 30-day | Historical vol, annualised (√252), used for premium assessment |

### Market regimes

Derived by combining RSI, ADX, and price-vs-EMA:

| Regime | Typical conditions |
|---|---|
| `STRONG_BULLISH` | RSI > 60, ADX > 25, price above EMA-21 |
| `MILD_BULLISH` | RSI 50–60, moderate ADX, price above EMA-21 |
| `SIDEWAYS` | RSI ~50, ADX < 20, price near EMA-21 |
| `MILD_BEARISH` | RSI 40–50, price below EMA-21 |
| `STRONG_BEARISH` | RSI < 40, ADX > 25, price below EMA-21 |

---

### Strategy 1: Protective / Married PUT

Buy a PUT option to insure a long stock position. Cost of insurance = PUT
premium.

```
Breakeven  = entryPrice + putPremium
Max loss   = (entryPrice - putStrike + putPremium) × qty
Max gain   = unlimited (uncapped upside)
```

The PUT strike sets the loss floor. Lower strike = cheaper premium, lower floor.

Best regime score: `STRONG_BEARISH`, `MILD_BEARISH` (insurance is most valuable).

---

### Strategy 2: Covered Call

Sell a CALL against the long stock position to collect premium. Upside is capped
at the CALL strike.

```
Breakeven  = entryPrice - callPremium
Max gain   = (callStrike - entryPrice + callPremium) × qty
Max loss   = (entryPrice - callPremium) × qty  [stock → 0, effectively]
```

Call premium reduces effective cost basis and adds a downside cushion equal to
the premium received.

Best regime score: `SIDEWAYS`, `MILD_BULLISH` (premium income offsets flat/slow
market; upside cap is less likely to bite).

---

### Strategy 3: Collar

Combine a long PUT (downside floor) and a short CALL (upside cap). Net premium
can be zero or close to zero (zero-cost collar).

```
Net premium = callPremium - putPremium    (positive = net credit)
Breakeven   = entryPrice - net
Max loss    = (entryPrice - putStrike - net) × qty
Max gain    = (callStrike - entryPrice + net) × qty
```

Zero-cost achieved when callPremium ≈ putPremium. Trade-off: both floor and
ceiling are fixed.

Best regime score: `SIDEWAYS`, `MILD_BEARISH` (controlled risk both ways with
minimal cost).

---

### Strategy 4: Futures Hedge

Short a same-underlying futures contract against the long stock position.
Effectively delta-neutral: stock gains offset futures losses (and vice versa).
Net profit ≈ cost-of-carry (basis).

```
Profit      ≈ (futuresPrice - spotPrice) × qty   [as basis converges on expiry]
Max gain    ≈ basis if basis > 0 (futures in contango)
Max loss    ≈ basis if basis < 0 (backwardation)
Breakeven   ≈ futuresPrice
```

Not suitable for directional positions. Useful when you want to hold the stock
(dividends, corporate actions) but temporarily neutralise market risk.

Best regime score: `STRONG_BEARISH` (full hedge), `STRONG_BULLISH` (lower score
— foregoes upside).

---

### P&L scenario table (example)

Position: 100 shares, entry ₹620, Protective PUT strike ₹560, PUT premium ₹11.50.

| Stock price | Change | Strategy P&L |
|---|---|---|
| ₹496 | −20% | −₹18,750 (floor at PUT strike) |
| ₹558 | −10% | −₹18,750 (floor — below PUT strike) |
| ₹589 | −5% | −₹4,250 |
| ₹620 | 0% | −₹1,150 (premium paid) |
| ₹651 | +5% | +₹1,850 |
| ₹682 | +10% | +₹4,850 |
| ₹744 | +20% | +₹10,850 |

Max loss is bounded regardless of how far the stock falls. P&L above breakeven
(₹631.50) is net positive.

---

### Regime scoring table

Score 1 = poor fit, 5 = best fit for the regime.

| Strategy | STRONG_BULLISH | MILD_BULLISH | SIDEWAYS | MILD_BEARISH | STRONG_BEARISH |
|---|---|---|---|---|---|
| Protective PUT | 2 | 3 | 3 | 4 | 5 |
| Covered Call | 3 | 5 | 5 | 3 | 1 |
| Collar | 2 | 3 | 5 | 4 | 3 |
| Futures Hedge | 1 | 2 | 3 | 4 | 5 |

The `/hedge/compare` response includes the numeric `score` per strategy so
the UI can rank and highlight the best fit for the detected regime.

---

## Common pitfalls

- **Look-ahead bias**: using future candles in `evaluate()`. The backtest
  WILL show 100% win rate and the live system WILL lose money. Fix:
  always index with `<= i`.
- **Division by zero**: BigDecimal.divide without rounding mode on edge
  cases. Use `RoundingMode.HALF_UP, scale=4` as a default.
- **Returning wrong-size list**: `evaluate()` must return exactly
  `candles.size()` elements (null for "no signal at that bar"). Consensus
  voter will skip strategies whose output size doesn't match.
- **Stop below previous low (BUY)**: trailing stops ratchet upward only;
  if you set stop too close to entry you'll get stopped by the entry
  bar's own wick. Add a small buffer.
