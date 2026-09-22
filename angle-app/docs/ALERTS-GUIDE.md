# Alerts Guide

Get pinged on your phone every time your strategy opens or closes a trade.
No more babysitting the terminal.

**Two channels supported: Telegram and WhatsApp.** Turn either on, or both.

---

## 1. What you'll get

Every time something happens in your paper-trading session, a message
arrives on your phone.

**On trade open:**
```
🟢 OPEN LONG
Strategy: ensemble
Source: angel-live(NSE:99926000,ONE_MINUTE)
Entry: 24680.75
Stop: 24450
Target: 24810
Session: a3f1c2d0
Reason: 3/5 long
```

**On trade close (winner):**
```
✅ CLOSE LONG (TARGET)
Strategy: ensemble
Source: angel-live(NSE:99926000,ONE_MINUTE)
Exit: 24810
P&L: +129.25
Session: a3f1c2d0
```

**On trade close (loser):**
```
❌ CLOSE LONG (STOP)
Strategy: ensemble
Exit: 24450
P&L: -230.50
Session: a3f1c2d0
```

**On session end:**
```
🏁 SESSION END
Strategy: ensemble
Trades: 8 (wins 5 / losses 3)
Net: +425.75
Session: a3f1c2d0
```

---

## 2. Pick your channel(s)

| Channel | Best for | Setup time |
|---------|---------|-----------|
| **Telegram** | Instant delivery, works anywhere, free | 3 min |
| **WhatsApp** | Same app as your friends, one place for everything | 5 min |
| **Both** | Redundancy — if one fails you still get pinged | 8 min |

You can toggle each independently. Enable one this week, add the other later.

---

## 3. Telegram setup

### Step 1 — Create a Telegram bot

1. Open Telegram
2. Search for **`@BotFather`**
3. Send `/newbot`
4. Give it a name (e.g. `AngleAppAlerts`)
5. Give it a username ending in `bot` (e.g. `angleapp_alerts_bot`)
6. BotFather replies with a **bot token** — looks like:
   ```
   1234567890:AAExxxxxxxxxxxxxxxxxxxxxxxxxx
   ```
7. Copy and save it.

### Step 2 — Get your chat ID

1. Open your new bot (search for its username) and send it any message (e.g. `hi`)
2. Open this URL in your browser (replace `<YOUR_TOKEN>` with your bot token):
   ```
   https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates
   ```
3. Find this in the response:
   ```json
   "chat": { "id": 123456789, ... }
   ```
4. Copy the **numeric id** — that's your chat ID.

### Step 3 — Set env vars

In the terminal where you'll run the app:

```bash
export TELEGRAM_BOT_TOKEN=1234567890:AAExxxxxxxxxxxxxxxxxxxxxxxxxx
export TELEGRAM_CHAT_ID=123456789
```

### Step 4 — Enable in `application.properties`

```properties
alerts.enabled=true
alerts.telegram.enabled=true
```

### Step 5 — Restart the app

```bash
./mvnw spring-boot:run
```

Look for this in the startup log:
```
AlertService initialised: enabled=true, channels=[telegram, whatsapp]
```

Done. Any paper session that opens a trade now pings your Telegram.

---

## 4. WhatsApp setup

### Step 1 — Get CallMeBot API key

1. Save this number to your phone contacts:
   ```
   +34 644 51 95 23
   ```
   Name it "CallMeBot".

2. Open WhatsApp → open that contact → send this exact message:
   ```
   I allow callmebot to send me messages
   ```

3. Wait ~2 minutes. CallMeBot replies:
   ```
   API Activated for your phone number.
   Your APIKEY is 1234567
   ```

4. Copy the **7-digit API key**.

### Step 2 — Set env vars

```bash
export WHATSAPP_PHONE=919876543210     # your number with country code, NO + sign
export WHATSAPP_API_KEY=1234567         # the key CallMeBot gave you
```

**Note:** phone must include country code, without `+`.
Examples: India `91xxxxxxxxxx`, US `1xxxxxxxxxx`.

### Step 3 — Enable in `application.properties`

```properties
alerts.enabled=true
alerts.whatsapp.enabled=true
```

### Step 4 — Restart the app

```bash
./mvnw spring-boot:run
```

Startup log should show `whatsapp` in the channels list.

### Step 5 — Test it works

Before running a session, test WhatsApp directly:

```bash
curl "https://api.callmebot.com/whatsapp.php?phone=$WHATSAPP_PHONE&text=hello&apikey=$WHATSAPP_API_KEY"
```

Should arrive on your WhatsApp within seconds. If yes → app alerts will
work. If no → check your env vars are set correctly.

---

## 5. Enabling BOTH channels

Same as above — just enable both:

```properties
alerts.enabled=true
alerts.telegram.enabled=true
alerts.whatsapp.enabled=true
```

Every alert now goes to both apps at the same time.

---

## 6. Turning off alerts

Any of these turn things off:

| To do this | Change |
|-----------|--------|
| Kill all alerts | `alerts.enabled=false` |
| Kill Telegram only | `alerts.telegram.enabled=false` |
| Kill WhatsApp only | `alerts.whatsapp.enabled=false` |
| Kill trade-open pings | `alerts.on-trade-open=false` |
| Kill trade-close pings | `alerts.on-trade-close=false` |
| Kill end-of-session ping | `alerts.on-session-end=false` |

Change, restart app, done.

---

## 7. Test everything works

Fastest way to trigger real alerts:

```bash
curl -X POST http://localhost:9010/api/paper/sessions \
  -H "Content-Type: application/json" \
  -d '{"strategyName":"ensemble","sourceType":"replay-nifty-csv","candlesPerSecond":30}'
```

Within ~10 seconds you should see multiple pings on your phone:
- Several 🟢 OPEN messages
- Matching ✅/❌ CLOSE messages
- Final 🏁 SESSION END with total P&L

---

## 8. Common issues

### Telegram

**No message arriving**
- Verify you messaged your bot at least once (Telegram requires this)
- Check env var: `echo $TELEGRAM_BOT_TOKEN`
- Test directly:
  ```bash
  curl -X POST "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage" \
    -d "chat_id=$TELEGRAM_CHAT_ID&text=hello"
  ```

**"Chat not found"**
- Wrong chat ID. Re-run `getUpdates` and check.

### WhatsApp

**"You need to ask for permission first"**
- You skipped step 1. Send `I allow callmebot to send me messages` from
  your WhatsApp first.

**Nothing arrives**
- Test directly:
  ```bash
  curl "https://api.callmebot.com/whatsapp.php?phone=$WHATSAPP_PHONE&text=hello&apikey=$WHATSAPP_API_KEY"
  ```
- If the direct test works but app doesn't → env vars aren't loaded in
  the app's terminal.

**Rate-limit errors on lots of alerts**
- CallMeBot allows ~10 messages/minute. If your strategy spams signals,
  some drop. Tune your strategy (`ensemble.min-agreement=3` for stricter)
  or increase your poll interval.

### General

**Startup log shows `channels=[]`**
- Config or env vars are wrong. Check the properties + env vars again.

**Alerts arrive but slowly**
- Not the app's fault — Telegram / WhatsApp servers can occasionally be
  slow. The app sends immediately.

---

## 9. When to have alerts on vs off

| Situation | Recommended |
|-----------|-------------|
| Live paper trading during market hours | ON — you want to know when signals fire |
| Historical replay / backtest (thousands of trades) | OFF — will spam your phone |
| Overnight / weekend testing | ON (nothing fires anyway) |
| Debugging / trying strategies | OFF — too noisy |
| Real money live trading | ON — critical for safety |

## 10. Safety note

Alerts tell you what the strategy is doing. **You still decide** whether
to place the trade in your real broker account. The app never places
real orders — it's an assistant, not an auto-trader.

---

## Where to go next

- [LIVE-TRADING-GUIDE.md](LIVE-TRADING-GUIDE.md) — full workflow for
  live paper trading (Angel + strategies + alerts working together)
- [FEATURES.md](FEATURES.md) — everything else the app can do
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) — general problems
