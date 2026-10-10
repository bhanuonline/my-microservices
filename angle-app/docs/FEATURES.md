# Features

AngleApp is a personal algorithmic-trading platform for Indian markets
(NSE / BSE / MCX via Angel One SmartAPI). Built with Spring Boot + Thymeleaf
+ HTMX + MySQL + Claude AI.

This document is a capability inventory for teammates joining the project.
For end-user walkthroughs see `HOW-TO-USE.md`. For architecture see
`ARCHITECTURE.md`.

---

## Trading signals pipeline

### Signal detection
- 8 technical strategies: moving-average-crossover, rsi-mean-reversion,
  macd-crossover, volume-breakout, bollinger-bounce, ob-retest (SMC order
  blocks), sweep-fvg (SMC liquidity sweep + fair value gap), ensemble.
- Consensus voting: configurable `min-agreement` (default 3) of 7 member
  strategies (ensemble excluded from vote).
- Fires on candle-close events for configured intervals (default FIVE_MINUTE,
  FIFTEEN_MINUTE).
- Dedupe window (default 15 min) prevents same-symbol/same-action spam.

### Pre-save filters (each independently toggleable)
1. **Regime** — Time-of-day + India VIX + ADX classifier (RANGE / MIXED /
   TREND). Default allows MIXED + TREND inside 09:30-12:00 + 14:00-15:00 IST
   with VIX ≤ 22.
2. **Multi-Timeframe Confirmation** — Signal direction must be confirmed by
   higher timeframes (default FIFTEEN_MINUTE + ONE_HOUR). Methods: EMA_STACK,
   PRICE_VS_EMA, SUPERTREND. Modes: ALL / MAJORITY / ANY.
3. **News Blackout** — YAML calendar of RBI / FOMC / earnings / F&O expiry.
   Severity threshold filterable (default MEDIUM). Per-symbol targeting
   (e.g. Reliance earnings blocks only Reliance).
4. **Dedupe** — same (symbol, action) in last N min.

### Lifecycle tracking
- `bias_signal` table — one row per signal.
- Status: OPEN → HIT_TARGET / HIT_STOP / EXPIRED / CANCELLED.
- `LiveSignalMonitor` listens to live ticks, closes signals on target/stop
  hit.
- Scheduled expirer sweeps stale OPEN signals.
- Profit calc: points, percent, rupees (if lot size set).

### Trailing stops
- 4 modes: FIXED (N pts), PERCENT (N% behind peak), MILESTONE (ladder like
  `50:0,75:33,90:66`), ATR (K × ATR14).
- Activation delay (default 30% of entry→target range).
- Ratchet-only — never loosens.
- Applied per tick in `LiveSignalMonitor`.

### AI confirmer
- After consensus signal saves, async BiasSheet → Claude.
- Claude returns LONG/SHORT/WAIT/AVOID + confidence + rationale.
- Non-WAIT replies saved as separate row (`source=AI`) on `/signals`.
- Lets you compare "did AI agree with technicals?" over time.

### Pages & endpoints
- `/signals` — card list with filters (status, source, token, hours).
- `/api/signals/feed` — JSON API.
- `/admin/pipeline` — unified gate dashboard with inline toggles.

---

## Backtest harness

### Pipeline replay
- `/admin/backtest` — form + results on one page.
- Replays historical candles through consensus + trailing stops.
- Walk-forward with NO look-ahead (strategies see only past + current bar).
- `SignalMarkerService.consensusMarkers()` called once per run, markers
  indexed by bar — ~30× faster than per-bar slicing.

### Realism fixes
- Skips intraday signals after 15:00 IST (can't reach target before close).
- Rejects signals where strategy's entry price is outside the bar's
  [low, high] range (unreachable).
- Same-bar target+stop resolution prefers STOP (pessimistic).

### Metrics returned
- Signals fired, wins, losses, trailed exits, expired.
- Win rate, avg return, total return, max drawdown.
- Profit factor, expectancy per trade.
- Equity curve + per-trade log (entry/exit/P&L/strategy names).

### Position sizing
- RISK_BASED (default): position = (capital × risk%) / stop_distance,
  rounded down to whole lots.
- FIXED_LOTS: force N lots per trade regardless of capital.
- Instrument lot size (Nifty=75, Bank Nifty=15, etc.) stored on
  `BiasInstrumentEntity.lotSize`.

---

## Portfolio & medium-term investing

### AI stock picks (`/portfolio`)
- Click "Generate" → scans all enabled instruments.
- For each: fetches 60-day daily candles + computes RSI14, 50-DMA, 60-day
  range, 30d/90d returns.
- Ships batch to Claude with pipe-delimited response format.
- Saves BUY/HOLD/AVOID with entry/target/stop/confidence/horizon/rationale.
- Dedupe window (default 7 days) prevents spamming same stock.

### Outcome tracker
- Daily cron at 15:45 IST walks all OPEN picks.
- Checks target/stop hit using daily candles since pick date.
- Expires if horizon days elapsed with no hit.
- Return % computed and persisted.

### Scorecard
- Win rate across 180-day window.
- Avg return per pick.
- Best / worst performer.
- Per-pick: outcome + closed price + return.

---

## News suite

### Global Markets (`/markets`)
- 8 instruments via Alpha Vantage free tier:
  Brent oil (BNO), Gold (GLD), USD/INR, Dow (DIA), Nasdaq (QQQ), Nikkei
  (EWJ), Hang Seng (EWH), US VIX (VXX).
- Scheduled refresh every 15 min (configurable). Market-hours gate avoids
  wasting daily quota (500 calls/day).
- 13-sec delay between calls respects 5-calls/min limit.
- Impact-analysis line auto-generated ("US closed higher. Oil down 1.5%").
- Rule-based per-card note ("⚠️ oil spike — bearish for Nifty").

### News Headlines (`/news`)
- 5 default RSS sources: MoneyControl Markets/Business, Economic Times,
  Business Standard, LiveMint.
- Dependency-free RSS 2.0 parser (regex-based).
- Dedupe by URL (unique index on `news_headline.url`).
- Scheduled fetch + tag loop every 15 min.
- Claude tags each untagged headline: BULLISH/BEARISH/NEUTRAL + sector
  (BANKING/IT/OIL_GAS/etc.) + 1-sentence rationale.
- Batch size 10 per Claude call keeps cost ~$1/month.
- Retention default 30 days; daily purge at 00:10 IST.
- UI filters by source, sentiment, sector, hours.

### Pre-Market Briefing (`/briefing`)
- Scheduled 08:30 IST weekdays. Also manually triggerable.
- Snapshots Global Markets + last-24h top 25 tagged headlines.
- Claude writes:
  - BIAS (BULLISH/BEARISH/NEUTRAL/CAUTIOUS)
  - 3-5 KEY DRIVERS bullets
  - 200-word summary (serif body for readable long-form)
- Persisted to `briefing` table — one row per `for_date` (upsert).
- 30-day history page.
- Email + Telegram delivery stubs; log-only until SMTP / bot token set.

---

## Bias engine

### Daily bias sheets
- Full market analysis per instrument every 15 min via warmer.
- Sections: market context, VIX, breadth (A/D), multi-TF bias, trend filters,
  market structure (BOS/CHoCH), zones (OB/FVG/liquidity), correlated check
  (Bank Nifty vs Nifty), consolidated score, trade plan.
- Cached in Caffeine (L1) + Redis (L2) + MySQL (L3).
- `/bias` page per instrument; AI opinion button for on-demand Claude
  analysis.

### Change detection
- `BiasChangeDetector` detects: bias flip, score jump ≥ N, new structural
  event, new sweep, ADX cross, divergence (Nifty ↔ Bank Nifty), VIX spike,
  breadth flip.
- `recentChanges` surface on dashboard.

### Sub-modules (independently toggleable)
- VIX section.
- Breadth section (A/D ratio of Nifty 50 constituents).
- Correlated instrument check (Bank Nifty parallel).
- SMC structure analysis.
- Change alerts.

---

## Live streaming

### Phase 1 — WebSocket ingestion
- Angel SmartStream connects on app boot.
- Subscribes to all enabled instruments.
- Binary tick decoder parses mode-1/mode-3 packets.
- Publishes `TickEvent` via Spring event bus.

### Phase 2 — Tick-to-candle aggregation
- Rolling buckets per (symbol, interval).
- Emits `CandleClosedEvent` when a bucket flushes.
- `SignalDetector` listens to these for signal generation.

### Phase 3 — Browser push via SSE
- `/api/live/stream` SSE endpoint with throttling + heartbeat.
- `/live/ticker` demo page with grid of price cards flashing on tick.

### Phase 4 — Live chart
- `/live/chart` with TradingView Lightweight Charts.
- Indicator overlays: EMA9/20/50, VWAP, SuperTrend.
- Sub-panes: RSI14, MACD, Volume.
- Signal markers (BUY/SELL arrows) via consensus or single-strategy toggle.
- Preset buttons (Compact / Full / Price only).

---

## Dashboard & UI

### Theme system
- CSS variables in `theme.css`.
- Light (default) + Dark palettes.
- Toggle button in sidebar + inline on each page.
- Choice persisted to `localStorage`.
- OS preference detected on first visit via `prefers-color-scheme`.
- Anti-flash init script runs before first paint.

### Shared sidebar
- `fragments/app-sidebar.html` used by `/signals`, `/admin/pipeline`,
  `/admin/news`, `/admin/instruments`, `/admin/backtest`, `/markets`,
  `/news`, `/briefing`, `/portfolio`, `/live/ticker`.
- Active-link highlighted via `sidebar(active='signals')` parameter.
- Hidden on screens < 900px.

### HTMX partial refresh
- Cards swap in place without full-page reload.
- Used by: Bias Engine (ON/OFF), Cache Service (Warm/Clear), Warmer Job
  (Force/Reset), Daily Reports (Finalize), Bias Modules (pill toggles),
  Backtest card (ON/OFF + gear edit form).
- Controllers dual-mode: HTMX request → return fragment; plain POST →
  return JSON.
- 14 KB `htmx.org@1.9.12` from CDN.

### Pipeline dashboard
- `/admin/pipeline` — 4 gate cards (regime, MTF, trailing, news).
- Inline enable/disable per gate + sub-flags.
- Mode / method quick-swap buttons.
- Today's gate-rejection counts.
- Active-news warning strip when a blackout is live.

### Elevated card style
- Colored accent stripe at top (via class `.c-blue/green/orange/purple/
  pink/cyan`).
- Subtle gradient tint in matching color.
- Hover lift + larger shadow.
- Icon with colored halo.

---

## Infrastructure

### Spring Security chains (4)
1. `/api/**` — stateless HTTP Basic, 401 on fail, no CSRF.
2. `/actuator/**` — HTTP Basic + ROLE_ADMIN, no CSRF.
3. `/admin/**` — form login + ROLE_ADMIN + custom access-denied page.
4. Catch-all — form login + session.

### Caching
- `bias.cache.provider` = caffeine | redis | caffeine-mysql.
- Keys per (instrument, interval).
- TTL + max size configurable.

### Warmer (`BiasWarmer`)
- Scheduled bias-sheet pre-fetch (cron: every 15 min during market hours).
- Circuit breaker: opens after N consecutive failures.
- Reset endpoint: `POST /admin/warmer/circuit/reset`.
- Stats endpoint: `GET /admin/warmer/stats`.

### Database (MySQL via JPA)
- `ddl-auto=update` (auto-migrates new columns on startup).
- Tables: `bias_signal`, `ai_pick`, `briefing`, `news_headline`,
  `bias_instrument`, `angel_token`, `candle_history`, `audit_log`, more.

### Observability
- Actuator at `/actuator/health`, `/actuator/info`, `/actuator/refresh`.
- Hot-reload @RefreshScope beans via `POST /actuator/refresh`.
- Structured debug logging under `com.angle.trading`.

---

## Operational tools

### Instrument management
- `/admin/instruments` CRUD UI.
- Autocomplete lookup against Angel scrip master.
- Fields: symbol, broker, exchange, symbol_token, interval, enabled,
  priority, lot_size.

### Admin dashboard (`/dashboard`)
- 13 service cards: App, Broker, Cache, Warmer, Market Calendar,
  Bias Engine, Trading Config, Database, Alerts, Bias Modules, Daily Reports,
  Paper Trading, Backtest.
- Health badge (critical / warn / healthy).
- Quick actions and recent-changes feed.

### Cache debug
- `/admin/cache/candles/stats` — hits, misses, hit rate, size.
- `/admin/cache/candles/keys` — list all cached keys.
- `/admin/cache/candles/clear` — wipe entries.
- `/admin/cache/candles/warm?force=true` — force-warm bypass breaker.

### Reports
- `/api/reports/today` — markdown render of today's report.
- `/api/reports/today/finalize` — write to disk.
- Scheduled auto-finalize at 15:35 IST (post-close).

---

## Config properties (prefixes)

| Prefix | What it controls |
|---|---|
| `broker.angel.*`     | Angel SmartAPI credentials + stream config |
| `broker.angel.stream.*` | WebSocket, aggregation, SSE live-stream |
| `broker.upstox.*`    | Stub (not wired) |
| `broker.kite.*`      | Stub (not wired) |
| `bias.*`             | Bias engine (lookback, timeframes, warmer) |
| `bias.cache.*`       | Cache provider + TTL |
| `bias.warmer.*`      | Cron + circuit breaker |
| `bias.vix.*`         | VIX fetch + thresholds |
| `bias.breadth.*`     | A/D ratio |
| `bias.correlated.*`  | Bank Nifty vs Nifty divergence |
| `bias.change-alerts.*` | Change detection flags |
| `analysis.*`         | Strategy params (periods, thresholds) |
| `signals.detector.*` | Consensus threshold, intervals, AI-confirm |
| `signals.monitor.*`  | Expiry hours + sweep |
| `signals.feed.*`     | UI defaults + auto-refresh |
| `regime.*`           | Time-of-day + VIX + ADX gate |
| `mtf.*`              | Multi-timeframe confirmation |
| `trailing.*`         | Trailing stop mode + knobs |
| `news.*`             | Blackout calendar |
| `news-feed.*`        | Headlines feed + AI tagging |
| `global-markets.*`   | Alpha Vantage dashboard |
| `briefing.*`         | Pre-market briefing + delivery |
| `ai.*`               | Claude API + model + cache |
| `ai-picks.*`         | Stock picks (portfolio) |
| `backtest.card.*`    | Dashboard tile customization |
| `trading.*`          | Capital + risk% + option strikes |
| `reports.*`          | Daily report output + auto-write |
| `paper.*`            | Paper trading sessions |
| `alerts.*`           | Slack / Telegram / WhatsApp alerts (stubs) |

See `CONFIGURATION.md` for every property + default + what it does.
