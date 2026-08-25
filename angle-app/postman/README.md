# AngleApp Postman Collection

Two files:

- `angle-app.postman_collection.json` — all endpoints organised by folder
- `angle-app.postman_environment.json` — variables (base URL, tokens, credentials)

## Import

1. Open Postman → **Import** (top-left)
2. Drag both `.json` files in
3. Top-right env dropdown → select **AngleApp Local**

## Folders

| Folder | Purpose | Auth |
|--------|---------|------|
| **Analysis** | Backtest + candles (index / equity / option) | HTTP Basic |
| **Instruments** | Scrip master lookup, option chain, expiries | HTTP Basic |
| **Portfolio & Trading** | Holdings, orders, watchlist, profile | HTTP Basic |
| **Auth** | Login / register / logout (form-based) | none |
| **Dashboard** | Web dashboard | session cookie |
| **Admin** | Admin console | HTTP Basic (admin) |
| **Home** | Landing page | none |

## Environment variables

Edit values via **Environments → AngleApp Local**.

| Variable | Default | Purpose |
|----------|---------|---------|
| `baseUrl` | `http://localhost:9010` | Server URL |
| `broker` | `ANGEL` | Broker name for candle requests |
| `exchange` | `NSE` | For equity / index |
| `exchangeFo` | `NFO` | For options / futures |
| `nifty50Token` | `99926000` | Nifty 50 index token |
| `bankNiftyToken` | `99926009` | Bank Nifty index token |
| `relianceToken` | `2885` | Reliance stock token |
| `sampleOptionToken` | `47432` | A Nifty option token (update after finding one) |
| `underlying` | `NIFTY` | For instrument lookups |
| `optionExpiry` | `2026-09-15` | For option / option-chain queries |
| `optionStrike` | `26000` | Strike price for option lookup |
| `optionType` | `PE` | `CE` or `PE` |
| `interval` | `ONE_DAY` | Candle interval |
| `fromDate` | `2026-08-01` | Range start |
| `toDate` | `2026-08-24` | Range end |
| `username` | `alex` | Default user |
| `password` | `demo123` | Default password |
| `adminUser` | `admin` | Admin user |
| `adminPass` | `admin123` | Admin password |

## Suggested first-run order

1. **Instruments → Status** — confirms scrip master loaded (`count > 0`)
2. **Instruments → Available expiries** — see live NIFTY expiries
3. Update env variable `optionExpiry` to one of those dates
4. **Instruments → Find option** — get a real token for today
5. Copy that token into env variable `sampleOptionToken`
6. **Analysis → Get candles — Nifty option (NFO)** — fetches candle data for that option

## Running with security off

Start the app with `spring.profiles.active=nosec`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=nosec
```

Then in Postman, **Collection root → Auth tab → No Auth**. All endpoints work without credentials.

## Running the whole collection

Collection root → **⋯** menu → **Run collection** to fire every request in sequence. Good smoke test after deploys.
