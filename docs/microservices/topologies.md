# Container topologies

The stack ships three ways. All run the same code, same configs, same
Spring profiles — only the surrounding containers differ. Topology is a
deployment-time choice, driven by Compose profiles + overlay files, so
you can switch modes without changing a line of Java.

Pick a mode based on what you're actually trying to do.

---

## Quick reference

| Mode | Command | Containers | What's in it |
|---|---|---|---|
| **minimal** (default) | `make up-minimal` OR plain `docker compose up -d` | ~12 | Just the core stack: 1 MySQL (shared), Kafka, Eureka, config-server, the 5 business services, api-gateway, Prometheus, Grafana. |
| **shared-db** | `make up` OR `make up-shared` | ~26 | Minimal + all optional services (observability extras, mailhog, vault, elasticsearch, debezium CDC, resource-server, notification, kafka-ui, kafka-exporter). |
| **individual** | `make up-individual` | ~30 | Shared-db PLUS 4 per-service MySQL containers (mysql-user, mysql-product, mysql-auth, mysql-payment) replacing mysql-shared. |

The default `docker compose up` with no `-f` and no `COMPOSE_PROFILES` is
MINIMAL. This is intentional — it's the safe choice for someone cloning
the repo and not sure what to run.

---

## Mode: minimal

The 12 containers include enough to:
- Run **F4 (Mock)** and **F5 (Stripe)** checkout flows (both pay-and-done).
- See metrics in Grafana (Prometheus scrapes services).
- Exercise saga STARTED → PAID transitions.

What's MISSING:
- `notification` service — the saga's notify step will time out, and the
  saga will NOT reach `NOTIFIED`. F1-F3 (which assert NOTIFIED) fail.
- `zipkin` — traces still generated in-process but not collected or
  queryable.
- `loki` / `promtail` — no log aggregation. Container stdout only.
- `mailhog` — Grafana alert emails have nowhere to go (alerts still fire
  into the /alerting UI).
- `kafka-ui`, `kafka-exporter` — no browser-based Kafka inspection and
  no Kafka lag Prometheus metrics.
- `vault-dev`, `elasticsearch`, `schema-registry`, `redis`, debezium
  stack — not needed for the headline flows.

**Use when**: constrained laptop, CI, or you just want the smallest set
of containers that gets a working saga + metrics.

---

## Mode: shared-db

Adds all 14 optional services on top of minimal. Still one MySQL container
hosting 4 (actually 5, counting authdb_jdbc) schemas. ~26 containers.

**Use when**: you want the full feature set — complete Grafana alerting
flow via mailhog, Zipkin traces, Kafka UI for debugging flows, DLT
tooling. This is the "demo everything" mode.

---

## Mode: individual

Same as shared-db but replaces mysql-shared with 4 per-service MySQL
containers (mysql-user:3307, mysql-product:3308, mysql-auth:3309,
mysql-payment:3310 on the host). Each service points at its own container.

**Use when**:
- You want to demonstrate prod-shaped per-service DB isolation.
- You're testing migration-drift scenarios where one schema is ahead or
  behind the others.
- You just want to see what the stack looked like before the topology
  refactor.

---

## Switching modes

```bash
make down                     # tear down current (regardless of mode)
make up-minimal               # bring up minimal
# ... later ...
make down
make up                       # bring up shared-db
# ... later ...
make down
make up-individual            # bring up individual
```

Each mode uses the SAME `mysql-shared-data` Docker volume (for shared-db
and minimal) or the per-service volumes (for individual). Switching
preserves data within each volume — you don't lose userdb when you
switch from minimal to shared.

Switching between **shared-db** and **individual** uses different volumes
though — those are different MySQL containers.

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
# 1. Stop everything.
make down

# 2. Make sure the old mysql-* containers' volumes still exist (they do
#    unless you ran `docker volume prune` yourself).
docker volume ls | grep mysql

# 3. Start just mysql-shared. The init SQL creates the empty schemas.
make up-minimal

# 4. Run the migration script.
make migrate-db-to-shared

# This spins each old mysql-* container up briefly, dumps its one DB,
# restores it into mysql-shared, verifies row counts match, and stops
# each old container if it wasn't running to begin with.
# Safe to re-run (it will re-dump + re-restore, but that's idempotent).
```

Once you've confirmed data is in mysql-shared, you can prune the old
volumes:
```bash
docker volume rm mysql-user-data mysql-product-data mysql-auth-data mysql-payment-data
```

---

## Why not "merge services into one container"?

Short answer: anti-pattern.

- Docker's model is one process per container. Running
  shop-ui + backoffice-ui in a single container via supervisord means
  you lose independent restart, independent logging, independent resource
  limits, and the ability to scale one without the other.
- The topology work above achieves "fewer containers" via the correct
  mechanism: optional services off by default. If notification isn't
  needed, don't start it — don't bundle it into order-service.

If you specifically want fewer HTTP endpoints exposed, that's a gateway
routing problem, not a container problem. The gateway already fronts all
of /api/v1/* and /admin/* — add more routes if you want more logical
grouping behind one URL.

---

## See also

- `docker-compose.yml` — base, source of truth.
- `docker-compose.topology-individual.yml` — overlay for individual.
- `init-shared-db/01-create-schemas.sql` — schemas created on first boot
  of mysql-shared.
- `scripts/migrate-to-shared-db.sh` — one-time data migration.
- `Makefile` — all the shortcuts.
