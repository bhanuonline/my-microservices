# Broker Integration

How the app talks to Indian brokers.

## Interface

```java
public interface BrokerClient {
    String getBrokerName();
    List<Candle> getCandles(String symbolToken, Interval interval, LocalDate from, LocalDate to);
    Quote getQuote(String symbolToken);
    // ... place order, get positions, etc.
}
```

Each broker implements `BrokerClient`. `MarketDataService` picks the right one
based on the `broker` request parameter.

## Supported Brokers

| Broker | Package | Status |
|--------|---------|--------|
| Angel One SmartAPI | `broker.angel` | Implemented |
| Upstox | `broker.upstox` | Stub |
| Zerodha Kite | `broker.kite` | Stub |

## Angel One (SmartAPI)

### Auth Flow

1. `AngelAuthService.login()`
2. Generate current TOTP via `TotpGenerator` using `broker.angel.totp-secret`
3. POST client code + password + TOTP to Angel's login endpoint
4. Receive `jwtToken`, `refreshToken`, `feedToken`
5. Cache tokens; use in `AngelHeaders` for subsequent calls

### Headers

`AngelHeaders` builds the required headers Angel expects:
- `X-PrivateKey: ${api-key}`
- `Authorization: Bearer ${jwtToken}`
- `X-ClientLocalIP`, `X-ClientPublicIP`, `X-MACAddress`
- `X-UserType: USER`
- `X-SourceID: WEB`

### Data DTOs

`broker/angel/dto/` holds Angel-specific request/response models. These are
converted to the shared `Candle` / `Quote` DTOs before returning to the caller.

## Adding a New Broker

1. Create `broker/<name>/` package
2. Implement `BrokerClient`
3. Add `broker.<name>.*` config keys in `application.properties`
4. Bind them via `BrokerProperties`
5. Register as a `@Component` — `MarketDataService` picks up all `BrokerClient`
   beans and routes by `getBrokerName()`

## TOTP (2FA)

`TotpGenerator` implements RFC 6238 to generate 6-digit codes from a base32
secret. Same algorithm as Google Authenticator. Angel's TOTP secret is a
one-time setup on their portal.

## Security Note

Never commit API keys. All secrets are pulled from environment variables via
`${ENV_VAR:}` placeholders in `application.properties`.
