# Flow Diagrams

Visual flows of how the angle-app works.
(Rendered with Mermaid — view on GitHub or any Mermaid-aware viewer.)

---

## 1. High-Level System Flow

```mermaid
flowchart TD
    User([User / Client])
    Controller[AnalysisController<br/>REST]
    Loader[NiftyFileLoader<br/>CSV]
    MDS[MarketDataService]
    Broker{Broker<br/>Router}
    Angel[AngelClient]
    Upstox[UpstoxClient]
    Kite[KiteClient]
    Strategy[Strategy<br/>SMA Crossover]
    Backtester[Backtester]
    Result[BacktestResult<br/>JSON]

    User -->|HTTP GET| Controller
    Controller -->|/backtest| Loader
    Controller -->|/candles| MDS
    Loader --> Backtester
    MDS --> Broker
    Broker -->|ANGEL| Angel
    Broker -->|UPSTOX| Upstox
    Broker -->|KITE| Kite
    Angel --> Backtester
    Backtester --> Strategy
    Strategy -->|Signal| Backtester
    Backtester --> Result
    Result --> User
```

---

## 2. Backtest Flow

```mermaid
sequenceDiagram
    participant U as User
    participant C as AnalysisController
    participant L as NiftyFileLoader
    participant B as Backtester
    participant S as Strategy (SMA)
    participant I as SimpleMovingAverage

    U->>C: GET /api/analysis/backtest
    C->>L: load()
    L-->>C: List<Candle>
    C->>B: run(strategy, candles)
    loop For each candle i
        B->>S: evaluate(candles, i)
        S->>I: compute short SMA
        I-->>S: shortValue
        S->>I: compute long SMA
        I-->>S: longValue
        S-->>B: BUY / SELL / HOLD
        B->>B: update positions + P&L
    end
    B-->>C: BacktestResult
    C-->>U: JSON response
```

---

## 3. Angel One Auth Flow (with TOTP)

```mermaid
sequenceDiagram
    participant App as angle-app
    participant Auth as AngelAuthService
    participant TOTP as TotpGenerator
    participant Angel as Angel SmartAPI

    App->>Auth: login()
    Auth->>TOTP: generate(secret)
    TOTP-->>Auth: 6-digit code
    Auth->>Angel: POST /loginByPassword<br/>{clientCode, password, totp}
    Angel-->>Auth: {jwtToken, refreshToken, feedToken}
    Auth->>Auth: cache tokens
    Auth-->>App: authenticated
    Note over App,Angel: Subsequent API calls
    App->>Angel: GET /candles<br/>Headers: X-PrivateKey, Bearer jwt
    Angel-->>App: candle data
```

---

## 4. Live Candles Fetch Flow

```mermaid
flowchart LR
    A[HTTP Request<br/>/candles?broker=ANGEL] --> B[AnalysisController]
    B --> C[MarketDataService]
    C --> D{Which<br/>broker?}
    D -->|ANGEL| E[AngelClient]
    D -->|UPSTOX| F[UpstoxClient]
    D -->|KITE| G[KiteClient]
    E --> H[AngelAuthService<br/>get token]
    H --> I[AngelHeaders<br/>build headers]
    I --> J[HTTP call to<br/>Angel API]
    J --> K[Angel DTO<br/>response]
    K --> L[Convert to<br/>List Candle]
    L --> M[Return JSON]
```

---

## 5. Strategy Decision Flow (SMA Crossover)

```mermaid
flowchart TD
    Start([New Candle]) --> A[Compute short SMA<br/>period 20]
    A --> B[Compute long SMA<br/>period 50]
    B --> C{Enough<br/>candles?}
    C -->|No| Hold1[Return HOLD]
    C -->|Yes| D{Short SMA<br/>vs Long SMA}
    D -->|Crossed above| Buy[Return BUY]
    D -->|Crossed below| Sell[Return SELL]
    D -->|No cross| Hold2[Return HOLD]

    Buy --> End([Signal])
    Sell --> End
    Hold1 --> End
    Hold2 --> End
```

---

## 6. Application Startup Flow

```mermaid
flowchart TD
    A[java -jar angle-app.jar] --> B[SpringApplication.run]
    B --> C[Load application.properties]
    C --> D[Bind BrokerProperties<br/>AnalysisProperties]
    D --> E[Component scan<br/>com.angle.trading]
    E --> F[Register beans:<br/>Controllers, Services,<br/>Strategies, Brokers]
    F --> G[SecurityConfig<br/>filter chain]
    G --> H[RestClientConfig<br/>HTTP client]
    H --> I[CorsConfig]
    I --> J[Tomcat starts on 9010]
    J --> K([Ready to serve])
```

---

## 7. Package / Layer Dependency

```mermaid
flowchart BT
    subgraph Config
        BP[BrokerProperties]
        AP[AnalysisProperties]
        SC[SecurityConfig]
        RC[RestClientConfig]
    end

    subgraph Broker
        BC[BrokerClient]
        AC[AngelClient]
        UC[UpstoxClient]
        KC[KiteClient]
    end

    subgraph Domain
        Candle
        Quote
        Interval
        Signal
    end

    subgraph Indicator
        IND[Indicator]
        SMA[SimpleMovingAverage]
    end

    subgraph Strategy
        STR[Strategy]
        MAC[MovingAverageCrossover]
    end

    subgraph Backtest
        BT[Backtester]
        BR[BacktestResult]
    end

    subgraph MarketData
        MDS[MarketDataService]
        NFL[NiftyFileLoader]
    end

    subgraph Controller
        CTRL[AnalysisController]
    end

    AC --> BC
    UC --> BC
    KC --> BC
    AC --> BP
    MAC --> STR
    MAC --> SMA
    SMA --> IND
    BT --> STR
    BT --> Candle
    MDS --> BC
    NFL --> AP
    CTRL --> BT
    CTRL --> MDS
    CTRL --> NFL
```

---

## 8. Adding a New Strategy (Developer Flow)

```mermaid
flowchart LR
    A[Create class<br/>strategy/impl/MyStrategy.java] --> B[Implement Strategy]
    B --> C[Annotate<br/>@Component]
    C --> D[Return unique<br/>getName]
    D --> E[Set property<br/>analysis.strategy.default]
    E --> F[Restart app]
    F --> G[GET /backtest<br/>uses new strategy]
```

---

## 9. Adding a New Broker (Developer Flow)

```mermaid
flowchart LR
    A[Create package<br/>broker/xyz/] --> B[Implement BrokerClient]
    B --> C[Add broker.xyz.*<br/>props]
    C --> D[Extend BrokerProperties]
    D --> E[Annotate @Component]
    E --> F[MarketDataService<br/>auto-picks it up]
    F --> G[GET /candles?broker=XYZ]
```
