# Features

Complete inventory of what's built in `angle-app`. Scannable reference —
find any capability by section, then jump to the linked deep-dive doc.

For the "why" behind these, see [ARCHITECTURE.md](ARCHITECTURE.md).
For hands-on flows, see [ONBOARDING.md](ONBOARDING.md) and
[LIVE-TRADING-GUIDE.md](LIVE-TRADING-GUIDE.md).

---

## 1. Infrastructure

| # | Item | Notes |
|---|------|-------|
| 1 | `AngleAppApplication` | Spring Boot entry point |
| 2 | Server port `9010` | Configured in `application.properties` |
| 3 | `nosec` Spring profile | Disables security config for local dev |
| 4 | IntelliJ run configs | AngleApp (secure) + AngleApp (nosec) under `.run/` |
| 5 | `FilterConfig` | Keeps `TraditionalFilter` registered even when security is disabled |
| 6 | Angel logout on shutdown | `@PreDestroy` in `AngelAuthService` closes broker session cleanly |
| 7 | Global `RestClient` config | Separate clients for broker calls (15s) and master file (2 min + gzip) |

---

## 2. Broker layer

| # | Item | Notes |
|---|------|-------|
| 8 | `Exchange` enum | NSE, BSE, NFO, BFO, MCX, CDS |
| 9 | `BrokerClient` interface | Common contract for every broker |
| 10 | `AngelClient` | Live SmartAPI integration (TOTP login, candles, headers) |
| 11 | `AngelAuthService` | JWT caching + auto-refresh + logout on shutdown |
| 12 | `TotpGenerator` | RFC 6238 TOTP (matches Google Authenticator) |
| 13 | `KiteClient`, `UpstoxClient` | Stubs, ready to implement |
| 14 | `MarketDataService` | Routes candle requests to the right broker by name |
| 15 | `BrokerProperties` | Config binding for every broker's credentials |

**Deep dive:** [BROKER-INTEGRATION.md](BROKER-INTEGRATION.md)

---

## 3. Instrument master

| # | Item | Notes |
|---|------|-------|
| 16 | `InstrumentMasterService` | Downloads Angel scrip master; 20h disk cache; async load |
| 17 | `Instrument` record | Typed accessors: `expiryDate()`, `strikeValue()`, `optionType()`, `lotSizeInt()` |
| 18 | `OptionType` enum | CE / PE |
| 19 | `InstrumentController` | 8 REST endpoints for lookup, option chain, futures |
| 20 | Case-insensitive expiry parser | Handles Angel's `15SEP2026` all-caps format |
| 21 | `isFuture()` / `isOption()` | Prefix match — recognises FUTIDX, FUTSTK, FUTCOM, FUTCUR, OPTIDX, OPTSTK, OPTCUR |

---

## 4. Indicators

| # | Item | Notes |
|---|------|-------|
| 22 | `Indicator` interface | Contract: `List<BigDecimal> compute(candles)` |
| 23 | `SimpleMovingAverage` | Existing |
| 24 | `ExponentialMovingAverage` | SMA-seeded, standard EMA formula |
| 25 | `RelativeStrengthIndex` | Wilder's smoothing, matches TradingView |
| 26 | `MACD` | With `MacdValue` record for line + signal + histogram |

---

## 5. Strategies

| # | Item | Notes |
|---|------|-------|
| 27 | `Strategy` interface | Returns `List<TradeIntent>` |
| 28 | `TradeIntent` record | Action, entry, stop, target, rationale |
| 29 | `IntentAction` enum | ENTER_LONG, ENTER_SHORT, EXIT, HOLD |
| 30 | `ExitReason` enum | STOP, TARGET, SIGNAL_EXIT, END_OF_SERIES |
| 31 | Shared `Trade` record | Used by Backtester + PaperOrderBook |
| 32 | `MovingAverageCrossover` | SMA short/long crossover — signal-driven exits |
| 33 | `RsiMeanReversion` | RSI oversold/overbought crossings |
| 34 | `MacdCrossover` | MACD line / signal line crossings |
| 35 | `OrderBlockRetestStrategy` | SMC — enter on OB touch in direction of bias, stop at OB edge |
| 36 | `LiquiditySweepFvgStrategy` | SMC — enter on FVG retest after liquidity sweep |
| 37 | `EnsembleStrategy` | Combines all 5 with tunable `minAgreement` + SMC fallback |
| 38 | `StrategyRegistry` | Auto-collects every `Strategy` bean, look up by name |

**Deep dive:** [STRATEGY-GUIDE.md](STRATEGY-GUIDE.md)

---

## 6. SMC (Smart Money Concepts) pipeline

| # | Item | Notes |
|---|------|-------|
| 39 | `SwingDetector` | Configurable lookback (default 3), strict comparison |
| 40 | `StructureAnalyzer` | Walks swings; emits BOS + CHoCH events |
| 41 | `SwingPoint`, `StructureEvent`, `Direction`, `StructureEventType` | Records/enums |
| 42 | `OrderBlockDetector` | Last opposing candle before BOS; tracks mitigation |
| 43 | `FvgDetector` | 3-candle imbalance; tracks mitigation |
| 44 | `LiquidityDetector` | BSL / SSL levels; sweep detection (wick + close-back-inside) |
| 45 | `OrderBlock`, `FairValueGap`, `LiquidityLevel`, `LiquiditySweep`, `LiquiditySide` | Zone records |
| 46 | `MarketContextBuilder` | Combines everything as-of any candle; safe for backtesting |
| 47 | `MarketContext` | Queryable snapshot with `nearestBullishOBBelow(price)` etc. |

**Deep dive:** [SMC-LIQUIDITY-PLAN.md](SMC-LIQUIDITY-PLAN.md)

---

## 7. Backtesting

| # | Item | Notes |
|---|------|-------|
| 48 | `Backtester` | Long + short + stops + targets + signal exits + force-close at series end |
| 49 | `BacktestResult` | Total trades, winners, losers, net P&L, full trade log |
| 50 | Auto-exit rules | Stop wins if stop+target hit in same candle (conservative) |

**Deep dive:** [BACKTESTING.md](BACKTESTING.md)

---

## 8. Analyst

| # | Item | Notes |
|---|------|-------|
| 51 | `AnalystService` | Runs all strategies, aggregates consensus, produces recommendation |
| 52 | `AnalystReport` | Instrument, market summary, per-strategy signals, consensus, recommendation |
| 53 | Consensus rules | Majority wins, MINIMAL = 1 agreeing (skip), STRONG = 4+ |
| 54 | Stop/target priority | SMC strategies first, then indicators, then MarketContext fallback |

---

## 9. Paper trading

| # | Item | Notes |
|---|------|-------|
| 55 | `CandleSource` interface | Contract for pushing candles into a session |
| 56 | `HistoricalReplayCandleSource` | Replays bundled Nifty CSV at N cps |
| 57 | `AngelLiveCandleSource` | Live Angel polling with warmup + per-exchange market hours |
| 58 | `AngelHistoricalReplayCandleSource` | Fetches Angel history for a date range, replays at speed |
| 59 | Per-exchange market hours | NSE 09:15–15:30, MCX 09:00–23:30, CDS 09:00–17:00 |
| 60 | `PaperOrderBook` | Thread-safe open position + trade log |
| 61 | `PaperPosition` record | Direction, entry, stop, target, rationale |
| 62 | `PaperTradingSession` | Orchestrator per session |
| 63 | Console output | `OPEN`/`CLOSE` at INFO with P&L; periodic status every 50 candles |
| 64 | `PaperTradingSessionManager` | Multi-session in-memory registry |
| 65 | `PaperTradingController` | REST endpoints: create / list / get / stop |
| 66 | `PaperAutostartService` | Boot-time session creation via `paper.autostart.*` config |
| 67 | `SessionSnapshot` | GET response: candles, trades, open position, P&L |

**Deep dive:** [LIVE-TRADING-GUIDE.md](LIVE-TRADING-GUIDE.md)

---

## 10. REST endpoints

### Analysis
| Method | URL | Purpose |
|--------|-----|---------|
| GET | `/api/analysis/backtest` | Backtest a strategy on the CSV |
| GET | `/api/analysis/backtest?strategy=ensemble&minAgreement=3` | Backtest with per-call override |
| GET | `/api/analysis/strategies` | List all registered strategies |
| GET | `/api/analysis/analyst` | One-shot decision (CSV) |
| GET | `/api/analysis/analyst?broker=ANGEL&symbolToken=...` | One-shot decision (live Angel) |
| GET | `/api/analysis/structure` | Swings + BOS + CHoCH events |
| GET | `/api/analysis/zones` | Order blocks + FVGs + liquidity |
| GET | `/api/analysis/context` | Full market context at last candle |
| GET | `/api/analysis/context?asOfIndex=N` | Historical context at candle N |
| GET | `/api/analysis/candles?...` | Raw broker candles for a date range |

### Instruments
| Method | URL | Purpose |
|--------|-----|---------|
| GET | `/api/instruments/status` | Loaded count |
| POST | `/api/instruments/refresh` | Force redownload |
| GET | `/api/instruments/lookup?symbol=X` | By symbol |
| GET | `/api/instruments/lookup-by-token?token=X` | By numeric token |
| GET | `/api/instruments/option?underlying=NIFTY&expiry=...&strike=...&type=CE` | Option lookup |
| GET | `/api/instruments/expiries?underlying=X` | All expiries for an underlying |
| GET | `/api/instruments/option-chain?underlying=X&expiry=Y` | Full option chain |
| GET | `/api/instruments/futures?underlying=X` | All active futures |

### Paper trading
| Method | URL | Purpose |
|--------|-----|---------|
| POST | `/api/paper/sessions` | Start (replay / live / historical replay) |
| GET | `/api/paper/sessions` | List all sessions |
| GET | `/api/paper/sessions/{id}` | Session snapshot (poll this) |
| POST | `/api/paper/sessions/{id}/stop` | Force stop + close open position |

**Deep dive:** [API.md](API.md)

---

## 11. Configuration surface

| Key | Purpose |
|-----|---------|
| `server.port` | App port (default 9010) |
| `broker.angel.*` | Angel credentials via env vars |
| `broker.kite.*`, `broker.upstox.*` | Other broker stubs |
| `analysis.nifty.data-file` | CSV path for backtesting |
| `analysis.strategy.default-strategy` | Which strategy `/backtest` uses when none specified |
| `analysis.strategy.sma.short-period` / `long-period` | SMA crossover periods |
| `analysis.strategy.rsi.period` / `oversold` / `overbought` | RSI thresholds |
| `analysis.strategy.macd.fast-period` / `slow-period` / `signal-period` | MACD periods |
| `analysis.strategy.ensemble.min-agreement` | Ensemble vote threshold |
| `analysis.smc.swing.lookback` | Swing detector sensitivity |
| `analysis.smc.sweep.window-candles` | How recent a sweep counts as "recent" |
| `paper.autostart.enabled` | Enable auto-start on boot |
| `paper.autostart.sessions[N].*` | Per-session auto-start config |

**Deep dive:** [CONFIGURATION.md](CONFIGURATION.md)

---

## 12. Documentation

| Doc | Purpose |
|-----|---------|
| [README.md](README.md) | Overview |
| [SETUP.md](SETUP.md) | How to install and run |
| [ONBOARDING.md](ONBOARDING.md) | 10-step new-developer tour |
| [ARCHITECTURE.md](ARCHITECTURE.md) | High-level design |
| [API.md](API.md) | REST endpoint reference |
| [CONFIGURATION.md](CONFIGURATION.md) | All properties |
| [ENV-VARIABLES.md](ENV-VARIABLES.md) | Angel/broker credentials |
| [BROKER-INTEGRATION.md](BROKER-INTEGRATION.md) | How brokers plug in |
| [STRATEGY-GUIDE.md](STRATEGY-GUIDE.md) | Writing a strategy |
| [BACKTESTING.md](BACKTESTING.md) | Backtester internals |
| [DATA-FORMAT.md](DATA-FORMAT.md) | Candle CSV format |
| [FLOW-DIAGRAM.md](FLOW-DIAGRAM.md) | 9 Mermaid diagrams |
| [DESIGN-PATTERNS-IN-USE.md](DESIGN-PATTERNS-IN-USE.md) | Every pattern in the code |
| [SMC-LIQUIDITY-PLAN.md](SMC-LIQUIDITY-PLAN.md) | SMC design + phases |
| [LIVE-TRADING-GUIDE.md](LIVE-TRADING-GUIDE.md) | End-to-end live trading workflow |
| [SECURITY.md](SECURITY.md) | Auth + secrets |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Docker / systemd / nginx |
| [TESTING.md](TESTING.md) | JUnit patterns |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | Common issues |
| [CHANGELOG.md](CHANGELOG.md) | Release notes (stale — this doc supersedes) |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution rules |
| [ROADMAP.md](ROADMAP.md) | Future plans |

---

## 13. Postman

| Item | Notes |
|------|-------|
| `postman/angle-app.postman_collection.json` | 12 folders, ~50 requests |
| `postman/angle-app.postman_environment.json` | 30+ env vars |
| `postman/README.md` | Import + usage guide |

Folders: Analysis (Backtest, Analyst, SMC, Candles), Instruments,
Paper Trading (Replay / Live NSE / Live MCX / Session management),
Portfolio & Trading, Auth, Dashboard, Admin, Home.

---

## 14. What's NOT built (future work)

- Real order placement to Angel — currently paper-only
- Persistence for sessions / trades — restart wipes everything
- Alerts (Telegram / Slack / email) on trade events
- Frontend chart with trades overlaid
- Multi-timeframe strategies (H4 bias + M15 entries)
- Position sizing / risk management (currently 1 unit per trade)
- Trailing stops / max holding period / time-of-day exits
- Metrics dashboard (max drawdown, Sharpe, equity curve)
- ML-driven strategies
- Regime-aware ensemble weighting
- Options-specific strategies (spreads, straddles)

See [ROADMAP.md](ROADMAP.md) for priority ordering.

---

## Number of production Java files

Roughly 70+ across:
- 8 packages: `analyst`, `backtest`, `broker`, `config`, `controller`, `indicator`, `marketdata`, `marketstructure`, `paper`, `strategy`
- Plus web/auth layer (existing): `security`, `filter`, `service`, `dto`, `miniapp`

Line count is small on purpose — most classes stay under 100 lines, each doing one thing.
