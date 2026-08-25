# Design Patterns in This Project — with Examples

Every pattern here has real code in `angle-app`. File paths point to where
you can go read the actual implementation.

---

## 1. Strategy Pattern ⭐

**What:** Define a family of algorithms, make them interchangeable at runtime.

**Where in code:**
- Interface: `strategy/Strategy.java`
- Implementation: `strategy/impl/MovingAverageCrossover.java`
- Consumer: `backtest/Backtester.java`, `controller/AnalysisController.java`

### The Interface

```java
// strategy/Strategy.java
public interface Strategy {
    String getName();
    Signal evaluate(List<Candle> candles, int currentIndex);
}
```

### The Implementation

```java
// strategy/impl/MovingAverageCrossover.java
@Component
public class MovingAverageCrossover implements Strategy {

    private final SimpleMovingAverage shortSma;  // period 20
    private final SimpleMovingAverage longSma;   // period 50

    @Override
    public String getName() {
        return "moving-average-crossover";
    }

    @Override
    public Signal evaluate(List<Candle> candles, int i) {
        if (i < 50) return Signal.HOLD;  // not enough data

        double shortNow  = shortSma.compute(candles, i);
        double longNow   = longSma.compute(candles, i);
        double shortPrev = shortSma.compute(candles, i - 1);
        double longPrev  = longSma.compute(candles, i - 1);

        if (shortPrev <= longPrev && shortNow > longNow) return Signal.BUY;
        if (shortPrev >= longPrev && shortNow < longNow) return Signal.SELL;
        return Signal.HOLD;
    }
}
```

### The Consumer

```java
// backtest/Backtester.java
public BacktestResult run(Strategy strategy, List<Candle> candles) {
    for (int i = 0; i < candles.size(); i++) {
        Signal signal = strategy.evaluate(candles, i);  // ← doesn't care WHICH strategy
        // ... update positions
    }
}
```

### Why this is Strategy Pattern

The `Backtester` never says `new MovingAverageCrossover()`. It just takes any
`Strategy`. Swap in `RsiMeanReversion` tomorrow — no change needed to
`Backtester`.

### How to spot Strategy in any codebase

- An interface with one "do it" method
- Multiple implementations doing the same *kind* of thing differently
- A caller that takes the interface as a parameter or field

---

## 2. Adapter Pattern

**What:** Wrap a third-party API so it fits your own interface.

**Where in code:**
- Interface: `broker/BrokerClient.java`
- Adapters: `broker/angel/AngelClient.java`, `broker/upstox/UpstoxClient.java`, `broker/kite/KiteClient.java`

### The Common Interface

```java
// broker/BrokerClient.java
public interface BrokerClient {
    String getBrokerName();
    List<Candle> getCandles(String symbolToken, Interval interval,
                            LocalDate from, LocalDate to);
    Quote getQuote(String symbolToken);
}
```

### An Adapter

```java
// broker/angel/AngelClient.java
@Component
public class AngelClient implements BrokerClient {

    private final RestClient restClient;
    private final AngelAuthService authService;
    private final AngelHeaders headers;

    @Override
    public String getBrokerName() { return "ANGEL"; }

    @Override
    public List<Candle> getCandles(String symbolToken, Interval interval,
                                   LocalDate from, LocalDate to) {
        // 1. Build Angel-specific request body
        AngelCandleRequest req = AngelCandleRequest.builder()
                .exchange("NSE")
                .symboltoken(symbolToken)
                .interval(mapInterval(interval))    // convert OUR enum to Angel's string
                .fromdate(from + " 09:15")
                .todate(to + " 15:30")
                .build();

        // 2. Call Angel API with their required headers
        AngelCandleResponse resp = restClient.post()
                .uri("/rest/secure/angelbroking/historical/v1/getCandleData")
                .headers(headers::apply)
                .body(req)
                .retrieve()
                .body(AngelCandleResponse.class);

        // 3. Convert Angel's response into OUR domain object
        return resp.getData().stream()
                .map(this::toCandle)
                .toList();
    }

    private Candle toCandle(List<Object> row) { /* map raw arrays to Candle */ }
}
```

### Why this is Adapter Pattern

Angel returns candles as raw arrays: `[timestamp, o, h, l, c, v]`. Upstox returns
JSON with named fields. Kite returns yet another shape. Each adapter converts
its native shape into your shared `Candle`.

Rest of the app (`Backtester`, `Strategy`, `Controller`) sees only `Candle`.

### How to spot Adapter

- Class implements one of *your* interfaces
- Constructor holds a reference to a *foreign* API client
- Methods translate between two "shapes"

---

## 3. Factory / Registry

**What:** Look up the right implementation at runtime by a key.

**Where in code:** `marketdata/MarketDataService.java`

```java
// marketdata/MarketDataService.java
@Service
public class MarketDataService {

    // Spring auto-injects ALL BrokerClient beans it finds
    private final Map<String, BrokerClient> brokersByName;

    public MarketDataService(List<BrokerClient> allBrokers) {
        this.brokersByName = allBrokers.stream()
                .collect(Collectors.toMap(BrokerClient::getBrokerName, b -> b));
    }

    public List<Candle> getCandles(String brokerName, String symbolToken,
                                   Interval interval, LocalDate from, LocalDate to) {
        BrokerClient broker = brokersByName.get(brokerName.toUpperCase());
        if (broker == null) {
            throw new IllegalArgumentException("Unknown broker: " + brokerName);
        }
        return broker.getCandles(symbolToken, interval, from, to);
    }
}
```

### Why this is Factory/Registry

Caller says `"give me ANGEL"`. Service looks up the right adapter and delegates.
Adding a new broker → just register a `@Component` implementing `BrokerClient`.
No `switch` statement to update.

### How to spot Factory/Registry

- A map keyed by name → implementation
- Or a method: `getFor(name)` that returns the right one
- Zero `if broker == "ANGEL"` chains

---

## 4. Chain of Responsibility (Filter Chain)

**What:** Pass a request through a chain of handlers until one handles it.

**Where in code:** `filter/TraditionalFilter.java`, `filter/LoggingFilter.java`, `config/SecurityConfig.java`

### A Filter

```java
// filter/LoggingFilter.java
@Component
public class LoggingFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        long start = System.currentTimeMillis();

        chain.doFilter(req, res);  // ← "next handler please"

        long took = System.currentTimeMillis() - start;
        log.info("Request took {}ms", took);
    }
}
```

### The Chain (Spring Security)

```java
// config/SecurityConfig.java
@Bean
@Order(1)
public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/**")
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults());
    return http.build();  // ← this returns a chain of ~15 filters
}
```

### Why this is Chain of Responsibility

Every HTTP request goes through: `LoggingFilter → CSRF filter → Auth filter → ...
→ your Controller`. Each link decides: handle, modify, block, or pass on.

### How to spot Chain of Responsibility

- Method signature includes a `chain` / `next` parameter
- Handler calls `chain.next()` or `chain.doFilter(...)` to continue
- Order matters — configured explicitly

---

## 5. Dependency Injection (Spring)

**What:** Objects don't create their dependencies — someone else supplies them.

**Where in code:** every `@Component`, `@Service`, `@RestController`.

### Without DI (imagine)

```java
public class AnalysisController {
    private Backtester backtester = new Backtester(new MovingAverageCrossover());
    // ↑ hard-wired. Can't swap strategy. Can't test with mock.
}
```

### With DI (your actual code)

```java
// controller/AnalysisController.java
@RestController
@RequiredArgsConstructor  // Lombok: generates constructor with all final fields
public class AnalysisController {

    private final NiftyFileLoader niftyFileLoader;
    private final Backtester backtester;
    private final Strategy defaultStrategy;
    private final MarketDataService marketDataService;

    @GetMapping("/api/analysis/backtest")
    public BacktestResult backtest() {
        List<Candle> candles = niftyFileLoader.load();
        return backtester.run(defaultStrategy, candles);
    }
}
```

Spring sees the constructor, looks up beans of each type, and passes them in.

### Why this matters

- **Testable** — pass mocks in tests
- **Swappable** — change one config, whole graph rewires
- **No boilerplate** — no factories, no service locators

### How to spot DI

- Constructor takes dependencies as parameters
- Fields are `final` and set in constructor
- Class has `@Component` / `@Service` / etc.

---

## 6. Singleton (via Spring)

**What:** Only one instance exists in the application.

**Where in code:** every Spring `@Component` — the default scope is singleton.

### Manual Singleton (old-school Java)

```java
public class Backtester {
    private static Backtester INSTANCE;
    private Backtester() {}
    public static Backtester getInstance() {
        if (INSTANCE == null) INSTANCE = new Backtester();
        return INSTANCE;
    }
}
```

### Your Code (Spring gives you singleton free)

```java
@Component
public class Backtester { ... }
```

That's it. Spring creates one `Backtester`, injects the same instance everywhere.

Verify — inject the same bean into two classes, print `System.identityHashCode(bean)` in each. Same number → same object.

---

## 7. Builder Pattern

**What:** Construct complex objects step-by-step, in a readable way.

**Where in code:** any Lombok class with `@Builder`, and Spring's `HttpSecurity`.

### Lombok Builder

```java
// broker/model/Candle.java
@Builder
@Value  // makes it immutable
public class Candle {
    LocalDateTime timestamp;
    double open, high, low, close;
    long volume;
}
```

Usage:

```java
Candle c = Candle.builder()
        .timestamp(LocalDateTime.now())
        .open(22000.0)
        .high(22150.0)
        .low(21980.0)
        .close(22100.0)
        .volume(1_250_000)
        .build();
```

Beats:
```java
new Candle(LocalDateTime.now(), 22000.0, 22150.0, 21980.0, 22100.0, 1_250_000);
// ← which one is high vs low? Easy to swap by accident.
```

### Spring's HttpSecurity Builder

```java
http.securityMatcher("/api/**")
    .csrf(csrf -> csrf.disable())
    .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
    .httpBasic(Customizer.withDefaults());
```

Method chaining, each call adds one piece. Same idea.

### How to spot Builder

- A `.builder()` static method
- Chained `.field(value)` calls
- Terminal `.build()` returning the object

---

## 8. Facade Pattern

**What:** Hide a complex subsystem behind one simple interface.

**Where in code:** `broker/angel/AngelClient.java` is a facade over:
- `AngelAuthService` (login + token management)
- `TotpGenerator` (2FA code generation)
- `AngelHeaders` (special HTTP headers)
- `RestClient` (HTTP call)
- Angel's DTO conversion

Caller just does:
```java
List<Candle> candles = angelClient.getCandles(token, ONE_DAY, from, to);
```

They don't know or care about JWT tokens, TOTP, or Angel's header quirks.

---

## 9. DTO Pattern

**What:** Plain data holders for moving data across layers/boundaries.

**Where in code:**
- Domain DTOs: `broker/model/Candle.java`, `broker/model/Quote.java`
- Broker-specific DTOs: `broker/angel/dto/AngelCandleRequest.java`, etc.
- Request/response DTOs: `dto/` package

### Two kinds in this project

**Domain DTO** — used across the app:
```java
public class Candle {
    LocalDateTime timestamp;
    double open, high, low, close;
    long volume;
}
```

**Vendor DTO** — matches broker's exact shape:
```java
// broker/angel/dto/AngelCandleResponse.java
public class AngelCandleResponse {
    private String status;
    private String message;
    private List<List<Object>> data;  // Angel's weird array-of-arrays
}
```

Vendor DTOs are converted to Domain DTOs inside the adapter (see `AngelClient.toCandle()`).

**Rule:** never leak vendor DTOs into your strategies or business logic.

---

## 10. MVC Pattern (Spring Web)

- **Model** — `Candle`, `Quote`, `BacktestResult`, user entities
- **View** — Thymeleaf templates in `resources/templates/` (login, dashboard, admin)
- **Controller** — everything in `controller/`

```java
// Controller returns a "view name"
@Controller
public class DashboardController {
    @GetMapping("/dashboard")
    public String home(Model model, Authentication auth) {
        model.addAttribute("user", auth.getName());   // ← M
        return "dashboard/welcome";                    // ← V (template name)
    }
}
```

Spring's `ViewResolver` maps `"dashboard/welcome"` to
`templates/dashboard/welcome.html` and renders it.

---

## System Design Concepts in Use

### Layered Architecture
```
Controller → Service → BrokerClient/Loader → External API/File
```
No shortcuts across layers. Controller never calls Angel's API directly.

### Interface-first Design
`Strategy`, `Indicator`, `BrokerClient` — all interfaces first. Callers depend
on interfaces, not concrete classes. Enables the patterns above.

### Config-Driven Behavior
```properties
analysis.strategy.default=moving-average-crossover
analysis.strategy.sma.short-period=20
analysis.strategy.sma.long-period=50
```
Change behavior without recompiling.

### Externalized Secrets
```properties
broker.angel.api-key=${ANGEL_API_KEY:}
```
Never commit secrets. Env vars for config that varies by environment.

### Profile-Gated Beans
`@Profile("!nosec")` on `SecurityConfig`. Same code base, different runtime
shape depending on active profile.

### Separation of Concerns
Each package has one job:
| Package | Job |
|---------|-----|
| `indicator/` | pure math |
| `strategy/` | decision making |
| `backtest/` | simulation loop |
| `broker/` | external IO |
| `controller/` | HTTP glue |
| `config/` | wiring |

You can rewrite one without touching the others.

---

## Cheat Sheet

| Pattern | File to read | One-line summary |
|---------|--------------|------------------|
| Strategy | `strategy/impl/MovingAverageCrossover.java` | Swap algorithms behind an interface |
| Adapter | `broker/angel/AngelClient.java` | Wrap a foreign API to fit yours |
| Factory/Registry | `marketdata/MarketDataService.java` | Look up impl by name |
| Chain of Responsibility | `filter/LoggingFilter.java` | Pass request through handlers |
| DI | `controller/AnalysisController.java` | Someone else supplies your dependencies |
| Singleton | any `@Component` | Spring gives you one instance |
| Builder | `broker/model/Candle.java` (`@Builder`) | Readable object construction |
| Facade | `broker/angel/AngelClient.java` | One door in front of a complex subsystem |
| DTO | `broker/model/Candle.java` | Plain data holder |
| MVC | `controller/DashboardController.java` | Model + View + Controller separation |

---

## Suggested Learning Order

1. Read `MovingAverageCrossover` — understand **Strategy** first
2. Read `AnalysisController` — understand **DI** (how strategy gets injected)
3. Read `AngelClient` — understand **Adapter + Facade** together
4. Read `MarketDataService` — understand **Factory/Registry**
5. Read `LoggingFilter` — understand **Chain of Responsibility**
6. Read any `@Builder` DTO — understand **Builder**

That's ~90% of the patterns you'll see in any real Spring codebase.
