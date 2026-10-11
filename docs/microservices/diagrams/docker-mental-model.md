# Docker mental model

What's actually running when you `make up` (shared-db mode). All
containers share a bridge network called `backend`; only `api-gateway`
publishes a port to the Mac host. Data lives in named Docker volumes.

```mermaid
graph TB
    subgraph Host ["🖥  Docker host (your Mac)"]
        subgraph Net ["network: backend"]
            subgraph Platform ["Platform"]
                EUREKA[eureka-server<br/>:8761]
                CONFIG[config-server<br/>:8888]
                AUTH[auth-server<br/>:8095]
            end

            subgraph Edge ["Edge"]
                GW[api-gateway<br/>:8080 → host :9010]
            end

            subgraph Biz ["Business services"]
                USER[user-service<br/>:8081]
                PROD[product-service<br/>:8082]
                ORDER[order-service<br/>:8083]
            end

            subgraph Data ["Data + messaging"]
                MYSQL[mysql-shared<br/>:3306]
                KAFKA[kafka<br/>:9092]
            end

            subgraph Obs ["Observability"]
                PROM[prometheus<br/>:9090]
                GRAF[grafana<br/>:3000]
            end
        end

        VOL[(Volumes:<br/>mysql-shared-data<br/>grafana-data<br/>prometheus-data)]
    end

    USER_ME([curl / browser]) -.->|only exposed port| GW
    GW --> USER
    GW --> PROD
    GW --> ORDER
    USER --> MYSQL
    PROD --> MYSQL
    ORDER --> MYSQL
    USER --> KAFKA
    ORDER --> KAFKA
    PROM -.scrapes.-> USER
    PROM -.scrapes.-> PROD
    PROM -.scrapes.-> ORDER
    GRAF --> PROM
    MYSQL -.persists to.-> VOL

    classDef edge fill:#FF9800,stroke:#E65100,color:#fff
    classDef platform fill:#2196F3,stroke:#1565C0,color:#fff
    classDef biz fill:#9C27B0,stroke:#6A1B9A,color:#fff
    classDef data fill:#4CAF50,stroke:#2E7D32,color:#fff
    classDef obs fill:#607D8B,stroke:#37474F,color:#fff
    class GW edge
    class EUREKA,CONFIG,AUTH platform
    class USER,PROD,ORDER biz
    class MYSQL,KAFKA data
    class PROM,GRAF obs
```

**Legend:**
- 🟠 Orange — edge (only container exposed to the outside world)
- 🔵 Blue — platform services (service discovery, config, auth)
- 🟣 Purple — business services (your domain code)
- 🟢 Green — data + messaging infrastructure
- ⚫ Grey — observability
- Dotted arrows — Prometheus scrapes, Grafana queries, MySQL writes to volume

See the ASCII version in [`setup/docker-howto.md`](../setup/docker-howto.md#1-mental-model--whats-actually-running).
