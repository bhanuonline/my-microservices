# Architecture

High-level design of the angle-app.

## Layered View

```
┌─────────────────────────────────────────────┐
│  Controllers (REST)                         │
│  AnalysisController, ApiController, ...     │
└────────────────┬────────────────────────────┘
                 │
┌────────────────▼────────────────────────────┐
│  Service Layer                              │
│  Backtester, MarketDataService              │
└────────────────┬────────────────────────────┘
                 │
     ┌───────────┼───────────┐
     │           │           │
┌────▼───┐  ┌────▼────┐ ┌────▼─────┐
│Strategy│  │Indicator│ │  Broker  │
│  (SMA) │  │  (SMA)  │ │(Angel...)│
└────────┘  └─────────┘ └──────────┘
```

## Core Concepts

### Broker abstraction
`BrokerClient` is a common interface. Each broker implementation (`AngelClient`,
`UpstoxClient`, `KiteClient`) implements it. `MarketDataService` picks the right
one based on a `broker` parameter.

### Strategy abstraction
`Strategy` interface takes candles and returns `Signal` (BUY / SELL / HOLD).
Strategies are Spring beans and can be swapped via `analysis.strategy.default`.

### Indicators
Small, reusable computations (moving averages, RSI, etc.). `Indicator` interface
+ implementations. Strategies compose indicators.

### Backtesting
`Backtester` runs a `Strategy` over historical `Candle` list and returns
`BacktestResult` (P&L, trades, drawdown).

## Data Flow

1. HTTP request → `AnalysisController`
2. Load candles → `NiftyFileLoader` (CSV) OR `MarketDataService` (live broker)
3. Run strategy → `Strategy.evaluate(candles)`
4. Backtest → `Backtester.run(strategy, candles)`
5. Return `BacktestResult` as JSON

## Key Design Decisions

- **Broker-agnostic core** — strategies and backtester never touch broker APIs directly
- **Config-driven** — strategy periods, broker credentials all in `application.properties`
- **Secrets via env vars** — no hardcoded API keys
- **Interface-first** — every capability has an interface + at least one impl
