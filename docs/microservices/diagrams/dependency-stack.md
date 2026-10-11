# Dependency stack

Bottom-up view: infrastructure feeds platform services, which feed
domain services. Each layer has to be healthy before the next can
start — Docker Compose enforces this via `depends_on` + healthchecks.

```mermaid
graph BT
    subgraph L1 ["Layer 1 — Infrastructure (Docker)"]
        MYSQL[MySQL x3<br/>or 1 shared]
        KAFKA[Kafka]
        ZIPKIN[Zipkin]
    end

    subgraph L2 ["Layer 2 — Support services"]
        EUREKA[Eureka] --> AUTH[Auth-server]
        AUTH --> GW[Gateway]
    end

    subgraph L3 ["Layer 3 — Domain services"]
        DOMAIN[user · product · order · payment · notification]
    end

    MYSQL --> L2
    KAFKA --> L2
    ZIPKIN --> L2
    L2 --> DOMAIN

    classDef infra fill:#4CAF50,stroke:#2E7D32,color:#fff
    classDef platform fill:#2196F3,stroke:#1565C0,color:#fff
    classDef biz fill:#9C27B0,stroke:#6A1B9A,color:#fff
    class MYSQL,KAFKA,ZIPKIN infra
    class EUREKA,AUTH,GW platform
    class DOMAIN biz
```

**Why bottom-up:**
- MySQL needs ~10s to accept connections — nothing above can start until
  it's healthy
- Eureka must exist before any Spring service, because they register
  with it at boot time
- Gateway needs auth-server's JWKS endpoint to validate incoming JWTs

**Shutdown is the reverse** — stop Layer 3 (apps) first so in-flight
requests can drain, then Layer 2, then Layer 1.

See the ASCII version in [`setup/01-startup-runbook.md`](../setup/01-startup-runbook.md#the-dependency-stack-for-this-project).
