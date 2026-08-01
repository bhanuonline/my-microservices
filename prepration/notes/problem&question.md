# URL Shortener — System Design Notes

## Problem Statement

Design a URL shortener (like bit.ly) that handles:
- **100M new URLs/day** (writes)
- **1B reads/day** (10x writes) — redirects/lookups

Key questions to answer:
1. How do you generate short codes?
2. What does the database schema look like?
3. How do you handle read-heavy traffic at scale?

---

## 1. Short Code Generation

### Approach A: Base62 encoding of a unique ID (Preferred)
- A distributed ID generator (e.g., Snowflake) produces a unique number, e.g. `125000000`.
- Convert to **Base62** (`a-z`, `A-Z`, `0-9` = 62 characters) → e.g. `8xKq2`.
- Base62 (not Base64) is used because it's URL-safe — no `+`, `/`, `=`.
- 7 Base62 characters → 62⁷ ≈ 3.5 trillion combinations — plenty of headroom.

### Approach B: Random generation + collision check
- Generate a random 7-character string, check DB for uniqueness, regenerate on collision.
- Simpler, but adds an extra DB read on every write.

### Why Approach A wins here
Given the write volume (100M/day ≈ 1,150 writes/sec average, higher at peak), Approach A avoids collision-retry overhead and scales without central coordination.

---

## 2. Database Choice & Schema

**Access pattern:** pure key-value lookup (`short_code → long_url`), no joins, no complex queries.

This makes a **NoSQL key-value store** (DynamoDB, Cassandra) a natural fit — scales horizontally well. A relational DB (MySQL/Postgres) also works if sharded manually.

**Core schema:**

| Column | Type | Notes |
|---|---|---|
| short_code | VARCHAR(10) | Primary key |
| long_url | TEXT | Destination URL |
| created_at | TIMESTAMP | Default current timestamp |

---

## 3. Snowflake ID Generation

**What it is:** Not a framework or language — an **algorithm/ID format** (created by Twitter, ~2010) for generating unique IDs across many servers with zero coordination.

**The problem it solves:** A simple auto-incrementing counter requires all servers to ask one central database for "the next number" — creating a bottleneck and single point of failure.

**How it works — 64-bit structure:**
```
[1 bit unused] [41 bits timestamp] [10 bits machine ID] [12 bits sequence]
```

- **Timestamp** (41 bits): milliseconds since a custom epoch.
- **Machine ID** (10 bits): unique per server (0–1023), assigned at startup.
- **Sequence** (12 bits): local counter, increments if multiple IDs are generated in the same millisecond.

**Example:**
```
Server A (machine_id = 1)  →  generateSnowflakeId()  →  IDs: ...001, ...002...
Server B (machine_id = 2)  →  generateSnowflakeId()  →  IDs: ...001, ...002...
Server C (machine_id = 3)  →  generateSnowflakeId()  →  IDs: ...001, ...002...
```

**Key benefit:** Each server generates IDs completely independently (in-process, sub-microsecond, no network call) — no two servers can collide because their machine_id namespaces never overlap.

---

## 4. Sharding

**Definition:** Splitting one large database into multiple smaller, independent databases (shards), each typically on its own physical/virtual machine.

**The problem it solves:** A single database server has finite CPU, memory, disk, and network capacity. As data and traffic grow, one machine eventually can't keep up.

### Before sharding (one DB)
```
                    ┌──────────────────┐
All 100M rows/day → │   ONE Database    │  ← everything lives here
                    │   (all URLs)      │
                    └──────────────────┘
```

### After sharding (4 shards)
```
25% of URLs →  ┌─────────────┐
               │  Shard 1    │
               └─────────────┘
25% of URLs →  ┌─────────────┐
               │  Shard 2    │
               └─────────────┘
25% of URLs →  ┌─────────────┐
               │  Shard 3    │
               └─────────────┘
25% of URLs →  ┌─────────────┐
               │  Shard 4    │
               └─────────────┘
```

### Sharding vs. Multiple Schemas (important distinction)

| Aspect | Multiple Schemas | Sharding |
|---|---|---|
| Machines | 1 | N (multiple) |
| Purpose | Organize/separate data logically | Scale out capacity (throughput, storage) |
| Same table structure repeated? | No — usually different tables/purposes | Yes — same schema, different rows distributed |
| Solves "too much traffic/data for one machine"? | No | Yes |

**Bottom line:** Sharding means different machines, each running a full, independent DB instance, each holding a slice of the rows.

### Shard routing: Consistent Hashing
- Naive `hash(key) % N` breaks badly when N changes (adding/removing a shard reshuffles almost everything).
- **Consistent hashing** places shards and keys on a ring; a key belongs to the first shard found clockwise from its position. Adding/removing a shard only affects a small arc of keys — not the whole dataset.
- **Virtual nodes** (multiple ring positions per physical shard) improve load balance across shards.

---

## 5. Local Environment Setup (Simulating Sharding on a Laptop)

### Design
```
Your Spring Boot App
        |
   -----------------------------------
   |        |         |          |
 3311     3312      3313       3314
   |        |         |          |
+--------+ +--------+ +--------+ +--------+
|Shard 1 | |Shard 2 | |Shard 3 | |Shard 4 |
| MySQL  | | MySQL  | | MySQL  | | MySQL  |
+--------+ +--------+ +--------+ +--------+
```

Each MySQL container = an isolated "machine" (own process, own port, own storage volume) — functionally equivalent to separate machines for testing routing logic, even though physically on one laptop.

### Step 1 — Spin up 4 MySQL shards via Docker

```bash
docker-compose up -d
docker ps
docker exec -it mysql-shard-1 mysql -uroot -proot urlshortener
```

### Step 2 — Create schema on each shard

```bash
docker exec -it mysql-shard-1 mysql -uroot -proot urlshortener -e \
"CREATE TABLE IF NOT EXISTS urls (short_code VARCHAR(10) PRIMARY KEY, long_url TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

docker exec -it mysql-shard-2 mysql -uroot -proot urlshortener -e \
"CREATE TABLE IF NOT EXISTS urls (short_code VARCHAR(10) PRIMARY KEY, long_url TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

docker exec -it mysql-shard-3 mysql -uroot -proot urlshortener -e \
"CREATE TABLE IF NOT EXISTS urls (short_code VARCHAR(10) PRIMARY KEY, long_url TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

docker exec -it mysql-shard-4 mysql -uroot -proot urlshortener -e \
"CREATE TABLE IF NOT EXISTS urls (short_code VARCHAR(10) PRIMARY KEY, long_url TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);"

# Verify
docker exec -it mysql-shard-1 mysql -uroot -proot urlshortener -e "DESCRIBE urls;"
```

### Step 3 — Java Spring Boot project structure

```
url-shortener/
├── pom.xml
├── docker-compose.yml
├── schema.sql
├── src/main/java/com/example/urlshortener/
│   ├── UrlShortenerApplication.java
│   ├── config/
│   │   └── ShardDataSourceConfig.java     ← configures DataSources (4 shards)
│   ├── sharding/
│   │   ├── ConsistentHashRing.java        ← routing logic
│   │   └── SnowflakeIdGenerator.java      ← ID generation
│   ├── controller/
│   │   └── UrlController.java             ← REST endpoints
│   ├── service/
│   │   └── UrlService.java                ← core create/lookup logic
│   └── util/
│       └── Base62Encoder.java             ← Snowflake ID → short code
```

### Manual testing

```bash
# Create a short URL
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://example.com/some/very/long/path"}'

# Resolve it back
curl -i http://localhost:8080/api/urls/ORZl76aemW
```

**Verify sharding is real** — check each shard directly:
```bash
docker exec -it mysql-shard-1 mysql -uroot -proot urlshortener -e "SELECT * FROM urls;"
docker exec -it mysql-shard-2 mysql -uroot -proot urlshortener -e "SELECT * FROM urls;"
docker exec -it mysql-shard-3 mysql -uroot -proot urlshortener -e "SELECT * FROM urls;"
docker exec -it mysql-shard-4 mysql -uroot -proot urlshortener -e "SELECT * FROM urls;"
```
Rows should be distributed across shards, not all landing on one.

---

## 6. Load Testing

### Tooling
```bash
brew install k6
```

### Standardized concepts (industry-wide)

**Units of measurement:**
- **RPS/QPS** (Requests/Queries Per Second) — universal throughput unit.
- **Percentiles** (p50, p95, p99) — standard way to report latency; averages can hide problems that percentiles reveal.

**Latency benchmark guide (rule of thumb, not law):**

| Response Time | User Perception |
|---|---|
| < 100ms | Feels instant |
| 100–300ms | Acceptable, slight delay |
| 300–1000ms | Noticeable delay, borderline |
| > 1000ms (1s+) | Feels slow / broken |

*Judge by p95/p99, not just average — average can hide a slow tail affecting real users.*

**Load test types:**

| Test Type | What It Does | Purpose |
|---|---|---|
| Load Test | Gradually increase to expected traffic | Confirm normal load is handled |
| Stress Test | Push beyond expected load until it breaks | Find the real breaking point |
| Spike Test | Sudden burst, then back to normal | Simulate flash sale / viral traffic |
| Soak Test | Sustained moderate load over hours | Catch memory leaks / slow degradation |

### Running tests

```bash
chmod +x run-load-test.sh
./run-load-test.sh
cat loadtest_history.log
tail -f loadtest_history.log      # live-follow new results

chmod +x run-read-test.sh
./run-read-test.sh
```

### Monitoring resource usage during a test
```bash
docker stats
```

### Findings from local stress testing

| Target Rate | Achieved | Avg Latency | Verdict |
|---|---|---|---|
| 200/sec | 200 | 6–8ms | Healthy, plenty of headroom |
| 400/sec | 400 | 8ms | Healthy, plenty of headroom |
| 800/sec | ~777 | 67ms (p95: 350ms) | Approaching saturation ("knee point") |
| 2000/sec (proper VU headroom) | ~833 | 3761ms (p95: 5666ms) | Severely saturated |

**Real measured ceiling on this local setup: ~750–850 requests/sec.**

### Root cause analysis — why did it degrade?

`docker stats` during a stressed run showed:
```
mysql-shard-4: CPU 53–62%
mysql-shard-2: CPU 45–60%
mysql-shard-1: CPU 42–61%
mysql-shard-3: CPU 42–63%
```
Combined DB CPU alone: ~190–245% — **before** even counting the Spring Boot app and k6 itself, all competing for the same limited CPU cores on one laptop. Memory usage stayed low (~5%) throughout — **CPU contention, not memory, was the bottleneck.**

**Key lesson:** Sharding solves a *data/throughput distribution* problem. The observed bottleneck was a *"too many processes sharing one machine's CPU"* problem — a laptop hardware limitation, not a flaw in the design.

```
Your laptop's CPU (fixed, limited amount)
│
├── k6 (load generator)      — needs CPU
├── Spring Boot app          — needs CPU
├── MySQL shard 1            — needs CPU
├── MySQL shard 2            — needs CPU
├── MySQL shard 3            — needs CPU
└── MySQL shard 4            — needs CPU

Adding shard 5, 6, 7, 8 just adds MORE
things competing for the SAME fixed CPU pool.
```

**Adding more shards would not fix this** — it would make it worse, since more shards means more processes competing for the same fixed CPU pool. More shards only help when each shard has its own **dedicated** hardware, as in real production deployments.

### Capacity planning math (using the measured ceiling)

```
Target peak:                  ~5,000–6,000 writes/sec
Measured single-instance cap: ~800/sec
Instances needed ≈ 5,500 / 800 ≈ 7 instances
```

This is **horizontal scaling (scale out)** — running ~7 identical app server instances behind a load balancer — not vertical scaling (scale up, making one machine more powerful). Scale out avoids the hard ceiling of any single machine and adds redundancy (one instance failing doesn't take down the whole system).

---

## 7. Caching Layer (Redis) — For the Read-Heavy Path

### Why it's needed
Every read was hitting MySQL directly — this cannot sustain the ~1B reads/day (~11,500/sec average, ~35,000–58,000/sec peak) target, even with sharding. Since `long_url` almost never changes after creation, this is a near-ideal caching use case: high read/write ratio, low staleness risk.

### Design — Cache-Aside Pattern

**Before (no cache):**
```
GET /api/urls/{code} → hash → route to shard → MySQL query → response
```

**After (with Redis):**
```
GET /api/urls/{code} → check Redis
                          ├── HIT  → return immediately (fast, no DB hit)
                          └── MISS → hash → route to shard → MySQL query
                                     → store result in Redis → return

POST /api/urls → generate code → insert into MySQL shard
                                → also store in Redis (write-through / pre-populate)
```

### Implementation summary
- **Redis** added as a new Docker container (`redis-cache`, port 6379).
- **Spring Data Redis** dependency added to `pom.xml`.
- **`UrlService`** updated:
   - `createShortUrl()`: writes to MySQL (source of truth) **and** writes to Redis immediately (write-through) — the very first read is fast.
   - `getLongUrl()`: checks Redis first; on miss, falls back to the correct shard via consistent hashing, then populates Redis for next time.
- **TTL**: 24 hours as a safety net, even though data rarely changes — self-heals any missed invalidation.

### Verifying it works
```bash
# Create
curl -X POST http://localhost:8080/api/urls -H "Content-Type: application/json" -d '{"longUrl": "https://example.com/testing-cache"}'

# Read back — should show CACHE HIT in app logs
curl http://localhost:8080/api/urls/{shortCode}

# Inspect Redis directly
docker exec -it redis-cache redis-cli GET "shorturl:{shortCode}"
```

### Next validation step
Re-run the read load test (`./run-read-test.sh`) and compare throughput/latency against the pre-cache baseline — this is the direct evidence that caching improves the read path's capacity toward the 1B/day target.

---

## Key Takeaways

1. **Snowflake IDs** enable unique ID generation across many servers with zero coordination.
2. **Consistent hashing** enables sharding that survives adding/removing shards without massive data movement.
3. **Load testing methodology matters more than specific numbers** — start at expected load, escalate to find the real ceiling, watch achieved-rate/latency/error-rate together.
4. **Always separate symptom from root cause** — k6 shows *what* degraded; `docker stats` (or equivalent) shows *why*.
5. **Local, single-machine testing has real limits** — shared CPU across app + load generator + multiple DBs produces bottlenecks that don't reflect real production behavior (separate dedicated machines per component).
6. **Sharding ≠ solves CPU contention** — it solves data/throughput distribution across dedicated hardware.
7. **Caching is essential for read-heavy systems** — especially when data is rarely updated after creation, as with short URLs.