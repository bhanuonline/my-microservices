# Backtesting

Running strategies over historical data.

## Trigger

```bash
curl http://localhost:9010/api/analysis/backtest
```

Uses:
- Data: `src/main/resources/nifty/nifty-daily-candles.csv`
- Strategy: whatever `analysis.strategy.default` points to

## Flow

1. `NiftyFileLoader` reads the CSV into `List<Candle>`
2. `Backtester.run(strategy, candles)` iterates candles chronologically
3. On each candle, `strategy.evaluate(candles, i)` returns a `Signal`
4. Backtester simulates entering/exiting positions
5. Returns `BacktestResult`

## BacktestResult Fields

| Field | Meaning |
|-------|---------|
| `totalTrades` | Total round-trips (entry + exit) |
| `winningTrades` | Trades closed with profit |
| `losingTrades` | Trades closed with loss |
| `totalPnL` | Sum of realized P&L |
| `winRate` | winningTrades / totalTrades |

(Extend `BacktestResult.java` to add Sharpe ratio, max drawdown, etc.)

## Assumptions

- One position at a time
- Enter on close of signal candle
- No slippage / no commission modeled by default
- Daily candles

## Extending

- **Position sizing**: currently 1 unit; add capital + risk-per-trade in `Backtester`
- **Commission**: subtract per-trade fee in P&L calc
- **Slippage**: apply % adjustment to entry/exit price
- **Multiple positions**: track a list of open positions instead of a single one
- **Stop loss / target**: check on each candle before checking signal

## Custom Data

Point `analysis.nifty.data-file` at any CSV with columns:

```
date,open,high,low,close,volume
2026-01-01,22000,22150,21980,22100,1250000
```
