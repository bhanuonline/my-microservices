# Onboarding — Understand This Project Step by Step

You're new to `angle-app`. This guide walks you through the codebase in the
right order so nothing feels random.

**Total time:** ~2–3 hours of focused reading. Don't rush.

---

## Step 0 — Know what the app does (5 min)

Read [README.md](README.md).

**One-line summary:**
> A Spring Boot app that reads stock market candles (from a CSV or a broker
> like Angel One), runs a trading strategy on them, and reports whether it
> would have made money.

Ask yourself: *"If I typed one URL to see this app work, what would happen?"*

Answer: hitting `/api/analysis/backtest` loads Nifty CSV → runs SMA crossover
strategy → returns profit/loss as JSON.

---

## Step 1 — Get it running (15 min)

Follow [SETUP.md](SETUP.md):

```bash
./mvnw spring-boot:run
```

Then hit:
```bash
curl http://localhost:9010/api/analysis/backtest
```

**Goal:** see JSON come back. Don't try to understand the numbers yet —
just prove the app works end-to-end on your machine.

Also open in browser:
- `http://localhost:9010/` → landing page
- `http://localhost:9010/auth/login` → login page

---

## Step 2 — Look at the folder structure (10 min)

```
src/main/java/com/angle/trading/
├── AngleAppApplication.java  ← entry point
├── controller/               ← HTTP endpoints
├── service/                  ← business logic for auth/users
├── config/                   ← Spring configuration
├── security/                 ← Spring Security setup
├── filter/                   ← servlet filters
├── dto/                      ← data transfer objects
├── broker/                   ← Angel/Upstox/Kite integrations
│   ├── angel/
│   ├── upstox/
│   ├── kite/
│   └── model/                ← Candle, Quote, Interval
├── marketdata/               ← loads candles from CSV or broker
├── indicator/                ← SMA and other math
├── strategy/                 ← trading strategies (SMA crossover)
├── backtest/                 ← runs strategies on historical data
└── miniapp/                  ← small side experiments
```

**Rule of thumb for reading Java code:**
1. Start at the entry point (`AngleAppApplication`)
2. Follow the request into a controller
3. Follow the controller into a service
4. Follow the service into whatever it calls

---

## Step 3 — Read the entry point (5 min)

Open `AngleAppApplication.java`.

Key things:
- `@SpringBootApplication` — tells Spring to scan `com.angle.trading` for beans
- `exclude = SecurityAutoConfiguration.class` — we wire security manually
- `SpringApplication.run(...)` — boots Tomcat + Spring context

If you don't know what "bean" or "Spring context" means, pause and google it.
Don't skip — this is the foundation.

---

## Step 4 — Read the config (15 min)

Open `src/main/resources/application.properties` and skim.

Notice three groups:
1. Logging + app name
2. Broker credentials (`broker.angel.*`, `broker.upstox.*`, `broker.kite.*`)
3. Analysis config (`analysis.strategy.*`)

Then open these config classes:
- `config/BrokerProperties.java` — binds broker props to a Java object
- `config/AnalysisProperties.java` — binds analysis props
- `config/SecurityConfig.java` — auth rules (who can see what)
- `config/CorsConfig.java` — cross-origin rules
- `config/RestClientConfig.java` — HTTP client used to call brokers

**Learn the pattern:** `@ConfigurationProperties(prefix = "broker")` on a
Java class → Spring auto-fills fields from `application.properties`. This is
the "config-driven" style used everywhere in this project.

Reference: [CONFIGURATION.md](CONFIGURATION.md)

---

## Step 5 — Follow one HTTP request end-to-end (30 min) ⭐

**This is the most important step.** Trace `/api/analysis/backtest` from URL
to JSON response.

Open these files in order and read them top-to-bottom:

1. **`controller/AnalysisController.java`**
   - `@GetMapping("/backtest")` method
   - It calls `niftyFileLoader.load()` and `backtester.run(...)`
   - Notice how it doesn't do any logic itself — it just wires things

2. **`marketdata/NiftyFileLoader.java`**
   - Reads the CSV file at `analysis.nifty.data-file`
   - Returns `List<Candle>`

3. **`broker/model/Candle.java`**
   - Just a data holder: date, open, high, low, close, volume
   - Fundamental building block — most classes take `List<Candle>`

4. **`backtest/Backtester.java`**
   - Loops through candles
   - On each candle asks the strategy: "buy, sell, or hold?"
   - Tracks positions and P&L
   - Returns `BacktestResult`

5. **`strategy/Strategy.java`**
   - Interface — one method: `evaluate(candles, index)` → `Signal`

6. **`strategy/impl/MovingAverageCrossover.java`**
   - Actual strategy implementation
   - Uses two SMAs (short 20, long 50)
   - Returns BUY when short crosses above long, SELL when below

7. **`indicator/SimpleMovingAverage.java`**
   - Simple math: average of last N closes

**Now you understand the "trading" part of the app.**

Reference diagrams: [FLOW-DIAGRAM.md](FLOW-DIAGRAM.md) sections 1, 2, 5

---

## Step 6 — Understand the broker abstraction (30 min)

Trace `/api/analysis/candles?broker=ANGEL&...`.

Open in order:

1. **`controller/AnalysisController.java`** — the `/candles` method
2. **`marketdata/MarketDataService.java`** — routes to the right broker
3. **`broker/BrokerClient.java`** — the common interface
4. **`broker/angel/AngelClient.java`** — Angel implementation
5. **`broker/angel/AngelAuthService.java`** — login + token management
6. **`broker/angel/TotpGenerator.java`** — 2FA code generation
7. **`broker/angel/AngelHeaders.java`** — builds Angel's required HTTP headers

**Key learning:** why an interface? Because tomorrow when you plug in Upstox,
the rest of the app (strategy, backtester, controller) doesn't change. This
is the **strategy pattern** applied to broker choice.

Reference: [BROKER-INTEGRATION.md](BROKER-INTEGRATION.md) and
[FLOW-DIAGRAM.md](FLOW-DIAGRAM.md) sections 3, 4

---

## Step 7 — Understand the auth side (20 min)

Web UI has login/registration/dashboard. Trace one login flow:

1. Browser hits `/auth/login` → `AuthController.showLoginPage()` returns
   `auth/login` (a Thymeleaf template)
2. User submits form → Spring Security's filter chain intercepts
3. `config/SecurityConfig.java` — this defines the rules
4. `filter/` — custom filters (JWT? session? read to find out)
5. `service/` — user lookup + password check
6. On success → redirect to `/dashboard`
7. `DashboardController.home()` returns `dashboard/welcome`

Templates live in `src/main/resources/templates/`.

---

## Step 8 — Skim the rest (15 min)

Files you don't need to read line-by-line yet — just know they exist:

- `controller/AdminController.java` — admin pages
- `controller/ApiController.java` — misc REST endpoints (holdings, watchlist)
- `controller/ErrorController.java` — error handling
- `controller/HomeController.java` — landing page
- `controller/RegistrationController.java` — signup
- `miniapp/` — small experiments, ignore for now
- `broker/upstox/`, `broker/kite/` — stubs, not fully implemented

---

## Step 9 — Run something small yourself (30 min)

Best way to confirm you understand: **change one thing and see it work.**

Suggested experiments (pick one):

**A. Change strategy parameters**
- Edit `application.properties`:
  ```
  analysis.strategy.sma.short-period=10
  analysis.strategy.sma.long-period=30
  ```
- Restart. Hit `/api/analysis/backtest`. P&L should be different.

**B. Add logging**
- In `MovingAverageCrossover.evaluate()`, add a `log.debug(...)` printing
  short and long SMA values.
- Restart. Watch logs during backtest.

**C. Add an endpoint**
- Add `GET /api/analysis/candles-count?broker=ANGEL&...` that returns just
  the number of candles, not the candles themselves.

**D. Add a new indicator**
- Copy `SimpleMovingAverage.java` → `ExponentialMovingAverage.java`
- Implement EMA formula
- Don't wire it into a strategy yet — just add unit tests

---

## Step 10 — Know where to find things (5 min)

| Question | Where to look |
|----------|---------------|
| How do I add a strategy? | [STRATEGY-GUIDE.md](STRATEGY-GUIDE.md) |
| How do I add a broker? | [BROKER-INTEGRATION.md](BROKER-INTEGRATION.md) |
| What API endpoints exist? | [API.md](API.md) |
| What env vars do I need? | [ENV-VARIABLES.md](ENV-VARIABLES.md) |
| App won't start — what now? | [TROUBLESHOOTING.md](TROUBLESHOOTING.md) |
| Big picture / arch decisions | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Visual flow of the code | [FLOW-DIAGRAM.md](FLOW-DIAGRAM.md) |
| What's planned next? | [ROADMAP.md](ROADMAP.md) |

---

## Reading Order Cheat Sheet

If you're really short on time, read these 6 files **in this order** to
understand 80% of the project:

1. `AngleAppApplication.java` — entry point
2. `application.properties` — config
3. `controller/AnalysisController.java` — where requests land
4. `broker/model/Candle.java` — the core data type
5. `strategy/Strategy.java` + `strategy/impl/MovingAverageCrossover.java` — the brain
6. `backtest/Backtester.java` — the driver loop

Everything else is either infrastructure (security, config) or extension
points (more brokers, more strategies).

---

## When You Get Stuck

- Read the class Javadoc / comments first
- `git log --follow <file>` to see how it was built up
- `grep -r "className"` to find who uses it
- Add a `log.debug(...)` and hit the endpoint — the log tells the truth
- Draw the call graph on paper — 3 boxes and 2 arrows beats staring at code

Good luck. This project is intentionally small — you *can* understand all of it.
