# Changelog

All notable changes to this project.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### News Suite
- **Pre-Market Briefing** — Auto-generated daily briefing at 08:30 IST. Combines
  global markets snapshot + last-24h tagged news → Claude produces bias (BULLISH/
  BEARISH/NEUTRAL/CAUTIOUS) + key drivers + 200-word summary. Persisted to
  `briefing` table. Email + Telegram delivery stubs ready for wire-up.
- **News Headlines Feed** — RSS fetcher pulls MoneyControl / ET / BS / LiveMint
  every 15 min. Dedupe by URL. Claude tags each headline (BULLISH/BEARISH/NEUTRAL
  + sector). Page `/news` with sentiment/sector filters. ~$1/month cost.
- **Global Markets Dashboard** — `/markets` shows oil, gold, USD/INR, Dow, Nasdaq,
  Nikkei, Hang Seng, VIX via Alpha Vantage free tier. Auto-refresh respects
  500 calls/day limit. Impact-analysis line auto-generated.

### Dashboard & UI
- **Theme system** — Light + Dark themes with CSS variables in `theme.css`.
  Toggle button persists choice in localStorage. Anti-flash init runs before
  first paint. Extended across all pages via shared `theme-head` fragment.
- **Shared sidebar** — `app-sidebar.html` fragment used by every page.
  Active-link highlighting via `active` parameter. Responsive on narrow screens.
- **HTMX partial refresh** — Bias Engine, Cache, Warmer, Reports, Backtest, and
  Bias Modules cards swap in place without full-page reload. ~30 ms vs ~300 ms.
- **Elevated card style** — Glass-morphism accent stripes + gradient tints
  per color class (c-blue/green/orange/purple/pink/cyan).
- **Pipeline Dashboard** — Unified `/admin/pipeline` with 4 gate cards
  (regime, MTF, trailing, news) + inline toggles + today's block counts.
- **CSS extracted** — Per-page CSS files under `static/css/` instead of inline.
- **Backtest card** — Config-based visibility, inline ON/OFF toggle, gear-icon
  edit form for look-back range + default days.

### Portfolio & AI
- **AI Stock Picks (`/portfolio`)** — Claude analyzes watched instruments
  (price history + RSI + 50-DMA + momentum) and recommends BUY/HOLD/AVOID with
  entry/target/stop/confidence/horizon/rationale. Each pick saved to `ai_pick`.
  Daily outcome tracker closes HIT_TARGET/HIT_STOP/EXPIRED and computes
  return %. Scorecard shows win rate + avg return over 180 days.
- **AI Confirmer on signals** — After consensus fires, async BiasSheet →
  Claude. Second row saved with `source=AI` + confidence + rationale.
  Visible as separate card on `/signals`. Disables via one flag.

### Signal Pipeline
- **Pipeline Backtest** (`/admin/backtest`) — Replays historical candles
  through consensus + trailing stops. Walk-forward, NO look-ahead. Trade log
  + win rate + max drawdown + profit factor + equity curve. Lot-size +
  risk-based or fixed-lots sizing. Shows strategy names per trade.
  Optimized: `consensusMarkers` called ONCE for whole range, markers indexed
  by bar (~30× speedup vs per-bar slicing). Realism fixes: skip end-of-day
  intraday signals; reject unreachable entries; pessimistic same-bar
  resolution.
- **News Blackout Calendar** — YAML-defined events (RBI/FOMC/earnings/expiry).
  Severity + window + per-symbol targeting. Signals auto-skipped during
  windows. `/admin/news` admin UI with reload button. Auto-reload every 5 min.
- **Multi-Timeframe Confirmation** — Before saving, verify N+ higher
  timeframes agree in direction. 3 detection methods (EMA_STACK, PRICE_VS_EMA,
  SUPERTREND). 3 agreement modes (ALL, MAJORITY, ANY). Admin toggle.
- **Trailing Stops** — 4 modes (FIXED/PERCENT/MILESTONE/ATR). Activation
  delay to skip entry-bar noise. Ratchet-only (never loosens). Applied per
  tick in `LiveSignalMonitor`. Admin page shows mode + percent + trail
  count on `/signals`.
- **Regime Detector** — Time-of-day + India VIX + ADX regime gates. Each
  gate independent on/off. ADX classifies RANGE/MIXED/TREND. Dry-run
  inspector at `/api/regime/check`.
- **Signal Lifecycle** — `bias_signal` table. OPEN → HIT_TARGET / HIT_STOP /
  EXPIRED / CANCELLED. `SignalDetector` fires on 5-min candle close with 3+
  of 7 strategies agreeing. `LiveSignalMonitor` watches ticks, closes on
  hit. `/signals` HTML page + `/api/signals/feed` JSON. Scorecard + filters.

### Strategies & Indicators
- **8 strategies** total: moving-average-crossover, rsi-mean-reversion,
  macd-crossover, ob-retest, sweep-fvg, volume-breakout, bollinger-bounce,
  ensemble. SMC strategies use order blocks + fair value gaps + liquidity
  sweeps.
- **Bias Engine 9/20/50** — EMA set changed from 20/50/200 to 9/20/50 for
  faster intraday response.
- **New indicators** — SuperTrend, VWAP, Bollinger Bands.
- **Volume analysis** — currentVolume, avgVolume20, volumeRatio,
  volumeBullish.

### Live Streaming
- **Phase 1-4 WebSocket pipeline** — Angel SmartStream → binary tick decoder
  → TickEvent → CandleAggregator → CandleClosedEvent → SignalDetector.
- **SSE browser push** — `/api/live/stream` + `/live/ticker` demo page.
- **TradingView chart** — `/live/chart` with EMA/VWAP/SuperTrend overlays,
  RSI/MACD/Volume sub-panes, BUY/SELL markers, preset toggles.

### Caching
- **Multi-tier cache** — Caffeine (L1) + Redis (L2) + MySQL (L3), pluggable
  via `bias.cache.provider`.
- **Warmer** — Scheduled bias-sheet pre-fetch with circuit breaker.
- **Warmer stats** — `/admin/warmer/stats` + manual trigger.

### Infrastructure
- **Spring Security chains** — 4 chains: `/api/**` basic auth, `/actuator/**`
  admin-only, `/admin/**` form login, catch-all form login.
- **Multi-broker scaffolding** — `BrokerClient` interface; Angel wired,
  Upstox/Kite stubs.
- **Instruments CRUD** — MySQL-backed, admin UI at `/admin/instruments`,
  autocomplete against scrip master, lot-size column.
- **@Async** enabled for AI confirmer and other background work.

### Security
- All broker credentials in env vars.
- `ANTHROPIC_API_KEY` required for AI features.
- `ALPHA_VANTAGE_KEY` for global markets.
- Admin forms use CSRF tokens.

## [0.1.0] - Initial

- Skeleton Spring Boot app.
- `spring.application.name=angle-app`.
- Basic security scaffolding.
- Multi-broker abstraction: `BrokerClient` interface with Angel, Upstox,
  Kite implementations.
- Angel One integration: `AngelClient`, `AngelAuthService`, `AngelHeaders`,
  `TotpGenerator`.
- Market data layer: `MarketDataService`, `NiftyFileLoader`.
- Strategy framework: `Strategy` interface, `Signal` enum.
- `MovingAverageCrossover` strategy (SMA 20/50).
- `SimpleMovingAverage` indicator.
- `Backtester` + `BacktestResult`.
- `AnalysisController` with `/api/analysis/backtest` and `/api/analysis/candles`.
