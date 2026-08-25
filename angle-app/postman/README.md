# AngleApp Postman Collection

Two files:

- `angle-app.postman_collection.json` — all endpoints
- `angle-app.postman_environment.json` — variables (base URL, tokens, credentials)

## Import

1. Postman → **Import** (top-left)
2. Drag both `.json` files in
3. Top-right dropdown → select **AngleApp Local**

## Folders

| # | Folder | Purpose |
|---|--------|---------|
| 1 | **Analysis — Backtest & Strategies** | Backtest each of the 6 strategies against the CSV; ensemble tuning |
| 2 | **Analysis — Analyst (buy/sell decision)** | One-shot analyst report — CSV or live Angel |
| 3 | **Analysis — SMC pipeline** | Structure, zones, MarketContext |
| 4 | **Analysis — Candles (live fetch)** | Raw candle data from Angel — NSE / NFO / MCX |
| 5 | **Instruments** | Scrip master lookup, option chain, MCX futures lookup |
| 6 | **Paper Trading — Replay (bundled CSV)** | Simulate trading on Nifty CSV at speed |
| 7 | **Paper Trading — Live NSE** | Live sessions on Nifty / Bank Nifty / equities |
| 8 | **Paper Trading — Live MCX** | Live sessions on Crude / Gold / Silver |
| 9 | **Paper Trading — Session management** | List / snapshot / stop sessions |
| 10 | Portfolio, Auth, Dashboard, Admin, Home | Existing web routes |

## Suggested first-run flow

### Offline learning (no broker credentials needed)

1. **Analysis — Backtest & Strategies → List available strategies** — confirm all 6 are loaded
2. **Analysis — Backtest & Strategies → Backtest — Ensemble** — see combined performance
3. **Analysis — Analyst → Analyst (CSV)** — get a decision report
4. **Paper Trading — Replay → Start replay — Ensemble** — watch live trades in the console

### Live NSE (needs Angel credentials set as env vars)

1. **Instruments → Status** — confirm scrip master loaded
2. **Analysis — Analyst → Live Nifty (NSE)** — see current recommendation
3. **Paper Trading — Live NSE → Start live — Nifty 50 (Ensemble, 5-min)**
4. **Paper Trading — Session management → Get session snapshot** — copy `sessionId` into env
5. Poll snapshot every few minutes; watch trades in the app console

### Live MCX (crude, gold, silver)

1. **Instruments → Futures — CRUDEOIL (MCX)** — get current front-month token
2. Copy `token` from response → paste into env var `crudeToken`
3. **Paper Trading — Live MCX → Start live — Crude Oil (Ensemble, 5-min)**
4. Repeat 1–3 for `goldToken` (search Futures with underlying=GOLD) and `silverToken`

## Environment variables

| Variable | Default | Set by |
|----------|---------|--------|
| `baseUrl` | `http://localhost:9010` | — |
| `broker` | `ANGEL` | — |
| `exchange` | `NSE` | — |
| `exchangeFo` | `NFO` | — |
| `exchangeMcx` | `MCX` | — |
| `nifty50Token` | `99926000` | — |
| `bankNiftyToken` | `99926009` | — |
| `sensexToken` | `99919000` | — |
| `relianceToken` | `2885` | — |
| `hdfcBankToken` | `1333` | — |
| `tcsToken` | `11536` | — |
| `crudeToken` | placeholder | **You** — from Instruments/Futures |
| `crudeSymbol` | `CRUDEOIL30SEP26FUT` | — |
| `goldToken` | placeholder | **You** — from Instruments/Futures |
| `silverToken` | placeholder | **You** — from Instruments/Futures |
| `sampleOptionToken` | `47432` | — |
| `optionExpiry` | `2026-09-15` | — |
| `optionStrike` | `26000` | — |
| `optionType` | `PE` | — |
| `interval` | `ONE_DAY` | — |
| `intervalMinute` | `ONE_MINUTE` | — |
| `intervalFive` | `FIVE_MINUTE` | — |
| `fromDate` / `toDate` | 2026-08-01 / 2026-08-25 | — |
| `strategyName` | `ensemble` | — |
| `minAgreement` | `2` | — |
| `sessionId` | placeholder | **You** — from create session response |
| `username` / `password` | `alex` / `demo123` | — |
| `adminUser` / `adminPass` | `admin` / `admin123` | — |

## Running with security off

Start the app:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=nosec
```

Then in Postman: **Collection root → Auth tab → No Auth**. All endpoints work without credentials.

## Running the entire collection

Collection root → **⋯** → **Run collection** → fires every request in sequence. Great smoke test after deploys.

Note: some paper-trading POSTs will each spawn a session — use the **Paper Trading — Session management → Stop session** afterwards, or restart the app to clear.

## Trading hours reference

| Exchange | Hours (IST) |
|----------|-------------|
| NSE/BSE equity | 09:15 – 15:30 Mon–Fri |
| NFO/BFO F&O | 09:15 – 15:30 Mon–Fri |
| MCX (non-agri: crude, gold, silver) | 09:00 – 23:30 Mon–Fri |
| MCX (agri) | 09:00 – 21:00 Mon–Fri |
| CDS (currency) | 09:00 – 17:00 Mon–Fri |

Note: as of now the app's market-hours gate is hardcoded to NSE hours (09:15–15:30). Live MCX sessions between 15:30 and 23:30 will skip polling until we add per-exchange hours (Fix A from earlier).
