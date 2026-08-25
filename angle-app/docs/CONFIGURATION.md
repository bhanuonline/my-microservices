# Configuration

Reference for `application.properties`.

## Application

| Key | Default | Purpose |
|-----|---------|---------|
| `spring.application.name` | `angle-app` | App name |
| `logging.level.com.angle.trading` | `DEBUG` | App package logging |
| `logging.level.org.springframework.security` | `DEBUG` | Security logging |
| `spring.output.ansi.enabled` | `ALWAYS` | Colored logs |

## Broker: Angel One

| Key | Env var | Purpose |
|-----|---------|---------|
| `broker.angel.enabled` | - | Enable Angel broker |
| `broker.angel.base-url` | - | `https://apiconnect.angelbroking.com` |
| `broker.angel.api-key` | `ANGEL_API_KEY` | SmartAPI key |
| `broker.angel.client-code` | `ANGEL_CLIENT_CODE` | Angel login code |
| `broker.angel.password` | `ANGEL_PASSWORD` | Angel password/MPIN |
| `broker.angel.totp-secret` | `ANGEL_TOTP_SECRET` | TOTP seed for 2FA |

## Broker: Upstox

| Key | Env var | Purpose |
|-----|---------|---------|
| `broker.upstox.enabled` | - | Enable Upstox broker |
| `broker.upstox.base-url` | - | `https://api.upstox.com/v2` |
| `broker.upstox.api-key` | `UPSTOX_API_KEY` | API key |
| `broker.upstox.api-secret` | `UPSTOX_API_SECRET` | API secret |
| `broker.upstox.redirect-uri` | `UPSTOX_REDIRECT_URI` | OAuth callback URL |

## Broker: Kite (Zerodha)

| Key | Env var | Purpose |
|-----|---------|---------|
| `broker.kite.enabled` | - | Enable Kite broker |
| `broker.kite.base-url` | - | `https://api.kite.trade` |
| `broker.kite.api-key` | `KITE_API_KEY` | API key |
| `broker.kite.api-secret` | `KITE_API_SECRET` | API secret |

## Analysis

| Key | Default | Purpose |
|-----|---------|---------|
| `analysis.nifty.data-file` | `classpath:nifty/nifty-daily-candles.csv` | CSV path |
| `analysis.strategy.default` | `moving-average-crossover` | Bean name of default strategy |
| `analysis.strategy.sma.short-period` | `20` | Short SMA window |
| `analysis.strategy.sma.long-period` | `50` | Long SMA window |

## Profile-Specific Configs

Add `application-dev.properties`, `application-prod.properties` for
per-environment overrides. Activate with `--spring.profiles.active=dev`.
