# Configuration Reference

Every tunable property in `application.properties`, grouped by concern.
Defaults match the shipped file. Environment-variable overrides shown as
`${VAR_NAME:default}`.

For the "why" behind each knob see the matching code or `FEATURES.md`.

---

## Application core

| Key | Default | Purpose |
|---|---|---|
| `server.port` | `9010` | HTTP port |
| `spring.application.name` | `angle-app` | App name |
| `spring.thymeleaf.cache` | `true` | Set `false` for template auto-reload during UI dev |
| `management.endpoints.web.exposure.include` | `health,info,refresh` | Actuator endpoints exposed |

## Logging

| Key | Default | Purpose |
|---|---|---|
| `logging.level.com.angle.trading` | `DEBUG` | App debug logging |
| `logging.level.org.springframework.security` | `DEBUG` | Security filter trace |
| `logging.level.org.springframework.web` | `INFO` | Request logging |
| `logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper` | `OFF` | Silence dup-key noise from candle writes |
| `spring.output.ansi.enabled` | `ALWAYS` | Color output |

## Database

| Key | Default | Purpose |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/angleapp?...` | MySQL connection |
| `spring.datasource.username` | `${DB_USER:root}` | DB user |
| `spring.datasource.password` | `${DB_PASSWORD:pass1234}` | DB pass |
| `spring.jpa.hibernate.ddl-auto` | `update` | Auto-migrates new columns on boot |
| `spring.jpa.open-in-view` | `false` | Avoid lazy-load traps in views |

---

## Broker — Angel One

| Key | Default | Purpose |
|---|---|---|
| `broker.angel.enabled` | `true` | Master switch |
| `broker.angel.base-url` | `https://apiconnect.angelone.in` | SmartAPI base |
| `broker.angel.api-key` | `${ANGEL_API_KEY:}` | API key (env var) |
| `broker.angel.client-code` | `${ANGEL_CLIENT_CODE:}` | Client code |
| `broker.angel.password` | `${ANGEL_PASSWORD:}` | Password |
| `broker.angel.totp-secret` | `${ANGEL_TOTP_SECRET:}` | TOTP secret |

### SmartStream (WebSocket)

| Key | Default | Purpose |
|---|---|---|
| `broker.angel.stream.enabled` | `true` | Enable WS |
| `broker.angel.stream.url` | `wss://smartapisocket.angelone.in/smart-stream` | WS URL |
| `broker.angel.stream.mode` | `1` | 1=LTP, 3=full depth |
| `broker.angel.stream.reconnect-seconds` | `5` | Reconnect delay |
| `broker.angel.stream.correlation-id` | `angleapp` | Correlation ID |

### Tick-to-candle aggregation

| Key | Default | Purpose |
|---|---|---|
| `broker.angel.stream.aggregate.enabled` | `true` | Enable aggregation |
| `broker.angel.stream.aggregate.intervals` | `ONE_MINUTE,FIVE_MINUTE,FIFTEEN_MINUTE` | Which bars to build |
| `broker.angel.stream.aggregate.sweep-seconds` | `1` | How often to flush closed bars |

### SSE live stream

| Key | Default | Purpose |
|---|---|---|
| `broker.angel.stream.live-stream.enabled` | `true` | Push to browsers |
| `broker.angel.stream.live-stream.throttle-ms` | `500` | Min gap per (client, token) |
| `broker.angel.stream.live-stream.heartbeat-seconds` | `30` | Keep-alive comment |

### Upstox / Kite (stubs)

Not wired yet. Set `enabled=true` and fill API keys when ready to extend.

---

## Signal detection & lifecycle

| Key | Default | Purpose |
|---|---|---|
| `signals.detector.enabled` | `true` | Master switch |
| `signals.detector.min-agreement` | `3` | Strategies needed for consensus |
| `signals.detector.intervals` | `FIVE_MINUTE,FIFTEEN_MINUTE` | Trigger candles |
| `signals.detector.dedupe-minutes` | `15` | Skip same signal within window |
| `signals.detector.ai-confirm` | `true` | Call Claude on every signal |
| `signals.monitor.enabled` | `true` | Watch ticks to close signals |
| `signals.monitor.expiry-hours` | `4` | Auto-expire OPEN after N hours |
| `signals.monitor.expiry-sweep-minutes` | `15` | How often to sweep |
| `signals.feed.default-max-age-hours` | `24` | UI default filter |
| `signals.feed.auto-refresh-seconds` | `30` | Page meta-refresh |

---

## Regime gates

| Key | Default | Purpose |
|---|---|---|
| `regime.enabled` | `true` | Master |
| `regime.adx.enabled` | `true` | ADX sub-gate |
| `regime.adx.period` | `14` | ADX period |
| `regime.adx.weak-threshold` | `20.0` | < threshold = RANGE |
| `regime.adx.strong-threshold` | `30.0` | ≥ threshold = TREND |
| `regime.adx.allowed-regimes` | `MIXED,TREND` | Which regimes fire signals |
| `regime.vix.enabled` | `true` | VIX ceiling |
| `regime.vix.max-allowed` | `22.0` | Skip signals above this |
| `regime.time.enabled` | `true` | Time-of-day gate |
| `regime.time.allowed-windows` | `09:30-12:00,14:00-15:00` | IST windows |

---

## Multi-timeframe confirmation

| Key | Default | Purpose |
|---|---|---|
| `mtf.enabled` | `true` | Master |
| `mtf.higher-timeframes` | `FIFTEEN_MINUTE,ONE_HOUR` | TFs to check |
| `mtf.agreement-mode` | `ALL` | ALL / MAJORITY / ANY |
| `mtf.method` | `EMA_STACK` | EMA_STACK / PRICE_VS_EMA / SUPERTREND |
| `mtf.ema-fast` | `9` | EMA fast period |
| `mtf.ema-slow` | `20` | EMA slow period |
| `mtf.super-trend-period` | `10` | SuperTrend period |
| `mtf.super-trend-multiplier` | `3.0` | SuperTrend ATR mult |
| `mtf.cache-minutes` | `5` | Direction cache TTL |

---

## Trailing stops

| Key | Default | Purpose |
|---|---|---|
| `trailing.enabled` | `true` | Master |
| `trailing.mode` | `PERCENT` | NONE / FIXED / PERCENT / MILESTONE / ATR |
| `trailing.activation-percent` | `0.30` | Fraction of range before activation |
| `trailing.fixed-distance` | `30.0` | Pts (mode=FIXED) |
| `trailing.percent-distance` | `0.40` | % (mode=PERCENT) |
| `trailing.atr-multiplier` | `2.0` | K × ATR (mode=ATR) |
| `trailing.atr-period` | `14` | ATR period |
| `trailing.milestone-ladder` | `50:0,75:33,90:66` | moveAt:stopAt pairs (mode=MILESTONE) |

---

## News blackout calendar

| Key | Default | Purpose |
|---|---|---|
| `news.enabled` | `true` | Master |
| `news.block-severity` | `MEDIUM` | LOW / MEDIUM / HIGH |
| `news.default-window-before` | `30` | Default minutes before |
| `news.default-window-after` | `60` | Default minutes after |
| `news.calendar-file` | `news-calendar.yml` | YAML path (classpath or absolute) |
| `news.reload-interval-seconds` | `300` | Auto-reload cadence |

---

## News headlines feed

| Key | Default | Purpose |
|---|---|---|
| `news-feed.enabled` | `true` | Master |
| `news-feed.refresh-minutes` | `15` | RSS fetch cadence |
| `news-feed.retention-days` | `30` | Purge older than |
| `news-feed.ai-tagging-enabled` | `true` | Call Claude for sentiment tags |
| `news-feed.ai-tagging-batch-size` | `10` | Headlines per Claude call |
| `news-feed.page-refresh-seconds` | `300` | /news auto-refresh |
| `news-feed.sources[N].name` | — | Add custom sources |
| `news-feed.sources[N].url` | — | RSS/Atom URL |
| `news-feed.sources[N].category` | `GENERAL` | MARKETS / ECONOMY / GENERAL |

Default sources (built-in): MoneyControl Markets, MoneyControl Business,
ET Markets, BS Markets, LiveMint Markets.

---

## Global markets dashboard

| Key | Default | Purpose |
|---|---|---|
| `global-markets.enabled` | `true` | Master |
| `global-markets.api-key` | `${ALPHA_VANTAGE_KEY:demo}` | Alpha Vantage key (free tier) |
| `global-markets.base-url` | `https://www.alphavantage.co` | AV base |
| `global-markets.refresh-minutes` | `15` | Scheduler cadence |
| `global-markets.fetch-only-during-market-hours` | `true` | Save daily quota |
| `global-markets.page-refresh-seconds` | `60` | /markets auto-refresh |

---

## Pre-market briefing

| Key | Default | Purpose |
|---|---|---|
| `briefing.enabled` | `true` | Master |
| `briefing.generate-cron` | `0 30 8 * * MON-FRI` | 08:30 IST weekdays |
| `briefing.retention-days` | `90` | Purge older |
| `briefing.max-headlines-in-prompt` | `25` | Cap Claude prompt size |
| `briefing.email-enabled` | `false` | Needs SMTP wire-up |
| `briefing.email-to` | — | Recipient |
| `briefing.telegram-enabled` | `false` | Needs bot token |
| `briefing.telegram-bot-token` | — | From @BotFather |
| `briefing.telegram-chat-id` | — | Chat ID |

---

## AI (Claude)

| Key | Default | Purpose |
|---|---|---|
| `ai.enabled` | `true` | Master |
| `ai.provider` | `anthropic` | Only anthropic wired |
| `ai.cache-minutes` | `5` | AiSignalService cache TTL |
| `ai.timeout-seconds` | `15` | HTTP timeout |
| `ai.anthropic.api-key` | `${ANTHROPIC_API_KEY:${ANTHROPIC_AUTH_TOKEN:}}` | API key |
| `ai.anthropic.base-url` | `https://api.anthropic.com` | API base |
| `ai.anthropic.model` | `claude-sonnet-4-5-20250929` | Model |
| `ai.anthropic.api-version` | `2023-06-01` | Required header |
| `ai.anthropic.max-tokens` | `4000` | Response cap |

### AI picks (portfolio)

| Key | Default | Purpose |
|---|---|---|
| `ai-picks.enabled` | `true` | Master |
| `ai-picks.max-universe-size` | `30` | Max stocks per Claude call |
| `ai-picks.candles-per-stock` | `60` | Daily bars sent |
| `ai-picks.min-confidence` | `MEDIUM` | HIGH / MEDIUM / LOW |
| `ai-picks.dedupe-days` | `7` | Skip same OPEN symbol within window |
| `ai-picks.expiry-days` | `90` | Auto-expire OPEN |
| `ai-picks.outcome-check-cron` | `0 45 15 * * MON-FRI` | 15:45 IST post-close |

---

## Dashboard

| Key | Default | Purpose |
|---|---|---|
| `backtest.card.enabled` | `true` | Show/hide Backtest card |
| `backtest.card.look-back-min` | `30` | Range label min |
| `backtest.card.look-back-max` | `365` | Range label max |
| `backtest.card.default-days` | `30` | Form default |

---

## Bias engine

| Key | Default | Purpose |
|---|---|---|
| `bias.enabled` | `true` | Master |
| `bias.lookback-days` | `90` | Candle fetch window |
| `bias.refresh-minutes` | `15` | Change-detection cadence |
| `bias.timeframes[N]` | `ONE_DAY,ONE_HOUR,FIFTEEN_MINUTE,FIVE_MINUTE` | Multi-TF list |
| `bias.cache.provider` | `caffeine-mysql` | caffeine / redis / caffeine-mysql |
| `bias.cache.ttl-seconds` | — | TTL per entry |
| `bias.cache.max-size` | — | L1 size cap |
| `bias.warmer.enabled` | `true` | Pre-fetch |
| `bias.warmer.cron` | — | Cron |
| `bias.warmer.timeout-seconds` | — | Per-round timeout |
| `bias.warmer.circuit.enabled` | `true` | Circuit breaker |
| `bias.vix.enabled` | `true` | VIX section |
| `bias.vix.symbol-token` | `99919011` | India VIX token |
| `bias.vix.spike-threshold` | `18.0` | Elevated |
| `bias.vix.calm-threshold` | `13.0` | Calm |
| `bias.correlated.enabled` | `true` | Bank Nifty cross-check |
| `bias.correlated.symbol-token` | `99926009` | Bank Nifty token |
| `bias.correlated.divergence-percent-threshold` | `0.15` | Flag threshold |
| `bias.breadth.enabled` | `true` | A/D section |
| `bias.breadth.bullish-ratio` | `2.0` | Advances/declines ≥ this = bullish |
| `bias.breadth.bearish-ratio` | `0.5` | ≤ this = bearish |
| `bias.change-alerts.enabled` | `true` | Change notifications |
| `bias.change-alerts.on-bias-flip` | `true` | Fire on direction change |
| `bias.change-alerts.on-score-jump` | `true` | Fire on score ≥ N move |
| `bias.change-alerts.score-jump-threshold` | `2` | Threshold |

---

## Analysis (strategy params)

| Key | Default | Purpose |
|---|---|---|
| `analysis.strategy.default-strategy` | `moving-average-crossover` | Fallback |
| `analysis.strategy.sma.short-period` | `20` | SMA fast |
| `analysis.strategy.sma.long-period` | `50` | SMA slow |
| `analysis.strategy.rsi.period` | `14` | RSI period |
| `analysis.strategy.rsi.oversold` | `30` | Buy below |
| `analysis.strategy.rsi.overbought` | `70` | Sell above |
| `analysis.strategy.macd.fast-period` | `12` | MACD fast EMA |
| `analysis.strategy.macd.slow-period` | `26` | MACD slow EMA |
| `analysis.strategy.macd.signal-period` | `9` | Signal line |
| `analysis.strategy.ensemble.min-agreement` | `2` | Legacy ensemble |
| `analysis.strategy.volume-breakout.lookback` | `20` | Range bars |
| `analysis.strategy.volume-breakout.volume-avg-period` | `20` | Vol MA |
| `analysis.strategy.volume-breakout.volume-multiplier` | `1.5` | Vol threshold |
| `analysis.strategy.volume-breakout.risk-reward` | `2.0` | Target multiple |
| `analysis.strategy.bollinger.period` | `20` | BB period |
| `analysis.strategy.bollinger.std-multiplier` | `2.0` | Band width |
| `analysis.strategy.bollinger.stop-buffer-percent` | `0.1` | Stop buffer |
| `analysis.smc.*` | — | SMC strategy params |

---

## Trading

| Key | Default | Purpose |
|---|---|---|
| `trading.capital` | — | Starting capital for position sizing |
| `trading.risk-percent` | `1.0` | Risk per trade |
| `trading.options.*` | — | Strike offsets by VIX regime |

---

## Reports

| Key | Default | Purpose |
|---|---|---|
| `reports.enabled` | `true` | Daily reports feature |
| `reports.output-dir` | `reports` | Where to write MD files |
| `reports.auto-write-at-close` | `true` | Auto-finalize at close |
| `reports.close-cron` | `0 35 15 * MON-FRI` | 15:35 IST |

---

## Paper trading

| Key | Default | Purpose |
|---|---|---|
| `paper.enabled` | `false` | Paper autostart |
| `paper.sessions[N].*` | — | Preconfigured sessions |

---

## Alerts (stubs)

| Key | Default | Purpose |
|---|---|---|
| `alerts.enabled` | `false` | Master |
| `alerts.telegram.enabled` | `false` | Telegram |
| `alerts.whatsapp.enabled` | `false` | WhatsApp |
| `alerts.on-trade-open` | — | Fire on new signal |
| `alerts.on-trade-close` | — | Fire on close |
| `alerts.on-session-end` | — | Fire on daily close |
| `alerts.on-errors` | — | Fire on errors |

---

## Environment variables used

See `ENV-VARIABLES.md` for the full list. Minimum required:

- `ANGEL_API_KEY`, `ANGEL_CLIENT_CODE`, `ANGEL_PASSWORD`, `ANGEL_TOTP_SECRET`
- `ANTHROPIC_API_KEY` (for AI features)
- `ALPHA_VANTAGE_KEY` (for Global Markets dashboard)
- `DB_USER`, `DB_PASSWORD` (if not using defaults)

---

## Hot-reload

After editing properties:

```bash
curl -X POST -u admin:admin123 http://localhost:9010/actuator/refresh
```

Reloads `@RefreshScope` beans without JVM restart. Non-RefreshScope beans
(the majority) still need a full restart.
