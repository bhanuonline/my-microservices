# System Design — Classic Interview Problems

Section 14 of the study series. Continues from [06-reliability-security-ops.md](./06-reliability-security-ops.md).

Sections 1–13 gave you the vocabulary and building blocks. Section 14 is where you **use them**. Each problem below is walked end-to-end using the same 7-step framework so the pattern sticks.

---

## 14. Classic Interview Design Problems

### 14.1 The 7-step framework — say this out loud

Interviewers are watching your **process**, not just your final architecture. Follow this sequence every time.

```
   ① Requirements & clarifications        — what are we building?
   ② Back-of-envelope estimation          — how big is it?
   ③ API design                           — how do clients call it?
   ④ Data model                           — what do we store?
   ⑤ High-level design (HLD)              — the box diagram
   ⑥ Deep dives                           — the hard parts
   ⑦ Bottlenecks, tradeoffs, scale        — where does it break?
```

**Step-by-step guide**

**① Requirements & clarifications (5-10 min — DO NOT SKIP)**

```
   Ask about:
     Functional:      what MUST it do? (core features)
     Non-functional:  scale (users, QPS), latency SLO, availability, consistency
     Out of scope:    "let's not worry about payments for now"
   
   Rule: rephrase back to the interviewer. Confirm scope before designing.
```

**② Estimation**

```
   Convert requirements to numbers using Section 4.4 recipe.
     Users → DAU → QPS
     QPS × payload size → bandwidth
     Data per event × time → storage
     Peak = 2-4× average
   
   Write numbers on the board. You'll refer back to them.
```

**③ API design**

```
   Sketch 3-5 endpoints. RESTful nouns + verbs. Show request/response shape.
   
   POST /shorten
   Body: { "url": "https://long...", "custom_alias": "foo" }
   → 201 { "short": "https://sh.io/abc123", "expires_at": ... }
```

**④ Data model**

```
   What entities, what columns/fields, what indexes?
   
   Decide storage type here: SQL / KV / document / column / graph.
```

**⑤ High-level design (HLD)**

```
   The picture. Client → LB → services → cache → DB → messaging → downstream.
   
   Draw it. Talk through the flow of ONE request.
```

**⑥ Deep dives (the interview signal)**

Interviewer picks one thing to grill on. Have opinions on:
- Sharding strategy
- Caching strategy
- Consistency choices
- Failure handling
- Hot key / hot partition handling

**⑦ Bottlenecks, tradeoffs, alternatives**

```
   What breaks first at 10× scale?
   What's the CAP tradeoff?
   What did you not do, and why?
```

**Golden rules**
- **Don't dive in.** Ask questions first.
- **Draw as you talk.** The board is a memory aid for both of you.
- **State assumptions out loud.** "Assuming 100M users, 10% DAU..."
- **Compare 2 options before picking.** Shows judgment.
- **Know when to say "I'd need to measure that."**

**Interview one-liner**
> "The interviewer is testing whether you think like an engineer. Structure beats fireworks. Clarify → estimate → API → data → HLD → deep dive → tradeoffs, every time."

---

### 14.2 Design a URL shortener (bit.ly / tinyurl)

The classic warmup. Deceptively simple; interview signal comes from the deep dives.

#### ① Requirements

**Functional**
- Given a long URL, return a short URL.
- Redirect short URL → original URL.
- Optional: custom aliases (`sh.io/my-brand`).
- Optional: expiration.
- Optional: click analytics.

**Non-functional**
- **Read-heavy** — 100 redirects per shorten (rough industry ratio).
- **Low latency** — redirect < 100 ms globally.
- **High availability** — 99.99%.
- **Consistency** — a short code always maps to the same URL (immutable after create).

**Out of scope** (say this)
- User accounts, teams, dashboards.
- Advanced analytics beyond click count.

#### ② Back-of-envelope

```
   Assumption:  100M shortens / month
   
   Shortens/sec:   100M / (30 × 86400) ≈ 40/sec average
   Peak:           ~100/sec
   Redirects/sec:  40 × 100 = 4000/sec (read:write = 100:1)
   Peak redirect:  ~10,000/sec
   
   Storage per record:
     short_code (7 chars) + long_url (~500 B) + metadata (~100 B) ≈ 600 B
   
   Yearly writes:  100M × 12 = 1.2B records
   Yearly storage: 1.2B × 600 B ≈ 720 GB
   
   Bandwidth (redirect responses):
     10,000/sec × ~200 B = 2 MB/sec → tiny.
   
   Cache hit rate target:  95%+ (URLs are re-shared heavily)
```

Fits comfortably on a modest sharded DB + Redis. This is not a "big data" problem — it's a **hot-key latency** problem.

#### ③ API

```
   POST /api/v1/shorten
   Body:  { "url": "https://very-long...",
            "custom_alias": "sale" (optional),
            "ttl_days": 30 (optional) }
   →  201 { "short_url": "https://sh.io/abc123",
            "expires_at": "..." }
   
   GET /{short_code}
   →  301 Location: https://very-long...
   
   (Optionally: 302 if you want per-request analytics with less caching.)
```

**301 vs 302 — the deep detail**
- `301 Moved Permanently` → browsers cache; fewer requests hit us, less analytics fidelity.
- `302 Found` → no caching; every click hits us — better analytics, more load.

Interview default: **302**, because analytics usually matter for the product.

#### ④ Data model

Simple KV shape. Two solid choices:

```
   Option A — SQL (Postgres/MySQL):
   
     Table: urls
       short_code  VARCHAR(7)   PRIMARY KEY
       long_url    TEXT         NOT NULL
       created_at  TIMESTAMP
       expires_at  TIMESTAMP    NULL
       user_id     BIGINT       NULL
       click_count BIGINT       DEFAULT 0     ← hot updates! separate?
   
   Option B — KV (DynamoDB / Cassandra):
     
     PK: short_code
     Value: { long_url, created_at, expires_at, ... }
```

Recommendation: **KV** for the URL map (huge scale, tiny records, pure key lookup). SQL if you have small scale and want joins for reporting.

**Analytics table (separate)** — either an event log in Kafka, or a click_events table in a warehouse. Never `UPDATE click_count` on the hot path.

#### ⑤ High-level design

```
   ┌─────────────┐
   │ Client      │
   └──────┬──────┘
          │
          ▼
   ┌───────────────┐
   │ CDN           │  ← cache 301/302 responses close to users
   └──────┬────────┘
          │
          ▼
   ┌───────────────┐
   │ Load balancer │
   └──────┬────────┘
          │
    ┌─────┴─────────────┐
    ▼                   ▼
   ┌──────────┐   ┌──────────────┐
   │ Write svc │   │ Read (redirect)│
   │ /shorten  │   │  service       │
   └──────┬────┘   └────────┬───────┘
          │                 │
          │       ┌─────────┴──────────┐
          │       ▼                    │
          │  ┌────────────┐            │
          │  │ Redis      │ MISS       │
          │  │ (LRU)      │────────┐   │
          │  └────┬───────┘        │   │
          │       │ HIT            │   │
          │       ▼                ▼   ▼
          │   respond           ┌──────────────┐
          │                     │ URL DB (KV)  │
          │                     └──────────────┘
          ▼
   ┌──────────────┐                        │
   │ Write to DB  │────────────────────────┘
   │ + populate   │
   │ Redis        │
   └───────┬──────┘
           │
           ▼
   ┌──────────────┐
   │ Kafka: clicks│  ← async analytics pipeline
   └──────┬───────┘
          ▼
   Warehouse / BigQuery
```

**Read path** (dominates traffic):
1. Request → CDN → LB → Read svc.
2. Redis lookup by `short_code`.
3. HIT → 302 redirect (mostly this).
4. MISS → DB lookup → cache → redirect.

**Write path**:
1. Client POSTs a URL → Write svc.
2. Generate `short_code`.
3. Store in DB → populate cache.
4. Return short URL.

#### ⑥ Deep dives

**Deep dive A — How do we generate the short code?**

This is where interviewers linger. Four options:

```
   Option 1: Hash the URL (MD5 truncated)
   ─────────────────────────────────────
   short_code = md5(long_url)[0..6]
   
   ✅ Deterministic (same URL → same short) — deduplication.
   ❌ Collisions possible; must handle.
   ❌ Same URL from two users → same code (privacy leak on custom aliases).
```

```
   Option 2: Base62-encode an auto-increment ID
   ──────────────────────────────────────────────
   next_id = 12345678
   short_code = base62(12345678) = "3D7"   (0-9, a-z, A-Z = 62 chars)
   
   ✅ Guaranteed unique.
   ✅ Compact.
   ❌ Predictable/enumerable — attackers can walk your URL space.
   ❌ Requires a global counter (bottleneck) — solve with Snowflake IDs
      or per-shard ranges.
```

```
   Option 3: Random Base62 + collision retry
   ──────────────────────────────────────────
   short_code = random_base62(7)   → 62^7 ≈ 3.5 trillion codes
   
   Check DB: if exists, retry.
   
   ✅ Unpredictable (privacy).
   ✅ 7 chars gives 3.5T space — collisions rare until we've used ~60M codes.
   ❌ Requires a DB check per generation (or a Bloom filter to short-circuit).
```

```
   Option 4: Pre-generated ID pool (best for scale)
   ──────────────────────────────────────────────────
   A "counter service" hands out ranges to app instances:
   
   Zookeeper / Redis / Snowflake  ──►  Instance A: IDs 0..999
                                       Instance B: IDs 1000..1999
                                       Instance C: IDs 2000..2999
   
   Each instance base62-encodes locally, no coordination on hot path.
   
   ✅ Very fast, no bottleneck.
   ❌ Predictable (Option 2's flaw). Fix by shuffling within range or salting.
```

**Interview answer**: "Random Base62 with a DB uniqueness check, backed by a Bloom filter for the fast path. Move to pre-generated ranges (Option 4) when the DB check becomes a bottleneck."

**Deep dive B — Cache strategy**

```
   Cache:  Redis, keyed by short_code, TTL 24h.
   
   Write path:  DB write → SET Redis (write-through), TTL 24h.
   Read path:   GET Redis; MISS → DB → SET Redis.
   
   Hit rate estimate:  Zipfian access → 95%+ with modest cache.
   
   For hot URLs (viral tweet): local L1 (Caffeine) on each pod.
   
   Cache eviction: LRU (or W-TinyLFU with Caffeine).
```

**Deep dive C — Sharding**

At 1B+ records, one DB isn't enough.

```
   Shard by short_code (consistent hash).
   
   Read path is a single-key lookup → no scatter-gather.
   
   Rebalancing: use vnodes (Cassandra-style) or a directory
   (short_code prefix → shard) → minimal data movement.
```

**Deep dive D — Analytics without hurting the hot path**

```
   ❌ UPDATE urls SET click_count = click_count + 1 WHERE short_code = ?
      → row contention, kills throughput.
   
   ✅ Publish click events to Kafka on redirect.
      Batch consumer aggregates → click_stats table / warehouse.
   
   Extra: near-real-time counts via Redis INCR (approximate).
```

**Deep dive E — Custom aliases and expiration**

```
   Custom alias:  POST body includes desired code; check availability first.
                  Reserve in DB with UNIQUE constraint.
   
   Expiration:    Store expires_at. Background job (or lazy expiry on read):
                    if expires_at < now → return 410 Gone, delete row.
                  Or use Redis TTL for hot entries + DB backfill.
```

**Deep dive F — Global low latency**

```
   ▸ CDN + Anycast → users hit nearest edge.
   ▸ Multi-region deployment:
       Reads served from local region's Redis + DB replica.
       Writes go to primary region (writes are 100× fewer than reads,
       so cross-region latency on write is acceptable).
   ▸ Or eventual-consistency multi-master (last-write-wins on short_code).
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Counter service** (Option 4) if it's a single Redis instance. Move to Snowflake IDs or per-instance ranges.
- **DB write throughput** if not sharded properly. Shard by short_code hash.
- **Hot key** (viral tweet's short URL): mitigate with local L1 + CDN caching of 302s.

**Tradeoffs to state out loud**
- 301 caches better, worse analytics fidelity; 302 the opposite.
- Random codes = privacy; sequential = enumerable.
- Analytics as async events = fast reads, delayed counts.

**Interview one-liner**
> "URL shortener is a read-heavy KV problem with hot-key hazards. The signal is in short-code generation, caching, and how you handle analytics without slowing the hot path."

---

### 14.3 Design a distributed rate limiter

You covered algorithms in Section 8.2. Here's the interview-shaped design.

#### ① Requirements

**Functional**
- Given `(user_id, endpoint)`, decide whether to allow or reject.
- Support different limits per API-key tier (free / paid / internal).
- Emit `429 Too Many Requests` with `Retry-After` on reject.

**Non-functional**
- **Global** — must work across many app instances.
- **Low overhead** — < 5 ms per decision.
- **High availability** — rate limiter down should not break the whole app (fail-open policy).
- **Accuracy** — small over/under-count acceptable (< 1%).

#### ② Estimation

```
   Assumption:  100k QPS at peak across all endpoints.
   
   Each request = 1 rate-limit check.
     → 100k Redis ops/sec.
   
   Redis single node: ~100k ops/sec (typical). Fits, tight.
   → Use Redis Cluster to be safe, or per-instance sharding.
   
   Memory per user:  2 counters × 8 bytes ≈ 32 B with overhead.
   For 10M active users: 10M × 32 B = 320 MB.  Trivial.
```

#### ③ API

The rate limiter is called *from* other services, not directly by clients.

```
   Internal:
     limiter.allow(key: "user:42:GET:/orders", limit: 100, window_sec: 60)
        → { allowed: true, remaining: 42, reset_at: 1734567890 }
        → { allowed: false, retry_after_sec: 15 }
   
   Response header when blocking:
     HTTP/1.1 429 Too Many Requests
     Retry-After: 15
     X-RateLimit-Limit: 100
     X-RateLimit-Remaining: 0
     X-RateLimit-Reset: 1734567890
```

#### ④ Data model

Keep counter state in Redis, keyed compactly.

```
   Redis key:   rl:{user_id}:{route}:{window_id}
   Value:       integer counter
   TTL:         window_size + slack
   
   Example (sliding window counter):
     rl:42:orders:prev  = 87       (previous 60s window count)
     rl:42:orders:curr  = 34       (current window count)
```

Choice of algorithm from Section 8.2. For interviews, default to **sliding-window counter** — O(1) memory, no boundary spike, common in prod (Cloudflare).

#### ⑤ High-level design

```
   Client
     │
     ▼
   ┌──────────────────┐
   │ API Gateway      │  ← optional first-line coarse limits (per-IP)
   └────────┬─────────┘
            │
            ▼
   ┌──────────────────┐
   │ Service          │
   │                  │
   │ ┌──────────────┐ │
   │ │ Rate-limit   │ │  ← library call, first thing in the filter chain
   │ │ middleware   │ │
   │ └──────┬───────┘ │
   └────────┼─────────┘
            │
            ▼
   ┌──────────────────┐
   │ Redis (cluster)  │  ← counters live here
   │                  │
   │  Lua script:     │
   │   check + increment atomically
   └──────────────────┘
```

**Middle-tier vs edge**
- **Edge (gateway)**: catches abusers before hitting the app; coarse (per-IP).
- **App middleware**: per-user, per-endpoint precision; nearer to business logic.

Usually **both**. Use each for what it's good at.

#### ⑥ Deep dives

**Deep dive A — Which algorithm, and why?**

Recap from Section 8.2:

```
   Fixed window          ✓ simple  ✗ boundary spike (2× the limit)
   Sliding log           ✓ exact   ✗ O(N) memory per user
   Sliding counter       ✓ O(1)    ✓ good approximation      ← DEFAULT
   Token bucket          ✓ bursts  ✓ O(1)                   ← for user-friendly APIs
   Leaky bucket          ✓ smooth  ✗ higher queue latency
```

**Interview answer**: "Sliding-window counter for most APIs. Token bucket if we want bursts (mobile app opens 5 requests in a burst → let it through, then rate-limit)."

**Deep dive B — Atomic increment (avoiding race)**

```
   Naive:
     count = GET rl:42:orders
     if count > limit → reject
     else INCR rl:42:orders
   
   Race: two requests read the same count, both pass, count over-shoots.
   
   Fix: use Redis Lua script (atomic within Redis):
   
     local current = redis.call('INCR', KEYS[1])
     if current == 1 then
       redis.call('EXPIRE', KEYS[1], ARGV[1])
     end
     if current > tonumber(ARGV[2]) then
       return 0  -- reject
     end
     return 1    -- allow
   
   One round-trip, atomic, simple.
```

**Deep dive C — Distributed correctness**

If N app instances all call Redis, is the count correct?

```
   ✅ YES — Redis is the single source of truth.
   ❌ Redis is now a critical dependency.
   
   Failure modes:
     Redis slow → adds latency to every request.
     Redis down → your app is down (fail-closed) or unlimited (fail-open).
   
   Mitigations:
     ▸ Fail-open with a warning metric (accept traffic; alert ops).
     ▸ Local fallback bucket per instance while Redis is unreachable.
     ▸ Redis Cluster with replication; short client-side timeout.
```

**Deep dive D — Reducing Redis load (advanced)**

Two-tier counters:

```
   Per-instance local bucket  →  batched sync to Redis every 100 ms
   
   ✅ Redis load drops N× (N = instance count).
   ❌ Approximate — instances briefly over-count until sync.
   
   Good when: 100% precision isn't required, and Redis is a bottleneck.
```

Called **"eventually accurate" rate limiting** — used by many high-scale APIs.

**Deep dive E — Multi-tenant, hierarchical limits**

Real APIs limit at multiple levels:

```
   Per-endpoint:   /search: 10/s
   Per-user:       user_42: 100/min
   Per-org:        org_ACME: 10,000/min
   Global:         total_traffic: 1M/s (system-wide brake)
   
   Check ALL applicable levels. Deny if ANY exceeds.
   Return the tightest 429 to the client.
```

**Deep dive F — Client behavior when rate-limited**

```
   Response:
     429 Too Many Requests
     Retry-After: 15
   
   Well-behaved client:
     Backs off, respects Retry-After.
     Uses exponential backoff + jitter for retries.
   
   Badly-behaved client:
     Hammers immediately.
     
   Defense:  progressive penalties (longer bans on repeated abuse).
             Ban list at the WAF layer for extreme cases.
```

**Deep dive G — Testing**

```
   ▸ Unit: algorithm correctness at window boundaries.
   ▸ Load test: hammer with 2× the limit, check reject rate.
   ▸ Chaos test: kill Redis mid-flight, verify fail-open policy.
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Redis hot slot** — one popular user or endpoint on one Redis key.
  - Fix: local per-instance counters + sync (two-tier).
- **Network round-trips** — 1 Redis op per request adds ~0.5 ms.
  - Fix: pipeline / batch checks, sidecar Redis on each pod.

**Tradeoffs to state**
- Strict global (Redis) vs approximate (local + sync): precision vs Redis load.
- Fail-open (user-friendly, may be abused) vs fail-closed (safe, may block real users).
- Sliding-window counter accuracy vs token-bucket friendly bursts.

**Interview one-liner**
> "A rate limiter is a hot-path decision on shared state. Redis + Lua for atomicity, sliding-window counter for accuracy, and a fail-open policy so the limiter's outage doesn't take down the API."

---

### 14.4 What's coming next in Section 14

The framework and warm-ups are in. Remaining classic problems, on request:

```
   Communication  →  Chat / WhatsApp, Notification system
   Feeds          →  Twitter / News feed, Instagram
   Media / files  →  YouTube / Netflix, Dropbox / Drive, Google Docs
   Location       →  Uber / ride-sharing, Nearby places
   Search         →  Autocomplete / typeahead, Web crawler
   Infra          →  Distributed cache (Redis-like), Job scheduler / cron
   Data pipeline  →  Ad click aggregator / analytics
   Money & scale  →  Payment system / ledger, Stock exchange / order matching
   Booking        →  BookMyShow / ticketing (concurrency + inventory)
   Others         →  E-commerce checkout, Recommendation system
```

Each will follow the same 7-step framework so you build muscle memory.

---

### 14.5 Design a chat / messaging system (WhatsApp, Messenger, Slack)

The canonical "real-time, stateful, bidirectional" problem. Interviewers love it because it forces you to design connection state, delivery semantics, presence, and group fan-out — all in one system.

#### ① Requirements

**Functional**
- One-to-one messaging (text first; media later).
- Group chats (up to ~500 members).
- Presence: online / offline / last-seen.
- Delivery status: sent / delivered / read (double-checkmark).
- Message history persisted; new devices can sync backlog.
- Push notifications when the app is closed.
- Offline messages queued and delivered on reconnect.

**Non-functional**
- **Real-time** — P99 delivery < 500 ms for online users.
- **Massive scale** — 1B users, 100M concurrent connections, 50B messages/day.
- **Highly available** — chat is critical.
- **Ordered per conversation** — messages in a chat appear in order for all participants.
- **Durable** — no message lost.
- **Optional but common** — End-to-end encryption (E2E).

**Out of scope**
- Voice / video calls (whole other design).
- Search across chat history.

#### ② Back-of-envelope

```
   Users:                1B total, ~500M DAU, ~100M concurrent connections.
   Messages/day:         50B
   Messages/sec avg:     50B / 86,400 ≈ 580K/sec
   Peak (3x):            ~1.8M/sec
   
   Per-message size:     ~200 B (text + metadata) — media stored separately.
   
   Storage/day (text):   50B × 200 B ≈ 10 TB/day
   Storage/year (text):  ~3.6 PB/year (sharded across many DBs)
   
   Connections:          100M WebSockets. At ~10K conns per server → 10,000 chat servers.
                          RAM per conn ≈ 50 KB → 500 GB across the fleet just for sockets.
```

**Key insight:** it's not the message count that hurts; it's the **100M live TCP connections**. This drives the architecture.

#### ③ API

Two flavors: **REST for setup** (login, contacts, history) and **WebSocket for real-time messaging**.

```
   REST
   ─────
   POST  /login                     → JWT
   GET   /conversations             → list of chats
   GET   /conversations/{id}/messages?before=<msg_id>&limit=50   → history
   POST  /media/upload              → returns media_url (S3)
   
   WebSocket (persistent connection)
   ──────────────────────────────────
   Client → Server:
     {"type":"send", "conv_id":"c1", "client_msg_id":"uuid",
      "text":"hi", "sent_at": 1734...}
   
     {"type":"ack", "msg_id":"m123"}     ← read receipt
     {"type":"typing", "conv_id":"c1"}
   
   Server → Client:
     {"type":"message", "msg_id":"m123", "conv_id":"c1",
      "from":"u1", "text":"hi", "server_ts":...}
   
     {"type":"status", "msg_id":"m123", "state":"delivered"}
     {"type":"presence", "user_id":"u2", "state":"online"}
```

The `client_msg_id` is a **client-generated UUID** — makes sends idempotent under retry (Section 8.6, 10.4).

#### ④ Data model

Split into three shapes: **conversation membership**, **messages**, **user/connection state**.

```
   Conversations
   ──────────────
   conversation_id  |  type (1:1 / group)  |  created_at  |  last_msg_ts
   
   Conversation members
   ─────────────────────
   conversation_id  |  user_id  |  joined_at  |  role (admin/member)
   
   Messages   (Cassandra — write-heavy, time-ordered)
   ──────────
   Partition key:  conversation_id
   Cluster  key:   message_ts DESC, message_id
   Columns:        sender_id, body, media_url, edited, deleted, ...
   
   → All messages of a chat live together, sorted newest-first.
   → Fetching last 50 = one partition read.
   
   User inbox (unread pointer, per user, per conversation)
   ─────────────────────────────────────────────────────────
   user_id  |  conversation_id  |  last_read_msg_id  |  unread_count
   
   Connections (Redis)
   ────────────────────
   user_id → { chat_server_id, session_id, expires_at }
   
   → tells the routing layer WHERE user's live socket is.
```

**Why Cassandra for messages?** Append-heavy writes, time-ordered reads, easy sharding by conversation_id, 3-5 replicas for durability. Postgres struggles at 1M+ writes/sec.

#### ⑤ High-level design

```
   ┌──────────┐
   │ Mobile / │
   │ Browser  │──── WebSocket (persistent) ──────────────┐
   └────┬─────┘                                          │
        │ REST for login, history                       │
        ▼                                                ▼
   ┌────────────────────┐                    ┌──────────────────────────┐
   │  API / Auth        │                    │  Connection Gateway       │
   │  (stateless)       │                    │  (Chat Servers)           │
   │  - login → JWT     │                    │  - holds live WebSockets  │
   │  - history GET     │                    │  - 10K conns per box      │
   └─────────┬──────────┘                    └─────┬────────────────────┘
             │                                     │
             │ query                               │ publish / consume
             │                                     ▼
             │                            ┌────────────────────────┐
             │                            │  Message Broker (Kafka │
             │                            │  or Redis Pub/Sub /    │
             │                            │  NATS)                 │
             │                            └────┬───────────────────┘
             │                                 │
             ▼                                 ▼
   ┌──────────────────┐               ┌─────────────────────┐
   │  Messages DB     │               │  Presence Service   │
   │  (Cassandra)     │◄──write───────│  (Redis: user →     │
   │  sharded by      │               │  chat_server_id)    │
   │  conversation_id │               └─────────────────────┘
   └──────────────────┘                         │
             │                                  │
             ▼                                  ▼
   ┌──────────────────┐               ┌─────────────────────┐
   │  Media Store     │               │  Push Notification  │
   │  (S3 + CDN)      │               │  (APNs / FCM)       │
   └──────────────────┘               └─────────────────────┘
```

**Flow of a single message (Alice → Bob, both online)**

```
   1.  Alice's client → WS frame {"type":"send"} → Chat Server A
   2.  Chat Server A:
         ▸ Writes message to Cassandra (durable)
         ▸ Publishes event to Kafka topic "conv-<hash>"
         ▸ ACKs Alice ("server received, msg_id=m123")
   3.  Presence Service says Bob is on Chat Server B.
   4.  Chat Server B consumes from Kafka → pushes frame to Bob's socket.
   5.  Bob's client → "delivered" ACK → back through the pipe.
   6.  Alice sees single-check → double-check when Bob's app reads it.
   
   If Bob is OFFLINE:
     Step 4 → send push via APNs/FCM instead.
     Message sits in Cassandra; delivered when Bob reconnects.
```

#### ⑥ Deep dives

**Deep dive A — Managing 100M WebSocket connections**

```
   Problem: WebSockets are stateful. Which chat server holds Bob's socket?
   
   Design:
     Presence Service (Redis) maps user_id → chat_server_id.
     Every connect: chat server registers "user u2 is here."
     Every disconnect: unregister (or TTL-based expiry via heartbeat).
   
   Chat server scaling:
     Stateless routing via LB (L4 TCP LB, since it's WS).
     Consistent hashing by user_id → sticky-ish assignment.
     Health-checks drain dying nodes; graceful drain gives 30s to migrate sessions.
   
   Failure of one chat server:
     ~10K users disconnected.
     Client reconnects → routed to a new server → re-registers with Presence.
     Backlog delivered from Cassandra + Kafka.
```

**Deep dive B — Message routing to the right chat server**

Two options:

```
   Option 1: Direct RPC via Presence lookup
   ──────────────────────────────────────────
   Sender's chat server → looks up recipient in Presence → RPC to their chat server.
   
   ✅ Low latency.
   ❌ Sender server must know all other servers' addresses.
   ❌ Presence lookup on every message.
   
   Option 2: Kafka fan-out per conversation (recommended)
   ───────────────────────────────────────────────────────
   Every conversation has a Kafka topic partition (hash(conv_id) → partition).
   Every chat server holding a user in that conversation subscribes to that partition.
   
   ✅ Decoupled — no direct server-to-server RPC.
   ✅ Durable buffer for slow consumers.
   ❌ Higher latency (adds a broker hop).
   
   Real systems often blend: direct RPC for 1:1 and small groups,
   pub/sub for large groups and durability.
```

**Deep dive C — Group chat fan-out**

```
   Bob sends to a 500-person group.
   
   Naive:  Chat Server A pushes 500 individual messages.
           500 × Presence lookups + 500 × routing decisions. Slow.
   
   Better: A publishes ONCE to Kafka partition "conv-<group_id>".
           All chat servers holding members of that group consume in parallel.
           Each server delivers to its own subset locally.
   
   Very large groups (10K+):
     Same pattern but with more partitions.
     Consider "channel-like" model: only recently-active users get pushes;
     others fetch on demand.
```

**Deep dive D — Delivery guarantees & ordering**

```
   Requirement:  messages in a conversation appear in the same order for everyone.
   
   Achieved by:
     ▸ Server assigns monotonic message_ts + message_id.
     ▸ All events for one conv_id go through ONE Kafka partition (hash by conv_id)
       → preserves order across the fanout.
     ▸ Client sorts by (server_ts, message_id).
   
   Delivery semantics:
     Publish to Kafka = at-least-once.
     Consumers (chat servers) dedupe by message_id.
     Clients tolerate duplicates (idempotent by message_id).
```

**Deep dive E — Presence and last-seen**

```
   Naive: every user pushes "I'm alive" every second → 100M ops/sec. Nope.
   
   Design:
     Client sends a heartbeat every 30-60s over WebSocket.
     Chat server updates Redis:  presence:{user_id} = "online", TTL 90s.
     Missing heartbeat + TTL expiry → user marked offline.
     last_seen = time of last heartbeat, stored on offline transition.
   
   Broadcast presence changes:
     Only to users' contacts (privacy).
     Only on state transitions (online → offline), not per heartbeat.
     Sub via Kafka topic "presence-updates".
```

**Deep dive F — Offline delivery & multi-device sync**

```
   Message pipeline:
     Always persist to Cassandra FIRST (durability).
     Then attempt real-time delivery.
   
   When Bob reconnects:
     Client says "last seen msg_id = X".
     Server queries Cassandra for messages after X → pushes as backlog.
   
   Multi-device (phone + laptop):
     Each device is its own WebSocket session.
     Presence tracks per-device, not per-user.
     Delivery fan-out to all active devices.
     "Read" event syncs across devices (Slack pattern).
```

**Deep dive G — Push notifications (offline users)**

```
   Bob is offline. Push mobile notification instead.
   
   Chat Server:
     If Presence says user offline → queue payload to Push Service.
   
   Push Service:
     Batches to APNs (iOS) / FCM (Android) with retry.
     Persists undelivered pushes; retries with backoff.
   
   Deduplication:
     Notification ID = message_id → APNs coalesces / iOS shows once.
```

**Deep dive H — Media (images, videos, files)**

Never send binary through the WebSocket pipe (fills the buffer, kills throughput).

```
   Upload path:
     Client → GET presigned S3 URL from API → PUT media directly to S3.
     On success → send WS message with { media_url, mime, size, thumbnail_url }.
   
   Download path:
     Client fetches media_url through CDN (Cloudfront/CloudFlare).
     Text messages carry only the URL — the WS pipe stays lean.
```

**Deep dive I — End-to-end encryption (E2E)**

Standard is the **Signal Protocol** (used by WhatsApp, Signal, Messenger).

```
   Key insight: server sees only ciphertext. Server can't decrypt anything.
   
   Simplified flow:
     ① Every user has an identity keypair (long-term) + prekey bundle.
     ② Server acts as a "prekey directory" — holds each user's public prekeys.
     ③ Alice fetches Bob's prekey bundle from server.
     ④ Alice + Bob perform X3DH → derive a shared session secret.
     ⑤ Double Ratchet protocol → new key per message (forward secrecy).
     ⑥ Alice encrypts each message locally, sends ciphertext through server.
     ⑦ Bob decrypts.
   
   Server responsibilities:
     ▸ Store prekey bundles.
     ▸ Route encrypted payloads.
     ▸ Never see plaintext.
   
   Trade-offs of E2E:
     ✅ Strongest privacy.
     ❌ Server-side search / analytics impossible.
     ❌ Losing device = losing message history (need encrypted backup).
     ❌ Group messages harder (must encrypt separately for each member).
```

**Deep dive J — Read receipts (double-check pattern)**

```
   Sent (single check)     → server acknowledged.
   Delivered (double gray) → recipient's device received.
   Read (double blue)      → recipient opened the chat.
   
   Each transition = a small event, propagated through the same pipeline.
   
   Optional privacy: user can disable read receipts → suppress the "read" event.
```

**Deep dive K — Rate limiting & abuse**

```
   Rate limit per user per second (messages sent).
   Group spam detection (same message to many groups).
   Report / block flow.
   E2E complicates content moderation → rely on client-side heuristics + user reports.
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Connection layer** (100M → 1B sockets) — need more chat servers, better graceful drain, geo-distribution.
- **Kafka topic partitions per conversation** — millions of active conversations = millions of partitions? No. Hash conversations to a bounded set of partitions (e.g., 10K). Each partition holds many conversations.
- **Cassandra hot partitions** — a viral group chat with 500 people posting rapidly. Mitigate with sub-partitioning (`conv_id + time_bucket`).
- **Presence Redis** — 100M entries, high churn. Shard by user_id; keep TTL-based cleanup.

**Tradeoffs to state out loud**
- **Direct RPC vs pub/sub** for chat-server-to-chat-server delivery. Pub/sub wins for durability + fan-out; RPC wins for latency.
- **Push everything to all devices vs smart delivery** — always push text (cheap); fetch media on demand.
- **E2E vs server-side features** — pick one; search / smart replies / moderation get much harder with E2E.
- **Message ordering** — global order is expensive; per-conversation order is cheap and enough.

**Interview one-liner**
> "Chat is a connection-density and fan-out problem. Persist first (Cassandra), route via presence + pub/sub, deliver in-order per conversation using a partition-per-conversation hash. Everything else — presence, receipts, group fan-out — is layered on the same pipeline."

---

### 14.6 Design a news feed (Twitter timeline / Facebook feed / Instagram)

The classic "fan-out" problem. Interview signal comes from the **fan-out on write vs read** debate and the **celebrity problem**.

#### ① Requirements

**Functional**
- User posts a tweet/status/photo.
- Home feed = posts from people you follow, most recent (or ranked) first.
- Support "follow" / "unfollow".
- Support media attachments.
- Support likes / replies / retweets (optional first pass).

**Non-functional**
- **Read-heavy** — feed reads dominate (100:1 or more).
- **Low latency** — feed load < 500 ms.
- **Freshness** — new posts visible within seconds.
- **Availability** > **strong consistency** (see stale post = fine; missing feed = user churns).
- **Scale** — 500M DAU, 200M+ posts/day, average 200 followers.

**Out of scope**
- Direct messaging (chat is 14.5).
- Deep ranking / ML (mention it, don't design it).

#### ② Back-of-envelope

```
   Users:                  1B total, 500M DAU
   Posts/day:              200M
   Posts/sec avg:          200M / 86,400 ≈ 2,300/sec
   Peak (3×):              ~7,000/sec
   
   Feed reads:
     Each DAU refreshes ~5 times/day → 2.5B reads/day
     Avg:                 29K reads/sec
     Peak (3×):           ~90K reads/sec
   
   → Read:Write ratio ≈ 30:1  (higher for social — likes / views amplify it)
   
   Fan-out (write path):
     Avg follower count:  ~200
     Total fan-outs/sec:  7,000 posts × 200 followers ≈ 1.4M/sec of "write-to-feed" ops.
     A Justin Bieber post (100M followers) alone = 100M ops for ONE tweet.
   
   Storage per post:      ~500 B text + metadata (media stored separately in S3/CDN)
   Yearly text storage:   200M × 365 × 500 B ≈ 36 TB/year (shard this)
```

**Key insight:** the average is fine, the **tail (celebrities)** breaks naive designs. Everything else in this design follows from that.

#### ③ API

```
   REST
   ─────
   POST /posts                 body: { text, media_ids[] }        → 201 { post_id }
   GET  /feed?cursor=<opaque>&limit=20                            → { posts[], next_cursor }
   POST /follow/{user_id}                                          → 204
   DELETE /follow/{user_id}                                        → 204
   POST /posts/{id}/like                                           → 204
   GET  /users/{id}/posts?cursor=...                              → user's own posts
   
   Cursor-based pagination (never offset for infinite feeds).
```

#### ④ Data model

Split across three storage layers optimized for their access pattern.

```
   Users (SQL — Postgres)
   ──────────────────────
   user_id | username | email | followers_count | ...
   
   Follows (SQL or KV — Cassandra)
   ──────────────────────────────
   follower_id  →  followee_id       (for "who I follow")
   followee_id  →  follower_id       (for "who follows me" — heavy for celebs)
   
   Posts (Cassandra / DynamoDB — append-heavy)
   ─────────────────────────────────────────────
   Partition key:   user_id
   Cluster key:     post_ts DESC, post_id
   Columns:         text, media_ids, reply_to, like_count, ...
   
   Home Timeline (Redis — precomputed inbox per user)
   ───────────────────────────────────────────────────
   Key:   timeline:{user_id}
   Value: sorted set (score = post_ts, member = post_id)
   Trim to newest ~500-800 post_ids per user.
```

Two "timeline" concepts to keep straight:
- **User timeline** = a user's own posts (partition key = user_id in Posts table).
- **Home timeline** = the feed shown to a user (precomputed union in Redis).

#### ⑤ High-level design — the two architectures

**Architecture A — Fan-out on Write ("push")**

```
   Alice posts a tweet.
      │
      ▼
   ┌──────────────────┐
   │ Post service     │  writes to Posts (Cassandra)
   └────────┬─────────┘
            │
            │ publishes "PostCreated"
            ▼
   ┌──────────────────┐
   │ Kafka: posts     │
   └────────┬─────────┘
            │
            ▼
   ┌───────────────────────────┐
   │ Fan-out worker             │
   │  ▸ read Alice's followers  │
   │  ▸ for each follower F:    │
   │      ZADD timeline:F ...    │
   └───────────────────────────┘
   
   Read path: GET timeline:me from Redis → hydrate post_ids with Post service.
   → SUB-100ms feeds. Trivial reads.
```

**Architecture B — Fan-out on Read ("pull")**

```
   Alice posts.
      │
      ▼
   Post service → Posts (Cassandra). Done.
   
   Read path:
   Bob asks for feed.
      │
      ▼
   ┌──────────────────────────────┐
   │ Feed service:                │
   │  ▸ fetch Bob's followees      │
   │  ▸ for each followee, get     │
   │    latest N posts             │
   │  ▸ merge, sort, paginate      │
   └──────────────────────────────┘
   
   → Fast writes. Slow reads (scatter-gather across many partitions).
```

**Comparison**

| | Fan-out on Write | Fan-out on Read |
|---|---|---|
| Write cost | O(followers) — huge for celebs | O(1) |
| Read cost | O(1) — just read timeline | O(followees) — scatter-gather |
| Freshness | Sub-second | Live |
| Celebrity problem | Explodes writes | Hits the DB hard on every read |
| Storage cost | Duplicates post_id per follower | None |
| Read latency | Very low | Higher |
| Best for | Most users (avg 200 followees) | Read-heavy, low-follower workloads |

**The hybrid approach (what Twitter / Instagram actually do)**

```
   Normal users (< 10K followers):    Fan-out on Write.
   Celebrities (> 10K followers):     Fan-out on Read.
   
   When Bob reads his feed:
     ① Read precomputed timeline:Bob  (fan-out on write portion — friends' posts)
     ② Read latest posts from every celebrity Bob follows (fan-out on read portion)
     ③ Merge, sort by ranking, return top N.
   
   Result: writes bounded (no more 100M fan-outs per Bieber tweet).
           Reads bounded (merging a handful of celeb streams, not 200).
```

**Full HLD**

```
   Client
      │
      ▼
   ┌──────────────┐
   │ CDN + LB     │
   └──────┬───────┘
          │
     ┌────┴────────────┬──────────────┬────────────────┐
     ▼                 ▼              ▼                ▼
   ┌──────────┐  ┌─────────────┐  ┌───────────┐  ┌────────────┐
   │ Post svc │  │ Feed svc    │  │ Follow svc│  │ Like/reply │
   └────┬─────┘  └──────┬──────┘  └────┬──────┘  └──────┬─────┘
        │               │              │                │
        ▼               ▼              ▼                ▼
   ┌──────────┐  ┌─────────────────────────────────────────────┐
   │ Posts DB │  │     Redis (Timelines + hot post cache)      │
   │ Cassandra│  └─────────────────────────────────────────────┘
   └────┬─────┘                        │
        │                              │
        ▼                              │
   ┌──────────┐                        │
   │ Kafka:   │──► Fan-out workers  ───┘
   │ posts    │      (for non-celebs)
   └──────────┘
        │
        ▼
   ┌──────────┐
   │ Warehouse│  ← analytics (later)
   └──────────┘
   
   Media path (separate): Client → S3 → CDN.
```

#### ⑥ Deep dives

**Deep dive A — The celebrity problem, formally**

```
   Naive fan-out on write for Justin Bieber (100M followers):
     ▸ 100M ZADD operations per tweet.
     ▸ 100M rows in Redis (assuming size 500 per user timeline).
     ▸ Runaway write amplification: 1 post = 100M writes.
   
   Solutions:
     ① Hybrid model (above) — biggest lever.
     ② Async fan-out with priority: friends first, celebs' followers later.
     ③ Deprioritize inactive followers (skip if last-seen > 30 days).
     ④ "Zero fan-out": push nothing; recompute on demand for high-fanout accounts.
```

**Deep dive B — Timeline storage & trimming**

```
   Redis sorted set per user: timeline:{user_id}
     Members: post_ids (references, not full posts)
     Score:   post_ts
   
   Trim to newest 500-800 post_ids.  Why?
     ▸ Users almost never scroll deeper.
     ▸ On deep scroll: fall through to DB (fan-out on read for the tail).
   
   Memory math:
     500 posts × 20 B per entry × 500M users = ~5 TB.
     Sharded Redis cluster with per-shard slave replicas.
   
   Cold users: don't precompute. Materialize timeline lazily on first read.
```

**Deep dive C — Feed hydration & post cache**

```
   Step 1: timeline gives you post_ids.
   Step 2: fetch actual post content.
   
   Redis "hot post" cache:
     post:{post_id} → { text, author, ts, like_count, media_url, ... }
     TTL: hours (posts change little; likes update via async increment).
     Hit rate: 95%+ (feeds concentrate on recent posts).
   
   Miss → Cassandra by (author_id, post_id). Populate cache.
```

**Deep dive D — Ranking & scoring (Facebook / Instagram)**

```
   Simple: sort by post_ts DESC (Twitter's original chronological).
   
   Ranked feed: sort by score = f(recency, affinity, engagement, ML model output).
     ▸ Batch job scores candidate posts.
     ▸ Cache top-N per user.
     ▸ Real-time signals updated on user interactions.
   
   In an interview: mention ranking exists, say "we could add a model-serving
   layer between timeline and hydration"; don't design it in detail.
```

**Deep dive E — New post visibility & write path**

```
   ① Post service persists to Cassandra (durability).
   ② Publishes PostCreated to Kafka.
   ③ Fan-out workers process:
      ▸ Get Alice's follower list.
      ▸ Skip if Alice is a celebrity (hybrid rule).
      ▸ For each follower F: ZADD timeline:F.
   ④ Trim timelines to max 800 entries per follower.
   
   Idempotency: fan-out worker keyed by (post_id, follower_id) → dedupe on retry.
   
   Backpressure: worker pool + Kafka partitions bounded.
```

**Deep dive F — Follow / unfollow**

```
   Follow:
     Insert (follower_id, followee_id) in Follows.
     Optional backfill: pull followee's recent posts into follower's timeline
     (or just start seeing new posts).
   
   Unfollow:
     Delete row.
     Purge future push; don't rewrite historical timelines (they age out).
```

**Deep dive G — Likes and counters**

```
   ❌ UPDATE posts SET like_count = like_count + 1  → hot row contention.
   ✅ Async pipeline:
     ▸ Client → Like service → Redis INCR like_counter:{post_id}
     ▸ Periodic flush to Cassandra (write-behind, Section 7.3).
     ▸ On read: read from Redis (source of truth for near-real-time counts).
```

**Deep dive H — Media**

Same pattern as chat (14.5):
- Client uploads to S3 via presigned URL.
- Post stores only media URL.
- Feed serves via CDN — never through the app tier.

**Deep dive I — Read your writes**

```
   Requirement: user posts a tweet → sees it in their own timeline immediately.
   
   Fix: after POST /posts, insert post_id into timeline:me synchronously
        before returning to client. Fan-out to others happens async.
```

**Deep dive J — Consistency & failure modes**

```
   Weak consistency is OK:
     Missing a post from feed for 30s = fine (async fan-out delay).
     Duplicate posts = client-side dedupe by post_id.
     Stale like count = fine.
   
   Failure of fan-out worker:
     Kafka retains events → workers replay on restart.
     Idempotent (post_id, follower_id) dedupe key.
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Fan-out workers** for celebrities: apply hybrid pull for anyone with > 10K followers.
- **Redis memory** for timelines: shard aggressively; drop inactive users; trim to 500 entries.
- **Cassandra hot partitions** on celeb posts (their user_timeline partition is huge). Bucket by month: `(user_id, year_month)`.
- **Feed hydration N+1** — batch post lookups via MGET, not one at a time.

**Tradeoffs to state out loud**
- **Chronological vs ranked** — chronological is honest but engagement suffers; ranked drives growth but complicates the pipeline.
- **Push vs pull vs hybrid** — the interview centerpiece. Hybrid is the practical answer at scale.
- **Consistency vs availability** — feeds are AP; a missed post beats a broken feed.
- **Freshness vs cost** — sub-second push everywhere is expensive; batch fan-out with a few seconds' lag is usually fine.

**Interview one-liner**
> "A feed is a fan-out problem. Push (write-time) makes reads cheap but explodes on celebrities; pull (read-time) makes writes cheap but reads scatter. A hybrid — push for normals, pull for celebs, merge at read — is what Twitter, Instagram, and Facebook all converged on."

---

### 14.7 Design search autocomplete / typeahead (Google, YouTube, Amazon)

The "5 suggestions in under 100 ms per keystroke" problem. Interview signal comes from **trie design**, **prefix caching**, and how you handle **real-time query updates**.

#### ① Requirements

**Functional**
- User types a prefix; return top-N (usually 5-10) suggestions.
- Suggestions are **ranked** — most likely / popular first.
- Suggestions come from historical queries + a static dictionary.
- New queries feed back into the system (learn what's trending).
- Support multi-word prefixes ("new yor" → "new york times").
- Optional: personalization (user history + location).

**Non-functional**
- **Latency < 100 ms per keystroke** — anything above and the UX feels laggy.
- **High QPS** — every keystroke is a request. 10 keystrokes per query.
- **High availability** — the search box always works.
- **Freshness within minutes** — a trending event (news, meme) should surface fast.
- **Availability > freshness**.

**Out of scope**
- The actual search results (that's a different system).
- Spelling correction ("did you mean") — mention, don't design.

#### ② Back-of-envelope

```
   Users:                  500M DAU (Google-scale)
   Searches/day:           ~10B  → 10B / 86,400 ≈ 115K searches/sec
   
   Keystrokes / search:    ~10 (each triggers autocomplete)
   Autocomplete QPS:       10 × 115K = 1.15M/sec average
   Peak (3×):              ~3.5M/sec
   
   Storage:
     Distinct historical queries: ~100M (long-tail)
     Avg query length:  ~20 chars
     Per-node data:      char + freq counter (~30 B)
     Trie total:         100M × 30 B ≈ 3 GB per replica  → fits in RAM easily.
   
   Cache hit rate target:  99%+
```

**Key insight:** trie fits in RAM. The challenge isn't storage — it's **read QPS + ranking + freshness**.

#### ③ API

```
   GET /autocomplete?q={prefix}&limit=5&lang=en&country=US
   →  200 { "suggestions": [
              {"text":"new york times", "score":9821},
              {"text":"new york city",  "score":7712},
              ...] }
   
   Client-side:
     ▸ Debounce ~30-50 ms so we don't send a request per keystroke.
     ▸ Cancel in-flight requests when a newer keystroke fires.
     ▸ Cache last few prefixes locally.
```

Backend for reporting:
```
   POST /log/query   (fired when user submits a full search)
   body: { query, user_id, ts, country, ... }
   → used to update popularity weights.
```

#### ④ Data model — the trie

A **trie** (prefix tree) is the natural fit: each node is a character; each path is a prefix.

```
         (root)
        /   |   \
       n    c    a
       │    │    │
       e    a    p
       │    │    │
       w    r    p
      /│    │    │
     _  y   s    l
     │  │        │
     y  o        e
        r        ▲
        k         top-K = ["apple", "apples", "apple watch", ...]
       /│
      _ e...
      │
      t  ← at each node, keep top-K completions for this prefix
      │  precomputed with their scores.
      i
      m
      e
      s
```

**The key optimization:** at each node, store the **top-K precomputed suggestions** for that prefix, so a lookup is `O(prefix_length)`, not a subtree walk.

```
   Node structure (conceptual):
   
     { char, children[], is_word, top_k: [(word, score)] }
   
   Lookup:
     ① Walk from root, character by character.
     ② At the last char's node → return top_k directly.
     ③ Complexity: O(len(prefix)).
```

**Where the top-K comes from:** offline batch job scans query logs, aggregates counts, walks the trie once, computes each node's top-K by frequency. Rebuild every N minutes (see 14.7-D).

**Alternative data model — precomputed key-value cache**

```
   Redis:  autocomplete:{prefix} → [top-K suggestions]
   
   ✅ Cache hit = 1 Redis GET (< 1 ms).
   ✅ Simple sharding by prefix hash.
   ❌ Explodes on cardinality (every 3-15 char prefix stored).
   
   In practice: pair a compact in-RAM trie (per pod) with Redis as L2 cache.
```

#### ⑤ High-level design

```
   Client (browser / mobile)
       │
       │ debounced autocomplete requests
       ▼
   ┌──────────────────┐
   │   CDN            │  optional: cache popular prefixes at edge (short TTL)
   └────────┬─────────┘
            │
            ▼
   ┌──────────────────┐
   │  Load balancer   │
   └────────┬─────────┘
            │
     ┌──────┴──────┬──────────┐
     ▼             ▼          ▼
   ┌───────────────────────────────┐
   │  Autocomplete service (pods)  │
   │                               │
   │  In-memory trie (per pod)     │  ← loaded at startup from S3
   │  + Redis cache (top-K per     │
   │    prefix)                    │
   └────────┬──────────────────────┘
            │
            ▼
   ┌────────────────────────────────┐
   │  Trie build pipeline (offline) │
   └────────┬───────────────────────┘
            │
            ▼
   ┌──────────────┐    ┌───────────────────┐
   │  Kafka:      │    │  Query logs       │
   │  query events│───►│  (Cassandra / S3) │
   └──────────────┘    └─────────┬─────────┘
                                 │
                                 ▼
                       ┌────────────────────┐
                       │  Spark / Flink job │
                       │  aggregate freq +  │
                       │  build new trie    │
                       └─────────┬──────────┘
                                 │
                                 ▼
                       ┌────────────────────┐
                       │  S3: latest trie   │
                       │  snapshot          │
                       └────────────────────┘
```

**Serve path (hot)**
1. Client → Autocomplete pod.
2. Look in local Redis (hot prefix cache) → HIT → return.
3. MISS → in-memory trie lookup → return + cache.
4. P99 target < 100 ms end-to-end.

**Update path (offline)**
1. Every completed search → Kafka event.
2. Batch job (every N min) reads events, updates frequency counts, rebuilds top-K per node.
3. New trie snapshot written to S3.
4. Autocomplete pods **hot-reload** the trie on rolling schedule (canary-style, not all at once).

#### ⑥ Deep dives

**Deep dive A — Ranking**

```
   Simple:   score = frequency of this query.
   Better:   score = frequency × recency-decay + personalization + business boost.
   
   Weighted score:
     score(q) = f_recent(q) × 1.0
              + f_last_7d(q) × 0.5
              + f_last_30d(q) × 0.2
              + user_affinity(user, q) × w_personal
              + geo_boost(country, q) × w_geo
   
   Compute offline per prefix; store in trie node's top_k.
```

**Deep dive B — Top-K storage per node**

```
   Not every node needs its own top-K.
   
   Rule of thumb:
     Keep top-K only at nodes with reasonable depth (≥ 2 chars).
     Below that, so many suggestions exist that the whole world is a candidate;
     handle single-char prefixes at the load balancer with static top-K.
   
   K = 5-10 typical.
   
   Memory math:
     ~10M interior nodes × 10 suggestions × 40 B ≈ 4 GB per replica. Manageable.
```

**Deep dive C — Handling long prefixes efficiently**

```
   User types "new york tim" — 12 chars.
   Naive:  walk 12 nodes deep.
   
   Optimizations:
     ▸ Compress single-child chains (radix/PATRICIA tree).
     ▸ Ternary Search Tree (TST) for better cache locality than a fat trie.
     ▸ FST (finite state transducer, used by Lucene) for compact + fast.
   
   In practice: memory is cheap; go with a normal trie for interview clarity,
   mention FST/radix as production optimization.
```

**Deep dive D — Freshness — trending queries**

```
   Requirement: a viral meme from the last 5 min surfaces in autocomplete.
   
   Approach — hybrid batch + streaming:
   
     Batch layer (every 30 min):
        Full trie rebuild from all logs.
        Slow but comprehensive.
   
     Speed layer (every 30 s):
        Stream processor (Flink) maintains per-prefix count with
        sliding-window (last 5 min).
        Publishes "delta top-K" to Redis.
   
     Serve merge:
        query → merge batch top-K + speed top-K → return.
   
   This is a small **Lambda architecture** — batch for accuracy, stream for freshness.
```

**Deep dive E — Distributed trie sharding**

```
   Trie fits in RAM per pod → the naive answer: replicate the whole trie to every pod.
     ✅ Any pod handles any request.
     ✅ Simple. Fastest reads.
     ❌ Memory duplicated N times.
   
   If trie grows > single-pod RAM (multi-language, huge dictionaries):
     Shard by first-1-or-2 characters.
       "a*" queries → shard 1
       "b*" queries → shard 2
       ...
     LB routes by prefix.
   
     ✅ Distributed memory.
     ❌ Hot shards (English "s*" is far bigger than "z*"). Fix with consistent hashing.
```

**Deep dive F — Caching layers**

```
   L1 — CDN
     Cache "GET /autocomplete?q=new" for 60 s at edge.
     ✅ Instant response for the most popular prefixes.
     ❌ Personalization becomes tricky; use vary keys carefully.
   
   L2 — Redis (in-region)
     Key: autocomplete:{prefix}:{country}
     Value: JSON top-K
     TTL: 5-10 min
   
   L3 — In-process (Caffeine) per pod
     Micro-cache for top 100 prefixes.
     Hit rate ~50% (Zipfian distribution).
   
   L4 — In-memory trie
     Full answer.
   
   Cascade:  CDN → Redis → Caffeine → trie.  Most requests never reach the trie.
```

**Deep dive G — Autocomplete for multi-word queries**

```
   User types "new yor" — should match "new york".
   
   Approach:
     ▸ Tokenize by whitespace.
     ▸ For each token, walk trie; for final token, do prefix match.
     ▸ Combine using a phrase-level lookup:
         Prefix "new york" → precomputed top-K continuations (city, times, ...)
   
   Precompute at ranking time: n-gram counts, not just single-word.
```

**Deep dive H — Personalization**

```
   User has history + geo.
   
   Serve path:
     ① Fetch generic top-K for the prefix from Redis / trie.
     ② Fetch user's personal top-K for the prefix from a user-history KV.
     ③ Merge with weighted scores; return top-K.
   
   Personal top-K storage:
     KV keyed by (user_id, prefix) → recent queries by that user starting with prefix.
     Kept small; TTL if inactive.
   
   Latency budget: personalization adds ~10 ms; use async concurrent fetches.
```

**Deep dive I — Blocklist / safety**

```
   Some prefixes must never show suggestions (offensive, banned, legally sensitive).
   
   Serve-time filter:
     bloom filter of banned strings → skip.
     denylist regex on completions.
   
   Log-time filter:
     Never feed banned queries back into the trie build.
```

**Deep dive J — Client-side behavior**

```
   Debounce (30-50 ms) so keystrokes coalesce.
   Cancel prior requests when a newer one fires (avoid stale responses).
   Local LRU of last N prefixes (offline / weak-network resilience).
   Skip if prefix < 2 chars (too noisy).
```

**Deep dive K — Cold start / new services**

```
   No history to bootstrap? Options:
     ▸ Load a static dictionary + web crawl frequencies.
     ▸ Use a partnered corpus (Wikipedia titles, product catalog).
     ▸ Weight by clicks / conversions once traffic starts flowing.
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Read QPS across pods** — solve with more pods; horizontal is trivial since each pod is stateless with a read-only trie.
- **Trie rebuild time** grows with query cardinality — use incremental streaming aggregation for the last N hours; full rebuild nightly.
- **Redis memory** on top-K cache — prune long tail; only cache prefixes with QPS > threshold.
- **Personalization latency** — cap per-request personalization; degrade gracefully to generic on timeout.

**Tradeoffs to state out loud**
- **Freshness vs latency** — a fully live trie is slow to rebuild; batch + streaming hybrid gives near-real-time with acceptable staleness.
- **Personalization vs cache hit rate** — CDN caches generic responses; personalized responses can't be edge-cached. Blend at the origin.
- **Memory vs compute** — bigger K per node = faster lookup, more RAM. Adjust per prefix depth.
- **Global suggestions vs geo/local** — separate tries per region if suggestions diverge; unified with geo boost if similar.

**Interview one-liner**
> "Autocomplete is a read-optimized prefix lookup with periodically-refreshed precomputed top-K per node. In-memory trie replicated across pods, Redis + CDN for hot prefixes, batch + streaming pipeline for freshness. The trick is precomputing the ranking at each node so serve-time is O(prefix_length)."

---

### 14.8 Design Uber / ride-sharing (geospatial matching)

The canonical "match X to Y in a 2D grid, in real-time, at global scale" problem. Interview signal comes from **geospatial indexing** (geohash / S2 / H3), **driver location updates**, and the **matching algorithm**.

#### ① Requirements

**Functional**
- Rider requests a ride from location A to B.
- System finds nearest available driver.
- Driver accepts or rejects.
- Track driver location during trip; show rider real-time position.
- Trip completes, price is charged, rating given.
- Support ride types (UberX, Pool, Black).

**Non-functional**
- **Real-time matching** — < 5 sec from request to driver assignment.
- **Live location updates** — driver → rider position under 1 sec.
- **High availability** — availability > strict consistency (a stale location is fine).
- **Global scale** — 100M+ users, ~5M active drivers, 20M trips/day.
- **Regional independence** — Delhi's outage shouldn't affect SF.

**Out of scope**
- Payment gateway integration (mention it, don't design it).
- Routing / ETA math (assume Google Maps / OSRM API).
- Fraud detection.

#### ② Back-of-envelope

```
   Users:                  100M total, ~10M DAU
   Active drivers:         ~5M globally, ~2M online at peak
   Trips/day:              ~20M
   Trips/sec avg:          20M / 86,400 ≈ 230/sec
   Peak:                   ~1,000/sec
   
   Driver location updates:
     Every 4 sec per online driver
     2M drivers × (1/4 sec) = 500K writes/sec  ← the big number
     Peak:  ~1.5M writes/sec
   
   Rider location updates:
     Every 4 sec once on trip (~1M concurrent trips)
     ~250K writes/sec at peak
   
   Storage per location record:
     driver_id + lat/lng + ts + status ≈ 60 B
     500K writes/sec × 60 B = 30 MB/sec = ~2.6 TB/day of raw location data.
   
   Trip storage:
     20M trips/day × 2 KB avg = 40 GB/day, ~15 TB/year.
```

**Key insight:** the problem isn't trip volume — it's **500K location writes/sec** and **finding "nearest N drivers" under 100 ms**. This drives the geospatial index choice.

#### ③ API

```
   Rider side
   ──────────
   POST /rides/request
     body: { pickup:{lat,lng}, dropoff:{lat,lng}, ride_type:"UberX" }
     → 200 { ride_id, eta_sec, estimated_price, driver:{...} }
   
   GET  /rides/{id}                     → status, driver location, ETA
   POST /rides/{id}/cancel               → 204
   
   Driver side
   ──────────
   POST /drivers/status
     body: { status: "available" | "offline" }
   
   WebSocket / long-poll:
     Driver → server every 4s: {"loc": {lat,lng}, "heading": ..., "speed": ...}
     Server → driver: "trip_offer" push (with 15 sec accept/reject window)
   
   Rider tracking:
     WebSocket / SSE: driver location events pushed every 4 sec during trip.
```

#### ④ Data model

Split by access pattern.

```
   Drivers (SQL - Postgres for stable data)
   ────────────────────────────────────────
   driver_id | name | vehicle | rating | current_status | ...
   
   Driver location (Redis — the hot state)
   ────────────────────────────────────────
   Geospatial index:  GEOADD "drivers:online" lng lat driver_id
                      → Redis stores geohash internally
                      → GEORADIUS for spatial queries
   
   Also:  driver:{id}:loc → { lat, lng, ts, status, heading }
          (with TTL 30s so ghost drivers age out)
   
   Trips (Cassandra — write-heavy, time-partitioned)
   ──────────────────────────────────────────────────
   Partition key:  city_id + (date bucket)   
   Cluster key:    trip_id
   Columns:        rider_id, driver_id, start_ts, end_ts, path, price, ...
   
   Location history (Cassandra / time-series DB)
   ─────────────────────────────────────────────
   Partition key:  driver_id + date
   Cluster key:    ts
   Columns:        lat, lng, heading, speed
   Retention:      TTL 30-90 days (for support & disputes)
```

#### ⑤ High-level design — the geospatial index is the centerpiece

**The core challenge: "find nearest 10 drivers to (lat, lng)"**

Naive: scan 2M driver rows, compute distance. Never < 100 ms.

**Fix: partition space into cells → only scan cells near the query.**

Three schemes dominate:

```
   Geohash               S2 (Google)           H3 (Uber)
   ────────────          ────────────          ──────────────
   
   Divides Earth         Hilbert space-        Hexagons at 16 resolutions.
   into rectangles       filling curve, cells  Same-size neighbors.
   using recursive       sized ~10cm to ~100km.Fixed neighbor distances.
   quadrants.            Fast conversion       Great for ring queries.
                         lat/lng ↔ cell_id.    Uber's original invention.
   
   9q8yy... = SF area    Cell tokens are       Cell 8928308280fffff = SF.
   Simple string.        int64.
   
   ❌ Rectangles skew     ✅ Balanced across    ✅ Consistent neighbor
      near poles.           poles (Hilbert).      distances.
   ✅ Widely supported    ✅ Range queries       ✅ Ideal for spatial
      (PostGIS, Redis).     natively via ID       aggregation.
                            ranges.
```

**For interviews**: mention all three, pick one (say Geohash for simplicity, or H3 to name-drop Uber's actual choice).

**How the index is used**

```
   Driver A at (37.775, -122.418) → geohash prefix "9q8yy"
   Driver B at (37.780, -122.420) → geohash prefix "9q8yy"    (same cell)
   Driver C at (37.900, -122.500) → geohash prefix "9q8yv"    (nearby cell)
   
   Redis:
     GEOADD drivers:online -122.418 37.775 driver_A
     GEOADD drivers:online -122.420 37.780 driver_B
     GEOADD drivers:online -122.500 37.900 driver_C
   
   Query for rider at (37.776, -122.419), radius 2 km:
     GEORADIUS drivers:online -122.419 37.776 2 km ASC COUNT 10
     → returns driver_A, driver_B (both within 2 km, closest first).
```

Redis GEO commands are backed by a sorted set keyed by geohash — O(log N) inserts, O(log N + k) radius queries.

**Full HLD**

```
   Rider app                                           Driver app
       │                                                    │
       │ WebSocket / HTTP                     WebSocket     │
       ▼                                                    ▼
   ┌──────────────────────────────────────────────────────────┐
   │                    LB + API Gateway                       │
   └───────┬───────────────────┬────────────────────┬─────────┘
           │                   │                    │
           ▼                   ▼                    ▼
    ┌───────────┐       ┌───────────────┐    ┌──────────────┐
    │ Rider svc │       │ Trip svc      │    │ Driver svc   │
    └─────┬─────┘       └───────┬───────┘    └───────┬──────┘
          │                     │                    │
          │                     │                    ▼
          │                     │           ┌──────────────────┐
          │                     │           │ Location update   │
          │                     │           │ ingestion         │
          │                     │           │ (Kafka partitioned│
          │                     │           │  by driver_id)    │
          │                     │           └────────┬──────────┘
          │                     │                    │
          │                     │                    ▼
          │                     │           ┌──────────────────┐
          │                     │           │ Location writer  │
          │                     │           │ (updates Redis   │
          │                     │           │  geoindex)       │
          │                     │           └────────┬──────────┘
          │                     │                    │
          │                     ▼                    ▼
          │           ┌──────────────────┐   ┌──────────────────┐
          │           │ Matching svc     │   │ Redis geoindex   │
          │           │ (finds nearest,  │◄──│ (drivers:online) │
          │           │  sends offers)   │   └──────────────────┘
          │           └────────┬─────────┘
          │                    │
          ▼                    ▼
    ┌──────────────────────────────────────┐
    │  Trip DB (Cassandra)                 │
    │  Location history (Cassandra / TSDB) │
    │  Users, drivers metadata (Postgres)   │
    └──────────────────────────────────────┘
```

**A single trip flow — traced**

```
   1.  Rider POST /rides/request → Rider svc → creates a "ride_request" event.
   2.  Matching svc:
         ▸ GEORADIUS on Redis for 10 nearest available drivers.
         ▸ Rank by ETA, rating, driver preferences.
         ▸ Send trip offer to #1 (WebSocket push).
         ▸ 15-sec accept window; if declined/timeout, offer to #2.
   3.  Driver accepts → Trip svc creates trip row, changes driver status to "busy".
   4.  Rider is pushed the assigned driver info + real-time location.
   5.  During trip:
         ▸ Driver location updates flow through Kafka → location writer → Redis.
         ▸ Rider receives location pushes every 4 sec via WebSocket.
   6.  Trip end:
         ▸ Driver marks complete; server computes final price via distance/time.
         ▸ Payment charged; ratings recorded.
         ▸ Driver status flips to "available"; Redis geoindex refreshed.
```

#### ⑥ Deep dives

**Deep dive A — Handling 500K location writes/sec**

```
   Direct writes to Redis at 500K/sec is possible but hot.
   
   Design:
     Driver → Kafka topic "loc-updates" (partitioned by driver_id)
              → ~64 partitions
              → each consumed by a location writer
              → writes to sharded Redis (also by driver_id hash).
   
   Why Kafka in front?
     ▸ Absorbs bursts.
     ▸ Retries and durability.
     ▸ Decouples ingestion from index update.
     ▸ Enables reprocessing for the location history table.
   
   Batch writes:
     Location writer batches N updates per Redis pipeline call → 10× throughput gain.
```

**Deep dive B — Nearest-driver search efficiency**

```
   GEORADIUS on Redis:
     For a 2 km radius in a dense city, ~50-200 candidates.
     O(log N + k) is fine at this scale.
   
   Multi-cell approach if manual:
     Query: (lat, lng, radius) → identify covering cells.
     Union candidates from those cells.
     Compute Haversine distance for each; keep top N.
   
   Refinement: filter by ride type, capacity, driver preference (surge zone).
```

**Deep dive C — Sharding by city / region**

```
   ❌ One global Redis → 500K writes/sec is too much for one shard.
   ✅ Shard geoindex by city_id:
       drivers:online:sf, drivers:online:blr, drivers:online:nyc
       
       Each city has its own Redis cluster + matching svc pod set.
       Cross-city queries never happen (rides are local).
   
   Benefits:
     ▸ Horizontal scale — each city independent.
     ▸ Regional outage isolation.
     ▸ Latency drops — regional Redis close to users.
   
   Airport edge cases: SFO is between cities — set explicit boundary rules.
```

**Deep dive D — Driver-rider matching algorithm**

```
   Simple: nearest available driver.
   
   Real: multi-objective optimization:
     score = w1 × distance
           + w2 × ETA (traffic-adjusted)
           + w3 × driver_rating
           + w4 × driver_utilization (fairness)
           + w5 × surge_multiplier
           - w6 × predicted_cancel_probability
   
   For UberPool / carpool:
     ▸ Batch requests (accumulate 30-60 sec).
     ▸ Solve as vehicle routing problem (VRP).
     ▸ NP-hard; use heuristics + ML.
   
   Sequential offer vs broadcast:
     Sequential:  offer driver #1, wait 15 sec, then #2, ...  → simple, but slow if declines.
     Batched broadcast: offer to top 3-5 simultaneously; first-accept wins. Faster,
                        but wastes offers.
   
   Uber uses batched auction internally.
```

**Deep dive E — Real-time location streaming to rider**

```
   Driver's WebSocket → Kafka → topic partitioned by trip_id.
   Rider's WebSocket subscribes to trip_id's stream.
   
   Interpolation:
     Push every 4 sec.
     Client smooths visually with dead-reckoning (heading + speed).
     Battery-friendly.
   
   Bandwidth:
     4 KB / 4 sec = 1 KB/s per rider — trivial.
```

**Deep dive F — Surge pricing**

```
   Divide city into hexagons (H3 resolution 8-9 ≈ ~1 km hexagons).
   
   Per hex, every 60 s:
     surge = f(demand, supply, historical)
             where demand = requests in last 5 min
                   supply = available drivers in the hex
   
   Publish surge multipliers via Redis / config service.
   Matching svc applies surge to price at request time.
   Rider sees multiplier before confirming.
   
   Anti-manipulation: smooth changes over time; cap max multiplier.
```

**Deep dive G — ETA computation**

```
   Approach:
     ▸ Naive: Haversine distance / avg speed. Fast, inaccurate.
     ▸ Better: precomputed traffic graph — OSRM / Google Maps API.
     ▸ Best: ML model on historical + real-time traffic.
   
   Interview: call the routing/ETA a black box service; focus on the plumbing.
   Cache ETAs for (from_cell, to_cell) pairs; refresh every N minutes.
```

**Deep dive H — Availability & failures**

```
   Redis geoindex is the critical path. Failure modes:
     ▸ Redis single node fails: replica takes over (Redis Sentinel / Cluster).
     ▸ Whole Redis cluster fails in a city: matching degrades to slower Cassandra fallback
       (last-known driver locations).
     ▸ Kafka lag builds up: matching sees stale locations → riders wait longer.
       Alert on lag; scale consumers.
   
   Trip-in-progress resilience:
     ▸ Trip state persisted in Cassandra.
     ▸ WebSocket disconnects → app polls REST fallback.
     ▸ Both driver and rider apps buffer location locally; re-sync on reconnect.
```

**Deep dive I — Idempotency**

```
   Ride request retries:
     Client sends an idempotency_key.
     Rider svc dedupes: same key → return original ride_id.
     Avoids duplicate charges / duplicate driver dispatch.
   
   Location updates:
     Are naturally OK to duplicate — last-write-wins on driver's Redis entry.
```

**Deep dive J — Anti-fraud & GPS spoofing**

```
   Signals:
     ▸ Impossible speed / teleportation between updates.
     ▸ Location matches a known "spoof cluster" (previous frauds).
     ▸ Driver rating + trip history.
   
   Server-side check on every location; drivers flagged for review.
```

**Deep dive K — Trip lifecycle state machine**

```
   requested → matched → driver_en_route → arrived → started → completed
       │             │           │              │          │
       └── canceled ─┴─ canceled ┴── canceled ──┘          │
                                                     ← rated
   
   State machine implemented as a transactional update in Trip svc.
   Each transition emits an event to Kafka (audit, analytics, notifications).
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Location ingestion** — solve with more Kafka partitions + more Redis shards; consider batching updates every 4→2 sec vs writes.
- **Redis GEORADIUS on huge dense city** — pre-partition into smaller sub-city hex zones.
- **Matching latency** — parallelize scoring, cache driver metadata.
- **Regional dependencies** — actively design for city-level isolation.

**Tradeoffs to state out loud**
- **Update frequency** — every 2 sec vs 4 sec vs 10 sec: latency vs battery/bandwidth vs infra cost.
- **Sequential vs batched matching offers** — user-fair vs fast.
- **Geohash vs S2 vs H3** — geohash is simple/universal; S2 is elegant; H3 is best for hex aggregation.
- **Consistency** — driver may briefly appear "available" after they've been offered a trip; matching svc must dedupe race conditions (two requests, one driver).

**Interview one-liner**
> "Ride-sharing is a geospatial matching problem at 500K location writes/sec. The core primitives: a partitioned geohash / H3 index for nearest-driver lookup, Kafka to absorb write bursts, per-city sharding for isolation, and a matching service that scores candidates by distance, ETA, rating, and business rules."

---

*Say "next" for the next in order (YouTube / Netflix video streaming), or name any specific one to jump to.*
