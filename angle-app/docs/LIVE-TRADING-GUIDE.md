# Live Trading Guide

Step-by-step guide to running the app on live market data. Paper trading —
no real orders placed, but real Angel candles feed the strategy.

**Reading time:** 15 minutes. **Setup time:** 30 minutes first run.

---

## 1. What "live trading" means here

- The app polls Angel every N seconds for the latest candle
- Your strategy runs on each new candle
- When strategy says BUY/SELL, the app logs it to the terminal
- **No real orders are placed** — you decide manually whether to trade

Think of it as a **live trading assistant** telling you "if I were trading, I'd do X right now."

---

## 2. Prerequisites

### 2.1 Angel SmartAPI account with credentials

You need FOUR credentials from your Angel account:
- API Key
- Client Code
- Password / MPIN
- TOTP Secret (base32 seed)

**Where to get them:** [smartapi.angelbroking.com](https://smartapi.angelbroking.com) → create app → note API key. Then enable TOTP 2FA and save the secret.

See [ENV-VARIABLES.md](ENV-VARIABLES.md) for detailed steps.

### 2.2 Environment variables set

```bash
export ANGEL_API_KEY=your_api_key
export ANGEL_CLIENT_CODE=A123456
export ANGEL_PASSWORD=your_mpin
export ANGEL_TOTP_SECRET=your_totp_base32_secret
```

Verify:
```bash
echo "API_KEY=$ANGEL_API_KEY"
echo "CLIENT=$ANGEL_CLIENT_CODE"
```

Both must be non-empty in the same terminal you'll launch the app from.

### 2.3 Java 17 + Maven

```bash
java -version    # should be 17+
./mvnw -v        # should show Maven 3.8+
```

### 2.4 Market open (for live-only)

For `angel-live` source, the market must be open:

| Exchange | Hours (IST) |
|----------|-------------|
| NSE / BSE equity / F&O | 09:15 – 15:30 Mon–Fri |
| MCX (crude / gold / silver) | 09:00 – 23:30 Mon–Fri |
| CDS (currency) | 09:00 – 17:00 Mon–Fri |

Off-hours: session runs but no new candles arrive.

---

## 3. First run — 5 minutes

### Step 1 — Start the app

```bash
cd /path/to/angle-app
./mvnw spring-boot:run -Dspring-boot.run.profiles=nosec
```

`nosec` disables auth so you can hit endpoints without login.

Wait for:
```
Started AngleAppApplication in ... seconds
```

**Keep this terminal open — you'll watch it during trading.**

### Step 2 — Confirm scrip master loaded

Open a new terminal (or Postman):

```bash
curl http://localhost:9010/api/instruments/status
```

Should return `{"count": 143000+}`. If it says 0, wait 30 seconds and retry.

### Step 3 — Start a live session (Nifty 50)

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

**Copy the `sessionId` from the response.** You'll use it in Step 5.

### Step 4 — Watch the app terminal

Within seconds you'll see:
```
[INFO] Angel login OK, JWT cached until ...
[INFO] Warmup emitted 100 candles (fetched 128, most recent 2026-09-05T14:00:00Z)
[INFO] ┌─ SESSION START [a3f1c2d0] strategy=ensemble source=angel-live(NSE:99926000,FIVE_MINUTE,poll=60s)
```

Every minute after that, if a new bar closes:
```
[DEBUG] Live poll emitted 1 new candle(s), latest 2026-09-05T14:05:00Z
```

When strategy fires:
```
│  [a3f1c2d0] OPEN  LONG  @ 24680.75  stop=24450 target=24810  :: 3/5 long (stop:sweep-fvg)
```

When target/stop hits:
```
│  [a3f1c2d0] CLOSE LONG  @ 24810  (TARGET)  pnl=+129.25
```

### Step 5 — Check session status any time

```bash
curl -s "http://localhost:9010/api/paper/sessions/YOUR_SESSION_ID" | jq
```

Key fields:
- `status` — RUNNING / STOPPED / COMPLETED
- `candleCount` — grows over time
- `lastIntent` — most recent decision
- `openPosition` — currently-open trade (null if flat)
- `trades` — all completed trades
- `netPnl` — running total

---

## 4. What to do when strategy fires

### 4.1 On OPEN log

The app says: **"I would buy/sell right now."**

**Your action:**
1. Open your real broker app (Angel/Zerodha/etc.)
2. Place the same trade **manually**
3. Set stop-loss and target at the levels shown

**Do NOT trust it blindly on day 1.** Watch for a week first. Only trade real money after you've seen the strategy make consistently good calls.

### 4.2 On CLOSE log

The app says: **"my trade would have exited here."**

**Your action:**
1. If you placed the real trade, exit it now
2. Note the P&L
3. Compare with the app's reported pnl

Ideally your real P&L matches the app's within a few rupees (difference = slippage + fees).

### 4.3 On HOLD (no log)

Nothing to do. Wait for the next candle.

---

## 5. Different instruments

### Nifty 50 index
```json
{ "symbolToken": "99926000", "exchange": "NSE" }
```

### Bank Nifty
```json
{ "symbolToken": "99926009", "exchange": "NSE" }
```

### Individual stocks
```bash
# Reliance
{ "symbolToken": "2885", "exchange": "NSE" }

# HDFC Bank
{ "symbolToken": "1333", "exchange": "NSE" }
```

### Crude Oil (MCX)

Tokens rotate monthly — look up the front-month contract:

```bash
curl "http://localhost:9010/api/instruments/futures?underlying=CRUDEOIL" | jq
```

Copy the front-month `token`, then:
```json
{
  "symbolToken": "436250",
  "exchange": "MCX",
  "interval": "FIVE_MINUTE",
  "warmupCandles": 200,
  "pollIntervalSeconds": 60
}
```

### Nifty option
```bash
curl "http://localhost:9010/api/instruments/option?underlying=NIFTY&expiry=2026-09-15&strike=26000&type=PE" | jq
```

Copy the token, then:
```json
{
  "symbolToken": "47432",
  "exchange": "NFO",
  "interval": "FIVE_MINUTE",
  "warmupCandles": 100,
  "pollIntervalSeconds": 30
}
```

---

## 6. Choosing strategy

Six strategies are available. Pick based on your style:

| Strategy | Style | When to use |
|----------|-------|-------------|
| `moving-average-crossover` | Slow trend follower | Strong trending days |
| `rsi-mean-reversion` | Fast reversal | Choppy / ranging days |
| `macd-crossover` | Medium momentum | Everything, but late |
| `ob-retest` | SMC — order block retest | Clean structural setups |
| `sweep-fvg` | SMC — liquidity + FVG | Reversal setups |
| `ensemble` | **All 5 combined** | **Recommended default** |

Pass in the POST body: `"strategyName": "ensemble"`.

### Ensemble tuning

The ensemble requires N strategies to agree before trading. Default `minAgreement=2`. Change via properties:

```properties
analysis.strategy.ensemble.min-agreement=3   # stricter — fewer higher-conviction trades
```

Or per-run for testing:
```bash
curl "http://localhost:9010/api/analysis/backtest?strategy=ensemble&minAgreement=3"
```

---

## 7. Multiple concurrent sessions

Run the ensemble on 3 instruments at once:

```bash
# Session 1 — Nifty
curl -X POST http://localhost:9010/api/paper/sessions \
  -H "Content-Type: application/json" \
  -d '{"strategyName":"ensemble","sourceType":"angel-live","symbolToken":"99926000","exchange":"NSE","interval":"FIVE_MINUTE","warmupCandles":100,"pollIntervalSeconds":60}'

# Session 2 — Bank Nifty
curl -X POST http://localhost:9010/api/paper/sessions \
  -H "Content-Type: application/json" \
  -d '{"strategyName":"ensemble","sourceType":"angel-live","symbolToken":"99926009","exchange":"NSE","interval":"FIVE_MINUTE","warmupCandles":100,"pollIntervalSeconds":60}'

# Session 3 — Reliance
curl -X POST http://localhost:9010/api/paper/sessions \
  -H "Content-Type: application/json" \
  -d '{"strategyName":"ensemble","sourceType":"angel-live","symbolToken":"2885","exchange":"NSE","interval":"FIVE_MINUTE","warmupCandles":100,"pollIntervalSeconds":60}'
```

Terminal shows all three interleaved — each prefixed by its short session ID.

List all:
```bash
curl "http://localhost:9010/api/paper/sessions"
```

---

## 8. Stopping sessions

### Stop one session

```bash
curl -X POST "http://localhost:9010/api/paper/sessions/YOUR_SESSION_ID/stop"
```

Force-closes any open position at the last candle's close, marks session STOPPED.

### Stop all sessions

Just restart the app — sessions live in memory only, restart wipes everything.

---

## 9. Auto-start on app boot

If you don't want to POST every morning, configure auto-start:

Edit `src/main/resources/application.properties`:

```properties
paper.autostart.enabled=true
paper.autostart.sessions[0].strategy-name=ensemble
paper.autostart.sessions[0].source-type=angel-live
paper.autostart.sessions[0].symbol-token=99926000
paper.autostart.sessions[0].exchange=NSE
paper.autostart.sessions[0].interval=FIVE_MINUTE
paper.autostart.sessions[0].warmup-candles=100
paper.autostart.sessions[0].poll-interval-seconds=60
```

Restart the app. Session auto-runs. Just leave the app running each day.

For multiple instruments, add `sessions[1]`, `sessions[2]`, ...

---

## 10. Common issues

### 10.1 `Invalid Bad Request` from Angel

- Check `symbolToken` — did you paste the placeholder `PASTE_AFTER_LOOKUP`?
- For options / MCX, tokens change — always look up fresh via `/api/instruments/futures` or `/api/instruments/option`

### 10.2 `Angel credentials missing`

- Check all 4 env vars are set: `echo $ANGEL_API_KEY`
- Set them in the **same terminal** that runs the app
- Restart the app after setting

### 10.3 `Invalid TOTP`

- Wrong TOTP secret, or your laptop clock is out of sync
- Fix clock: `sudo sntp -sS time.apple.com`
- Verify TOTP: compare with Google Authenticator app on your phone (both should show the same 6 digits)

### 10.4 Session runs but nothing appears

Three possibilities:
1. **Outside market hours** — check IST time
2. **Strategy says HOLD** — normal, especially on calm days
3. **Bug** — verify:
   ```bash
   curl "http://localhost:9010/api/paper/sessions/YOUR_ID" | jq '.candleCount'
   ```
   If `candleCount` isn't growing every minute, polling isn't happening

### 10.5 App terminal is quiet

Enable debug logging in `application.properties`:
```properties
logging.level.com.angle.trading.paper=DEBUG
```

Then you'll see every candle arrive.

### 10.6 Sessions lost after restart

Known limitation — no persistence yet. All sessions and trade history vanish on restart.

Solutions:
- Don't restart during the trading day
- Or set up auto-start (Section 9) so sessions restart automatically

---

## 11. Reading the console output

```
┌─ SESSION START [a3f1c2d0] strategy=ensemble source=angel-live(NSE:99926000,FIVE_MINUTE,poll=60s)
│  [a3f1c2d0] candles=100 trades=0 wins=0 losses=0 net=+0
│  [a3f1c2d0] OPEN  LONG  @ 24680.75  stop=24450 target=24810  :: 3/5 long (stop:sweep-fvg)
│  [a3f1c2d0] CLOSE LONG  @ 24810  (TARGET)  pnl=+129.25
│  [a3f1c2d0] candles=150 trades=1 wins=1 losses=0 net=+129.25
```

| Symbol | Meaning |
|--------|---------|
| `┌─ SESSION START` | Session opened |
| `│  ` | Session running / event |
| `└─ SESSION COMPLETED` | Session ended (source exhausted or stopped) |
| `[a3f1c2d0]` | Short session id — useful when running multiple sessions |
| `OPEN LONG` | Strategy signalled a buy — this is the "act now" moment |
| `CLOSE LONG (TARGET)` | Trade closed at profit target |
| `CLOSE LONG (STOP)` | Trade closed at stop-loss |
| `CLOSE LONG (SIGNAL_EXIT)` | Trade closed because strategy said EXIT |
| `pnl=+129.25` | Green: +number → profit. Red: -number → loss |
| Periodic status | `candles=... trades=... wins=... losses=... net=...` — every 50 candles |

---

## 12. Safety recommendations

### 12.1 Never trade real money on Day 1

Watch for at least a week. Note:
- How often does the strategy fire? (should feel reasonable)
- Are the entry prices actually achievable? (or is there a slippage problem)
- What's the win rate? (aim for 40%+ before real trading)

### 12.2 Start with small size

When you do go real:
- 1 lot only
- Only on Nifty (highest liquidity)
- Set your OWN stop-loss even if the app suggests one

### 12.3 Don't override the strategy

If the app says HOLD, don't trade. If it says SELL, don't hold hoping for more.
The strategy has rules — override them and you lose the whole point.

### 12.4 Know the max loss

Before every trade, know:
- Entry: what price you'll enter
- Stop: what price you'll exit if wrong (max loss = entry - stop)
- Target: what price you'll exit at profit

If any of these is missing or unclear → **skip the trade**.

### 12.5 Track everything

Even in paper mode, keep a spreadsheet:
- Date, time, strategy, symbol
- Entry, stop, target
- Exit, exitReason, pnl
- Your note ("would I have taken this?")

After a month of data you'll know if the strategy is worth real money.

---

## 13. Postman shortcuts

If curling from terminal feels tedious, use the Postman collection in
`postman/`:

- **Paper Trading — Live NSE** folder — one-click starts for Nifty, Bank Nifty, Reliance
- **Paper Trading — Live MCX** folder — one-click for Crude, Gold, Silver
- **Paper Trading — Session management** folder — list, snapshot, stop

Set the `sessionId` env var once after starting a session; then poll and
stop with just clicks.

---

## 14. Quick reference — full URL list

| Action | Method | URL |
|--------|--------|-----|
| List strategies | GET | `/api/analysis/strategies` |
| Instrument status | GET | `/api/instruments/status` |
| Lookup by symbol | GET | `/api/instruments/lookup?symbol=X` |
| Find NIFTY option | GET | `/api/instruments/option?underlying=NIFTY&expiry=...&strike=...&type=CE` |
| MCX futures list | GET | `/api/instruments/futures?underlying=CRUDEOIL` |
| One-shot analyst | GET | `/api/analysis/analyst?broker=ANGEL&symbolToken=99926000` |
| **Start live session** | POST | `/api/paper/sessions` |
| List sessions | GET | `/api/paper/sessions` |
| Session snapshot | GET | `/api/paper/sessions/{id}` |
| Stop session | POST | `/api/paper/sessions/{id}/stop` |

---

## 15. Where to go from here

Once you're comfortable with live paper trading:

1. **Try different strategies** — compare which fires more / wins more on live data
2. **Test on different instruments** — some strategies work better on Nifty than crude
3. **Tune `minAgreement`** on ensemble — see [DESIGN-PATTERNS-IN-USE.md](DESIGN-PATTERNS-IN-USE.md) for context
4. **Read the console patterns** — after a few days you'll spot bad setups
5. **Add persistence** — so restart doesn't lose your trade history (Phase 3 in plan)
6. **Add alerts** — get Telegram pings on entries (paper-trading Phase C)

Related docs:
- [ONBOARDING.md](ONBOARDING.md) — new-developer tour
- [ARCHITECTURE.md](ARCHITECTURE.md) — how the pieces fit together
- [DESIGN-PATTERNS-IN-USE.md](DESIGN-PATTERNS-IN-USE.md) — every pattern in the code
- [SMC-LIQUIDITY-PLAN.md](SMC-LIQUIDITY-PLAN.md) — how SMC strategies work
- [ENV-VARIABLES.md](ENV-VARIABLES.md) — credentials setup
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) — common problems
