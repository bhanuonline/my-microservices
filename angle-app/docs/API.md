# REST API

## Base URL

`http://localhost:9010`

## Analysis

### `GET /api/analysis/backtest`

Run the default strategy against the Nifty CSV data.

**Response**
```json
{
  "totalTrades": 42,
  "winningTrades": 25,
  "losingTrades": 17,
  "totalPnL": 12500.50,
  "winRate": 0.595
}
```

### `GET /api/analysis/candles`

Fetch historical candles from a broker.

**Query params**
| Name | Type | Required | Example |
|------|------|----------|---------|
| `broker` | string | yes | `ANGEL` |
| `symbolToken` | string | yes | `99926000` |
| `interval` | enum | no (default `ONE_DAY`) | `ONE_DAY`, `FIVE_MINUTE` |
| `from` | date (ISO) | yes | `2026-01-01` |
| `to` | date (ISO) | yes | `2026-08-01` |

**Response**
```json
{
  "broker": "ANGEL",
  "symbolToken": "99926000",
  "interval": "ONE_DAY",
  "count": 150,
  "candles": [
    { "timestamp": "...", "open": 22000, "high": 22150, "low": 21980, "close": 22100, "volume": 1250000 }
  ]
}
```

## Auth / Admin / Dashboard

- `AuthController` — login / logout endpoints
- `RegistrationController` — new user signup
- `AdminController` — admin-only endpoints
- `DashboardController` — user dashboard
- `HomeController` — landing page
- `ErrorController` — error handling

(See source for exact paths — controllers under `com.angle.trading.controller`.)
