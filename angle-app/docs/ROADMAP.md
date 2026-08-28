# Roadmap

Planned direction for angle-app.

## Now (In Progress)

- Angel One SmartAPI: complete order placement + positions API
- Backtester: add max drawdown, Sharpe ratio to `BacktestResult`
- More indicators: RSI, MACD, Bollinger Bands
- Persist backtest runs to a database for later comparison

## Next

- Upstox client — full implementation (currently stub)
- Kite client — full implementation (currently stub)
- Live paper trading mode (signals → simulated fills, no real orders)
- Strategy comparison endpoint: run N strategies over same data, return ranking
- Multi-timeframe strategies (e.g. daily + 5m together)
- Custom data upload: POST a CSV, get a backtest response

## Later

- Real-time signal engine (WebSocket feed → strategy → alert)
- Web dashboard: chart + strategy overlay + trade markers
- Portfolio-level backtesting across multiple instruments
- ML-based strategies (feature extraction + classification)
- Options strategies (spreads, straddles)
- Alert channels: Telegram, email, SMS
- Order execution safety: kill switch, position limits, daily loss cap

## Ideas / Not Committed

- Broker order routing (send to best-priced broker)
- Backtest replay UI (step through candles interactively)
- Community strategy marketplace
- Mobile app

## Non-Goals

- HFT / sub-millisecond execution — this is retail-grade
- US markets — focused on NSE/BSE
- Auto-execution without user review (regulatory + risk reasons)

## Contributing

Open to PRs on any "Now" or "Next" item. See [CONTRIBUTING.md](CONTRIBUTING.md).
