# First-time setup

First time running this project? Start here.

## What you're setting up

This is a Spring Boot microservices playground — a mini e-commerce
stack with user, product, order, and payment services talking to each
other through a gateway, Kafka, and MySQL. Everything runs in Docker
on your Mac, so you don't need to install databases or message brokers
yourself.

## What you'll have at the end

A working stack you can hit with `curl` — place an order, see it flow
through 4 services and a saga, land in the database. You'll also have
Grafana dashboards showing live metrics (if you pick the full mode).

## What this doc gets you

The shortest path from `git clone` to "the stack is running and I can
curl it." No deep explanations here — the architecture lives in
[`../00-architecture-overview.md`](../00-architecture-overview.md).

- Install the 2-4 tools you need (Docker, Git, optionally Java + Maven)
- Create a `.env` file for secrets
- Pick a mode (small/medium/full)
- Verify everything's healthy

## Contents

1. [Prereqs — install these once](#1-prereqs--install-these-once----required--one-time) — *Required · One-time*
2. [Clone and enter the repo](#2-clone-and-enter-the-repo----required--one-time) — *Required · One-time*
3. [Set up your `.env` file](#3-set-up-your-env-file----required) — *Required*
4. [Pick a mode and start the stack](#4-pick-a-mode-and-start-the-stack----required--pick-one) — *Required · pick one*
    - Option A — nano (lightest)
    - Option B — minimal (full stack in Docker)
    - Option C — hybrid (infra in Docker, apps on Mac)
5. [Verify it's working](#5-verify-its-working----required) — *Required*
6. [Run a sanity test](#6-run-a-sanity-test----optional--recommended) — *Optional · recommended*
7. [Stop everything when done](#7-stop-everything-when-done----optional) — *Optional*

- [Troubleshooting first-run failures](#troubleshooting-first-run-failures)
- [What's next](#whats-next)
- [Quick reference card](#quick-reference-card)
- [Option A deep dive — nano mode](#option-a-deep-dive--nano-mode)

---

## 1. Prereqs — install these once  — **Required · One-time**

Open Terminal and check each:

```bash
docker --version        # need Docker Desktop 4.0+ (Compose v2 built in)   Required
java --version          # need Java 17.x                                   Optional (only Option C)
mvn --version           # need Maven 3.8+                                  Optional (only Option C)
git --version           # ships with macOS, should Just Work               Required
```

**Docker + Git are the only MUST-HAVES for the pure-Docker modes
(Options A + B).** Java and Maven are needed ONLY if you plan to run
services on your host via Option C (hybrid). Skip them for now if
you're unsure.

If any are missing:

| Tool | Install with | When needed |
|---|---|---|
| Docker Desktop | https://www.docker.com/products/docker-desktop/ — install, open, let it start | Always |
| Git | `xcode-select --install` (usually preinstalled on macOS) | Always |
| Java 17 | `brew install openjdk@17` (then follow brew's post-install symlink notes) | Only Option C |
| Maven | `brew install maven` | Only Option C |

### Docker Desktop — set memory to at least 8 GB — **Required**

Open Docker Desktop → Settings → Resources:

```
Memory:  8 GB    (minimum — 10-12 GB is better if you have the RAM)
CPUs:    5       (minimum)
Disk:    60 GB
```

Click **Apply & Restart**. If you give Docker less than 8 GB, the
shared-db mode will OOM-kill containers.

> **For nano mode only (Option A), 4 GB is enough.** But 8 GB is the
> safe default for all modes.

---

## 2. Clone and enter the repo  — **Required · One-time**

```bash
git clone git@github.com:bhanuonline/my-microservices.git
cd my-microservices
```

(If you're reading this, you've probably already done that.)

---

## 3. Set up your `.env` file  — **Required**

The project uses a `.env` file for secrets (passwords, tokens). It's
gitignored — you create it from a template:

```bash
cp .env.example .env
```

Open `.env` in any editor and set these two to any value you want.
Compose refuses to start if they're blank:

```bash
MYSQL_ROOT_PASSWORD=changeme       # Required. Any string. MySQL root password.
GF_ADMIN_PASSWORD=admin            # Required. Any string. Grafana admin login.
                                   # (Still required even in nano mode — the
                                   #  Grafana block is defined in the base
                                   #  compose file, so compose validates it.)
```

Every other variable in `.env.example` has a sensible default in
`docker-compose.yml` — you can leave them blank or as-is unless you
want that specific feature:

- `DB_USERNAME`, `DB_PASSWORD` — defaults to `root` / `pass1234`
- `VAULT_DEV_ROOT_TOKEN` — only matters in `full` mode
- `STRIPE_*`, `RAZORPAY_*`, `PAYPAL_*`, `CHECKOUTCOM_*` — only if you
  want to test that payment provider

Save and close.

---

## 4. Pick a mode and start the stack  — **Required · pick one**

Three ways to run. Each trades off RAM + feature coverage differently.
Pick ONE based on what you want to do.

### Quick comparison

| | **Option A — nano** | **Option B — minimal** | **Option C — hybrid** |
|---|---|---|---|
| **Command** | `make up-nano` | `make up-minimal` | `./start-stack.sh` |
| **Containers** | 7 | 13 | 3 (infra) + apps on host |
| **RAM** | ~1.6 GB | ~2.6 GB | ~1.5 GB Docker + ~2 GB host |
| **First-run time** | 5-8 min | 5-10 min | 5-10 min |
| **Later runs** | ~30-45 s | ~45-60 s | ~1-2 min |
| **Business services** | ❌ off | ✓ in Docker | ✓ on your Mac |
| **Can hit `/api/v1/*` endpoints** | ❌ no | ✓ yes | ✓ yes |
| **Grafana + Prometheus** | ❌ no | ✓ yes | ✓ yes |
| **IDE debugging / breakpoints** | ❌ no | ❌ no | ✓ yes |
| **Fast code-rebuild cycle** | — | ❌ needs image rebuild | ✓ `mvn` + rerun |
| **Needs Java 17 + Maven on host** | ❌ no | ❌ no | ✓ yes |

### What each option is for

| Option | Pick this when… | Good as a first run? |
|---|---|---|
| **A — nano** | You want to prove the stack boots cleanly before investing time. Smallest footprint. | ✓ **Recommended first-time** |
| **B — minimal** | You want to curl business APIs end-to-end without opening an IDE. | ✓ Also good |
| **C — hybrid** | You're actively editing Java code and want IDE breakpoints + fast re-runs. | ✗ Skip until you need it |

### If still unsure

Pick **A (nano)** first. If it boots cleanly, you've proven:
- Docker Desktop has enough RAM
- Your ports (3306, 8080, etc.) are free
- Your `.env` is set up right
- The images build successfully

Then upgrade to B with `make down && make up-minimal` — 2 extra
minutes and you've got the full stack.

---

### Option A — nano  (lightest)

7 containers, ~1.6 GB RAM. Just the platform floor — no business
services yet.

```bash
make up-nano
```

**What runs:** `mysql-shared`, `kafka`, `redis`, `eureka-server`,
`config-server`, `auth-server`, `api-gateway`.

**What does NOT run:** the 4 business services (user, product, order,
payment), Prometheus/Grafana, Zipkin, notification, resource-server.
`curl /api/v1/users` will return 503 — expected.

**You can run a business service from your IDE** (connects to this
Docker-hosted platform).

→ **[Deep dive on nano mode](#option-a-deep-dive--nano-mode)** —
why these 7, startup order diagram, next steps, nano-specific troubleshooting.

---

### Option B — minimal  (full stack in Docker)

13 containers, ~2.6 GB RAM. Everything in nano PLUS the 4 business
services and observability (Prometheus + Grafana).

```bash
make up-minimal
```

**What runs:** everything in nano + `user-service` (8081),
`product-service` (8082), `order-service` (8083),
`payment-service` (8091), `prometheus` (9090), `grafana` (3000).

**What does NOT run:** notification, resource-server, zipkin, kafka-ui,
elasticsearch, vault, etc. — those live behind other mode profiles
(see [`topologies.md`](topologies.md)).

---

### Option C — hybrid  (infra in Docker, apps on your Mac)

Infra containers run in Docker (fast + stable). Business services run
as `java -jar` on your host (fast rebuild, IDE breakpoints, debugger
attach).

```bash
./start-stack.sh    # auto-detects first run → full bootstrap
                    # (builds all jars, pulls images, starts everything)
```

**Needs:** JDK 17 + Maven installed on your Mac (see Step 1).

See [`docker-howto.md`](docker-howto.md) section 0 for when to pick
which. For now, if you're not sure, pick A or B.

---

## 5. Verify it's working  — **Required**

Wait ~60-90 seconds after step 4 finishes (Spring Boot services need
time to register with Eureka). Then:

```bash
docker compose ps                    # should show containers "Up (healthy)"
```

Open these in your browser (adjust by which Option you picked):

| URL | Option A (nano) | Option B (minimal) | Option C (hybrid) |
|---|:---:|:---:|:---:|
| http://localhost:8761 — Eureka | ✓ | ✓ | ✓ |
| http://localhost:8080/actuator/health — Gateway | ✓ | ✓ | ✓ |
| http://localhost:3000 — Grafana | — | ✓ | ✓ |
| http://localhost:9090 — Prometheus | — | ✓ | ✓ |

If any of these fail, see [troubleshooting](#troubleshooting-first-run-failures) below.

---

## 6. Run a sanity test  — **Optional · recommended**

Not strictly required — step 5 already proved the stack is healthy.
This step just confirms end-to-end request flow works.

### If you picked Option A (nano) — gateway + eureka only

```bash
# Gateway health JSON
curl -s http://localhost:8080/actuator/health

# Eureka — list of registered apps (should show at least api-gateway + config-server)
curl -s http://localhost:8761/eureka/apps | grep -oE '<name>[^<]+' | sort -u
```

Expected: gateway returns `{"status":"UP",...}`. Eureka shows 2
`<name>…` entries. Nano doesn't include business services, so there's
no product endpoint to hit yet — add one from your IDE (see
[`topologies.md`](topologies.md) "Mode: nano — Typical workflow").

### If you picked Option B (minimal) — fetch a product

```bash
curl -u admin:admin123 http://localhost:8080/api/v1/products
```

Should return a JSON array of products (empty `[]` is fine on first run —
means the API works, no products inserted yet).

If you get `Connection refused`: wait 30 more seconds for services to
finish starting, then retry.

---

## 7. Stop everything when done  — **Optional**

Run only when you're done for the day. Containers can stay running
across sessions without issue; data persists in Docker volumes.

```bash
make down                 # if you used Option A
./stop-stack.sh           # if you used Option B
```

Data stays in Docker volumes between runs. To wipe data clean (fresh
start):

```bash
docker compose down -v    # removes volumes = DELETES all DB data
```

---

## Troubleshooting first-run failures

### "set MYSQL_ROOT_PASSWORD in .env"
You skipped step 3, or your `.env` has a blank value. Open `.env`, set
`MYSQL_ROOT_PASSWORD` to any string, save.

### Container exits immediately
Check its log:
```bash
docker compose logs <container-name>
```
Most common cause: a required env var isn't set. The compose file uses
`${VAR:?set VAR in .env}` syntax so missing values fail fast with a
clear message.

### "Cannot connect to Docker daemon"
Docker Desktop isn't running. Open it from Applications. Wait for the
whale icon in the top bar to stop animating.

### Grafana login fails
Default `admin/admin` was changed in step 3. Use what you put in
`GF_ADMIN_PASSWORD`. If forgotten, re-edit `.env` and run `make down &&
make up-minimal`.

### Port already in use (e.g. `3306`)
Something else on your Mac is already using that port (common for
MySQL). Stop it:
```bash
lsof -i :3306                          # find what's using it
brew services stop mysql               # if Homebrew mysql is running
```

### Everything looks healthy but APIs return 401
You're missing the `-u admin:admin123` in curl. Every API through the
gateway needs HTTP Basic auth in dev mode.

### Build takes forever
Normal on first run: 2 GB image downloads + full mvn package. Subsequent
runs skip both — ~30s to start.

### Still stuck
Check [`troubleshooting.md`](../debug/troubleshooting.md) and
[`setup-error.md`](setup-error.md) for known errors.

---

## What's next

Now that it's running:

- [`00-architecture-overview.md`](../00-architecture-overview.md) — 5-min overview of the system
- [`docker-howto.md`](docker-howto.md) — day-to-day Docker commands for this repo
- [`topologies.md`](topologies.md) — all the modes you can run (`make up-saga`, `--trace`, etc.)
- [`02-verification-checklist.md`](02-verification-checklist.md) — full "is it healthy?" curl checklist
- [`debugging-flows.md`](../debug/debugging-flows.md) — Postman collections for walking a saga step-by-step
- [`concepts/`](concepts/) — the patterns you'll see across the code

---

## Quick reference card

```bash
# Pure-Docker mode
make up-nano              # start, platform only (7 containers, biz svcs OFF)
make up-nano-trace        # nano + zipkin for distributed traces (8 containers)
make up-minimal           # start, platform + biz svcs (12 containers)
make up                   # start, everything (26 containers)
make down                 # stop
make logs                 # tail everything
make logs-user-service    # tail one service
make stats                # live CPU/MEM usage
make ps                   # what's running
make verify               # health-check all running services

# MySQL shell shortcuts (password pulled from .env)
make mysql                # root prompt, no schema
make mysql-user           # into userdb
make mysql-product        # into productdb
make mysql-auth           # into authdb
make mysql-payment        # into paymentdb

# Redis shell shortcuts (no password — dev only)
make redis                # redis-cli interactive prompt
make redis-info           # INFO (version/memory/clients)
make redis-keys           # list ALL keys
make redis-flush          # wipe all data (confirms first)

# Kafka CLI shortcuts (TOPIC= and GROUP= where shown)
make kafka-topics                                  # list topics
make kafka-describe TOPIC=order.created            # one topic
make kafka-consume  TOPIC=order.created            # tail live
make kafka-consume  TOPIC=order.created FROM_BEGINNING=1
make kafka-groups                                  # list consumer groups
make kafka-group-describe GROUP=order-saga         # group lag
make kafka-shell                                   # shell into container

# Hybrid mode
./start-stack.sh                        # auto-detect + run
./start-stack.sh --minimal              # explicit minimal
./start-stack.sh user-service           # one service + its deps
./start-stack.sh --status               # what's running
./stop-stack.sh                         # stop
./stop-stack.sh --apps-only             # stop apps, keep infra

# Docker directly
docker compose ps                       # list containers
docker compose logs -f <service>        # tail one service
docker compose exec <service> sh        # shell inside a container
docker stats                            # live resource usage
```

---

## Option A deep dive — nano mode

Linked from Step 4 Option A. Read this if you want to understand WHY
these 7 containers, HOW they start up, and WHAT to do once they're up.

### Why these 7 containers

Each container earns its spot — remove any one and the stack won't start.

| Container | Port | Why it's in nano |
|---|---|---|
| `mysql-shared` | 3306 | Holds 4 schemas (userdb, productdb, authdb, paymentdb). Needed by auth-server at boot (JDBC password hash lookup) and by any business service you later run from your IDE. |
| `kafka` | 9092 | Event bus for sagas + outbox pattern. Pre-started so services you add later don't have to wait for broker boot. |
| `redis` | 6379 | **Required by api-gateway.** Backs the rate-limiter, API-key store, idempotency store, and response cache. Gateway refuses to start without it. |
| `eureka-server` | 8761 | Service discovery. Every other service registers here on boot; without it, nothing can find anything. |
| `config-server` | 8888 | Serves YAML config files to every other service. Services ask for `http://config-server:8888/<service-name>/<profile>`. Mounted from `config-repo/` in the repo. |
| `auth-server` | 8095 | OAuth2 token issuer. Gateway verifies incoming JWTs via its `/oauth2/jwks` endpoint. Also owns the user authentication flow. |
| `api-gateway` | 8080 | Reactive gateway. Only service with a port the outside world (your browser/curl) talks to. Routes based on Eureka lookups. |

### Startup order (handled by `depends_on` healthchecks)

```
   Wave 1  (parallel, independent)
   ┌───────────────┐  ┌───────┐  ┌───────┐
   │ mysql-shared  │  │ kafka │  │ redis │
   └───────┬───────┘  └───┬───┘  └───┬───┘
           │              │          │
   Wave 2  │              │          │
           ▼              │          │
     ┌──────────────┐     │          │
     │ eureka-server│     │          │
     └──────┬───────┘     │          │
            │             │          │
   Wave 3   ▼             │          │
     ┌──────────────┐     │          │
     │ config-server│     │          │
     └──────┬───────┘     │          │
            │             │          │
   Wave 4   ▼             ▼          │
     ┌──────────────┐                │
     │ auth-server  │  (needs mysql) │
     └──────┬───────┘                │
            │                        │
   Wave 5   ▼                        ▼
     ┌───────────────────────────────────┐
     │           api-gateway             │
     │  (needs eureka + auth + redis)    │
     └───────────────────────────────────┘
```

Compose waits for each container's healthcheck to pass before starting
the next wave. End-to-end: ~45-60 seconds on a warm boot.

### Verify each container is healthy

**Shortcut — one command runs all the checks:**

```bash
make verify
```

Runs [`scripts/verify-stack.sh`](../../../scripts/verify-stack.sh)
which prints a colored pass/fail report of containers up, RAM budget,
MySQL/Kafka/Redis probes, Spring Boot health endpoints, and Eureka
registration. Exit code 0 = all green, 1 = something failed.

If you want to understand WHAT it's checking (or need to debug a
specific layer), run the individual commands below:

**1. Are all 7 containers up?**

```bash
docker compose ps
```

Expected: 7 rows, each `STATUS` showing `Up X seconds (healthy)` or
just `Up X seconds` (api-gateway doesn't have a healthcheck configured;
`Up` is enough).

**2. Live resource usage (RAM budget check)**

```bash
docker stats --no-stream
```

Expected: total memory in use ~1.6 GB across all 7 containers. If any
single container is at 95%+ of its limit, see troubleshooting.

**3. Infra health — MySQL, Kafka, Redis**

```bash
# MySQL — list schemas (uses ./scripts/mysql.sh under the hood)
make mysql -- -e "SHOW DATABASES;"
# OR directly:  ./scripts/mysql.sh "" -e "SHOW DATABASES;"

# Kafka — broker responds to API-versions query
docker exec kafka kafka-broker-api-versions.sh --bootstrap-server localhost:9092 2>&1 | head -3

# Redis — PONG
docker exec redis redis-cli ping
```

Expected:
- MySQL: 4 schema names printed (userdb, productdb, authdb, paymentdb)
- Kafka: version numbers list (not an error)
- Redis: `PONG`

> **Tip — MySQL shell:** use
> [`scripts/mysql.sh`](../../../scripts/mysql.sh) — password is pulled
> from `.env` automatically:
>
> ```bash
> make mysql              # root prompt, no schema
> make mysql-user         # into userdb
> make mysql-product      # into productdb
> make mysql-auth         # into authdb
> make mysql-payment      # into paymentdb
> ```
>
> **Tip — Redis shell:** use
> [`scripts/redis.sh`](../../../scripts/redis.sh) — no password (dev-only):
>
> ```bash
> make redis              # redis-cli interactive prompt
> make redis-info         # INFO (version, memory, clients, …)
> make redis-keys         # list ALL keys (don't do this in prod)
> make redis-flush        # wipe all data (with confirmation)
> ```
>
> **Tip — Kafka CLI:** use
> [`scripts/kafka.sh`](../../../scripts/kafka.sh) — hides the long
> `docker exec kafka kafka-X.sh --bootstrap-server ...` prefix:
>
> ```bash
> make kafka-topics                            # list all topics
> make kafka-describe TOPIC=order.created      # one topic's partitions
> make kafka-consume  TOPIC=order.created      # tail live messages
> make kafka-consume  TOPIC=order.created FROM_BEGINNING=1
> make kafka-groups                            # list consumer groups
> make kafka-group-describe GROUP=order-saga   # group members + lag
> ```
>
> For anything exotic: `./scripts/kafka.sh --help`.

**4. Spring Boot services — HTTP health probes**

```bash
echo "=== eureka  ===" ; curl -s http://localhost:8761/actuator/health; echo
echo "=== config  ===" ; curl -s http://localhost:8888/actuator/health; echo
echo "=== auth    ===" ; curl -s http://localhost:8095/actuator/health; echo
echo "=== gateway ===" ; curl -s http://localhost:8080/actuator/health; echo
```

Expected: each returns `{"status":"UP"...}`. If auth-server or gateway
return `DOWN`, it usually means a dependency (MySQL / Redis) isn't
reachable from inside the container.

**5. Eureka registration — are services visible to each other?**

```bash
curl -s http://localhost:8761/eureka/apps | grep -oE '<name>[^<]+' | sort -u
```

Expected: at least `<name>API-GATEWAY` and `<name>CONFIG-SERVER`.
auth-server appears a few seconds after the others. If any are missing
after 1 minute, that service hasn't registered — check its logs:
`docker compose logs --tail 30 <service>`.

### After nano is up — next steps

**1. Open the Eureka dashboard**

```
http://localhost:8761
```

You should see 2 services registered: `API-GATEWAY` and `CONFIG-SERVER`.
(auth-server registers lazily — refresh after ~10s to see it join.)

**2. Hit the gateway**

```bash
curl -s http://localhost:8080/actuator/health | head -c 400
```

Should return JSON including `"status":"UP"` and components for
`redis`, `eureka`, `r2dbc` (gateway's own H2).

**3. Add a business service from your IDE or terminal**

```bash
# From the repo root
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH=$JAVA_HOME/bin:$PATH

cd services/user-service
mvn spring-boot:run
```

Wait ~30 seconds. Refresh http://localhost:8761 — `USER-SERVICE` now
appears. Then:

```bash
curl -u admin:admin123 http://localhost:8080/api/v1/users
```

End-to-end flow works: browser → gateway (JWT check) → Eureka lookup →
user-service → MySQL.

Repeat for product/order/payment services as needed. See
[`topologies.md`](topologies.md) "Mode: nano — Typical workflow."

### Nano-specific troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `Ports are not available: :3306` | Homebrew MySQL already running | `brew services stop mysql` |
| `api-gateway` keeps restarting | Redis didn't start or isn't healthy | `docker compose logs redis` then `docker compose restart redis api-gateway` |
| `auth-server` exits with "Public Key Retrieval is not allowed" | MySQL JDBC URL missing `allowPublicKeyRetrieval=true` | Should be fixed in current compose. Check `docker-compose.yml` line for `AUTH_DB_URL` |
| `MYSQL_ROOT_PASSWORD` error on `make up-nano` | `.env` missing or value blank | Step 3 of this doc |
| Everything "Up" but gateway returns 503 | Business service not running (expected — nano has none) | Run one via IDE or `mvn spring-boot:run` |
| Lots of DNS errors in gateway logs mentioning `zipkin` | Tracing accidentally enabled | Shouldn't happen with current compose (`TRACING_ENABLED=false` default). If it does: `docker compose up -d --force-recreate api-gateway` |

For non-nano-specific issues, see [`../debug/troubleshooting.md`](../debug/troubleshooting.md).
