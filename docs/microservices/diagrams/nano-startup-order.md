# Nano mode — startup order

5-wave dependency chain. Docker Compose waits for each wave's
healthchecks to pass before starting the next. End-to-end: ~45-60s
on a warm boot.

```mermaid
graph TD
    subgraph Wave1 ["Wave 1 — Infra (parallel, no deps)"]
        MYSQL[mysql-shared<br/>:3306]
        KAFKA[kafka<br/>:9092]
        REDIS[redis<br/>:6379]
    end

    subgraph Wave2 ["Wave 2"]
        EUREKA[eureka-server<br/>:8761]
    end

    subgraph Wave3 ["Wave 3"]
        CONFIG[config-server<br/>:8888]
    end

    subgraph Wave4 ["Wave 4"]
        AUTH[auth-server<br/>:8095]
    end

    subgraph Wave5 ["Wave 5"]
        GATEWAY[api-gateway<br/>:8080]
    end

    EUREKA --> CONFIG
    MYSQL --> AUTH
    EUREKA --> AUTH
    EUREKA --> GATEWAY
    AUTH --> GATEWAY
    REDIS --> GATEWAY

    classDef infra fill:#4CAF50,stroke:#2E7D32,color:#fff
    classDef platform fill:#2196F3,stroke:#1565C0,color:#fff
    class MYSQL,KAFKA,REDIS infra
    class EUREKA,CONFIG,AUTH,GATEWAY platform
```

**Legend:**
- 🟢 Green — infrastructure containers (no deps between them, start in parallel)
- 🔵 Blue — Spring Boot services (each depends on an earlier wave)

See the ASCII version in [`setup/00-first-time-setup.md`](../setup/00-first-time-setup.md#startup-order-handled-by-depends_on-healthchecks).
