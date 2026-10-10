# How to Use Angle App

Short, in-order guide. Follow top to bottom the first time.

For architecture, see `ARCHITECTURE.md`.
For every endpoint, see `API.md`.
For every config knob, see `CONFIGURATION.md`.

---

## 1. One-time setup

### Prerequisites

- Java 17+
- Maven
- MySQL running on `localhost:3306`
- Angel One SmartAPI credentials (API key, client code, password, TOTP secret)
- Anthropic API key (for AI features)
- Alpha Vantage API key (free tier — https://www.alphavantage.co/support/#api-key)

### Clone + configure

```bash
cd angle-app
cp .env.example .env           # if present; otherwise create one
```

Set env vars in the shell where you'll run the app:

```bash
export ANGEL_API_KEY="..."
export ANGEL_CLIENT_CODE="..."
export ANGEL_PASSWORD="..."
export ANGEL_TOTP_SECRET="..."
export ANTHROPIC_API_KEY="sk-ant-..."
export ALPHA_VANTAGE_KEY="..."
export DB_USER="root"            # if not root
export DB_PASSWORD="..."
```

Then start:

```bash
./mvnw spring-boot:run
```

First boot: Hibernate creates all tables. Visit http://localhost:9010 →
redirects to `/auth/login` (`admin` / `admin123`).

---

## 2. Configure instruments

Visit `/admin/instruments` and add the symbols you want to track:

| Field | Example (Nifty 50) |
|---|---|
| Symbol | Nifty 50 |
| Broker | ANGEL |
| Exchange | NSE |
| Symbol token | 99926000 |
| Interval type | FIVE_MINUTE |
| Lot size | 75 |
| Priority | 10 (lower = warmed first) |

Use the autocomplete field (type "Nifty" / "Reliance" / "Crude" and pick
from the dropdown — it queries Angel's scrip master).

Common tokens:
- Nifty 50: `99926000`
- Bank Nifty: `99926009`
- Fin Nifty: `99926037`
- Reliance: `2885`
- India VIX: `99919011`

---

## 3. Verify the data pipeline

Open `/live/ticker`. During market hours (09:15 – 15:30 IST Mon-Fri) you
should see your instruments ticking in real-time with price flashes.

- Status pill shows `Connected` → WebSocket is up.
- Prices update every 1-5 seconds per instrument.
- Outside market hours, cards show last-close prices (no flashing — normal).

If prices don't appear:
1. Check logs for `AngelAuthService` success.
2. Check `/live/stream` SSE endpoint directly (`curl -N -u admin:admin123
   http://localhost:9010/api/live/stream`).
3. Verify broker.angel.stream.enabled=true in config.

---

## 4. Check the bias engine

Open `/bias?token=99926000` (or whatever token you added).

You'll see a full bias sheet:
- Market context (open/close, VIX, breadth)
- Multi-timeframe bias (daily / 1H / 15M / 5M)
- Trend filters (EMA, VWAP, SuperTrend, ADX, ATR)
- Market structure (BOS/CHoCH + swing points)
- Zones (order blocks, FVGs, liquidity levels)
- Cross-market check (Bank Nifty vs Nifty)
- Consolidated score + trade plan

Click `Ask AI` for Claude's opinion on this specific setup.

---

## 5. Watch signals

Open `/signals`. During market hours, signals fire automatically when:
- 3+ of 7 strategies agree on direction
- Regime gate allows (time + VIX + ADX)
- MTF confirmation passes (15m + 1H agree)
- No news blackout active
- Not deduped against recent signal

Filter by:
- Status (OPEN / HIT_TARGET / HIT_STOP / EXPIRED)
- Source (CONSENSUS / AI)
- Instrument
- Hours

Each OPEN signal is tracked: `LiveSignalMonitor` watches ticks and closes
on target/stop. Trailing stops ratchet when price moves favourably.

Scorecard: top of page shows win rate + today's counts.

---

## 6. Backtest a strategy

Open `/admin/backtest`.

Pick:
- Instrument (from your watchlist)
- Interval (FIVE_MINUTE for intraday testing)
- Days back (30 recommended — must be ≥ 10 for enough history)
- Min agreement (3 default)
- Trailing mode (PERCENT default)
- Capital (₹500,000)
- Risk % (1.0)
- Lot size (match your instrument)

Click `Run`. ~5 seconds later: full stats, equity curve, per-trade log.

Columns in trade log:
- `#` — trade index
- `Entry / Target / Stop / Agreed / Strategies / Lots / Units / Notional ₹`
- `Exit / Reason` — TARGET / STOP / TRAILED / EXPIRED
- `Return % / P&L ₹`

What to look for:
- Win rate > 50%
- Profit factor > 1.3
- Max drawdown < 15% of capital

If results look wrong, see `TROUBLESHOOTING.md` → "Backtest returns zero
signals" or "Entry price doesn't match chart".

---

## 7. Portfolio (medium-term)

Open `/portfolio`. Click `Generate` to ask Claude for picks based on your
instrument list.

Each pick shows:
- Action: BUY / HOLD / AVOID
- Entry / Target / Stop
- Confidence: HIGH / MEDIUM / LOW
- Horizon: typically 30-90 days
- Rationale (2-3 sentences)

Picks are tracked daily by `AiPickOutcomeService` (runs 15:45 IST). You'll
see them close as HIT_TARGET / HIT_STOP / EXPIRED over coming weeks.

Scorecard shows your running win rate — treat as a research assistant,
not a guarantee.

Cost: ~$0.05-0.15 per generation.

---

## 8. Daily morning routine

### 08:30 IST (auto)

`BriefingService` kicks off:
1. Snapshots global markets
2. Pulls top-25 tagged news from last 24h
3. Asks Claude for a 200-word briefing with BIAS + DRIVERS + SUMMARY

### You, 08:35 – 09:15

Open `/briefing`. Read today's bias and drivers. Note the suggested
direction for Nifty + sectors to watch.

### Secondary pages

- `/markets` — oil/gold/USD-INR/Dow/Nikkei at a glance.
- `/news` — filter by sentiment + sector for context.
- `/bias` — full bias sheet for each instrument.

### 09:15 – 15:30

Market open. Signals fire automatically on `/signals`. Positions tracked
automatically. Trailing stops ratchet. You just execute trades on your
broker platform matching the signals (manual).

### 15:35

Daily report auto-finalizes to disk under `reports/`.

### 15:45

Portfolio outcome tracker runs. OPEN picks that hit target/stop are
marked.

---

## 9. Toggles & kill switches

If any filter is causing problems, flip it off:

| Feature | Toggle |
|---|---|
| Master signal detection | `POST /api/bias/enable?on=false` (actually disables bias; signal detector has its own flag — edit `signals.detector.enabled`) |
| Regime gates | `POST /api/regime/enable?on=false` |
| MTF confirmation | `POST /api/mtf/enable?on=false` |
| Trailing stops | `POST /api/trailing/enable?on=false` |
| News blackout | `POST /api/news/enable?on=false` |
| AI confirmer | Edit `signals.detector.ai-confirm=false` + restart |

All of these have web toggles too on `/admin/pipeline`.

---

## 10. Common tasks

| Task | How |
|---|---|
| Add a stock to watch | `/admin/instruments` → "+ Add Instrument" |
| Add a news event | Edit `src/main/resources/news-calendar.yml` → `POST /api/news/reload` |
| Switch theme | Click ☀️/🌙 button in sidebar |
| Force a backtest run | `/admin/backtest` → fill form → Run |
| See today's AI picks | `/portfolio` |
| Check why a signal didn't fire | Logs: `grep "SignalDetector" logs/app.log` |
| Clear bias cache | `/dashboard` → Cache card → `🧹 Clear` |
| Reset warmer circuit | `/dashboard` → Warmer card → `🔧 Reset` |

---

## 11. When something goes wrong

See `TROUBLESHOOTING.md`. Most common issues:

- Angel auth fails → delete `angel_token` DB row + restart (forces fresh
  TOTP login).
- Backtest returns 0 signals → days-back too low (need ≥ 10 for 5-min).
- `/markets` shows "unavailable" → `ALPHA_VANTAGE_KEY` not set in the
  shell that started the app.
- Claude calls fail → `ANTHROPIC_API_KEY` missing or quota exceeded.

---

## 12. Costs (rough monthly)

| Service | Cost |
|---|---|
| Angel SmartAPI | Free (retail) |
| MySQL | Local → free |
| Alpha Vantage | Free (500 calls/day) |
| Anthropic Claude | ~$5-15 depending on usage |
| Hosting (optional) | $5-10 if deployed to a VPS |

**Expected total: $5-25/month** depending on how often you use AI features.
