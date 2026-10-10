# First-time setup

You just cloned this repo. This doc walks you through getting the
stack running on your Mac for the first time. ~5-min active work +
some download/build waiting.

Follow top-to-bottom. Don't skip steps.

---

## 1. Prereqs — install these once

Open Terminal and check each:

```bash
docker --version        # need Docker Desktop 4.0+ (Compose v2 built in)
java --version          # need Java 17.x
mvn --version           # need Maven 3.8+
git --version           # ships with macOS, should Just Work
```

If any are missing:

| Tool | Install with |
|---|---|
| Docker Desktop | https://www.docker.com/products/docker-desktop/ — install, open, let it start |
| Java 17 | `brew install openjdk@17` (then follow the brew's post-install instructions to symlink) |
| Maven | `brew install maven` |
| Git | should be preinstalled. If not: `xcode-select --install` |

### Docker Desktop — set memory to at least 8 GB

Open Docker Desktop → Settings → Resources:

```
Memory:  8 GB    (minimum — 10-12 GB is better if you have the RAM)
CPUs:    5       (minimum)
Disk:    60 GB
```

Click **Apply & Restart**. If you give Docker less than 8 GB, the shared-db
mode will OOM-kill containers.

---

## 2. Clone and enter the repo

```bash
git clone git@github.com:bhanuonline/my-microservices.git
cd my-microservices
```

(If you're reading this, you've probably already done that.)

---

## 3. Set up your `.env` file

The project uses a `.env` file for secrets (passwords, tokens). It's
gitignored — you create it from a template:

```bash
cp .env.example .env
```

Open `.env` in any editor and change at minimum these two:

```bash
MYSQL_ROOT_PASSWORD=changeme       # pick any string; this is your dev MySQL root pw
GF_ADMIN_PASSWORD=admin            # Grafana login password
```

Everything else in `.env` has sensible defaults for a dev machine. Save
and close.

---

## 4. Pick a mode and start the stack

There are two ways to run everything. Pick ONE for your first run:

### Option A — pure Docker (recommended for first run)

Everything runs inside Docker containers. No Java/Maven on your host.

```bash
make up-minimal     # 11 containers, ~2.5 GB RAM
                    # First time: downloads ~2 GB of images + builds. ~5 min.
                    # Later runs: ~30s.
```

Not sure which mode? Pick `make up-minimal` first. You can always
switch later. See [`topologies.md`](topologies.md) for all modes.

### Option B — hybrid (infra in Docker, apps on your Mac)

Faster for actively editing Java (IDE breakpoints, fast rebuild). Needs
JDK + Maven on your host. First-time run auto-detects and does
everything:

```bash
./start-stack.sh    # auto-detects first run → full bootstrap
                    # (builds all jars, pulls images, starts everything)
                    # Takes ~5 min first time.
```

See [`docker-howto.md`](docker-howto.md) section 0 for when to pick which.

---

## 5. Verify it's working

Wait ~60 seconds after step 4 finishes (Spring Boot services need time
to register). Then:

```bash
docker compose ps                    # should show containers "Up (healthy)"
```

Open these in your browser:

| URL | What you should see |
|---|---|
| http://localhost:8761 | Eureka — table of registered services |
| http://localhost:9010/actuator/health | Gateway health JSON: `{"status":"UP"}` |
| http://localhost:3000 | Grafana login (admin / password from your .env) |
| http://localhost:9090 | Prometheus UI |

If any of these fail, see [troubleshooting](#troubleshooting-first-run-failures) below.

---

## 6. Run a sanity test — fetch a product

```bash
curl -u admin:admin123 http://localhost:9010/api/v1/products
```

Should return a JSON array of products (empty `[]` is fine on first run —
means the API works, no products inserted yet).

If you get `Connection refused`: wait 30 more seconds for services to
finish starting, then retry.

---

## 7. Stop everything when done

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
Check [`troubleshooting.md`](troubleshooting.md) and
[`setup-error.md`](setup-error.md) for known errors.

---

## What's next

Now that it's running:

- [`00-architecture-overview.md`](00-architecture-overview.md) — 5-min overview of the system
- [`docker-howto.md`](docker-howto.md) — day-to-day Docker commands for this repo
- [`topologies.md`](topologies.md) — all the modes you can run (`make up-saga`, `--trace`, etc.)
- [`02-verification-checklist.md`](02-verification-checklist.md) — full "is it healthy?" curl checklist
- [`debugging-flows.md`](debugging-flows.md) — Postman collections for walking a saga step-by-step
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
