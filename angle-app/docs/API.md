# REST API Reference

Base URL: `http://localhost:9010`

Authentication:
- `/api/**` and `/actuator/**` → HTTP Basic (`admin:admin123` by default).
- `/admin/**` and HTML pages → form login (session cookie).

For complete request/response examples, see the Postman collection at
`postman/angle-app.postman_collection.json`.

---

## Signals

### `GET /api/signals/feed`
List saved signals, newest first.

| Param | Type | Default | Description |
|---|---|---|---|
| `token` | string | — | Filter by symbol token |
| `status` | enum | all | OPEN / HIT_TARGET / HIT_STOP / EXPIRED / CANCELLED |
| `source` | enum | all | CONSENSUS / AI |
| `hours` | int | 24 | Max age |

Returns `List<SignalEntity>`.

### `GET /signals`
HTML feed page (card view with filters).

---

## Backtest

### `POST /api/backtest/pipeline`
Run full-pipeline backtest.

| Param | Type | Default | Description |
|---|---|---|---|
| `symbolToken` | string | required | Angel token |
| `exchange` | enum | NSE | Exchange |
| `interval` | enum | FIVE_MINUTE | Candle interval |
| `daysBack` | int | 30 | Lookback days |
| `minAgreement` | int | 3 | Consensus threshold |
| `trailingMode` | enum | PERCENT | NONE / FIXED / PERCENT / MILESTONE / ATR |
| `trailingPercent` | double | 0.40 | % below peak |
| `trailingActivation` | double | 0.30 | Fraction of range before activating |
| `expiryBars` | int | 48 | Auto-exit after N bars |
| `capital` | double | 500000 | Starting capital (₹) |
| `riskPercent` | double | 1.0 | % risk per trade |
| `lotSize` | int | 1 | Units per lot |
| `sizingMode` | enum | RISK_BASED | RISK_BASED / FIXED_LOTS |
| `fixedLots` | int | 1 | Lots per trade when sizing=FIXED_LOTS |

Returns `PipelineBacktestResult` (stats + trade log + equity curve).

### `GET /admin/backtest` / `POST /admin/backtest/run`
HTML form + results page. Honours `backtest.card.enabled` flag.

### `POST /api/backtest-card/enable?on=true|false`
Toggle backtest card visibility.

### `POST /api/backtest-card/save?lookBackMin=...&lookBackMax=...&defaultDays=...`
Update card config at runtime (resets on restart).

### `GET /api/backtest-card/edit` / `GET /api/backtest-card/view`
HTMX fragment endpoints.

---

## Portfolio (AI picks)

### `POST /api/ai-picks/generate`
Scan enabled instruments, ask Claude for picks, save to `ai_pick`.

Returns `GenerateResult` with universe size, parsed/saved counts, skipped
reasons, list of saved picks.

### `POST /api/ai-picks/check`
Manually run outcome tracker (otherwise auto at 15:45 IST).

### `GET /api/ai-picks`
List picks.

| Param | Type | Default | Description |
|---|---|---|---|
| `status` | enum | all | OPEN / HIT_TARGET / HIT_STOP / EXPIRED |
| `days` | int | 180 | Lookback |

### `GET /portfolio`
HTML portfolio page with scorecard.

---

## News suite

### Global Markets
- `GET /markets` — HTML dashboard.
- `GET /api/markets/quotes` — JSON snapshot of all 8 quotes.
- `POST /api/markets/refresh` — force fetch (bypasses market-hours gate).

### News headlines
- `GET /news` — HTML feed with filters.
- `GET /api/news/headlines` → params: `source`, `sentiment`, `sector`,
  `hours`, `limit`.
- `POST /api/news/refresh` → fetch + tag.
- `POST /api/news/tag` → tag untagged rows only.

### News blackout calendar
- `GET /admin/news` — HTML admin page.
- `GET /api/news/status` — snapshot (total events, active now, upcoming 7d).
- `GET /api/news/active` — events currently blocking.
- `GET /api/news/upcoming?days=7` — upcoming events.
- `GET /api/news/all` — full calendar.
- `GET /api/news/check?token=99926000` — dry-run: is this symbol blocked?
- `POST /api/news/reload` — re-read YAML.
- `POST /api/news/enable?on=true|false`.
- `POST /api/news/severity?value=HIGH|MEDIUM|LOW`.

### Pre-market briefing
- `GET /briefing` — HTML (latest + history).
- `GET /api/briefing/latest` — JSON of latest briefing.
- `GET /api/briefing/history` — 30-day history.
- `POST /api/briefing/generate` — force generation now.

---

## Pipeline gates

### Regime
- `GET /api/regime/status` — snapshot.
- `GET /api/regime/check?token=99926000` — dry-run.
- `POST /api/regime/enable?on=true|false`.
- `POST /api/regime/gate/{name}?on=true|false` — name ∈ `adx` / `vix` / `time`.
- `POST /api/regime/reload`.

### Multi-timeframe
- `GET /api/mtf/status`.
- `GET /api/mtf/check?token=...&interval=FIVE_MINUTE&action=BUY`.
- `POST /api/mtf/enable?on=...`.
- `POST /api/mtf/mode?value=ALL|MAJORITY|ANY`.
- `POST /api/mtf/method?value=EMA_STACK|PRICE_VS_EMA|SUPERTREND`.
- `POST /api/mtf/tfs?value=FIFTEEN_MINUTE,ONE_HOUR`.
- `POST /api/mtf/reload`.

### Trailing stops
- `GET /api/trailing/status`.
- `POST /api/trailing/enable?on=...`.
- `POST /api/trailing/mode?value=NONE|FIXED|PERCENT|MILESTONE|ATR`.
- `POST /api/trailing/tune?percentDistance=...&activationPercent=...&...`.

---

## Bias engine

### Reads
- `GET /bias` — HTML dashboard (per-instrument select).
- `GET /api/bias/sheet?token=99926000` — JSON bias sheet for one instrument.
- `GET /api/bias/all` — all configured instruments.
- `GET /api/bias/section/{section}?token=...` — single section
  (marketContext / vix / breadth / structure / zones / correlated / etc.).
- `GET /api/bias/status` — is bias engine enabled.

### Toggle
- `POST /api/bias/enable?on=true|false` — master switch (returns fragment
  for HTMX, JSON otherwise).

### Sub-module toggles (HTMX)
- `POST /api/dashboard/action/bias-module/{name}/toggle` — name ∈ `vix` /
  `correlated` / `breadth` / `circuit`.

---

## Instruments

### CRUD (web form + REST)
- `GET /admin/instruments` — HTML CRUD UI.
- `GET /admin/instruments/data` — JSON list.
- `POST /admin/instruments` — create.
- `PUT /admin/instruments/{id}` — update.
- `DELETE /admin/instruments/{id}` — delete.
- `GET /admin/instruments/lookup?query=NIFTY` — autocomplete scrip master.
- `POST /api/instruments/refresh` — reload scrip master.
- `GET /api/instruments/status` — scrip master stats.

### Options & futures (Angel-backed)
- `GET /api/instruments/expiries?underlying=NIFTY`.
- `GET /api/instruments/option-chain?underlying=NIFTY&expiry=2026-10-08`.
- `GET /api/instruments/option?underlying=NIFTY&expiry=...&strike=24700&type=CE`.
- `GET /api/instruments/futures?underlying=NIFTY`.

---

## Live stream

- `GET /api/live/stream` — SSE endpoint pushing ticks + closed candles.
- `GET /api/live/candles?token=...&interval=...&from=...&to=...` —
  historical candles for chart bootstrap.
- `GET /api/live/candles-with-indicators?token=...&strategy=consensus-3` —
  candles + computed indicator series + consensus signal markers.
- `GET /live/ticker` — demo page.
- `GET /live/chart?token=...&interval=...` — TradingView chart.

---

## Dashboard actions (HTMX)

- `POST /api/dashboard/action/cache/warm` → warms cache, returns fragment.
- `POST /api/dashboard/action/cache/clear` → clears, returns fragment.
- `POST /api/dashboard/action/warmer/force` → force-warm bypass breaker.
- `POST /api/dashboard/action/warmer/reset` → reset circuit.
- `POST /api/dashboard/action/reports/finalize` → write today's report.
- `POST /api/dashboard/action/bias-module/{name}/toggle` → sub-flag toggle.

---

## Cache debug

- `GET /admin/cache/candles/stats` — hits, misses, hit rate, size, memory.
- `GET /admin/cache/candles/keys` — list all keys.
- `POST /admin/cache/candles/clear` — wipe entries.
- `DELETE /admin/cache/candles?token=99926000` — clear one instrument.
- `POST /admin/cache/candles/warm?force=false|true` — trigger warmer round.
- `GET /admin/warmer/stats` — warmer health history.
- `POST /admin/warmer/circuit/reset` — reset circuit breaker.

---

## Daily reports

- `GET /api/reports/today` — markdown render of today's report.
- `GET /api/reports/{yyyy-MM-dd}` — specific date.
- `POST /api/reports/today/finalize` — write to disk.
- `POST /api/reports/{yyyy-MM-dd}/finalize`.

---

## Paper trading

- `POST /api/paper/sessions` — create new session.
- `GET /api/paper/sessions` — list.
- `GET /api/paper/sessions/{id}` — poll snapshot.
- `POST /api/paper/sessions/{id}/stop` — stop session.
- `GET /api/paper/history` — closed sessions.
- `GET /api/paper/history/{id}` + `/trades`.

---

## AI opinion (on-demand)

- `GET /api/ai/signal?token=99926000` — ask Claude for one instrument.
  Returns `{ action, confidence, rationale, keyPoints, model, cached,
  generatedAt }`.
- 5-min cache per (token, bias-snapshot).

---

## Auth / admin

- `GET /auth/login` — login form.
- `POST /auth/login` — submit login.
- `POST /logout` — logout (CSRF-protected).
- `GET /admin` — admin landing.
- `GET /admin/audit-log` — audit log viewer.

---

## Actuator

- `GET /actuator/health` — up/down.
- `GET /actuator/info`.
- `POST /actuator/refresh` — reload @RefreshScope beans (BiasProperties,
  CandleCacheProperties, TradingProperties, CalendarProperties) without JVM
  restart. Requires ROLE_ADMIN.

---

## Error responses

| Status | When |
|---|---|
| 401 | Missing / wrong HTTP Basic creds (`/api/**`). |
| 403 | Wrong role, CSRF missing, or feature disabled (e.g. backtest). |
| 404 | Resource not found. |
| 429 | Upstream rate limit (Alpha Vantage, Angel). |
| 500 | Unhandled server error — check logs. |

All error bodies are JSON when the request was JSON, HTML (default
Spring error page) otherwise.
