# Docker — day-to-day how-to for this repo

Everything you need to run, inspect, and debug this stack with Docker.
Not a Docker tutorial — assumes you know what a container is. For
*which* topology to run, read [`topologies.md`](topologies.md) first;
this doc is about *using* Docker once you've picked one.

---

## 0. Pure-Docker vs. `start-stack.sh` — which to use

Two ways to run this stack:

```
┌──────────────────────────────────────────────────────────────────────┐
│  make up / up-minimal / up-saga / …                                  │
│    Everything in Docker. Fewer moving parts. Closest to prod.        │
│    Slower iteration — rebuild image on each code change.             │
│                                                                      │
│  ./start-stack.sh                                                    │
│    Infra in Docker, apps as `java -jar` on your Mac.                 │
│    Fast iteration — rebuild jar + re-run, no image step.             │
│    IDE debugger attaches directly.                                   │
└──────────────────────────────────────────────────────────────────────┘
```

Pick by what you're doing today:

| Task | Use |
|---|---|
| Hands-off demo / verifying a flow works | `make up` or `make up-saga` |
| Running tests from CI / scripts | `make up-saga` |
| Editing Java and want fast rebuild + IDE breakpoints | `./start-stack.sh --infra-only` + run app from IDE |
| First time ever cloning this repo | `./start-stack.sh` (auto-detects, builds, starts everything) |
| Just one service at a time | `./start-stack.sh user-service` (resolves deps) |

`start-stack.sh` reference:

```bash
./start-stack.sh                    # auto-detect: first run → full bootstrap
./start-stack.sh --all              # everything
./start-stack.sh --lean             # ~5 GB  — shop + backoffice + CQRS
./start-stack.sh --minimal          # ~2.5 GB — shop checkout
./start-stack.sh --demo             # ~1.8 GB — no auth, permit-all gateway
./start-stack.sh --infra-only       # containers only; IDE runs the apps
./start-stack.sh --apps-only        # infra assumed up; just launch JVMs
./start-stack.sh --skip-build       # don't re-run mvn package
./start-stack.sh user-service       # start one service + its deps
./start-stack.sh --list             # catalogue of all 15 apps
./start-stack.sh --status           # what's running right now
```

Stop:
```bash
./stop-stack.sh                     # everything
./stop-stack.sh --apps-only         # keep infra up (useful for IDE workflow)
./stop-stack.sh user-service        # stop ONE app, leave others + infra
./stop-stack.sh --purge             # down -v — DELETES all docker volumes
```

Rest of this doc is about the pure-Docker path (`make up` family).

---

## 1. Mental model — what's actually running

```
┌─────────────────────────────────────────────────────────────────┐
│                     Docker host (your Mac)                      │
│                                                                 │
│   ┌────────────── network: backend ──────────────────────┐      │
│   │                                                      │      │
│   │  [eureka]   [config-server]   [auth-server]          │      │
│   │                                                      │      │
│   │  [api-gateway]  ◀── exposed on host :9010            │      │
│   │       │                                              │      │
│   │       ▼ routes to                                    │      │
│   │  [user-service]  [product-service]  [order-service]  │      │
│   │       │                │                 │           │      │
│   │       └────────┬───────┴─────────────────┘           │      │
│   │                ▼                                     │      │
│   │           [mysql-shared]   [kafka]                   │      │
│   │                                                      │      │
│   │  [prometheus] ─scrapes──▶ every /actuator/prometheus │      │
│   │  [grafana]    ─queries──▶ prometheus                 │      │
│   │                                                      │      │
│   └──────────────────────────────────────────────────────┘      │
│                                                                 │
│   Volumes: mysql-shared-data, grafana-data, prometheus-data …   │
└─────────────────────────────────────────────────────────────────┘
```

- **One network** (`backend`) — every service resolves others by
  container name. `user-service` talks to `mysql-shared` by the string
  `mysql-shared`, not an IP.
- **One MySQL** (in default `shared-db` mode) holding 4 schemas:
  `userdb`, `productdb`, `authdb`, `paymentdb`.
- **Only `api-gateway`** publishes a host port (`9010`). Everything else
  is reachable only from inside the `backend` network.
- Volumes keep MySQL data, Grafana dashboards, Prometheus TSDB alive
  across `down`/`up`.

---

## 2. Starting the stack

| Command | What you get |
|---|---|
| `make up-minimal` | ~11 containers — core only. Fastest. |
| `make up` *(= `make up-shared`)* | ~25 containers — core + all optional (mailhog, zipkin, loki, kafka-ui, …). |
| `make up-individual` | ~29 containers — same as shared-db but with 4 per-service MySQLs. |

Behind the scenes:
- `make up-minimal` → `docker compose up -d`
- `make up` → `COMPOSE_PROFILES=full docker compose up -d`
- `make up-individual` → `COMPOSE_PROFILES=full docker compose -f docker-compose.yml -f docker-compose.topology-individual.yml up -d`

`-d` = detached. Containers run in the background; you get your shell
back immediately.

**First run** on a new machine will download images (~2 GB) — subsequent
`up`s are seconds. Pre-cache images without actually starting anything:

```bash
docker compose pull
```

---

## 3. Stopping, restarting, nuking

| Command | What it does |
|---|---|
| `make down` | Stop + remove containers. Volumes stay. **Data preserved.** |
| `docker compose down -v` | Stop + remove containers + delete volumes. **Data gone.** |
| `make restart` | `down` then `up` (shared mode). |
| `docker compose restart user-service` | Restart ONE container, keep others running. |
| `docker compose stop user-service` | Stop one container without removing it. |
| `docker compose start user-service` | Start a stopped container. |

Rule of thumb: use `restart` to pick up config changes that need a JVM
reboot, use `stop`/`start` when you want to pause a service without
losing its container-level state (useful for simulating a crashed
service during saga demos).

---

## 4. Seeing what's running

```bash
docker compose ps                 # just this project's containers
docker ps                         # ALL containers on the host
docker compose ps --format wide   # full command, ports, image
```

Columns to care about:
- **STATUS** — `Up 2 minutes (healthy)` is what you want. `(unhealthy)`
  means the container's own healthcheck is failing (fix: check its
  logs).
- **PORTS** — `0.0.0.0:9010->8080/tcp` means container port 8080 is
  reachable at host `localhost:9010`.

Quick "what services do I have running":

```bash
docker compose ps --services       # list of service NAMES
docker compose ps --services --filter status=running
```

---

## 5. Logs

Three patterns depending on what you need:

```bash
# Follow all logs, newest 100 lines first
make logs

# Follow one service
make logs-user-service
# equivalent to: docker compose logs -f --tail=200 user-service

# Last 50 lines, don't follow
docker compose logs --tail=50 order-service

# Only errors from the last 10 min
docker compose logs --since 10m order-service | grep -i error

# Multiple services at once (great for saga debugging)
docker compose logs -f --tail=50 order-service paymentservice notification
```

**Tip** — for a long debug session, open 3-4 terminal tabs each tailing
one service. The interleaved stream from `make logs` gets noisy once
Kafka chatter kicks in.

---

## 6. Getting a shell inside a container

```bash
docker compose exec user-service sh
# then inside the container:
env | grep -i spring
cat /app/application.yml
ps aux
```

Common uses:

- **MySQL CLI**:
  ```bash
  docker compose exec mysql-shared mysql -uroot -p$MYSQL_ROOT_PASSWORD userdb
  ```

- **Hit a service from inside the network** (bypass gateway, useful to
  isolate "is it the gateway or the service?"):
  ```bash
  docker compose exec api-gateway curl -s http://user-service:8080/actuator/health
  ```

- **Check what env the JVM actually sees** (your `.env` ↔ compose ↔
  container chain can hide a typo):
  ```bash
  docker compose exec order-service env | grep -E 'KAFKA|SPRING|CONFIG'
  ```

- **Inspect config-server fetched config** on the client side:
  ```bash
  docker compose exec user-service curl -s http://config-server:8888/user-service/default | jq .
  ```

Containers without a shell (minimal images) — use `docker compose exec
<svc> /bin/sh` instead of `bash`.

---

## 7. Rebuilding after a code change

Most of our services are built OUTSIDE Docker (`mvn install`) then
packaged into an image by the service's `Dockerfile`. So the loop is:

```bash
# After editing Java code in e.g. user-service
mvn -pl user-service -am install -DskipTests
docker compose build user-service
docker compose up -d user-service          # recreates just this container
```

Or in one shot:

```bash
mvn -pl user-service -am install -DskipTests \
  && docker compose up -d --build user-service
```

To rebuild everything (nuclear):

```bash
make build                                 # mvn install, all modules
docker compose build                       # rebuild all images
docker compose up -d --force-recreate      # replace all containers
```

**Watch out** — `docker compose build` uses cached layers aggressively.
If you suspect a stale image, add `--no-cache`:
```bash
docker compose build --no-cache user-service
```

---

## 8. Compose profiles — opt-in services

Services tagged `profiles: ["full"]` in `docker-compose.yml` are OFF by
default. They turn on only when `COMPOSE_PROFILES` includes the matching
tag.

| Profile | Services it unlocks | How to enable |
|---|---|---|
| *(none / default)* | 11 core services always on | `docker compose up` |
| `full` | vault-dev, kafka-exporter, kafka-ui, postgres-order, kafka-connect, redis, elasticsearch, schema-registry, zipkin, loki, promtail, mailhog, resource-server, notification | `COMPOSE_PROFILES=full docker compose up` |
| `loadtest` | k6 | `COMPOSE_PROFILES=loadtest docker compose run k6 …` |

Mix them:
```bash
COMPOSE_PROFILES=full,loadtest docker compose up -d
```

Check which profiles a service has:
```bash
docker compose config | grep -A1 profiles
```

---

## 9. Networks — "why can't X reach Y?"

All services sit on the `backend` bridge network. Within it, resolution
is by **service name** (= container name in our setup).

```bash
# From inside order-service, can I reach kafka?
docker compose exec order-service sh -c 'nc -zv kafka 9092'

# What's on the network?
docker network inspect my-microservices_backend | jq '.[0].Containers | keys'
```

Common gotchas:

- `localhost` INSIDE a container ≠ your Mac's localhost. It's the
  container itself.
- To call your Mac from inside a container (e.g. a locally running
  paymentservice on your host): use `host.docker.internal:PORT`.
- Services are reachable from the host **only** on their published
  ports (shown by `docker compose ps`). Everything else is network-
  internal.

Published ports in this stack:
- `9010` → api-gateway
- `3306` → mysql-shared (and `3307`-`3310` in individual mode)
- `9092` → kafka
- `8761` → eureka
- `8888` → config-server
- `3000` → grafana
- `9090` → prometheus
- `9411` → zipkin (full profile)
- `8025` → mailhog UI (full profile)
- `8085` → kafka-ui (full profile)

---

## 10. Volumes — where the data actually lives

```bash
docker volume ls | grep my-microservices
```

Important ones:
- `my-microservices_mysql-shared-data` — all 4 schemas.
- `my-microservices_mysql-user-data` etc. — only in individual mode.
- `my-microservices_grafana-data` — your dashboards + alert state.
- `my-microservices_prometheus-data` — metrics history.
- `my-microservices_kafka-data` — Kafka log segments.

Inspect a volume (where is it on disk):
```bash
docker volume inspect my-microservices_mysql-shared-data
```

Nuke one volume (and only one):
```bash
docker compose stop mysql-shared
docker volume rm my-microservices_mysql-shared-data
docker compose up -d mysql-shared          # init SQL recreates schemas
```

Nuke ALL volumes for this project:
```bash
docker compose down -v
```

**Why this matters** — after a `make down`, your data is still there.
After `docker compose down -v`, it's gone and Grafana wakes up with the
provisioned dashboards only (your custom panels, alert silences,
annotations — all gone).

---

## 11. Debugging a broken container

Decision tree when something's wrong:

```
Container not even showing in `docker compose ps`?
   └─▶ `docker compose up -d <svc>` and read the output.
       Usually an invalid env var or missing dependency.

Container is Up but (unhealthy)?
   └─▶ `docker compose logs --tail=200 <svc>`
       Healthcheck is in docker-compose.yml. If MySQL,
       check MYSQL_ROOT_PASSWORD matches your .env.

Container is Up (healthy) but my request fails?
   ├─▶ `curl` the service directly from the gateway:
   │     docker compose exec api-gateway curl -s http://<svc>:8080/actuator/health
   │   If this works, the problem is between gateway and host.
   │   If this fails, the service itself is sick despite healthcheck.
   │
   ├─▶ `docker compose logs -f <svc>` and reissue the request.
   │   Watch for stack traces timestamped around the request.
   │
   └─▶ Check it registered with Eureka:
         curl http://localhost:8761/eureka/apps | grep -i <svc>

Container is Up but can't reach another container?
   └─▶ From inside the sick container:
         docker compose exec <svc> nc -zv <other-svc> <port>
       If connection refused: the other service's port isn't open yet.
       If no route to host: wrong network (shouldn't happen in this
       repo — everything's on `backend`).
```

---

## 12. Config: `.env` → compose → container

The pipeline is:

```
.env file                   docker-compose.yml                 container env
───────────                 ───────────────────                ─────────────
MYSQL_ROOT_PASSWORD=foo  ─▶ environment:               ─▶     $MYSQL_ROOT_PASSWORD=foo
                               MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
```

Verify what compose will actually resolve to:
```bash
docker compose config                 # expanded compose file with .env substituted
docker compose config | grep -A1 PASSWORD
```

Verify what the container *actually* got:
```bash
docker compose exec user-service env | grep MYSQL
```

If these two disagree, the container is running on a stale image
(rebuild with `--force-recreate`) or `.env` wasn't loaded (compose reads
`.env` from the directory you run compose IN, not where the yml lives).

---

## 13. The commands you'll actually use day-to-day

Save these somewhere accessible:

```bash
# Daily
make up                         # bring up full stack
make down                       # tear down
docker compose ps               # what's running
make logs-order-service         # tail one service
docker compose exec mysql-shared mysql -uroot -p$MYSQL_ROOT_PASSWORD userdb

# When you break something
docker compose logs --tail=100 <svc>
docker compose restart <svc>
docker compose up -d --force-recreate <svc>

# When you change code
mvn -pl <svc> -am install -DskipTests
docker compose up -d --build <svc>

# When you change .env
docker compose up -d --force-recreate <affected svcs>

# When nothing makes sense
docker compose down
docker compose up -d
```

---

## 14. See also

- [`topologies.md`](topologies.md) — which mode to pick and why.
- [`01-startup-runbook.md`](01-startup-runbook.md) — green-field boot.
- [`02-verification-checklist.md`](02-verification-checklist.md) — "is
  the stack actually working?" curl list.
- `Makefile` — all shortcuts in one file.
- `docker-compose.yml` — base topology, source of truth for ports,
  images, networks.
