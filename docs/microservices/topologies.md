# Container topologies

The stack ships three ways and lets you mix in optional services by
profile. All run the same code, same configs, same Spring profiles —
only the surrounding containers differ. Topology + profile combo is a
deployment-time choice, driven by Compose profiles + overlay files, so
you can switch modes without changing a line of Java.

Pick a mode based on what you're actually trying to do.

---

## Quick reference — full topologies

| Mode | Command | Containers | ~RAM | What's in it |
|---|---|---|---|---|
| **minimal** (default) | `make up-minimal` OR plain `docker compose up -d` | ~11 | ~2.5 GB | Core: 1 MySQL, Kafka, Eureka, config-server, auth-server, api-gateway, user/product/order-service, Prometheus, Grafana. |
| **shared-db** | `make up` OR `make up-shared` | ~25 | ~5.5 GB | Minimal + ALL optional (notification, resource-server, mailhog, zipkin, loki/promtail, kafka-ui/exporter, elasticsearch, schema-registry, kafka-connect+postgres, redis, vault). |
| **individual** | `make up-individual` | ~29 | ~6.5 GB | Shared-db PLUS 4 per-service MySQL containers (mysql-user:3307, mysql-product:3308, mysql-auth:3309, mysql-payment:3310). |

Plain `docker compose up` with no `-f` and no `COMPOSE_PROFILES` is
MINIMAL. This is intentional — safe default for someone cloning the
repo and not sure what to run.

---

## Slim mixes (minimal + specific profiles)

If you don't need `full`, add only what you need. These wrappers
compose `minimal` with specific profile tags:

| Command | Adds on top of minimal | ~RAM | Use when |
|---|---|---|---|
| `make up-saga` | `core-plus` (notification + resource-server) | +0.5 GB | F1-F3 saga needs to reach NOTIFIED. |
| `make up-trace` | `core-plus + tracing + email` (+zipkin, +mailhog) | +0.8 GB | Debugging distributed traces, Grafana alert emails. |
| `make up-kafka-debug` | `core-plus + kafka-ops` (+kafka-ui, +kafka-exporter) | +0.6 GB | Browsing topics, checking consumer lag, DLT inspection. |
| `make up-with PROFILES='…'` | Any comma-separated profile tags | varies | Escape hatch for custom combos. |

Examples:

```bash
# Everything to debug a specific saga failure:
make up-with PROFILES="core-plus,tracing,kafka-ops,email"

# F1-F3 saga + Redis cache:
make up-with PROFILES="core-plus,cache"

# Full elastic search integration:
make up-with PROFILES="core-plus,search"
```

---

## Available profile tags

Mix and match. Each tag enables one or two closely-related containers.

| Tag | Containers | ~RAM | Purpose |
|---|---|---|---|
| `core-plus` | notification, resource-server | ~0.5 GB | F1-F3 saga completion + OAuth2 demo. |
| `email` | mailhog | ~20 MB | Catches Grafana alert emails. |
| `tracing` | zipkin | ~150 MB | Distributed tracing across services. |
| `logs` | loki, promtail | ~0.4 GB | Log aggregation + queries from Grafana. |
| `kafka-ops` | kafka-ui, kafka-exporter | ~0.5 GB | Topic browser + consumer-lag Prometheus metrics. |
| `search` | elasticsearch | ~1 GB | CQRS read store. HEAVY — avoid unless needed. |
| `cdc` | postgres-order, kafka-connect | ~0.8 GB | Debezium CDC → Kafka. |
| `avro` | schema-registry | ~0.4 GB | Schema evolution for Kafka messages. |
| `cache` | redis | ~30 MB | Rate-limit backend, cache. |
| `secrets` | vault-dev | ~50 MB | Vault transit engine (JWT signing). |
| `loadtest` | k6 | one-shot | k6 load tests. Not started with `up`. |
| `full` | **all of the above except loadtest** | — | Shortcut for shared-db mode. |

---

## Resource caps (why your laptop will survive)

Every container has `mem_limit` enforced. Every JVM is heap-capped via
`JAVA_TOOL_OPTIONS`. This means a stray leak or runaway JVM can't eat
your Mac — it OOM-kills itself first.

| Service | `mem_limit` | JVM heap |
|---|---|---|
| api-gateway | 512m | -Xmx384m |
| auth-server | 512m | -Xmx384m |
| user-service | 512m | -Xmx384m |
| product-service | 512m | -Xmx384m |
| order-service | 512m | -Xmx384m |
| config-server | 384m | -Xmx256m |
| eureka-server | 384m | -Xmx256m |
| notification | 384m | -Xmx256m |
| resource-server | 384m | -Xmx256m |
| mysql-shared | 768m | — (InnoDB buffer-pool capped at 256M) |
| kafka | 1g | -Xmx512m |
| elasticsearch | 1g | -Xmx384m |
| zipkin / loki / promtail | 128-384m | — |
| redis | 128m | — (maxmemory 64m) |

Spring services all use `-XX:+UseSerialGC` — single-threaded GC trades
throughput for low memory overhead. Correct for a dev laptop. For
"small" services (config-server, eureka-server) also `TieredStopAtLevel=1`
to skip C2 JIT (saves ~50 MB each).

Check live usage any time:
```bash
make stats       # docker stats with CPU/MEM/MEM%
```

---

## Switching modes

```bash
make down                     # tear down current (any topology/profile combo)
make up-minimal               # core only
# ... later ...
make down
make up-saga                  # minimal + F1-F3 saga
# ... later ...
make down
make up                       # shared-db (everything)
```

Volumes survive across switches:
- `mysql-shared-data` is reused by minimal + shared-db modes.
- `grafana-data`, `prometheus-data`, `kafka-data` persist.
- Individual mode uses DIFFERENT per-service MySQL volumes — switching
  minimal↔individual means your data is in different places.

---

## Env vars that drive topology behavior

For each service with a database, there's a `*_DB_URL` env var that
defaults to `mysql-shared:3306` and gets overridden by the individual
overlay:

| Service | Env var | Shared default | Individual override |
|---|---|---|---|
| user-service | `USER_DB_URL` | `jdbc:mysql://mysql-shared:3306/userdb` | `jdbc:mysql://mysql-user:3306/userdb` |
| product-service | `PRODUCT_DB_URL` | `…/mysql-shared:3306/productdb` | `…/mysql-product:3306/productdb` |
| auth-server | `AUTH_DB_URL` | `…/mysql-shared:3306/authdb` | `…/mysql-auth:3306/authdb` |
| paymentservice | `PAYMENT_DB_URL` | `…/mysql-shared:3306/paymentdb` | `…/mysql-payment:3306/paymentdb` |

You can override these from `.env` without touching the compose files
if, say, you want paymentservice talking to its own DB while the rest
share.

---

## Migrating from the old 4-MySQL layout to shared-db

If you had the stack running before the topology refactor and want to
preserve your data:

```bash
make down
docker volume ls | grep mysql           # confirm old volumes exist
make up-minimal                         # start empty mysql-shared
make migrate-db-to-shared               # dump+restore each schema
```

Once you've confirmed data is in mysql-shared, you can prune:
```bash
docker volume rm mysql-user-data mysql-product-data mysql-auth-data mysql-payment-data
```

---

## "My laptop is slow — what should I do?"

In priority order:

1. **Use `make up-minimal`**, not `make up`. 11 vs 25 containers = ~3 GB saved.
2. **Need the saga's notify step?** Use `make up-saga` instead (+2 containers only).
3. **Need tracing/kafka-ui?** Use `make up-trace` / `make up-kafka-debug` instead of full.
4. **Avoid `search` profile** unless you actually need Elasticsearch — it's 1 GB alone.
5. **Watch `make stats`** — any JVM pushing its mem_limit may need its `JAVA_TOOL_OPTIONS` nudged up (if the container is OOM-killing) or your code checked for a leak.
6. **Bump Docker Desktop allocation** (Settings → Resources) if you have the host RAM: 8 → 10-12 GB, keeping 4 GB free for macOS.
7. **Run the service you're editing on the host** via `mvn spring-boot:run` and leave infra in Docker. Faster rebuild loop + less RAM.

---

## Why not "merge services into one container"?

Short answer: anti-pattern.

- Docker's model is one process per container. Supervisord-style
  bundling kills independent restart, logging, resource limits, and
  horizontal scaling.
- The topology work above achieves "fewer containers" via the correct
  mechanism: optional services off by default. If notification isn't
  needed, don't start it — don't fuse it into order-service.

If you specifically want fewer HTTP endpoints exposed, that's a gateway
routing problem, not a container problem.

---

## See also

- [`docker-howto.md`](docker-howto.md) — day-to-day Docker commands for this repo.
- `docker-compose.yml` — base, source of truth.
- `docker-compose.topology-individual.yml` — overlay for individual.
- `init-shared-db/01-create-schemas.sql` — schemas created on first boot.
- `scripts/migrate-to-shared-db.sh` — one-time data migration.
- `Makefile` — all shortcuts in one file.
