# Troubleshooting

## App won't start

**Port 9010 already in use**
```
Web server failed to start. Port 9010 was already in use.
```
Fix: kill the other process or set `server.port=8081` in `application.properties`.

**Java version mismatch**
```
UnsupportedClassVersionError
```
Fix: `java -version` must be 17+. Check `JAVA_HOME`.

**Lombok not applied in IDE**
Symptoms: getters/setters "not found", `@RequiredArgsConstructor` doesn't inject.
Fix: enable annotation processing in IDE (IntelliJ: Settings → Build → Compiler → Annotation Processors → enable).

## Broker auth fails

**Angel: `Invalid TOTP`**
- TOTP secret wrong or clock drift on host
- Sync clock: `sudo sntp -sS time.apple.com`

**Angel: `Invalid credentials`**
- Client code or password wrong
- Check env vars are actually exported: `echo $ANGEL_CLIENT_CODE`

**Angel: `Access denied`**
- API key not activated on Angel portal, or IP not whitelisted

## `NiftyFileLoader` fails

**`FileNotFoundException: nifty-daily-candles.csv`**
- File missing from `src/main/resources/nifty/`
- Check `analysis.nifty.data-file` path

**`NumberFormatException` while parsing CSV**
- Blank line or bad row in CSV
- Check header row matches expected columns

## Backtest returns 0 trades

- Not enough candles for long SMA period (need at least 50 by default)
- Strategy never generates a signal — verify with logs at `DEBUG` level
- Check `analysis.strategy.default` matches an actual strategy bean name

## Security / login redirect loop

- `SecurityConfig` and controller mappings conflict
- Clear browser cookies for `localhost:9010`
- Enable `logging.level.org.springframework.security=TRACE` to see filter chain

## Build fails: `Could not resolve dependencies`

```bash
./mvnw dependency:purge-local-repository
./mvnw clean install
```

## `Bean not found` errors

- Missing `@Component` / `@Service` on the class
- Class outside `com.angle.trading` package (Spring won't scan it)
- Circular dependency — break it by extracting an interface

## Enable Debug Logging

```properties
logging.level.com.angle.trading=DEBUG
logging.level.com.angle.trading.broker=TRACE
```

## Still stuck?

- Check `git log` for recent changes to the failing area
- Grep for the exact error string in the codebase
- File an issue with logs, `git rev-parse HEAD`, and steps to reproduce
