# Architecture

High-level design of `angle-app`. For specific feature behaviour see
`FEATURES.md`.

## One-paragraph summary

Single Spring Boot application that connects to Angel One SmartAPI
(REST + WebSocket), ingests ticks, aggregates them into candles, runs 8
technical strategies through a consensus voter, applies filter gates
(regime, MTF, news blackout), persists signals to MySQL, tracks them
through target/stop/expiry with trailing stops, and surfaces everything
through Thymeleaf pages enhanced with HTMX. Claude AI augments it at
three points: signal second-opinion, portfolio stock-picking, and
pre-market briefing. Alpha Vantage provides global-markets context.

---

## Layered view

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Web layer                                                                │
│   Thymeleaf templates + HTMX fragments + shared app-shell sidebar       │
│   /dashboard /bias /signals /portfolio /markets /news /briefing         │
│   /admin/{pipeline,backtest,instruments,news}                           │
│   /live/{ticker,chart}                                                   │
└─────────────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────────────┐
│ Controllers (REST + MVC)                                                 │
│   Dual-mode: HTMX returns fragment, plain POST returns JSON             │
└─────────────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────────────┐
│ Services (business logic)                                                │
│   Signal pipeline: SignalDetector → RegimeService → MtfConfirmation →   │
│                    NewsBlackoutService → SignalService (save)           │
│                 → AiSignalService (async)                                │
│                 → LiveSignalMonitor (per-tick)                           │
│                 → TrailingStopService (per-tick)                         │
│   Bias:          BiasSheetService + BiasWarmer + BiasChangeDetector     │
│   Backtest:      PipelineBacktestService                                 │
│   AI picks:      AiPickService + AiPickOutcomeService                    │
│   News suite:    GlobalMarketService + NewsHeadlineService +            │
│                    NewsSentimentTagger + BriefingService                 │
└─────────────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────────────┐
│ Persistence (Spring Data JPA → MySQL)                                    │
│   bias_signal · ai_pick · briefing · news_headline · bias_instrument    │
│   angel_token · candle_history · audit_log                               │
└─────────────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────────────┐
│ Integration adapters                                                     │
│   AngelClient (REST) · AngelStream (WebSocket) · AnthropicClient        │
│   AlphaVantageClient · RssFeedFetcher                                    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Signal pipeline (sequence)

```
Angel WebSocket                                      ┌─────────────────┐
     │                                                │  /signals page  │
     ▼                                                │  SSE from       │
  Tick packet (binary)                                │  LiveStream     │
     │                                                └────────▲────────┘
     ▼                                                         │
 TickDecoder                                                   │
     │                                                         │
     ▼                                                         │
 TickEvent (Spring event)                                      │
     │                                                         │
     ├──► CandleAggregator ───► CandleClosedEvent              │
     │                               │                          │
     │                               ▼                          │
     │                       SignalDetector.@EventListener      │
     │                               │                          │
     │                               ▼                          │
     │                       Fetch last 250 candles             │
     │                               │                          │
     │                               ▼                          │
     │             ┌───────── Regime gate (ADX/VIX/time) ───────┤
     │             │                 │                          │
     │             │                 ▼                          │
     │             │         Consensus check (3+ of 7)          │
     │             │                 │                          │
     │             │                 ▼                          │
     │             │         Dedupe (15-min window)             │
     │             │                 │                          │
     │             │                 ▼                          │
     │             │         MTF confirmation (15m + 1H)        │
     │             │                 │                          │
     │             │                 ▼                          │
     │             │         News blackout check                │
     │             │                 │                          │
     │             │                 ▼                          │
     │             │         Save SignalEntity (OPEN)           │
     │             │                 │                          │
     │             │                 ├──► @Async AI confirmer   │
     │             │                 │    → save source=AI row  │
     │             │                 │                          │
     │             └─────────────────┘                          │
     │                                                           │
     └──► LiveSignalMonitor.@EventListener                      │
             │                                                   │
             ├──► TrailingStopService.computeNewStop             │
             │       → update stop (ratchet-only)                │
             │                                                   │
             └──► checkHit(ltp) against (possibly tightened) stop│
                     target hit → closeSignal(HIT_TARGET) ───────┘
                     stop hit   → closeSignal(HIT_STOP)
```

---

## Backtest pipeline

```
BacktestRequest (symbolToken, interval, dates, sizing, trailing params)
     │
     ▼
Fetch all candles for date range
     │
     ▼
SignalMarkerService.consensusMarkers(all)   ← ONE call for the whole range
     │                                         (indexed by bar → O(1) lookup)
     ▼
For i = 250 to end:
     ├─ marker at bar i?  no → next
     ├─ end-of-day signal? skip
     ├─ entry reachable in bar i's [low,high]? no → skip
     │
     ├─ walkForwardToExit from bar i+1
     │     for each bar j:
     │       update trailing stop
     │       check hits (STOP preferred over TARGET if both)
     │
     ├─ compute lots (RISK_BASED or FIXED_LOTS)
     ├─ compute P&L (points, rupees)
     └─ append to trade log + update equity curve + drawdown

Return PipelineBacktestResult
```

---

## News → briefing pipeline

```
┌─ Scheduled 15-min loop ─────────────────────────────────────────┐
│                                                                   │
│  For each RSS source:                                             │
│    RssFeedFetcher.fetch(url) → List<RssItem>                      │
│    For each item: dedupe by URL, save to news_headline           │
│                                                                   │
│  NewsSentimentTagger.tagBatch():                                  │
│    Pick 10 untagged → single Claude call → apply tags            │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘

┌─ Scheduled 15-min loop (parallel) ───────────────────────────────┐
│                                                                   │
│  GlobalMarketService.scheduledRefresh():                          │
│    For each of 8 instruments:                                     │
│      AlphaVantageClient.globalQuote(symbol)                       │
│      13-sec pause (rate limit)                                    │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘

┌─ Scheduled daily 08:30 IST ──────────────────────────────────────┐
│                                                                   │
│  BriefingService.scheduledGenerate():                             │
│    quotes      = GlobalMarketService.allQuotes()                  │
│    headlines   = NewsHeadlineService.findRecent(24h, top 25)      │
│    userPrompt  = buildPrompt(quotes, headlines)                   │
│    raw         = AnthropicClient.complete(system, userPrompt)     │
│    parsed      = parseResponse(raw)   [BIAS, DRIVERS, SUMMARY]    │
│    Upsert briefing by for_date                                    │
│    maybeSendEmail / maybeSendTelegram (log-only until wired)     │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

---

## Request flow — HTMX pattern

```
Browser                     Controller                  Service
   │                              │                        │
   │ POST /api/bias/enable?on=... │                        │
   │ HX-Request: true             │                        │
   ├──────────────────────────────►                        │
   │                              │ setEnabled(on)         │
   │                              ├────────────────────────►
   │                              │                        │
   │                              │ view name:             │
   │                              │ "fragments/... :: X"   │
   │ HTML fragment                │                        │
   ◄──────────────────────────────┤                        │
   │                              │                        │
   │ HTMX replaces #bias-card     │                        │
```

Same endpoint, no HX-Request header → returns `ResponseEntity<Map>` JSON
for curl / Postman.

---

## Data model (key tables)

```
bias_signal
┌─────────────────┬──────────────────────────────────────────────┐
│ id              │ PK                                            │
│ created_at      │ When fired                                    │
│ symbol_token    │ Angel token                                   │
│ action          │ BUY / SELL                                    │
│ entry, stop,    │ Original levels                               │
│   target        │                                               │
│ status          │ OPEN / HIT_TARGET / HIT_STOP / EXPIRED        │
│ closed_at,      │ Exit details                                  │
│   closed_price, │                                               │
│   profit_pts,   │                                               │
│   profit_pct    │                                               │
│ source          │ CONSENSUS / AI                                │
│ ai_confidence   │ HIGH / MEDIUM / LOW                           │
│ ai_rationale    │ For source=AI rows                            │
│ original_stop   │ Pre-trailing (for audit)                     │
│ high_water_mark │ Peak price seen (for trailing)                │
│ trail_updates   │ Count of trail tightenings                    │
└─────────────────┴──────────────────────────────────────────────┘

ai_pick (portfolio) — same shape, horizon in days instead of hours

briefing — one row per for_date (unique), bias + summary + raw audit JSON

news_headline — url unique, sentiment tagged by AI, retention 30 days

bias_instrument — watchlist, lot_size, enabled flag
```

---

## Design patterns in use

- **Strategy pattern** — `Strategy` interface + `StrategyRegistry`.
- **Observer / Event-driven** — Spring `@EventListener` (TickEvent,
  CandleClosedEvent).
- **Repository pattern** — Spring Data JPA.
- **Dual-response controllers** — HTMX fragment vs JSON based on
  `HX-Request` header.
- **Fragment composition** — Thymeleaf `th:fragment` + `th:replace` for
  reusable UI (sidebar, cards, theme-head).
- **Circuit breaker** — custom in `BiasWarmer` guards Angel API outages.
- **Caching layers** — Caffeine (L1) + Redis (L2) + MySQL (L3).
- **Scheduler** — Spring `@Scheduled` with cron / fixedRate + IST timezone.
- **Walk-forward backtest** — no look-ahead; strategies see tail-sliced
  candles up to current bar.
- **Rate-limit respect** — 13-sec pause between Alpha Vantage calls;
  batched Claude tagging to stay under daily cost.

See `DESIGN-PATTERNS-IN-USE.md` for the full inventory.

---

## Deployment

Single JAR via `./mvnw package`. Needs:
- MySQL (local or RDS)
- Redis (optional, for L2 cache)
- Env vars (Angel + Anthropic + Alpha Vantage keys)
- Java 17+

See `DEPLOYMENT.md`.

---

## Non-goals

- Not an order management system — read-only; no live order placement.
- Not multi-user — single trader account.
- Not real-time to the millisecond — tick latency ~50-200 ms, acceptable
  for medium-frequency signals.
- Not an options engine (though option-chain endpoints exist, no Greeks).

See `ROADMAP.md` for what we're intentionally NOT building next.
