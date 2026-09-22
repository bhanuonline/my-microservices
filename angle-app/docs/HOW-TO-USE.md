# How to Use Angle App

Short, in-order guide. Follow top to bottom the first time.

---

## 1. Prerequisites (one-time)

- Java 17+, Maven, MySQL running
- Angel SmartAPI credentials
- Optional: Telegram bot token OR WhatsApp CallMeBot key

See [SETUP.md](SETUP.md) + [ENV-VARIABLES.md](ENV-VARIABLES.md).

---

## 2. Configure

Edit `src/main/resources/application.properties` — set:

```properties
# MySQL
spring.datasource.username=root
spring.datasource.password=your_mysql_pass

# Alerts (optional, but recommended)
alerts.enabled=true
alerts.telegram.enabled=true
```

Set env vars in your shell:
```bash
export ANGEL_API_KEY=...
export ANGEL_CLIENT_CODE=...
export ANGEL_PASSWORD=...
export ANGEL_TOTP_SECRET=...
export TELEGRAM_BOT_TOKEN=...  # if using alerts
export TELEGRAM_CHAT_ID=...
```

---

## 3. Start the app

```bash
cd /Users/bhanupratap/My/my-microservices/angle-app
./mvnw spring-boot:run -Dspring-boot.run.profiles=nosec
```

Wait for: `Started AngleAppApplication in ... seconds`

**Keep this terminal open** — you'll watch it for trade signals.

---

## 4. Verify things are working (30 seconds)

```bash
# Scrip master loaded?
curl http://localhost:9010/api/instruments/status

# Strategies loaded?
curl http://localhost:9010/api/analysis/strategies
```

Both should return data. If either fails, check the terminal for errors.

---

## 5. Open the bias dashboard (browser)

Go to: **`http://localhost:9010/bias`**

You'll see:
- Recommendation (LONG / SHORT / NO-TRADE)
- All indicators + market structure + zones + Bank Nifty + VIX + breadth
- Auto-refreshes every 15 min

**This is your primary morning tool.** Look at it and decide direction.

---

## 6. Start a live paper session (optional but recommended)

Fires alerts to your phone when strategy signals:

```bash
curl -X POST http://localhost:9010/api/paper/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "strategyName": "ensemble",
    "sourceType": "angel-live",
    "symbolToken": "99926000",
    "exchange": "NSE",
    "interval": "FIVE_MINUTE",
    "warmupCandles": 100,
    "pollIntervalSeconds": 60
  }'
```

Save the `sessionId` from response.

---

## 7. During the trading day

**Nothing to do actively.** Watch:

- **Your phone** → alerts fire on OPEN / CLOSE
- **App terminal** → detailed log of each trade
- **Dashboard** → refresh anytime to see current bias

When an alert fires with an OPEN signal you like:
- Manually place the trade in the actual Angel app
- Use the same stop and target the alert shows

---

## 8. Analyze a past day (any time)

```bash
curl -X POST http://localhost:9010/api/paper/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "strategyName": "ensemble",
    "sourceType": "angel-historical-replay",
    "symbolToken": "99926000",
    "exchange": "NSE",
    "interval": "FIVE_MINUTE",
    "from": "2026-09-04",
    "to": "2026-09-04",
    "candlesPerSecond": 30
  }'
```

Watch the terminal — replays that entire day in ~5 seconds.

---

## 9. End of day (15:35 IST or later)

App auto-writes a report to `reports/YYYY-MM-DD.md`.

Force-write on demand:
```bash
curl -X POST http://localhost:9010/api/reports/today/finalize
open reports/$(date +%Y-%m-%d).md
```

Open the file → review trades → add manual notes at bottom.

---

## 10. Stop or restart

**Stop app:** Ctrl+C in the terminal.

**Restart:** re-run step 3. Sessions from before are lost (in-memory), but trade history stays in MySQL.

---

## Daily workflow — in 3 lines

```
1. Start app (step 3)
2. Open /bias in browser (step 5) — decide direction
3. Watch alerts + terminal (step 7) — place real trades in Angel app manually
```

That's it. Everything else is on-demand.

---

## Quick URL cheat sheet

| URL | What |
|-----|------|
| `http://localhost:9010/bias` | Live bias dashboard |
| `http://localhost:9010/api/analysis/analyst` | One-shot analyst JSON |
| `http://localhost:9010/api/paper/sessions` | List / create paper sessions |
| `http://localhost:9010/api/paper/sessions/{id}` | Session snapshot |
| `http://localhost:9010/api/reports/today` | Today's report markdown |
| `http://localhost:9010/api/indicators/csv` | Indicator values on CSV |
| `http://localhost:9010/dashboard` | Web dashboard (auth on) |

---

## Related docs

- **[LIVE-TRADING-GUIDE.md](LIVE-TRADING-GUIDE.md)** — full live-trading walkthrough
- **[ALERTS-GUIDE.md](ALERTS-GUIDE.md)** — Telegram + WhatsApp setup
- **[DAILY-BIAS-SHEET.md](DAILY-BIAS-SHEET.md)** — the checklist behind the dashboard
- **[FEATURES.md](FEATURES.md)** — everything the app can do
- **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** — when things break

---

## Safety reminder

App is **paper trading only** — no real orders are placed. YOU decide whether to trade based on its signals. Trade small, journal everything, review weekly.
