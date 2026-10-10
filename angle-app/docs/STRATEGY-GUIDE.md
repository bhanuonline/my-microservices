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
