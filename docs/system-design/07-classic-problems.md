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

### 14.12 Design a notification system (email / SMS / push at scale)

The "one internal event triggers a message to a user via 3+ channels" problem. Interview signal comes from **provider abstraction**, **fan-out to millions**, **deduplication**, and **user preferences**.

#### ① Requirements

**Functional**
- Send notifications via multiple channels: **email**, **SMS**, **mobile push** (iOS/Android), **in-app** (WebSocket), **web push**.
- Triggered by internal events ("order placed", "friend liked your post", "OTP code").
- **Templates** — same event, localized/personalized message.
- **User preferences** — per-channel opt-in/out, quiet hours, per-category subscriptions.
- **Batched campaigns** — send to a segment of users (e.g., 10M in a marketing blast).
- **Transactional vs marketing** — different priority lanes.

**Non-functional**
- **High throughput** — 100K notifications/sec sustained, spike to millions during campaigns.
- **Low latency for transactional** — OTP delivered in < 5 sec P99.
- **Reliability** — never lose a notification silently (retry, DLQ).
- **Idempotency** — the same event never notifies twice.
- **Compliance** — GDPR, CAN-SPAM, TCPA (respect opt-outs, unsubscribe links).

**Out of scope**
- Content composition / A/B testing pipeline.
- Deep analytics / attribution (mention, don't design).

#### ② Back-of-envelope

```
   Users:              100M
   Notifications/day:  ~1B (avg 10 per active user across channels)
   Notifications/sec:  1B / 86,400 ≈ 12K/sec average
   Peak (campaigns):   1M/sec for short bursts
   
   Payload sizes:
     Push:   ~1 KB  (title + body + metadata)
     SMS:    ~200 B
     Email:  ~50 KB (HTML + inline)
   
   Bandwidth (avg): 12K/sec × 5 KB avg = 60 MB/sec  → tiny.
   Storage:  metadata + logs, ~500 B per notification × 1B/day = 500 GB/day.
             Retention 30-90 days → ~15-45 TB.
   
   Concurrent provider connections:  hundreds (SES, SendGrid, Twilio, APNs, FCM).
```

**Key insight:** notifications are **fan-out + provider-integration** problem. Providers are the bottleneck (rate limits, quotas, downtime), not compute.

#### ③ API

```
   Internal (used by other services)
   ─────────────────────────────────
   POST /notify
     body: {
       user_id: "u42",
       template_id: "order_shipped",
       channels: ["email","push"],       (optional; else per user prefs)
       data: { order_id: "o99", eta: "..." },
       priority: "transactional" | "marketing",
       idempotency_key: "order_o99_shipped_u42"
     }
     → 202 Accepted { notification_id }
   
   POST /notify/batch                       (for campaigns)
     body: { segment_id or user_ids[], template_id, data, ... }
     → 202 { batch_id }
   
   User-facing preferences
   ──────────────────────
   GET  /users/{id}/notification-prefs
   PUT  /users/{id}/notification-prefs
        body: { channels: {email:true, sms:false, push:true},
                categories: {marketing:false, security:true, ...},
                quiet_hours: {start:"22:00", end:"07:00", tz:"..."} }
```

**Design choice:** `POST /notify` is **fire-and-forget** (returns 202). Real delivery is async through a pipeline. Callers don't wait for provider ACKs.

#### ④ Data model

Split by access pattern.

```
   Notification event (Cassandra — write-heavy, time-partitioned)
   ────────────────────────────────────────────────────────────────
   Partition key:  user_id + date
   Cluster key:    ts DESC, notification_id
   Columns:        template_id, channels, data, status_per_channel, ...
   
   Templates (Postgres / config store — read-mostly)
   ──────────────────────────────────────────────────
   template_id | version | subject_template | body_template | channel | locale
   
   User preferences (Postgres — small, read-hot; cached in Redis)
   ─────────────────────────────────────────────────────────────
   user_id | channels | categories_opt_in | quiet_hours | timezone
   
   Delivery attempts / status (Cassandra)
   ────────────────────────────────────────
   Partition key:  notification_id
   Cluster key:    attempt_ts
   Columns:        channel, provider, status, error, next_retry_at
   
   Dedup store (Redis)
   ────────────────────
   Key:   dedup:{idempotency_key}
   Value: notification_id
   TTL:   24 h
   
   Device tokens (Postgres / KV)
   ──────────────────────────────
   user_id | device_id | platform (ios/android/web) | token | last_seen
   → one user can have many devices (phone + tablet + web).
```

#### ⑤ High-level design

```
   Producer services (Order, Auth, Social, ...)
                    │
                    │  POST /notify (idempotency_key)
                    ▼
   ┌────────────────────────────────────────────┐
   │  Notification API                          │
   │  ▸ dedupe (Redis)                          │
   │  ▸ fetch user prefs, timezone              │
   │  ▸ compute channels (intersect asked+prefs)│
   │  ▸ persist notification row (Cassandra)    │
   │  ▸ publish to Kafka                        │
   └───────────────────┬────────────────────────┘
                       │
                       ▼
          ┌───────────────────────────────┐
          │  Kafka: notifications         │
          │  topics:                      │
          │    transactional-high-pri     │
          │    marketing-low-pri          │
          │  partitioned by user_id       │
          └───────────────────┬───────────┘
                              │
       ┌──────────────────────┼──────────────────────┬───────────────────┐
       ▼                      ▼                      ▼                   ▼
   ┌─────────┐          ┌─────────┐            ┌─────────┐         ┌──────────┐
   │ Email   │          │ SMS     │            │ Push    │         │ In-app / │
   │ worker  │          │ worker  │            │ worker  │         │ Web push │
   └────┬────┘          └────┬────┘            └────┬────┘         └────┬─────┘
        │                    │                      │                   │
        ▼                    ▼                      ▼                   ▼
   ┌─────────┐          ┌─────────┐            ┌─────────┐         ┌──────────┐
   │Provider │          │Provider │            │Provider │         │WebSocket │
   │adapter  │          │adapter  │            │adapter  │         │gateway   │
   │(SES/    │          │(Twilio/ │            │(APNs/   │         │(chat-svc)│
   │SendGrid)│          │Nexmo)   │            │FCM)     │         └──────────┘
   └────┬────┘          └────┬────┘            └────┬────┘
        │                    │                      │
        └──────────┬─────────┴──────────┬───────────┘
                   │                    │
                   ▼                    ▼
        ┌─────────────────────┐   ┌───────────────────┐
        │  Delivery status DB │   │  Analytics stream │
        │  (Cassandra)         │   │  (Kafka → warehouse)│
        └─────────────────────┘   └───────────────────┘
                   │
                   ▼
        ┌──────────────────────────────────┐
        │  Provider callbacks (bounces,    │
        │  delivery reports, click, open)  │
        │  → update status                 │
        └──────────────────────────────────┘
```

**Single-notification flow (traced)**

```
   1.  Order svc: POST /notify { user_id, template_id, idempotency_key }
   2.  Notification API:
         ▸ Redis: SET dedup:{key} IF NOT EXISTS
              → already exists? return existing notification_id (idempotent).
         ▸ Fetch prefs (Redis-cached), template, user's tz.
         ▸ Determine channels: intersect requested with user opt-ins.
         ▸ Respect quiet hours: schedule for later, or drop by policy.
         ▸ Persist notification row (Cassandra).
         ▸ Publish to Kafka topic ("transactional-high-pri" or "marketing-low-pri").
         ▸ Return 202 with notification_id.
   3.  Push worker (consumer):
         ▸ Reads event.
         ▸ Renders push payload from template + data.
         ▸ Looks up device tokens for user.
         ▸ Calls APNs / FCM via provider adapter.
         ▸ On success: mark delivered.
         ▸ On retryable error: exponential backoff + DLQ.
   4.  APNs / FCM later sends delivery reports → webhook → update status.
```

#### ⑥ Deep dives

**Deep dive A — Provider abstraction**

```
   Each channel has 1+ providers. Providers fail, rate-limit, get expensive.
   
   Adapter pattern:
   
     interface EmailProvider {
       SendResult send(EmailPayload);
     }
   
     implementations: SESAdapter, SendGridAdapter, MailgunAdapter
   
   Routing rules (in a config DB):
     ▸ Primary: SendGrid.
     ▸ On 5xx or rate limit: failover to SES.
     ▸ On volume caps: split 80/20 across providers.
     ▸ Regional providers for compliance (China → local SMS).
   
   Health monitoring:
     Provider circuit breakers based on error rate (Section 8.7).
     Auto-shift traffic when a provider degrades.
```

**Deep dive B — Priority queues (transactional vs marketing)**

```
   Transactional (OTP, order, security):
     ▸ Highest priority. Never queued behind marketing.
     ▸ Separate Kafka topics, separate worker pools.
     ▸ Tight SLO: P99 < 5 sec.
   
   Marketing (newsletters, promos):
     ▸ Lower priority; can wait minutes.
     ▸ Rate-limited to protect providers.
     ▸ Respect quiet hours strictly.
   
   Why separate topics?
     A 10M-user marketing blast never delays a critical 2FA code.
     Backpressure on marketing lane doesn't affect transactional.
```

**Deep dive C — Fan-out for campaigns**

```
   POST /notify/batch { segment_id, template_id }
   
   Fan-out worker:
     ▸ Load segment membership (10M user_ids) — from a precomputed list or query.
     ▸ Chunk into batches of ~1000.
     ▸ Publish per-user events to Kafka in the marketing topic.
     ▸ Workers pick up and deliver.
   
   Rate control:
     ▸ Provider quota-aware: don't publish faster than providers can absorb.
     ▸ Global rate limiter shared across workers (Section 14.3).
   
   Progress tracking:
     ▸ Batch status row in Cassandra: total, queued, sent, failed.
     ▸ Marketing dashboard reads this.
```

**Deep dive D — Deduplication & idempotency**

```
   Every /notify call carries idempotency_key.
   
   API layer:
     Redis SETNX dedup:{key} → true means first arrival.
     True → proceed, store notification_id under the key.
     False → return the stored notification_id (dedup).
   
   TTL 24-72 h (long enough that retries don't slip past).
   
   Worker-side idempotency:
     Provider adapters use notification_id as their idempotency handle to APIs
     (SES has Message-ID, Twilio has MessagingServiceSid, etc.).
     Retries don't send duplicates.
```

**Deep dive E — Retries & DLQ**

```
   Retry policy per channel:
     Push:   3 attempts, exponential 1s → 5s → 30s.
     Email:  5 attempts, up to 2 hours.
     SMS:    3 attempts within 5 minutes (regulatory + cost).
   
   Errors:
     Retryable:    5xx, rate-limit 429, timeout, network.
     Non-retryable: invalid token, unsubscribed, bad phone number.
   
   Non-retryable → immediate DLQ with reason.
   Retryable exhausted → DLQ with attempt log.
   
   DLQ processing:
     ▸ Alert threshold (spike in one provider).
     ▸ Some entries requeued after config fix.
     ▸ Others archived for audit.
```

**Deep dive F — Device tokens & invalidations**

```
   User's push token can go stale:
     App reinstall → new token.
     Uninstalled → old token → APNs returns "invalid device".
   
   On invalid-token response:
     ▸ Mark token inactive in Postgres.
     ▸ If user has other devices, keep those active.
     ▸ Trigger cleanup job to purge zombie tokens.
   
   Token rotation on app open:
     App sends current token to server on every launch → keeps mapping fresh.
```

**Deep dive G — User preferences & compliance**

```
   Preferences resolved at API layer, before Kafka publish:
     ▸ Channel opt-out → drop that channel.
     ▸ Category opt-out → drop notification.
     ▸ Quiet hours → schedule for morning (or drop for marketing).
   
   Compliance:
     ▸ CAN-SPAM: unsubscribe link in every marketing email.
     ▸ GDPR: user can DELETE preferences → forget them.
     ▸ TCPA (SMS in US): opt-in required, STOP keyword handling.
     ▸ Do-Not-Disturb sync from user's OS (best effort via APNs/FCM).
   
   Audit log every send: template, channel, ts, delivery status.
```

**Deep dive H — Templating & localization**

```
   Templates stored versioned:
     template_id + version → subject + body (Handlebars/Mustache/Jinja).
     data payload → rendered per-user.
   
   Localization:
     Fetch user.locale → pick template variant.
     Fallback chain: fr-CA → fr → en.
   
   Preview / testing:
     Render endpoint for QA; A/B test IDs supported (mention, don't design).
```

**Deep dive I — Real-time in-app notifications**

```
   For users currently in the app (WebSocket open):
     ▸ In-app worker publishes to WS gateway (like chat 14.5).
     ▸ Immediate visual indicator (badge, banner).
   
   Multi-device:
     User online on phone AND web → push to both channels.
     Read on one device → sync "read" status to others via WS event.
```

**Deep dive J — Observability**

```
   Metrics per (channel, template, provider):
     ▸ published_rate, sent_rate, delivered_rate, bounce_rate
     ▸ P50/P99 latency from event → send
     ▸ retry count histograms
     ▸ DLQ size
   
   Alerts:
     ▸ Delivery rate drops > 5% → page.
     ▸ Provider error rate > threshold → auto-failover + alert.
     ▸ DLQ growing → investigate poison messages.
   
   Traces:
     Every notification carries a trace_id from the source event → provider call.
```

**Deep dive K — Cost control**

```
   SMS is expensive ($0.005-0.05 per message).
   Email is cheap ($0.0001 per send).
   Push is basically free.
   
   Cost governance:
     ▸ Per-service budget (Order svc gets 1M SMS/day, no more).
     ▸ Auto-degrade: transactional SMS falls back to push+email if quota exhausted.
     ▸ Fraud detection: prevent OTP-flood attacks (rate limit per phone).
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Provider rate limits** — need multi-provider failover, quota-aware pacing.
- **Kafka fan-out for campaigns** — split segments into shards, parallelize.
- **Redis dedup memory** — TTL aggressively; shard by hash.
- **Device-token lookup** — cache in Redis, denormalize by user_id.
- **Cassandra hot partitions** for very active users — bucket by hour, not day.

**Tradeoffs to state out loud**
- **Speed vs cost** — SMS delivers fastest but costs 100× email. Prefer push when possible.
- **Deliverability vs freedom** — strict template review reduces marketing agility but avoids ISP blacklisting.
- **Personalization vs cache** — rendered payloads can't be shared → less caching benefit.
- **Fail-open vs fail-closed** on preferences — if prefs service is down, do we send? Usually fail-**closed** for marketing (safer), fail-**open** for security-critical.

**Interview one-liner**
> "A notification system is a channel-abstracted fan-out with strict priority lanes: dedupe at the API, separate transactional from marketing Kafka topics, per-channel workers behind provider adapters with circuit breakers and quota-aware routing, retries + DLQ for reliability, and preferences resolved before publish for compliance and cost control."

---

### 14.13 Design a web crawler (Googlebot / Bingbot)

The classic "traverse a graph of 100B nodes politely" problem. Interview signal comes from **URL deduplication at scale**, **politeness / rate limiting per host**, and **coordinating a distributed fleet of workers**.

#### ① Requirements

**Functional**
- Given a seed list of URLs, crawl the web recursively.
- **Fetch → parse → extract links → enqueue new URLs.**
- **Politeness** — respect `robots.txt`, honor `Crawl-Delay`, don't hammer any host.
- **Deduplication** — don't crawl the same URL twice.
- **Freshness** — recrawl popular/changed pages periodically.
- **Prioritization** — high-value pages crawled sooner.
- Store fetched content for downstream indexing.

**Non-functional**
- **Scale** — crawl ~1B pages/day (Google-scale is ~10B+/day).
- **Efficiency** — bandwidth is the bottleneck.
- **Resilience** — a bad page must not stall the pipeline.
- **Extensibility** — new content types (video, PDF, JS-rendered pages).
- **Distributed** — many machines, coordinated but loosely coupled.

**Out of scope**
- Search indexing / ranking (separate system).
- Real-time crawling (news / social).
- JavaScript rendering deep dive (mention headless Chrome, don't design).

#### ② Back-of-envelope

```
   Target:            1B pages/day
   Pages/sec avg:     1B / 86,400 ≈ 12,000/sec
   Peak (3×):         ~35,000/sec
   
   Avg page size:     ~500 KB (with HTML + inline scripts)
   Bandwidth:         12K × 500 KB = 6 GB/sec = ~50 Gbps
                       → 6-10 racks of 10 Gbps NICs.
   
   Storage:
     Raw HTML:        500 KB × 1B = 500 TB/day.
     Compressed (~5×): ~100 TB/day → ~36 PB/year.
   
   URLs to track:
     Discovered URLs: ~100B (Google indexed 100T+; we're a smaller crawler).
     Per URL metadata: ~200 B (URL + hash + last-crawled + priority).
     ~20 TB of URL metadata.
   
   Threads / workers:
     One fetch takes ~1-2 sec (network + parse).
     12K pages/sec × 2 sec = 24K concurrent fetches.
     → ~2,400 workers, 10 concurrent fetches each.
```

**Key insight:** the challenge isn't compute — it's **URL deduplication across 100B items** and **not getting banned by websites**. Bandwidth + politeness set the pace.

#### ③ API — internal (no public API)

Crawlers don't expose an API to end-users. They expose control interfaces to operators.

```
   POST /seeds { urls: [...] }        → add starting URLs
   POST /crawls { policy, priority }  → schedule a targeted recrawl
   GET  /stats                        → pages/sec, queue depth, error rates
   POST /host/{domain}/pause          → operator override
   
   Internal event bus:
     "url.discovered"  → schedulers pick up
     "url.fetched"     → parsers/indexers consume
```

#### ④ Data model

Split by hot vs cold state.

```
   URL Frontier (Kafka + Redis, per-host queues)
   ──────────────────────────────────────────────
   Kafka topics per priority band: "high-pri", "medium-pri", "low-pri".
   Redis sorted set per host:  frontier:{host} → sorted by priority score.
   
   Seen-URLs store (Bloom filter + KV backing)
   ───────────────────────────────────────────
   In-memory Bloom filter per shard (fast negative).
   Cassandra:  url_hash → { first_seen, last_crawled, etag, hash_of_content }
   
   Content store (S3 / HDFS / GCS)
   ────────────────────────────────
   Path:  s3://crawl/{yyyy-mm-dd}/{url_hash_prefix}/{url_hash}.html.gz
   Metadata sidecar with fetch time, headers, size.
   
   Host state (Redis)
   ──────────────────
   host:{domain} → {
     last_fetch_ts, robots_rules, crawl_delay,
     circuit_state, error_count
   }
   
   Crawl scheduling (Cassandra)
   ─────────────────────────────
   Partition key:  host_domain
   Cluster key:    next_crawl_ts
   Columns:        url, priority, retry_count, ...
   Enables "give me the next 100 URLs due for host X".
```

#### ⑤ High-level design

```
   ┌──────────────────┐
   │  Seed loader     │  ← operator supplies URL seeds
   └────────┬─────────┘
            │
            ▼
   ┌──────────────────────────────────────┐
   │  URL Frontier (multi-priority)       │
   │                                      │
   │  Kafka topics: high / medium / low   │
   │  Sharded by host                     │
   └────────┬─────────────────────────────┘
            │
            ▼
   ┌──────────────────────────────────────┐
   │  Scheduler / Politeness gate          │
   │  ▸ per-host token bucket              │
   │  ▸ respects Crawl-Delay & robots      │
   │  ▸ picks next URL when host is ready  │
   └────────┬─────────────────────────────┘
            │
            ▼
   ┌──────────────────────────────────────┐
   │  Fetcher pool (many workers)         │
   │  ▸ HTTP client (async, connection    │
   │    pool per host)                    │
   │  ▸ handles retries, DNS, timeouts    │
   └────────┬─────────────────────────────┘
            │
            ▼
   ┌──────────────────────────────────────┐
   │  Parser & Link extractor              │
   │  ▸ HTML → DOM → links, text          │
   │  ▸ URL normalization                 │
   │  ▸ content hash (dedupe similar)     │
   └────────┬────────────────┬────────────┘
            │                │
            ▼                ▼
   ┌───────────────┐  ┌──────────────────┐
   │  Seen-URLs    │  │ Content store    │
   │  (Bloom +     │  │ (S3 / HDFS)      │
   │  Cassandra)   │  └────────┬─────────┘
   └───────┬───────┘           │
           │                   ▼
           ▼           ┌──────────────────┐
   ┌───────────────┐   │ Downstream       │
   │  New URLs     │───│ pipelines        │
   │  back to      │   │ (indexer, ML)    │
   │  frontier     │   └──────────────────┘
   └───────────────┘
```

**Traced flow: one page from queue → storage**

```
   1.  Scheduler: pick next URL for host with token available.
   2.  Fetcher:
         ▸ Check robots.txt cache; skip if disallowed.
         ▸ HTTP GET the URL; capture headers, body, status.
         ▸ Retryable errors → exponential backoff.
   3.  Parser:
         ▸ Compute content_hash (SHA256 of normalized text).
         ▸ If content_hash matches previous version → skip storage, update metadata.
         ▸ Else store to S3; publish "content.fetched" event.
         ▸ Extract & normalize outbound links.
   4.  Deduper:
         ▸ For each link: Bloom check → likely-seen? skip.
         ▸ Bloom miss → Cassandra confirm → enqueue if new.
   5.  Frontier: newly-discovered URLs go into per-host queues with priority.
```

#### ⑥ Deep dives

**Deep dive A — URL deduplication at 100B scale**

```
   Naive: check DB on every discovered URL.
     100M URLs/sec discovery → 100M DB checks/sec → impossible.
   
   Two-tier design:
     Tier 1 — In-memory Bloom filter (per shard):
       ▸ Holds hashes of already-seen URLs.
       ▸ False positive rate ~1% → miss <1% of new URLs (acceptable).
       ▸ 100B URLs × 10 bits ≈ 125 GB total → shard across ~64 nodes.
   
     Tier 2 — Cassandra "seen" table:
       ▸ Confirms Bloom-negative results are truly new.
       ▸ Only queried on Bloom NEGATIVE → drastically fewer queries.
       ▸ Bloom POSITIVE = definitely-seen (skip).
   
   URL normalization BEFORE hashing (critical):
     ▸ Lowercase host.
     ▸ Sort query params, drop tracking (utm_*, fbclid, gclid).
     ▸ Trim fragments (#anchor).
     ▸ Resolve relative → absolute.
     → prevents "same page counted twice".
```

**Deep dive B — Politeness & robots.txt**

```
   Before hitting any URL on a host:
     ▸ Fetch host/robots.txt once, cache (TTL 24h).
     ▸ Parse User-Agent rules, Disallow lists, Crawl-Delay.
     ▸ Apply defaults if missing (e.g., 1 req/sec).
   
   Per-host token bucket in Redis:
     Key:   host:{domain}:tokens
     Refill rate matches Crawl-Delay (say 1 token / sec).
     Scheduler pops URL, waits for token, then fetches.
   
   Adaptive politeness:
     If host responds slowly / with 5xx / 429 → slow down further.
     If /robots.txt says "Crawl-delay: 10" → strictly respect.
     If host bans (403 permanent) → circuit-open, back off hours.
   
   This is THE most important thing operators care about. Get it wrong,
   you get banned by Cloudflare / Cloudfront everywhere.
```

**Deep dive C — Distributed frontier & partitioning**

```
   Requirements:
     ▸ Every URL discovered ends up in ONE queue (not duplicated).
     ▸ Politeness enforced ACROSS the fleet — one host shouldn't get
       hit by 10 different workers simultaneously.
   
   Design:
     Shard by hash(host).
     All URLs for host X land on shard S(X).
     Shard S owns:
       ▸ Kafka partition for host X.
       ▸ Redis token bucket for host X.
       ▸ Scheduler workers pinned to shard S.
     
     → Politeness is a local concern within each shard.
     → No cross-shard coordination on hot path.
   
   Rebalancing: consistent hashing (Section 6.2). Adding a shard moves
   ~1/N of hosts.
```

**Deep dive D — Prioritization / crawl budget**

```
   Not all URLs are equal.
   
   Score inputs:
     ▸ PageRank / inbound link count (offline computed).
     ▸ Domain trust (major news > random blog).
     ▸ Content freshness (news changes hourly; encyclopedias don't).
     ▸ User query signals (searched but never crawled → high priority).
   
   Frontier is multi-priority:
     high-pri:   top 5% pages, recrawl every hour.
     medium-pri: 60%,        recrawl every day.
     low-pri:    35%,        recrawl every week or on demand.
   
   Scheduler picks from high-pri first when politeness allows.
   
   Crawl budget: cap per host per day so we don't crawl a spam site
   with 1M auto-generated URLs indefinitely.
```

**Deep dive E — Fetcher design**

```
   Async HTTP with connection pooling:
     ▸ Java: OkHttp / Netty; Python: aiohttp; Go: net/http with tuned transport.
     ▸ ~10-100 concurrent fetches per worker.
     ▸ Reuse TCP + TLS to the same host (huge speedup).
   
   Timeouts:
     Connect: 5s
     Read:    15s
     Total:   30s
   
   DNS caching:
     Local Unbound / dnsmasq → avoid re-resolving every fetch.
     TTL respected but bounded (5-30 min).
   
   Content type filters:
     Skip binary/audio/video by default (Content-Type header).
     Skip pages > N MB.
   
   Redirects:
     Follow up to 5 hops.
     Each hop normalized + deduped.
     Store final URL alongside canonical.
```

**Deep dive F — Freshness recrawl**

```
   Same URL may need recrawling because:
     ▸ Content changed.
     ▸ Cache TTL expired.
     ▸ Higher priority now.
   
   Change detection:
     ▸ HEAD request first: check ETag / Last-Modified. If unchanged, skip.
     ▸ Full GET only when necessary.
   
   Adaptive schedule per URL:
     If previous 5 fetches showed content changed → recrawl faster.
     If unchanged → back off (up to a cap).
```

**Deep dive G — Duplicate content detection**

```
   Beyond exact-URL dedupe:
     ▸ Same content served under many URLs (mirrors, session IDs).
     ▸ Near-duplicate pages (product page A/B variants).
   
   Techniques:
     ▸ Content hash (SHA256 of normalized text).
     ▸ SimHash for near-duplicate detection.
     ▸ Canonical URL from <link rel="canonical">.
   
   If duplicate → don't re-store; mark URL as alias to canonical.
```

**Deep dive H — JavaScript-rendered pages**

```
   Modern web: much content only exists after JS runs.
   Options:
     ▸ Naive: fetch HTML, miss content. Fast but incomplete.
     ▸ Headless browser (Puppeteer / Playwright):
         Fully render, extract post-JS DOM.
         10× slower and 100× more RAM.
         Only for a small allowlist of high-value domains.
   
   Modern crawlers do hybrid: HTML by default, headless for specific hosts.
```

**Deep dive I — Traps & malicious content**

```
   Common gotchas:
     ▸ Infinite calendars: /events/2024/01, /events/2024/02, ... forever.
       Fix: URL depth limit; pattern-based trap detection.
     ▸ Query-param explosions: ?sort=asc&color=red&size=L... 10K variants.
       Fix: normalize params, cap depth.
     ▸ Malware / phishing: don't render untrusted content in your workers.
       Fix: sandbox headless browsers; treat all fetched content as untrusted.
     ▸ Redirect loops: capped at 5 hops.
     ▸ Very large pages: hard cutoff at N MB.
```

**Deep dive J — Storage & pipeline**

```
   Raw content → S3 / HDFS (object per page, compressed).
   Metadata → Cassandra (indexed by URL hash, host, crawl_ts).
   Publish "content.fetched" to Kafka:
     Downstream consumers: indexer, ranking, ML training, analytics.
   
   Cost: object storage is cheap ($20/TB/month S3 Standard);
   compress with gzip / zstd for 3-5× savings.
```

**Deep dive K — Distributed coordination & failure**

```
   Workers are stateless; state lives in Redis/Cassandra/Kafka.
   
   Worker failures:
     ▸ In-flight URL: Kafka offset not committed → replayed on next assignment.
     ▸ Idempotent parse + dedupe means retries are safe.
   
   Shard rebalance:
     ▸ Consistent hashing on host → adding/removing shards moves few hosts.
     ▸ Kafka rebalance handles partition ownership.
   
   Backpressure:
     ▸ If parser lags, Kafka builds up → scale parser workers.
     ▸ If frontier grows unbounded, drop low-priority discoveries beyond a cap.
```

**Deep dive L — Observability**

```
   Metrics per host, per shard:
     ▸ pages/sec fetched
     ▸ bytes/sec bandwidth
     ▸ error rates by HTTP status
     ▸ queue depth per priority band
     ▸ Bloom false-positive rate
   
   Alerts:
     ▸ Sudden ban wave (403/429 spike from many hosts) → maybe our UA changed.
     ▸ Bandwidth cliff → provider throttling.
     ▸ Frontier growth stalled → scheduler stuck.
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Bandwidth** — bigger network fabric, more edge PoPs.
- **DNS load** — local resolver farm; anycast to multiple resolvers.
- **Bloom filter memory** — shard finer or move to a disk-backed structure (Cuckoo filter on SSD).
- **Cassandra "seen" reads** — add SSTable-backed prefix sharding by host.

**Tradeoffs to state out loud**
- **Freshness vs bandwidth cost** — aggressive recrawl catches changes but wastes traffic on unchanged pages. ETag/HEAD saves both.
- **Coverage vs quality** — crawl-budget cap keeps low-value sites bounded but you might miss the one gem.
- **Politeness vs speed** — 1 req/sec per host is safe but slow. Higher-quality hosts sometimes let you go faster; use adaptive politeness.
- **JS rendering everywhere vs selective** — full headless is 100× the cost; do it only for high-value domains.

**Interview one-liner**
> "A web crawler is a graph traversal at internet scale. The bottlenecks are URL deduplication (Bloom filter + Cassandra), politeness (per-host token buckets, robots.txt), and coordinating a distributed fleet without stepping on hosts — solved by sharding by host so politeness stays local."

---

### 14.14 Design a distributed cache (Redis / Memcached from scratch)

The "build the thing you've been using in every other design" problem. Interview signal comes from **consistent hashing**, **replication + failover**, **eviction under memory pressure**, and **client-side vs proxy-based sharding**.

#### ① Requirements

**Functional**
- **GET / SET / DELETE** on string keys.
- **TTL** per key.
- **Atomic operations** — INCR, expire, CAS (compare-and-set).
- **Optional richer types** — lists, sorted sets, hashes (Redis-style) OR just KV (Memcached-style).
- **Cluster mode** — automatic sharding across nodes.
- **Replication** — replicas for failover.

**Non-functional**
- **Latency P99 < 1 ms** on cache HIT.
- **Throughput** — 1M ops/sec per node, 10M+ across cluster.
- **Availability** — one node failure = no data loss, minor reconfig.
- **Scalability** — add nodes without full data reshuffle.
- **Durability** — optional (cache use case) or configurable (session store use case).

**Out of scope**
- Full Redis feature parity (streams, pub/sub, Lua).
- Multi-tenant SaaS layer.

#### ② Back-of-envelope

```
   Cluster target:    10M ops/sec, 1 TB total working set.
   
   Per node (commodity server):
     RAM:              64-256 GB.
     Ops/sec:          ~1M for single-threaded event loop (Redis pattern).
     Network:          10 Gbps NIC → ~1M small requests/sec bandwidth-wise.
   
   Cluster size:
     10 nodes × 1M ops/sec = 10M ops/sec.
     10 nodes × 128 GB = 1.28 TB.  → fits our 1 TB target with 25% headroom.
   
   With RF=2 (each key on 2 nodes for failover):
     Effective capacity halves → need 20 nodes for 1 TB.
   
   Values:
     Avg ~1 KB per value → ~1B keys.
     Overhead per key: hash slot, TTL, eviction metadata ~64 B.
     Overhead alone: 1B × 64 B = 64 GB.
```

**Key insight:** the design is dominated by **partitioning (which node holds this key?)** and **failover** — not raw throughput.

#### ③ API — the client protocol

Wire protocol matters. Redis uses RESP (Redis Serialization Protocol) — text-ish, simple, fast.

```
   Basic
   ─────
   SET key value [EX seconds] [NX|XX]           → OK / (nil)
   GET key                                       → value / (nil)
   DEL key1 [key2 ...]                          → integer removed
   EXPIRE key seconds                            → integer 0/1
   TTL key                                       → integer seconds
   
   Atomic
   ──────
   INCR key                                      → integer
   SETNX key value                              → 0/1 (SET if Not eXists)
   CAS key old_value new_value                  → 0/1
   
   Cluster control (admin)
   ───────────────────────
   CLUSTER INFO
   CLUSTER NODES
   CLUSTER SHARDS
```

Client sends binary/text frames over persistent TCP; server pipelines responses.

**Pipelining (huge perf win)**

```
   Naive:            SET foo bar → wait for OK → SET baz qux → wait for OK
   Pipelined:        SET foo bar
                     SET baz qux                   (all sent in one batch)
                     ← [OK, OK]                    (all responses in one batch)
   
   → 10-100× throughput for bulk workloads.
```

#### ④ Data model — the internals

Each node holds:

```
   Key store (in-memory hash map)
   ──────────────────────────────
   Redis dict:  hash table with incremental rehashing.
   → O(1) GET/SET average.
   
   Expiration
   ──────────
   Two-tier expiry:
     ▸ Passive: on GET, check TTL, evict if expired.
     ▸ Active:  background sampler picks 20 random keys/sec, expires ones past TTL.
   
   Eviction
   ────────
   When memory > maxmemory, evict per policy (LRU / LFU / random).
   
   Persistence (optional)
   ──────────────────────
   RDB — periodic snapshot to disk.
   AOF — append every write to a log; replay on restart.
   
   Cluster state
   ─────────────
   Hash slot table (0..16383 slots).
   Each node owns a subset of slots.
   Gossip protocol keeps slot ownership consistent.
```

#### ⑤ High-level design

**A single-node cache**

```
   Client
     │  TCP (RESP protocol)
     ▼
   ┌─────────────────────────────────────┐
   │  Event loop (single thread — like    │
   │  Redis) or multi-thread (Memcached)  │
   │                                      │
   │   Parse request                      │
   │        │                             │
   │        ▼                             │
   │   Dispatch:                          │
   │     ▸ Read (GET, EXISTS): hash lookup│
   │     ▸ Write (SET, DEL): mutate       │
   │     ▸ Expire check                   │
   │     ▸ Increment eviction counters    │
   │        │                             │
   │        ▼                             │
   │   Serialize response                 │
   │        │                             │
   │        ▼                             │
   │   Write to socket                    │
   │                                      │
   │   Background:                        │
   │     ▸ Active expiry sampler          │
   │     ▸ RDB snapshot / AOF writer      │
   │     ▸ Replication stream sender      │
   └─────────────────────────────────────┘
```

**Multi-node cluster with sharding + replication**

```
   ┌───────────────────────────────────────────────────────┐
   │                     Cluster                            │
   │                                                        │
   │   Node A (primary, slots 0-5460)                       │
   │       └─► Replica A' (async replication)               │
   │                                                        │
   │   Node B (primary, slots 5461-10922)                   │
   │       └─► Replica B'                                   │
   │                                                        │
   │   Node C (primary, slots 10923-16383)                  │
   │       └─► Replica C'                                   │
   │                                                        │
   │   All nodes gossip:                                    │
   │     ▸ Slot ownership                                   │
   │     ▸ Node health                                      │
   │     ▸ Cluster topology changes                         │
   └───────────────────────────────────────────────────────┘
```

**Client-side routing (Redis Cluster model)**

```
   Client GET foo:
     ① slot = CRC16("foo") mod 16384 = 12182
     ② Client's slot map says slot 12182 → Node C.
     ③ Client connects to C, sends command.
     ④ If C moved slot recently:
          C replies "MOVED 12182 10.0.0.5:6379" → client updates map, retries.
   
   → Zero-hop reads/writes. No proxy.
```

**Proxy-based routing (Twemproxy / envoy pattern)**

```
   Client ──► Proxy ──► correct node
     
   ✅ Client is dumb.
   ❌ Extra hop (adds ~200 µs).
   ❌ Proxy is a scaling/failure point.
```

**Trade:** Redis Cluster picks client-side routing for speed. Memcached traditionally uses client-side hashing too.

#### ⑥ Deep dives

**Deep dive A — Consistent hashing (why not modulo?)**

```
   Naive modulo sharding:
     shard = hash(key) mod N
     Adding a node → N changes → almost EVERY key remaps → whole cache invalidated.
   
   Consistent hashing:
     Hash both keys AND nodes onto a ring (0 .. 2^32).
     Key's owner = first node clockwise from key's position.
     Adding a node → only keys between it and its neighbor move (~1/N).
   
                            0
                        ┌───┴───┐
                      ▓ │       │ ▓
                (Node A)│       │(Node B)
                        │       │
                (Node C)│       │
                      ▓ │       │
                        └───────┘
                           180
```

**Virtual nodes (vnodes)**

```
   Real node → 100-500 virtual points on the ring.
   Better load distribution.
   Node with more RAM → gets more vnodes → holds more keys.
   
   Redis Cluster uses a fixed 16384 hash slots (simpler than continuous ring).
   Slot → node mapping stored in cluster state.
   Adding node: reassign some slots.
```

**Deep dive B — Replication & failover**

```
   Master-replica per shard:
     Primary handles writes.
     Replicas receive async stream of writes.
   
   Async replication (Redis default):
     Primary acks client BEFORE replica receives.
     → Possible data loss on primary crash BETWEEN ack and replicate.
     Acceptable for cache; not for durable KV.
   
   Failover (Redis Sentinel / Cluster):
     Sentinels probe primaries; majority quorum agrees "primary is down".
     Elect a replica → promote to primary.
     Clients discover via gossip / MOVED response → reconnect.
   
   Failover time:  ~15-30 sec typically.
```

**Deep dive C — Multi-key operations across shards**

```
   MSET a=1 b=2 c=3 — if a, b, c hash to different slots?
     ▸ Redis Cluster refuses (CROSSSLOT error).
     ▸ Workaround: hash tags {user:42}:cart, {user:42}:profile → same slot.
   
   Transactions & Lua scripts:
     ▸ All keys must be in the SAME slot.
     ▸ Otherwise abort.
   
   → App design must consider co-location.
```

**Deep dive D — Eviction policies**

Recall from Section 7.4. Redis supports:

```
   noeviction                → return error when full (safest, breaks writes)
   allkeys-lru               → classic LRU across all keys
   allkeys-lfu               → LFU (needs count decay to avoid stickiness)
   allkeys-random            → random victim
   volatile-lru              → LRU among keys with TTL only
   volatile-lfu              → LFU among keys with TTL
   volatile-ttl              → evict soonest-to-expire first
   
   Approximate LRU:
     Instead of a precise LRU list (expensive), sample K random keys
     and evict the oldest among them. Redis default K=5.
     ~99% as good as true LRU at a fraction of the cost.
```

**Deep dive E — Persistence: RDB vs AOF**

```
   RDB (snapshots):
     Fork process, dump memory to disk file periodically.
     ✅ Fast restart (single sequential read).
     ✅ Compact.
     ❌ Data loss window = time since last snapshot.
     ❌ Fork = CPU + memory copy-on-write cost.
   
   AOF (append-only file):
     Every write appended to a log.
     ✅ Minimal data loss (fsync policy).
     ❌ Slower restart (replay log).
     ❌ File grows; needs periodic rewrite.
   
   Fsync policy for AOF:
     always     → fsync every write. Safe. Slow.
     everysec   → fsync once per second. Loss ≤ 1s. Good default.
     no         → OS decides. Fast. Riskier.
   
   Hybrid (Redis 7):
     RDB snapshot + AOF tail → best of both.
   
   Cache use case: often "no persistence" — fast restart from scratch is fine.
```

**Deep dive F — Concurrency model**

```
   Redis: single-threaded event loop per shard.
     ✅ No lock contention. Simple. Deterministic.
     ✅ Atomic multi-key ops within same slot.
     ❌ One thread per node = need many nodes to use big machines.
   
   Memcached: multi-threaded.
     ✅ Uses all cores on one box.
     ❌ Lock contention on hash table.
   
   Redis 6+: I/O threads (read/write on separate threads; command exec still single).
     Compromise; helps for network-bound workloads.
```

**Deep dive G — Cluster membership & gossip**

```
   Each node periodically pings a few random peers.
   Info exchanged:
     ▸ Node liveness.
     ▸ Slot ownership.
     ▸ Config epoch (for conflict resolution).
   
   Membership changes propagate epidemically → O(log N) rounds.
   
   Failure detection:
     PFAIL — one node suspects another.
     FAIL  — quorum agrees; triggers failover.
   
   Split-brain protection:
     Only majority partition can perform failovers.
     Minority partition → read-only or refuses writes.
```

**Deep dive H — Client design**

```
   Smart clients (Jedis Cluster, Lettuce, redis-py cluster):
     ▸ Cache slot → node map.
     ▸ Send commands directly to the right node.
     ▸ Handle MOVED / ASK redirects.
     ▸ Refresh topology on error or periodically.
   
   Connection pool per node.
   Pipelining for throughput.
   Async APIs for high concurrency (Lettuce with Netty).
   
   MOVED vs ASK:
     MOVED — slot has permanently moved. Client updates map.
     ASK   — slot is currently migrating. This one command should retry
              at target; don't update map yet.
```

**Deep dive I — Adding / removing nodes (resharding)**

```
   Add Node D:
     ▸ Join cluster (gossip).
     ▸ Cluster admin (or auto-scaler): reassign some slots from A/B/C to D.
     ▸ For each slot being moved:
         D marks slot as MIGRATING.
         Source marks slot as IMPORTING.
         Keys migrated one by one (MIGRATE command).
         Clients accessing during migration get ASK redirects.
     ▸ Once slot is fully moved: gossip new ownership.
   
   Remove node:
     ▸ Redistribute its slots first.
     ▸ Then decommission.
   
   Zero downtime — clients follow ASK/MOVED transparently.
```

**Deep dive J — Hot key problem**

```
   Same key gets 90% of QPS (viral post, top user).
   → One node's CPU pegged, others idle.
   
   Mitigations:
     ▸ Local L1 (Caffeine) on each app pod → hot key served in nanoseconds.
     ▸ Multiple copies of the hot key with random suffix (item:42:0..9).
     ▸ Read replicas: N replicas of the hot slot, load-balance reads across them.
     ▸ Client-side caching (Redis 6 client-side caching feature).
```

**Deep dive K — Big key & big value hazards**

```
   500 MB value:
     Single GET blocks event loop for hundreds of ms.
     Network transfer stalls other commands.
   
   Fixes:
     ▸ Cap value size (say 100 KB).
     ▸ Split large blobs into chunks under an index key.
     ▸ Store big blobs in S3; keep only a reference in cache.
   
   Big data structures (huge lists, sets):
     Use SCAN-family commands (non-blocking iteration).
     Split into shards manually if hot.
```

**Deep dive L — Observability**

```
   Metrics per node:
     ▸ ops/sec by command
     ▸ hit ratio
     ▸ memory usage, fragmentation
     ▸ evictions/sec
     ▸ replication lag
     ▸ P99 latency by command
   
   Slow log — commands > threshold.
   Client-side latency dashboards.
   
   Alerts:
     ▸ Hit rate drop → warmup issue or eviction storm.
     ▸ Memory > 90% → scale or reduce TTLs.
     ▸ Replication lag > threshold → replica falling behind.
     ▸ Cluster state != ok → failover in progress.
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Single-thread CPU per node** — add more nodes (horizontal scale-out).
- **Network** — 10 Gbps NIC saturates around ~1M small ops/sec; move to 25/40 Gbps or spread traffic.
- **Cluster gossip volume** — Redis Cluster caps out around 1000 nodes; beyond that, hierarchical cluster or federation.
- **Client topology cache churn** during frequent reshardings.

**Tradeoffs to state out loud**
- **Client-side sharding vs proxy** — client-side is faster but clients must be smart; proxy simpler but adds hop.
- **Single-thread vs multi-thread per node** — single-thread simpler, deterministic; multi-thread uses cores but adds lock contention.
- **Async vs sync replication** — async: fast writes, possible data loss on failover; sync: strong consistency, slow writes.
- **Consistent hashing vs fixed slot table** — slot table (Redis) is simpler to reason about; ring (Cassandra style) is more elegant with vnodes.
- **Cache vs source of truth** — treating a cache as source of truth (session store) demands persistence; pure cache doesn't.

**Interview one-liner**
> "A distributed cache is a sharded, replicated in-memory KV store. Consistent hashing minimizes reshard pain, async replication tolerates single-node loss, single-threaded event loops per shard buy simplicity and determinism, and smart clients route directly to the owning node. The hardest problems are hot keys and network saturation, not raw throughput."

---

### 14.15 Design a distributed job scheduler / cron (Airflow-lite, Sidekiq, Quartz cluster)

The "run a task at time T, reliably, on some worker, without double-running or losing it" problem. Interview signal comes from **leader-safe scheduling**, **at-least-once vs exactly-once execution**, **delayed queues**, and **dependency graphs / retries**.

#### ① Requirements

**Functional**
- Schedule jobs by:
  - **Cron expression** ("every hour", "0 3 * * MON").
  - **Delay** ("run in 30 min", "run at 2025-12-31 18:00").
  - **Dependency** ("run job B after A succeeds").
- Distribute execution across a fleet of workers.
- **At-least-once** delivery of the job to a worker.
- **Retries** with backoff on failure.
- Query job status: pending, running, succeeded, failed, retrying.
- **Cancel / pause / resume** jobs.
- **Priority** — high-priority jobs jump the queue.

**Non-functional**
- Handle millions of jobs pending, ~10K firing per second at peak.
- Timing accuracy: within a few seconds of scheduled time.
- No lost jobs during scheduler / worker crashes.
- Idempotent execution semantics (business responsibility with system support).
- Multi-tenant: jobs from many teams, isolation of misbehavers.

**Out of scope**
- Full DAG orchestration UI (Airflow-scale).
- Data lineage / observability of business data.

#### ② Back-of-envelope

```
   Total jobs:            100M pending
   Peak firing rate:      10K jobs/sec
   Avg job duration:      5 sec (mix of quick + long)
   Concurrent running:    10K × 5 sec = 50K workers busy at peak
   Fleet size:            ~500 worker pods × 100 concurrent each = 50K concurrent
   
   Storage per job:       ~1 KB (params + metadata + status log)
   Total pending:         100M × 1 KB = 100 GB
   With history (30d):    ~3 TB
   
   Scheduler tick rate:   1 tick / sec → each tick processes jobs due in next window.
```

**Key insight:** the challenge isn't compute — it's **making sure "run at T" happens once, on time, under crashes and reshards.**

#### ③ API

```
   Job control
   ───────────
   POST /jobs
     body: {
       job_id: (optional, else generated),        ← idempotency key
       name: "send_daily_digest",
       schedule: { cron: "0 8 * * *", tz: "..." }  OR
                 { run_at: "2025-11-30T14:00:00Z" }  OR
                 { delay_sec: 300 },
       payload: { ... },
       priority: 1..10,
       max_retries: 3,
       retry_backoff: "exponential:1s..5m",
       depends_on: [job_id, ...],                  ← optional DAG edges
       timeout_sec: 60
     }
     → 201 { job_id }
   
   GET  /jobs/{id}                                → status + attempts
   POST /jobs/{id}/cancel                          → 204
   POST /jobs/{id}/pause / resume
   
   Worker protocol
   ───────────────
   POST /workers/dequeue
     → { job_id, payload, lease_expires_at }
   POST /workers/heartbeat { job_id, lease_expires_at }
   POST /workers/complete  { job_id, status: ok|failed, error?: ..., result?: ... }
```

The worker **leases** jobs (long-poll or pull); server reassigns if lease expires without heartbeat.

#### ④ Data model

```
   Jobs (Postgres or Cassandra)
   ────────────────────────────
   job_id       PRIMARY KEY
   name, tenant_id, priority
   schedule (cron / run_at / delay)
   next_fire_at TIMESTAMP INDEXED       ← the hot column
   status       enum (pending, scheduled, running, succeeded, failed, canceled)
   payload      JSON
   attempts_used
   depends_on   [job_id]
   created_at, updated_at
   
   Execution log (Cassandra — time-series)
   ────────────────────────────────────────
   PK:  job_id
   CK:  attempt_ts
   Columns: worker_id, status, error, duration_ms
   
   Ready queue (Redis / Kafka)
   ────────────────────────────
   Once a job's next_fire_at ≤ now, it's placed here.
   Priority-tiered: queue:pri1, queue:pri2, ...
   Delayed set: Redis ZSET score=next_fire_at, member=job_id
   
   Leases (Redis)
   ──────────────
   Key: lease:{job_id} → worker_id
   TTL: lease_duration (e.g., 60s)
```

#### ⑤ High-level design — two subsystems

**Subsystem 1: Scheduler** — decides *when* jobs should run.
**Subsystem 2: Executor** — runs the job on a worker.

```
   Producer (any service)
       │
       │ POST /jobs
       ▼
   ┌───────────────────────────┐
   │  Scheduler API             │
   │  ▸ validate                │
   │  ▸ persist to Jobs table   │
   │  ▸ compute next_fire_at    │
   └─────────────┬─────────────┘
                 │
                 ▼
   ┌───────────────────────────────────────────┐
   │  Scheduler tick loop (leader-elected)      │
   │  every 1 sec:                              │
   │   ▸ SELECT jobs WHERE next_fire_at ≤ now  │
   │   ▸ For each: publish to ready queue      │
   │   ▸ Compute next_fire_at (for recurring)  │
   └─────────────┬─────────────────────────────┘
                 │
                 ▼
   ┌───────────────────────────────────────────┐
   │  Ready queue                              │
   │  ▸ Redis lists / Kafka partitions          │
   │  ▸ Prioritized                             │
   └─────────────┬─────────────────────────────┘
                 │
                 ▼
   ┌───────────────────────────────────────────┐
   │  Workers (pull model)                     │
   │  ▸ dequeue                                 │
   │  ▸ acquire lease                           │
   │  ▸ run payload                             │
   │  ▸ heartbeat every N sec                   │
   │  ▸ complete / fail                         │
   └─────────────┬─────────────────────────────┘
                 │
                 ▼
   ┌───────────────────────────────────────────┐
   │  Result handler                            │
   │  ▸ persist result                          │
   │  ▸ evaluate deps → enqueue children        │
   │  ▸ retry on failure with backoff           │
   └───────────────────────────────────────────┘
```

**Traced flow — one recurring job**

```
   Job "send_daily_digest" (cron "0 8 * * *", tz America/LA)
   
   T = 07:59:59 LA time
   ▸ Scheduler tick:
       Finds job with next_fire_at ≤ now.
       Publishes { job_id, payload } to queue:pri3.
       Computes next_fire_at = tomorrow 08:00 LA → updates Jobs table.
   
   T = 08:00:00
   ▸ Worker W-42 dequeues.
       Sets lease:{job_id} = W-42 (TTL 60s).
       Runs handler.
       Sends heartbeat every 15s.
   
   T = 08:00:03
   ▸ Handler succeeds → POST /workers/complete { ok }.
       Job status = succeeded (this attempt).
       Job remains "scheduled" for tomorrow (recurring).
```

#### ⑥ Deep dives

**Deep dive A — The scheduler loop, safely (leader election)**

Multiple scheduler processes exist for HA. But if all of them fire the same due job, we double-run.

```
   Options:
   
   ① Single leader (Raft/ZooKeeper/etcd)
      One scheduler node is elected leader.
      Only leader runs the tick loop.
      ✅ Simple correctness.
      ❌ Throughput capped by one leader.
      ❌ Failover time (seconds).
   
   ② Sharded schedulers (partitioned)
      Jobs partitioned by hash(job_id) into N shards.
      Each shard has its own leader.
      Scale by shard count.
      ✅ Horizontal scale.
      ✅ Isolated failure domain per shard.
      Preferred for scale.
   
   ③ Optimistic dequeue with unique claim
      Every scheduler node runs the loop but uses UPDATE ... WHERE status='scheduled'
      RETURNING to atomically claim jobs.
      Row-level lock or an atomic status change ensures only one succeeds.
      ✅ No leader election needed.
      ❌ Contention if many schedulers race for the same rows.
   
   Popular real systems:
     Airflow scheduler:  single active (soft leader) — HA via failover.
     Sidekiq:            just enqueues; no scheduler leader problem.
     Temporal:           consensus-backed (Cassandra + workflow engine).
```

**Deep dive B — Firing precisely at scale**

```
   Problem: SELECT ... WHERE next_fire_at ≤ now every second.
     At 100M pending jobs, index scan gets expensive.
   
   Fix — hierarchical time buckets:
     next_fire_bucket = floor(next_fire_at / 60s)      ← bucket by minute
     Table indexed on (next_fire_bucket, next_fire_at)
     Each tick: SELECT WHERE next_fire_bucket IN {this_min, last_min}
       → tiny index range.
   
   For millisecond precision → smaller buckets.
   Sub-second precision → in-memory time wheel per shard.
```

**Deep dive C — Delayed queues (short-term precision)**

```
   For jobs firing in seconds-to-minutes future, in-memory beats DB.
   
   Redis ZSET as delayed queue:
     ZADD delayed_jobs <score=fire_at_unix_ms> <job_id>
     
   Ticker every N ms:
     ZRANGEBYSCORE delayed_jobs 0 <now>  → due jobs
     ZREM due jobs
     Publish to ready queue.
   
   Great for TTL retries, exponential backoff sleeps, short delays.
   
   Persisted-only fallback: for long delays (days), avoid Redis memory pressure.
```

**Deep dive D — At-least-once semantics + idempotency**

```
   The system guarantees: your handler will be called ≥ 1 time per scheduled fire.
   
   Failure modes producing duplicates:
     ▸ Worker crashes after starting but before ACKing → lease expires → another worker picks it up.
     ▸ Scheduler crashes after publishing but before marking dispatched → replays on recovery.
     ▸ Network partition: worker completed but ACK never received.
   
   Business must be idempotent:
     ▸ Handler carries fire_id (job_id + fire_ts).
     ▸ Stores processed fire_ids in DB or Redis for TTL.
     ▸ Duplicate arrival → no-op.
   
   Exactly-once is a mirage across handler side effects (Section 10.4).
   Provide "effectively-once" via at-least-once + idempotency.
```

**Deep dive E — Leases & orphaned jobs**

```
   Worker dequeues job → sets lease:{job_id} = worker_id with TTL 60s.
   
   Worker heartbeats every 15s → EXPIRE lease:{job_id} 60.
   
   Worker dies:
     No heartbeat → TTL expires.
     Reaper (background job) scans expired leases.
     Job status flipped back to "scheduled", republished to ready queue.
   
   Race: worker was slow, not dead — comes back, completes.
     Server checks current lease owner before accepting completion.
     If lease has been reassigned → reject the late worker's result.
     Fencing token: monotonic attempt_id (Section 11.10).
```

**Deep dive F — Retries & DLQ**

```
   Retry policy per job:
     max_retries: 3
     backoff: exponential 10s → 60s → 5min
     jitter: ±20%    ← prevents retry storms
   
   On failure:
     ▸ Record failed attempt.
     ▸ If attempts < max_retries → schedule retry in the delayed queue.
     ▸ Else → mark job failed; publish to DLQ.
   
   DLQ:
     Kafka topic or DB queue.
     Alerts fire when DLQ grows.
     Operators inspect payload, fix, requeue.
```

**Deep dive G — Dependency graphs (mini-DAG)**

```
   Job B depends_on [A].
   
   On job A success:
     Result handler queries: SELECT jobs WHERE A ∈ depends_on
     For each dependent, decrement pending_deps.
     When pending_deps == 0 → enqueue.
   
   On job A failure:
     Cascade: dependents marked "blocked" (not automatically failed).
     Retry policy: sometimes retry A; sometimes requires operator.
   
   For big DAGs (Airflow-scale):
     Precompute topological order.
     Cache dependency edges in Redis for fast lookups.
```

**Deep dive H — Multi-tenant fairness**

```
   Problem: Tenant X submits 1M jobs; starves tenant Y's normal load.
   
   Fix: per-tenant queues + weighted fair queueing.
     Ready queue = union of per-tenant queues.
     Dispatcher picks round-robin or weighted by tenant SLA.
     Per-tenant concurrency caps.
   
   Priority within tenant: separate priority lanes as needed.
```

**Deep dive I — Timezone-aware cron**

```
   Cron "0 8 * * *" in America/Los_Angeles.
   
   Server computes:
     next_fire_at (UTC) = next tick where local time in LA = 08:00.
   
   Complications:
     ▸ Daylight Saving Time — "8am LA" moves in UTC on DST boundaries.
     ▸ Historical timezone data — use IANA tzdata; refresh in scheduler.
     ▸ "Every 15 min" during DST fall-back → duplicate 1am hour? Define policy.
   
   Store timezone alongside cron; recompute per fire.
```

**Deep dive J — Worker pool design**

```
   Workers scale independently from scheduler.
   Types:
     ▸ Homogeneous: any worker runs any job.
     ▸ Sharded by queue: Python jobs to Python workers, ML to GPU workers.
   
   Autoscaling:
     Metric: queue depth or lag.
     HPA scales worker pods up when depth > threshold.
   
   Isolation:
     ▸ Container per job (Airflow's Kubernetes executor).
     ▸ Threads / async on shared worker (Sidekiq, Celery).
     ▸ Choose per job's blast radius: risky jobs get their own container.
```

**Deep dive K — Monitoring & observability**

```
   Metrics:
     ▸ Scheduling lag (fire_at - actual_fire_at).
     ▸ Queue depth per priority.
     ▸ Success / failure / retry rates.
     ▸ Worker utilization.
     ▸ DLQ growth.
   
   Alerts:
     ▸ Lag > threshold → scheduler stuck or overloaded.
     ▸ DLQ growing → poison messages.
     ▸ Long-running jobs beyond timeout.
   
   Job-level tracing: propagate trace_id from producer through worker.
```

**Deep dive L — Interactions with the outside world**

```
   Jobs may make external calls (HTTP, DB writes).
   Best practices:
     ▸ Handler idempotent — same fire_id → same outcome.
     ▸ External retries at handler level with own backoff.
     ▸ Circuit breakers for downstream (Section 8.7).
     ▸ Deadline propagation — pass timeout budget to downstream.
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Scheduler tick queries** — bucket by minute + index tightly; consider streaming approach (materialized view of "due soon").
- **Redis delayed queue memory** — shard by tenant / job class; move long delays to DB.
- **Coordination overhead** — sharded schedulers scale; single leader doesn't.
- **DB writes on every status change** — batch or use Cassandra for time-series log.

**Tradeoffs to state out loud**
- **Precision vs cost** — millisecond precision requires in-memory ticking per shard; second precision is fine for most workloads.
- **At-least-once + idempotency vs pursued "exactly-once"** — pursued costs more, still not perfect.
- **Single-leader scheduler vs sharded** — simpler vs scalable.
- **Push vs pull to workers** — pull is more resilient (self-throttling); push has less latency for hot workers.
- **DB-backed vs in-memory-only queue** — DB survives crashes; memory is fast. Combine.

**Interview one-liner**
> "A distributed scheduler is two subsystems: a leader-safe (or sharded) ticker that promotes jobs to a ready queue when their fire time arrives, and a worker fleet that leases and executes with heartbeat-based failover. At-least-once + idempotent handlers, plus a delayed queue for short-term precision, are the load-bearing patterns."

---

### 14.16 Design a distributed logging / metrics / tracing system (ELK, Prometheus, Datadog)

The "swallow petabytes of telemetry, index it, query it in seconds, keep it forever cheaply" problem. Interview signal comes from **pipeline design under massive write pressure**, **storage tiering hot→warm→cold**, **sampling vs full-fidelity**, and **query patterns per pillar** (logs, metrics, traces).

#### ① Requirements

**Functional**
- **Ingest** logs, metrics, and traces from thousands of services.
- **Structured logs** (JSON) — searchable by fields.
- **Metrics** — time-series, aggregatable (`rate`, `sum`, `histogram_quantile`).
- **Traces** — one request across services, correlated by `trace_id`.
- **Query** — dashboards, ad-hoc explore, alerting.
- **Retention policy** — hot (7 days), warm (30 days), cold (1 year+).
- **Multi-tenant** — team-level isolation & quotas.

**Non-functional**
- **Write throughput** — millions of events/sec sustained.
- **Query latency** — dashboards refresh in seconds; ad-hoc searches in tens of seconds.
- **Reliability** — losing 0.1% under overload is acceptable; losing entire logs is not.
- **Cost-efficient at scale** — telemetry can easily 10× your infra bill if unbounded.
- **No impact on producers** — a slow logging system must not slow the app.

**Out of scope**
- Log parsing DSLs / query language design details.
- Root-cause ML on traces (mention, don't design).

#### ② Back-of-envelope

```
   Services:          5,000 microservices, ~50K pods
   Logs per pod:      ~100 log lines/sec sustained
   Total log rate:    50K × 100 = 5M lines/sec  ← peak far higher
   
   Avg log line:      500 B JSON → 2.5 GB/sec raw → ~200 TB/day
   With compression   (~10×) → 20 TB/day → 7 PB/year
   
   Metrics:
   Per pod: ~1,000 unique series × 15s scrape → ~65 samples/sec/pod
   Total:   50K × 65 = 3M samples/sec
   Cardinality budget: ~10M active series total
   
   Traces:
   Requests/sec system-wide: 100K
   Spans/request average:    15 → 1.5M spans/sec
   With 1% head sampling: 15K spans/sec into permanent storage
   
   Query load:
   Dashboards: ~50K panels refreshing every 15s → ~3.5K queries/sec
   Ad-hoc:     spikes to 1K QPS during incidents
```

**Key insight:** the pipelines for **logs**, **metrics**, and **traces** have very different write/read patterns. Same collectors, three specialized backends.

#### ③ API

```
   Ingest (from agents on each host / pod)
   ────────────────────────────────────────
   POST /v1/logs      (batched, gRPC or HTTP)
     body: [{ ts, level, svc, trace_id, msg, fields{...} }, ...]
   
   POST /v1/metrics    (OpenTelemetry OTLP)
     body: [{ name, ts, value, labels{svc, endpoint, ...} }, ...]
   
   POST /v1/traces     (OTLP)
     body: [{ trace_id, span_id, parent_id, svc, name, start, end, attrs{...} }, ...]
   
   Query
   ─────
   GET /api/logs?q=level:ERROR AND svc:orders&from=...&to=...
   GET /api/metrics/query?promql=histogram_quantile(0.99, ...)
   GET /api/traces/{trace_id}
   
   Alerts
   ──────
   POST /alerts/rules  { promql: ..., threshold: ..., duration: ..., notify: [...] }
```

#### ④ Data models — three specialized stores

Each pillar has a different indexing strategy.

**Logs — inverted index for text search**

```
   Elasticsearch / OpenSearch / Loki
   ────────────────────────────────────
   
   Doc-shaped storage; inverted index per field.
   Time-based indices (index-per-day).
   Fields extracted from JSON logs.
   
   Query patterns:
     ▸ full-text ("timeout")
     ▸ field filter (level=ERROR)
     ▸ time-range
     ▸ aggregations (count by service)
   
   Loki variant: index only labels, store logs raw (chunked in object storage).
   Cheaper than Elasticsearch; less flexible for ad-hoc text.
```

**Metrics — time-series database (TSDB)**

```
   Prometheus / VictoriaMetrics / TimescaleDB / InfluxDB
   ─────────────────────────────────────────────────────
   
   Every metric = series = (name + label set) → time series of samples.
   Delta encoding + Gorilla compression → 1-2 bytes per sample amortized.
   
   Key insight: query "P99 latency of orders svc over 5 min" needs
     ▸ Fast range scan over one series (sequential disk read).
     ▸ Aggregation on the fly.
   
   Cardinality is the enemy:
     Every new label value → new series → RAM & disk cost.
     Runaway labels ("user_id=42") explode memory.
   Rule: labels are for LOW-cardinality dimensions only.
```

**Traces — spans stored keyed by trace_id**

```
   Jaeger / Tempo / Zipkin / Datadog APM
   ──────────────────────────────────────
   
   Store spans indexed by trace_id (KV lookup path).
   Optionally: secondary index by service + duration for search.
   
   Query patterns:
     ▸ "show me trace abc123" → single lookup.
     ▸ "slow traces in orders svc" → search by service + duration bucket.
   
   Tempo optimization: NO index by service/tag — pure trace_id lookup.
   Users find trace_id from logs / metrics / exemplars instead.
   Radically cheaper.
```

#### ⑤ High-level design

```
   Services (5K)                                Alerts / Dashboards
       │                                             ▲
       │ writes stdout / metrics / spans             │
       ▼                                             │
   ┌───────────────────────────────────────┐        │
   │ Agent (Fluentbit / OpenTelemetry      │        │
   │        collector) — per host           │        │
   │                                        │        │
   │  ▸ tail files, scrape /metrics         │        │
   │  ▸ batch + compress                    │        │
   │  ▸ tag with host / k8s labels          │        │
   │  ▸ ship via gRPC/HTTP                  │        │
   └────────────┬───────────────────────────┘        │
                │                                    │
                ▼                                    │
   ┌───────────────────────────────────────┐        │
   │  Ingest gateway (per pillar)          │        │
   │  Kafka-buffered                       │        │
   └────────────┬───────────────────────────┘        │
                │                                    │
     ┌──────────┼────────────┐                       │
     ▼          ▼            ▼                       │
   ┌───────┐ ┌────────┐  ┌────────┐                  │
   │ Logs  │ │Metrics │  │Traces  │                  │
   │ topic │ │ topic  │  │ topic  │                  │
   └───┬───┘ └───┬────┘  └───┬────┘                  │
       │        │            │                       │
       ▼        ▼            ▼                       │
   ┌──────┐ ┌──────┐    ┌────────┐                   │
   │ Log  │ │Metric│    │ Trace  │                   │
   │writer│ │writer│    │writer  │                   │
   └──┬───┘ └──┬───┘    └───┬────┘                   │
      │       │             │                        │
      ▼       ▼             ▼                        │
   ┌──────┐ ┌────────┐  ┌────────┐                   │
   │Elastic│ │TSDB   │  │Tempo/  │                   │
   │/ Loki │ │(Prom  │  │Jaeger  │                   │
   │       │ │ VM)   │  │        │                   │
   └───┬──┘ └───┬────┘  └───┬────┘                   │
       │       │            │                        │
       ▼       ▼            ▼                        │
   ┌──────────────────────────────────────┐          │
   │  Query gateway / API                 │──────────┘
   │  ▸ authz, routing per pillar         │
   │  ▸ unifies filters (svc, time)       │
   └──────────────────────────────────────┘
       │
       ▼
   ┌──────────────────────────────────────┐
   │  Warm / Cold tier (S3, Glacier)      │
   │  ▸ old logs / spans as object files  │
   │  ▸ TSDB downsampled aggregates       │
   └──────────────────────────────────────┘
```

**Traced flow — one log line, one metric, one span**

```
   Service emits ERROR log.
   Fluentbit tails, adds pod / cluster labels.
   Batches with 999 other lines.
   Ships to log-ingest gateway (gRPC).
   Gateway writes to Kafka logs topic (partitioned by service).
   Log writer consumer:
     ▸ Parses JSON.
     ▸ Adds trace_id from log body if present.
     ▸ Bulk indexes into Elasticsearch (today's index).
     ▸ Also writes gzip file to S3 (archive).
```

#### ⑥ Deep dives

**Deep dive A — Never lose data, never slow the app**

```
   Rules for the producer path:
   
   ▸ Producer code MUST be non-blocking. Fire-and-forget to a buffer.
   ▸ Bounded in-memory buffer per pod (say 8 MB).
     Overflow → drop oldest (log a metric about it).
   ▸ Agent (Fluentbit) tails app-written files or reads stdout.
     Position tracked in a checkpoint file so restarts don't re-ship.
   ▸ Agent → ingest is retriable; local disk buffer during network issues.
   ▸ Ingest → Kafka is durable buffer for downstream slowness.
   
   Never: synchronous HTTP from app to ingest. Ever.
```

**Deep dive B — Sampling strategies (traces)**

```
   1B spans/sec is impossible to store forever. Sample.
   
   Head sampling:
     Decision at start of trace: "keep this trace" (deterministic hash on trace_id).
     ✅ Simple, consistent across services.
     ❌ No knowledge of interesting events; may drop the one 500-error trace.
   
   Tail sampling:
     Buffer entire traces briefly (~30s) → keep based on:
       ▸ Any span had error.
       ▸ Trace duration > threshold.
       ▸ Rare route.
     ✅ Keeps the interesting traces.
     ❌ Complex; needs collector cluster with cross-service coordination.
   
   Typical: 1% head sampling + 100% tail for errors/slow traces.
```

**Deep dive C — Metrics cardinality control**

```
   Every unique label combo = new series = RAM + disk.
   
   Danger patterns:
     ▸ user_id label → 100M series. Bad.
     ▸ URL path with IDs → millions of series. Use route templates.
     ▸ Timestamp label → infinite series. Never.
   
   Defenses:
     ▸ Enforce label allowlist at ingest gateway.
     ▸ Reject metrics that exceed per-tenant cardinality budget.
     ▸ Auto-detect "runaway metric" — sudden spike in unique labels → alert + drop.
     ▸ Push high-cardinality data to logs/traces instead of metrics.
```

**Deep dive D — Storage tiers (hot / warm / cold)**

```
   Hot (0-7 days):
     ▸ On SSD in Elasticsearch / TSDB primary nodes.
     ▸ Full index, fast queries.
     ▸ Expensive per GB.
   
   Warm (7-30 days):
     ▸ On HDD / bigger cheaper nodes.
     ▸ Read-only, force-merged indices.
     ▸ Slower but still queryable.
   
   Cold (30+ days):
     ▸ S3 / GCS object storage.
     ▸ Files stored as compressed chunks.
     ▸ Query loads chunks on demand (Loki-style, Athena for exports).
     ▸ Cheapest.
   
   Lifecycle policies auto-move between tiers.
   
   Metrics-specific: downsample old data.
     Raw 15s samples for 7 days → 5m aggregates for 30d → 1h aggregates for 1y.
     Query planner picks tier based on time range.
```

**Deep dive E — Index management (Elasticsearch)**

```
   Time-based indices: logs-2025-11-30, logs-2025-12-01, ...
     Rotation daily so old indices can be deleted/archived cheaply.
   
   Sharding: N shards per index, spread across nodes.
     Rule of thumb: 20-50 GB per shard.
     Too few shards → hot node.
     Too many → coordination overhead.
   
   Replicas: 1-2 for HA.
   
   Rollover: automatic when index reaches size or age threshold.
   ILM (Index Lifecycle Management) automates move to warm/cold + delete.
```

**Deep dive F — Query patterns per pillar**

```
   Logs
   ────
   "Show me error logs from orders-svc in the last hour containing 'timeout'."
     ▸ Field filter + time-range + text search.
     ▸ Uses inverted index; returns most recent N.
   
   Metrics
   ───────
   "P99 latency of /checkout over last 5 min."
     ▸ PromQL: histogram_quantile(0.99, rate(http_request_bucket[5m]))
     ▸ Range scan on histogram series; O(N samples).
     ▸ Cache aggregated recent-window results for dashboards.
   
   Traces
   ──────
   "Show trace where the frontend call had a 5s error."
     ▸ User finds trace_id in error log or exemplar in metrics.
     ▸ Single trace_id lookup in Tempo.
     ▸ Or filtered search in Jaeger by service + duration.
```

**Deep dive G — Correlation across pillars**

```
   Every log line, metric sample, and span carries:
     ▸ service, environment, cluster
     ▸ trace_id (if applicable)
     ▸ (optional) exemplar span_id embedded in histogram buckets
   
   User debug flow:
     Alert: P99 spike on orders-svc.
     Click through to logs filtered by (svc=orders, time_range).
     Find ERROR line with trace_id.
     Jump to trace → see which downstream call was slow.
   
   "3 pillars" become "one debugging story" if the correlation ids match.
```

**Deep dive H — Alerting**

```
   Rule:
     alert: HighLatency
     expr:  histogram_quantile(0.99, ...) > 1
     for:   5m
     annotations: runbook=...
   
   Rule evaluator (Prometheus Alertmanager pattern):
     Every N sec: evaluate expr.
     If true for `for` duration → fire.
     Deduped + grouped → sent to notification system (14.12).
   
   Multi-window multi-burn-rate alerts (SLO):
     Alert if 5m burn rate > 14× budget AND 1h burn rate > 6×.
     Reduces false positives from short spikes.
```

**Deep dive I — Multi-tenant isolation**

```
   Teams share the platform → one noisy team can't break others.
   
   Ingest quotas per tenant (bytes/sec, series-count).
   Storage quotas per retention tier.
   Query quotas — max concurrent, max scan size, rate limit.
   
   Chargeback: bill tenants by ingest volume + retention footprint.
```

**Deep dive J — Backpressure & drops**

```
   Under overload, prefer dropping newest low-value data to blocking.
   
   Priority tiers:
     ▸ Alerts source metrics (system health) → never drop.
     ▸ Application logs → drop lowest-severity first.
     ▸ Trace samples → drop non-error head samples first.
   
   Feedback to producers:
     ▸ Return 429 with Retry-After.
     ▸ Agent slows ingestion; buffers to disk.
     ▸ Metrics on drops so operators see they're happening.
```

**Deep dive K — Cost governance**

```
   Telemetry costs 5-30% of infra bill. Controls:
   
   ▸ Log level default INFO, DEBUG only in troubleshooting.
   ▸ Drop verbose fields at ingest via a filter rule.
   ▸ Kill high-cardinality metrics.
   ▸ Downsample old metrics.
   ▸ Retention tiers.
   ▸ Cost dashboards per team.
```

**Deep dive L — Delivery guarantees**

```
   Producers → agent:        best-effort (buffered, may drop).
   Agent → Kafka ingest:      at-least-once (retries + on-disk buffer).
   Kafka → writer → storage:  at-least-once (dedupe by event ID if needed).
   Storage → query:           strong read of committed data.
   
   Duplicates are usually harmless in logs (aggregation is idempotent
   for counts) but painful for exactly-once metrics accounting → dedupe
   by (svc, ts, series, hash) at write time.
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Ingest gateway CPU** — batch harder, compress at agent, more Kafka partitions.
- **Elasticsearch shard count** explosion → move to Loki-style architecture (labels indexed, logs in object storage).
- **Metrics cardinality** — enforce aggressively; sample high-cardinality dims to logs/traces.
- **Query fan-out** across shards → parallel query engines, aggressive caching of dashboard queries.

**Tradeoffs to state out loud**
- **Elasticsearch (rich search, expensive) vs Loki (cheap, label-indexed)** — pick based on how often ad-hoc full-text matters.
- **Head sampling vs tail sampling** — head is simple, cheap; tail keeps interesting traces at coordination cost.
- **Retention length vs cost** — nobody looks at 6-month-old logs; keep aggregates, drop raw.
- **Fidelity vs cost** — dropping 90% of DEBUG logs is fine 99% of the time; expensive 1% of the time.
- **Central platform vs per-team stacks** — central: easier governance, correlation; per-team: no noisy-neighbor risk but duplicated cost.

**Interview one-liner**
> "A logging / metrics / tracing platform is three specialized pipelines sharing collectors and a query surface. Buffer with Kafka at ingest, specialize storage per pillar (inverted index for logs, TSDB for metrics, KV by trace_id for traces), tier hot→warm→cold, sample and cap cardinality relentlessly, and correlate everything by trace_id so the three pillars become one debugging story."

---

### 14.19 Design an ad click aggregator / real-time analytics pipeline (Google Ads, Meta Ads)

The "count billions of events per hour, in near-real-time, exactly-once, for advertiser billing" problem. Interview signal comes from **windowing + watermarks**, **exactly-once semantics for money**, **Lambda vs Kappa architecture**, and **fraud filtering**.

#### ① Requirements

**Functional**
- Track two event types: **impression** (ad shown) and **click** (ad clicked).
- Real-time counters: clicks/impressions **per ad, per minute, per hour, per day**.
- Advertiser dashboards refreshing every ~1 minute.
- Historical queries: last 90 days, arbitrary time range, grouping by campaign / geo / device.
- **Billing accuracy** — advertisers get charged from these numbers → dedup + reprocess.
- **Fraud filtering** — bot clicks, duplicate clicks from same user, click-farms.

**Non-functional**
- **Ingest** — 1M events/sec average, 10M/sec peak.
- **Freshness** — real-time counters within ~30 sec of the event.
- **Correctness** — billing off by more than 0.1% is a real problem.
- **Availability** — never lose events under overload.
- **Reprocessability** — if a bug is found in the pipeline, reprocess last N days.
- **Retention** — raw events ~90 days; aggregates forever (cheap).

**Out of scope**
- Ad auction / bidding (that's a whole other system).
- ML click-prediction models.

#### ② Back-of-envelope

```
   Traffic:
     Impressions/day:  10B → ~115K/sec avg, ~350K/sec peak
     Clicks/day:       ~200M (CTR ~2%) → ~2K/sec avg, ~20K/sec peak
     Combined events:  ~500K/sec peak sustained
   
   Event size:  ~300 B (event_id, ad_id, user_id, ts, geo, device, ...)
   Bandwidth:   500K × 300 B = 150 MB/sec = ~1.2 Gbps.
   
   Storage:
     Raw events (7-90d): 300 B × 10B/day = 3 TB/day raw → ~250 TB (90d) compressed.
     Aggregates: tiny relative to raw. Kept forever.
   
   Cardinality:
     ~10M active ads at any time.
     Per-minute buckets: 10M × 1440 = 14.4B rows/day theoretical.
       In practice, most ads have zero impressions in a given minute — store sparse.
```

**Key insight:** two orthogonal problems — **massive write throughput with correctness** for real-time, and **cheap batch reprocessing** for corrections. This is why **Lambda architecture** (batch + speed layer) exists.

#### ③ API

```
   Ingest (server-to-server, from ad-serving infra)
   ─────────────────────────────────────────────────
   POST /events (batched)
     body: [{
       event_type: "impression" | "click",
       event_id: uuid,                     ← idempotency key
       ad_id, campaign_id, advertiser_id,
       user_id (hashed), device_id,
       geo, timestamp_ms,
       page_url, referrer
     }]
     → 202 Accepted (fire-and-forget from ad server's POV)
   
   Query (advertiser dashboards, billing)
   ──────────────────────────────────────
   GET /analytics/query
     params: metric=clicks|impressions|ctr|spend,
             ad_id or campaign_id,
             from, to,
             group_by=[minute|hour|day, geo, device]
     → { time_series: [ {ts, value}, ... ] }
   
   Billing (invoice generation)
   ────────────────────────────
   GET /billing/{advertiser_id}?period=2025-11
     → committed daily totals from batch layer
```

#### ④ Data model

Split by lifecycle: raw ingestion → real-time aggregates → batch-committed aggregates.

```
   Raw events (Kafka + object storage sink)
   ─────────────────────────────────────────
   Kafka topics: impressions, clicks (partitioned by ad_id)
   Retention:    7-14 days for reprocessing.
   Sink to S3 (Parquet, partitioned by date/hour) — retained 90 days.
   
   Real-time aggregates (KV / TSDB — Cassandra, Druid, Pinot)
   ──────────────────────────────────────────────────────────
   Rowkey:  ad_id + minute_bucket
   Cols:    impressions, clicks, unique_users_hll, spend_estimate
   
   Also: (campaign_id + minute_bucket), (advertiser_id + hour_bucket).
   
   Batch-committed aggregates (Data warehouse — BigQuery, Snowflake, Iceberg)
   ─────────────────────────────────────────────────────────────────────────
   Partition: date
   Columns:   ad_id, campaign_id, advertiser_id, hour, geo, device,
              impressions, clicks, unique_users, filtered_clicks (fraud),
              spend_final
   
   These are the SOURCE OF TRUTH for billing.
   
   Dedup ledger (KV, TTL 24h)
   ──────────────────────────
   event_id → seen_ts
   Prevents at-least-once from becoming "at-least-twice" for the same event.
```

#### ⑤ High-level design — Lambda architecture

**Two paths from raw events:**
- **Speed layer** — approximate, seconds-fresh, powers dashboards.
- **Batch layer** — exact, hourly-fresh, powers billing.

Both write to the same query surface; queries merge.

```
   Ad servers (thousands)
       │
       │ POST /events (batched, gRPC)
       ▼
   ┌───────────────────────────┐
   │  Ingest gateway            │
   │  ▸ validate               │
   │  ▸ enrich (geo lookup)     │
   │  ▸ dedupe by event_id      │
   │  ▸ push to Kafka           │
   └────────────┬──────────────┘
                │
                ▼
   ┌───────────────────────────┐
   │  Kafka: events             │
   │  partitioned by ad_id      │
   │  RF=3, 7-day retention     │
   └────────────┬──────────────┘
                │
       ┌────────┴─────────────┐
       │                      │
       ▼                      ▼
   ┌────────────────┐   ┌────────────────────────────┐
   │  Speed layer   │   │  Batch layer               │
   │  (Flink /      │   │  (Spark / Beam every hour) │
   │   Kafka Streams│   │                             │
   │   / Samza)     │   │  Reads raw from S3.        │
   │                │   │  Applies fraud filters.    │
   │  ▸ window      │   │  Exact aggregates.          │
   │  ▸ dedup       │   │  Writes warehouse.          │
   │  ▸ aggregate   │   │                             │
   │  ▸ emit        │   │  Reprocessable — safe rerun.│
   └───────┬────────┘   └──────────────┬─────────────┘
           │                            │
           ▼                            ▼
   ┌────────────────┐            ┌────────────────┐
   │ Real-time      │            │ Data warehouse │
   │ store (Druid   │            │ (BigQuery /    │
   │ or Pinot or    │            │ Snowflake /    │
   │ Cassandra)     │            │ Iceberg)       │
   └────────┬───────┘            └───────┬────────┘
            │                            │
            └────────────┬───────────────┘
                         │
                         ▼
   ┌──────────────────────────────────────────┐
   │  Query gateway                            │
   │  Merges speed (last hour) + batch (older) │
   └──────────────────────────────────────────┘
                         │
                         ▼
                   Dashboards / Billing
   
   Separate path: Ad servers also write raw events to S3 archive
   (via Kafka Connect) → source of truth for reprocessing.
```

**Traced flow — one click event**

```
   1.  User clicks. Ad server: builds event with event_id = UUID.
   2.  Batched to ingest gateway (100 events / 200 ms).
   3.  Gateway:
         ▸ Redis SETNX dedup:{event_id} → new? proceed.
         ▸ Enrich: user's geo, device parse.
         ▸ Publish to Kafka topic "clicks", partition by hash(ad_id).
       Returns 202 immediately.
   4.  Speed layer (Flink):
         ▸ Consumes with checkpointed offsets.
         ▸ Keyed by ad_id → tumbling window(1 min).
         ▸ On window close: emit { ad_id, minute, clicks_count, hll_unique_users }.
         ▸ Sink to Druid.
   5.  Kafka Connect: continuously writes raw events to S3 as Parquet.
   6.  Batch layer (Spark job hourly):
         ▸ Reads last hour's S3 partition.
         ▸ Full fraud detection + dedup.
         ▸ Writes committed aggregates to warehouse.
         ▸ Speed-layer numbers for that hour become "superseded".
   7.  Dashboard query: "last 6h clicks by ad_id":
         ▸ Batch layer for hours -6 to -2.
         ▸ Speed layer for hours -1 to now.
         ▸ Merge, return.
```

#### ⑥ Deep dives

**Deep dive A — Exactly-once vs at-least-once for billing**

```
   For billing, duplicates or drops are unacceptable.
   
   Layered defense:
     ▸ event_id UUID at source. First line of defense.
     ▸ Ingest gateway: Redis dedup (Section 8.6, 10.4).
     ▸ Kafka producer: enable.idempotence=true, transactional producer.
     ▸ Flink checkpoints + two-phase commit sink → exactly-once into Druid.
     ▸ Batch layer: idempotent overwrite by (date, hour) partition.
   
   The reality:
     Speed layer = at-least-once + idempotent dedup → effectively-once.
     Batch layer = deterministic reprocess → exactly-once for billing.
   
   Billing runs from the BATCH layer only, never the speed layer.
   The speed layer is for advertiser dashboards, not invoices.
```

**Deep dive B — Windowing & watermarks**

```
   Events arrive out of order (mobile networks, retries, clock skew).
   
   Event time vs processing time:
     event.timestamp (when it happened) != Kafka arrival time.
   
   Tumbling window by event time:
     Window [12:00, 12:01) accumulates events with ts in that range.
     Emit when watermark passes 12:01.
   
   Watermark:
     A promise: "no more events with ts < W will arrive."
     Set as: max_event_ts_seen - allowed_lateness (say 30s).
   
   Late arrivals:
     ▸ Within lateness (say 30s): update window.
     ▸ Beyond lateness: emit side-output; batch layer picks them up.
   
   Interview trick: emphasize event time, not processing time.
```

**Deep dive C — Deduplication at scale**

```
   500K events/sec × TTL 24h ≈ 43B events to dedupe.
   
   Naive Redis map: expensive.
   
   Design:
     ▸ Shard Redis by hash(event_id).
     ▸ Bloom filter per shard as fast-negative check.
     ▸ Confirmed miss → Redis SETNX with TTL.
   
   At Flink layer:
     Keyed state per (ad_id, event_id) with TTL → drops duplicates
     that leaked past ingest dedup.
   
   Layered dedup is fine — cost is cheap; correctness matters.
```

**Deep dive D — Fraud & bot filtering**

```
   Signals:
     ▸ Multiple clicks same user_id same ad within N sec.
     ▸ Impossible click rate per IP.
     ▸ Known bot User-Agents / data center IPs.
     ▸ Missing prior impression for the click (clicks without impression).
     ▸ Behavioral: click without dwell / mouse movement.
     ▸ ML model score (offline features).
   
   Speed layer: cheap rule-based filters (drop obvious fraud immediately).
   Batch layer: heavier ML + cross-event correlation (fingerprinting a
                click-farm across accounts).
   
   Batch layer's "filtered_clicks" column is the authoritative billable count.
   
   Never bill on speed-layer numbers. Ever.
```

**Deep dive E — Kappa architecture — the simpler alternative**

```
   Lambda: two pipelines (batch + speed), two codebases, two sources of truth.
   Painful: any bug in one path but not the other → data divergence.
   
   Kappa: one pipeline, always streaming.
     Reprocess = replay Kafka from the beginning.
     Requires:
       ▸ Retention long enough to reprocess (weeks-months, or S3 tiered).
       ▸ Stream processor that can handle full-history replay.
       ▸ Idempotent sinks (upsert-by-(ad_id, minute)).
   
   Modern trend: Kappa via Flink + Iceberg. Simpler; requires disciplined engineering.
   
   Interview:
     Mention both. Say "Lambda is the safe interview answer; Kappa is where
     the industry is moving with better tooling."
```

**Deep dive F — Real-time store choice**

```
   Druid / Pinot / ClickHouse:
     ▸ Columnar, purpose-built for OLAP.
     ▸ Sub-second queries on billions of rows.
     ▸ Real-time ingestion with offset-tracked consumers.
   
   Cassandra with wide-column time-buckets:
     ▸ Simpler ops, no OLAP engine.
     ▸ Query patterns must be known upfront (denormalize).
     ▸ Fine for point lookups; group-by aggregations are harder.
   
   Trade: Druid gives you SQL-ish ad-hoc queries; Cassandra gives you brute simplicity.
```

**Deep dive G — Hot ads (skew)**

```
   99% of clicks go to top 1% of ads (Super Bowl ads, viral).
   Partitioning by ad_id → one partition burns.
   
   Fixes:
     ▸ Sub-partition hot ads: ad_id + user_id_bucket (0..15) → 16 partitions per hot ad.
     ▸ Merge back at aggregation time.
     ▸ Local pre-aggregation in the collector → send only per-minute deltas.
   
   Auto-detect hot keys → dynamic re-keying.
```

**Deep dive H — Approximate structures**

```
   Unique users per ad → cost of full set is prohibitive.
   Use HyperLogLog (HLL):
     ▸ Merges — count(HLL_A ∪ HLL_B) computable from stored HLLs.
     ▸ ~1% error, ~16 KB per structure.
   
   Top-K queries (top ads by CTR):
     Count-Min Sketch + Space-Saving algo.
   
   These trade tiny accuracy for massive memory savings — critical at PB scale.
```

**Deep dive I — Reprocessing / backfill**

```
   Bug found: fraud filter had wrong threshold for the last 3 days.
   
   Batch job:
     ▸ Run Spark over S3 partitions (date=2025-11-28..2025-11-30).
     ▸ Applies fixed filter.
     ▸ Writes to warehouse with idempotent overwrite (partition-by-date).
   
   Speed layer:
     Rewinds Kafka to the affected time, replays into a shadow topic.
     Cutover once caught up.
   
   This is why Kafka retention + S3 archive matter more than clever real-time tricks.
```

**Deep dive J — Multi-tenant advertiser dashboards**

```
   1M advertisers, each viewing their own data.
   
   Query authz: filter by advertiser_id in every query.
   Rate limit per advertiser.
   Cache per (advertiser_id, dashboard_id) for common windows.
   
   Per-advertiser quotas on query complexity (max scanned rows).
```

**Deep dive K — Data pipeline reliability**

```
   Every stage must be:
     ▸ Idempotent (safe to reprocess).
     ▸ Checkpointed (know where it left off).
     ▸ Observable (metrics on lag, errors, throughput).
   
   Kafka lag alert → speed-layer falling behind.
   S3 write age alert → connector stuck.
   Spark job SLA miss → batch-layer late.
   
   SLIs:
     ▸ Ingest → dashboard latency P99.
     ▸ Batch job completion time.
     ▸ Reconciliation delta: speed layer vs batch layer for same window.
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Ingest gateway CPU** — batch harder, push more to agent-side.
- **Kafka partitions** — cap around ~10K per cluster; scale to more clusters (per region, per event type).
- **Real-time OLAP node fan-out** — cache dashboards, use materialized rollups.
- **S3 partition explosion** — file compaction jobs to keep file count manageable.

**Tradeoffs to state out loud**
- **Lambda vs Kappa** — two paths safe but complex; Kappa simpler but demands strong tooling.
- **Real-time freshness vs correctness** — speed layer approx, batch exact.
- **Exactly-once vs cost** — full EOS is expensive; effectively-once via dedup + idempotent sink is usually enough.
- **Approximate structures (HLL, CMS) vs exact** — pay tiny accuracy for huge memory savings.
- **Store raw forever vs aggregate + drop** — raw is expensive; aggregates are cheap and enough for most queries.

**Interview one-liner**
> "Ad click aggregation is a two-headed pipeline: a speed layer for near-real-time dashboards, a batch layer as the billing source of truth. Kafka + S3 archive make everything reprocessable, layered dedup gives effectively-once, watermarks + event-time windows handle out-of-order events, and approximate structures (HLL) tame the cardinality. Billing always reads batch, never speed."

---

### 14.20 Design a recommendation system (YouTube / Netflix / Instagram / Amazon)

The "personalized top-N in under 200ms from a catalog of millions" problem. Interview signal comes from the **two-stage architecture (candidate generation → ranking)**, **feature store**, **train / serve split**, and how to handle **cold start** + **feedback loops**.

You don't need to design ML models. You need to design the **system** around them.

#### ① Requirements

**Functional**
- For a user, return top-N items (videos, products, posts) they'll likely engage with.
- Support multiple surfaces: home feed, "related items", "you might also like".
- Learn from user actions: clicks, views, likes, purchases, dwell time.
- Adapt over time as user's taste evolves.
- Business rules: promoted items, freshness boosts, diversity constraints, avoid duplicates.

**Non-functional**
- **Latency P99 < 200 ms** for a home-feed request.
- **Catalog size** — 100M items.
- **User base** — 500M DAU.
- **Freshness** — new items surfacing within hours; user actions influencing next request.
- **Availability** — degrade gracefully to trending / popular items on model failure.

**Out of scope**
- ML model architectures (assume off-the-shelf: two-tower, DLRM, transformers).
- A/B testing framework internals.

#### ② Back-of-envigualope

```
   Users:                   500M DAU
   Home-feed requests/day:  ~10 per DAU = 5B/day
   Requests/sec avg:        5B / 86,400 ≈ 58K/sec
   Peak (3×):               ~180K/sec
   
   Catalog:      100M items, ~2 KB metadata each = 200 GB
   Embeddings:   128-dim float per item = 512 B → 50 GB (index + serving)
                 Same per user (users have a live embedding) → 250 GB
   
   Feedback events (clicks / views / likes):
     Per DAU: ~200 events/day → 100B events/day system-wide
     Peak: ~2M events/sec
   
   Query budget breakdown for 200 ms P99:
     Candidate generation: ~50 ms
     Feature fetch:        ~20 ms
     Ranking (model inf):  ~80 ms
     Filtering / dedup:    ~10 ms
     Serialize + network:  ~40 ms
```

**Key insight:** you can't score 100M items in 200 ms. **Two stages:** cheap candidate generation prunes to a few thousand; expensive ranker scores those.

#### ③ API

```
   GET /recommend
     params:
       user_id
       surface: "home_feed" | "related" | "cart_upsell"
       context: { device, geo, time_of_day, session_events[] }
       count: 20
     → {
         items: [ {item_id, score, reasons[]}, ... ],
         request_id                       ← used for feedback attribution
       }
   
   POST /feedback
     body: {
       user_id, request_id,
       events: [{item_id, event_type, ts, value}]
     }
     → 202
```

The `request_id` links what was **shown** to what was **clicked** — feeds the training pipeline.

#### ④ Data model

Split by lifecycle: catalog → features → embeddings → user history → serving state.

```
   Catalog (Postgres or search index)
   ──────────────────────────────────
   item_id | title | desc | category | tags | created_at | is_active | ...
   
   Item features (Feature store — offline + online)
   ────────────────────────────────────────────────
   Offline: warehouse table
     item_id → { avg_ctr_30d, popularity_score, price_range, video_length, ... }
   Online: KV (Redis / Cassandra / DynamoDB)
     item:{id} → same features, low-latency lookup at serving time.
   
   User features
   ─────────────
   Offline (Snowflake / BigQuery):
     user_id → aggregated 30d behaviors, demographics, segments.
   Online (Redis):
     user:{id} → live session state, last-N interactions,
                  updated embedding, current context.
   
   Embeddings (vector DB)
   ──────────────────────
   item_id → 128-dim float vector
   user_id → 128-dim float vector
   
   ANN index (FAISS / ScaNN / Milvus / Pinecone) for fast nearest-neighbor.
   
   Feedback events (Kafka + warehouse)
   ────────────────────────────────────
   Impression events (was shown), interaction events (clicked, liked, purchased).
   
   Model registry
   ──────────────
   model_name / version → binary artifact + metadata.
   Router config: which surface uses which model.
```

#### ⑤ High-level design — the two-stage funnel

```
                    User request (user_id, context)
                                │
                                ▼
   ┌─────────────────────────────────────────────────────┐
   │                                                     │
   │   ①  Candidate generation                           │
   │       Cheap. Prune 100M → ~1000 candidates.         │
   │                                                     │
   │   ┌───────────────────────────────────────────┐    │
   │   │                                           │    │
   │   │  ▸ ANN vector search (two-tower model)    │    │
   │   │     nearest N items to user embedding     │    │
   │   │                                           │    │
   │   │  ▸ Collaborative filtering                 │    │
   │   │     "users like you also viewed"          │    │
   │   │                                           │    │
   │   │  ▸ Content-based (tag/topic match)         │    │
   │   │                                           │    │
   │   │  ▸ Trending / popular fallback             │    │
   │   │                                           │    │
   │   │  ▸ Rules-based (subscriptions, business)  │    │
   │   │                                           │    │
   │   │  UNION → dedupe → ~1000 candidates         │    │
   │   └───────────────────────────────────────────┘    │
   │                       │                             │
   │                       ▼                             │
   │   ②  Ranker                                          │
   │       Expensive DL model. Score all ~1000.          │
   │                                                     │
   │   ┌───────────────────────────────────────────┐    │
   │   │  Fetch features per candidate:            │    │
   │   │    ▸ item features (Redis)                │    │
   │   │    ▸ user features (Redis)                │    │
   │   │    ▸ cross features (user × item)          │    │
   │   │  Model inference:                          │    │
   │   │    p(click | user, item, context)         │    │
   │   │    p(watch time), p(purchase), ...         │    │
   │   │  Composite score                          │    │
   │   └───────────────────────────────────────────┘    │
   │                       │                             │
   │                       ▼                             │
   │   ③  Post-processing                                 │
   │       Business rules + diversity + freshness.       │
   │                                                     │
   │   ┌───────────────────────────────────────────┐    │
   │   │  ▸ Filter already-seen (last 7d)           │    │
   │   │  ▸ Blocklist                               │    │
   │   │  ▸ Diversify (max 3 items per creator)     │    │
   │   │  ▸ Business boost (promoted items)         │    │
   │   │  ▸ Take top N                              │    │
   │   └───────────────────────────────────────────┘    │
   │                       │                             │
   └───────────────────────┼─────────────────────────────┘
                           ▼
                     Response to user
```

**Serving architecture**

```
   Client
     │
     ▼
   ┌────────────────────────────────┐
   │  Recommendation gateway        │
   └────────────────┬───────────────┘
                    │
        ┌───────────┼───────────┐
        ▼           ▼           ▼
   ┌─────────┐ ┌─────────┐ ┌─────────┐
   │Candidate│ │Feature  │ │Ranking  │
   │service  │ │store    │ │service  │
   │(vector  │ │(Redis)  │ │(TF-     │
   │ANN,     │ └─────────┘ │Serving/ │
   │Redis)   │             │Triton)  │
   └────┬────┘             └────┬────┘
        │                       │
        ▼                       ▼
   ┌─────────────────────────────────┐
   │ Post-processor + business rules │
   └──────────────┬──────────────────┘
                  ▼
             Response
   
   Async paths:
      Client → POST /feedback → Kafka → training + real-time features
```

**Traced flow — one recommendation request**

```
   1. Client → /recommend { user_id, surface=home_feed, context }
   
   2. Gateway:
      ▸ Fetch user's live features (Redis) — recent events, embedding.
   
   3. Candidate generation (in parallel):
      ▸ ANN query on user embedding → 500 similar items.
      ▸ Collaborative filter lookup → 200 items.
      ▸ Trending items in user's segment → 200 items.
      ▸ Union → 800 candidates after dedup.
      Budget: 50 ms.
   
   4. Feature fetch:
      ▸ Batch MGET from Redis for 800 items.
      Budget: 20 ms.
   
   5. Ranker (TF-Serving / Triton):
      ▸ Single batched call, 800 candidates.
      ▸ Model outputs score per (user, item).
      Budget: 80 ms.
   
   6. Post-processing:
      ▸ Filter already-seen (Bloom of last 7d impressions).
      ▸ Diversify (limit per creator).
      ▸ Take top 20.
      Budget: 10 ms.
   
   7. Return to client + emit "impression" events to Kafka.
```

#### ⑥ Deep dives

**Deep dive A — Candidate generation with ANN**

```
   Problem: user embedding is a 128-dim vector. Find nearest N items
            from 100M vectors — in < 50 ms.
   
   Approach:
     ▸ Precomputed item embeddings (batch job from two-tower model).
     ▸ Stored in vector DB with an ANN index (FAISS, ScaNN, HNSW).
     ▸ Query = k-NN with cosine or dot-product similarity.
   
   HNSW (Hierarchical Navigable Small World):
     Layered graph; upper layers are sparse, bottom is dense.
     Query walks from top → drills down. O(log N) hops.
   
   Sharding: index sharded by hash(item_id) or by item category.
   Serving: dedicated pods; embeddings kept in RAM.
```

**Deep dive B — The feature store**

```
   Same features used at training AND serving. If they diverge, model
   silently underperforms. This is "training-serving skew."
   
   Design:
     Offline store (Snowflake / BigQuery):
       ▸ Training data pipeline reads from here.
       ▸ Batch feature pipelines (Spark) compute daily / hourly.
       ▸ Snapshotted historical values for point-in-time correctness.
     
     Online store (Redis / DynamoDB / Cassandra):
       ▸ Materialized latest values.
       ▸ Sub-millisecond lookups at serving.
       ▸ Streamed updates from Kafka for real-time features
         ("clicks in last 5 min").
   
   Feature engineering pipeline:
     Same code (or DSL) generates both.
     Feast, Vertex Feature Store, Tecton — off-the-shelf platforms.
```

**Deep dive C — Ranker inference latency**

```
   Requirement: score 1000 candidates in ~80 ms.
   
   Techniques:
     ▸ Batching: single model call for all candidates → GPU parallelism.
     ▸ Model distillation: small student model at serving, big teacher for training.
     ▸ Model quantization (INT8 vs FP32).
     ▸ Two-stage ranker: cheap first-pass on 1000 → top 100 → deep model.
     ▸ Feature caching: cache "user tower" output for the session; only compute
       "item tower" per request.
     ▸ Dedicated GPU serving (Triton / TF-Serving).
   
   Autoscaling on QPS; separate model versions on canary pods.
```

**Deep dive D — Training / retraining pipeline**

```
   Retraining cadence:
     ▸ Daily: batch retrain on last 30d events.
     ▸ Hourly: incremental fine-tune for freshness.
     ▸ Real-time signals (event streams) may update embeddings via online SGD.
   
   Pipeline:
     Feedback events → Kafka → warehouse (with joined labels + features).
     Training job (Spark / Ray / Beam) samples data, trains, evaluates.
     Model artifact → registry.
     Canary deploy: 1% traffic → measure metrics vs baseline.
     Promote to full traffic if improved.
   
   Feature freshness matters: features used for scoring must match
   what training saw for the same (user, item, ts).
```

**Deep dive E — Cold start**

```
   New user:
     ▸ No history → can't personalize.
     ▸ Fallback: trending items, popular in user's country/language.
     ▸ Onboarding survey ("what are you interested in?").
     ▸ As they interact, blend personalized signal in.
   
   New item:
     ▸ No engagement history → not in embeddings.
     ▸ Fallback: content-based signals (tags, category, creator affinity).
     ▸ Bandit-style exploration: give new items to some users to gather signal.
     ▸ Once enough data → embedding trained on next batch.
   
   Cold start diagram:
   
     User has < N events?
       yes → mix 20% personalized + 80% popular
        │
        no → full personalized
   
     Item has < M impressions?
       yes → boost slightly to explore
        │
        no → normal scoring
```

**Deep dive F — Diversity & serendipity**

```
   Naive top-N by score → all similar items ("filter bubble").
   
   Post-processing techniques:
     ▸ MMR (Maximal Marginal Relevance): reward relevance, penalize similarity
       to already-picked items.
     ▸ Category caps: max 3 items per creator, per category.
     ▸ Injected diversity: guarantee at least 1 "explore" item (bandit).
     ▸ Reranking to satisfy business constraints
       (freshness boost, promoted items).
```

**Deep dive G — Feedback loop & attribution**

```
   Every response includes request_id.
   Client emits impression events with request_id when items become visible.
   Interactions (click / purchase / dwell) → same request_id.
   
   Attribution:
     For each impression, was it clicked? → positive label.
     Non-click → implicit negative (with sampling to avoid label imbalance).
   
   Delay handling:
     Purchase might happen 3 days after impression.
     Label window: keep events open for delayed attribution.
```

**Deep dive H — Online experimentation (A/B testing)**

```
   Every request tagged with experiment bucket.
   
   Bucketing:  hash(user_id + experiment_id) mod N.
   
   Different buckets get different:
     ▸ Candidate generation strategies.
     ▸ Ranker models.
     ▸ Business rules / diversity weights.
   
   Metrics measured per bucket:
     ▸ CTR, watch time, session length, retention.
   
   Guardrails: latency, error rate.
```

**Deep dive I — Real-time signals**

```
   User just watched a video → next request should reflect it.
   
   Path:
     Client → feedback event → Kafka → online feature store.
     User's "last 10 items viewed" cached in Redis → updated within seconds.
     Ranker features use this cache at serving time.
   
   Some systems recompute user embedding on the fly for high-fidelity personalization.
   Trade: latency vs freshness.
```

**Deep dive J — Fallbacks & availability**

```
   Ranker model down → serve candidate ordering (score by popularity).
   Vector DB slow → serve trending only.
   Feature store partial outage → skip missing features; model still works.
   
   Degradation ladder:
     Full personalized → shallow personalized → trending → cached popular list.
```

**Deep dive K — Filtering & business rules**

```
   ▸ Age gate (18+ content).
   ▸ Geo-restrictions (licensing).
   ▸ Blocklist (moderation).
   ▸ Frequency capping (don't show same item twice today).
   ▸ Ad slot rules (every 5th item is an ad, respecting user opt-out).
   
   Rule engine after ranking; must be fast (~10 ms budget).
```

**Deep dive L — Observability & experiment iteration**

```
   Metrics per model / experiment / user segment:
     ▸ CTR, dwell time, conversion, churn.
     ▸ Latency P99 per stage.
     ▸ Coverage: fraction of catalog getting recommended (avoid rich-get-richer).
     ▸ Diversity: unique creators per user per day.
   
   Model shadow evaluation: log predicted score of new model on live traffic
   without acting on it → compare offline.
   
   Feature debugging: for a given (user, item, request), reproduce features
   the model saw. Key for investigating "why did we recommend that?"
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **Ranker throughput** — bigger GPU cluster, model distillation, more aggressive caching.
- **Vector DB memory** — 100M embeddings × 512 B = 50 GB; sharding + quantization (product quantization) shrink further.
- **Feature store write rate** — sharded Redis / DynamoDB writes; batch streaming updates.
- **Feedback pipeline lag** — feature freshness suffers if Kafka backs up; scale consumers.

**Tradeoffs to state out loud**
- **Personalization vs coverage** — a strong ranker can trap users in a narrow bubble; enforce diversity.
- **Freshness vs stability** — fine-tuning hourly captures trends but risks noisy updates; daily retrain safer.
- **Speed vs quality** — deeper models cost latency; distillation or two-pass ranking trades off.
- **Explicit vs implicit signals** — likes are strong but sparse; views are dense but noisy.
- **Explore vs exploit** — always ranking the "best" starves discovery; inject bandit exploration.
- **On-device vs server** — small on-device rankers (Netflix, TikTok) reduce latency and privacy exposure; harder to update.

**Interview one-liner**
> "A recommendation system is a two-stage funnel: cheap candidate generation prunes 100M items to a few thousand via ANN + collaborative filtering, then a heavy DL ranker scores them using features from an online feature store. Post-processing enforces business rules and diversity. The whole thing lives inside a feedback loop where every impression and interaction retrains tomorrow's model."

---

### 14.21 Design a payment system / ledger (Stripe, PayPal, Uber Payments)

The "never lose a cent, always sum to zero, prove any balance from history" problem. Interview signal comes from **double-entry accounting**, **idempotency at every layer**, **saga-based orchestration across PSPs**, **reconciliation**, and **PCI-safe design**.

Money designs are graded not on cleverness but on **paranoia**.

#### ① Requirements

**Functional**
- **Charge** a customer's payment method (card / bank / wallet) for an order.
- **Refund** — partial or full.
- **Payouts** to merchants / drivers / users.
- **Balance queries** — real-time balance per account.
- **Statements / history** — auditable, exportable.
- **Multi-currency** — with FX rates.
- Support multiple PSPs (Stripe, Adyen, Braintree, bank rails) with routing.

**Non-functional**
- **Correctness** first. Duplicate charges, lost payments = business-ending.
- **Idempotent** — retries never double-charge.
- **Consistency** — the ledger is always internally consistent (debits = credits).
- **Auditability** — every entry immutable, every state derivable from events.
- **Availability** — degrade gracefully; queue writes rather than fail.
- **Latency** — charge P95 < 3 s (dominated by PSP call).
- **Compliance** — PCI-DSS, SOX, KYC/AML.

**Out of scope**
- Card issuing (that's a whole other Stripe product).
- Chargeback dispute UI.

#### ② Back-of-envelope

```
   Transactions/day:      10M (Uber-scale marketplace)
   Peak TPS:              ~1000 charges/sec
   Ledger entries:        each charge = 4-8 postings (customer, fee, tax, payout...)
                           → ~50M ledger entries/day
   
   Retention:             7 years for financial records (regulatory).
   Storage/year:          50M × 365 × 500 B = ~9 TB/year of ledger data.
   Small compared to raw event streams; must be flawless.
   
   PSP latency:           500 ms - 2 s for card auth. This dominates the charge path.
   Idempotency window:    24 h - 7 d per PSP conventions.
```

**Key insight:** the design isn't about speed — it's about **correctness under partial failure**. Every deep dive returns to "what if this step succeeded but we didn't hear the response?"

#### ③ API

```
   Payments (customer-facing)
   ──────────────────────────
   POST /payments
     headers: Idempotency-Key: <client-uuid>       ← REQUIRED
     body: {
       amount: 10000, currency: "USD",
       source: pm_token, customer_id,
       order_id, description,
       capture: true | false   ← auth-only vs auth+capture
     }
     → 201 { payment_id, status: "pending" | "succeeded" | "failed", ... }
   
   POST /payments/{id}/capture   { amount? }                    → 200
   POST /payments/{id}/refund    { amount, reason }             → 201 { refund_id }
   GET  /payments/{id}                                          → status, timeline
   
   Ledger (internal)
   ─────────────────
   POST /ledger/postings                             ← never called directly by clients
     body: {
       transaction_id, currency,
       entries: [
         { account_id, amount: +10000 (debit) },
         { account_id, amount: -10000 (credit) },
       ]
     }
     → invariant: sum(entries) = 0
   
   GET /accounts/{id}/balance
   GET /accounts/{id}/entries?from=...&to=...       ← statement
   
   Payouts
   ───────
   POST /payouts
     body: { destination, amount, currency, idempotency_key }
     → 201 { payout_id }
```

**The Idempotency-Key** is the single most important header in the whole system. Not optional.

#### ④ Data model — double-entry ledger

**The rule:** every transaction is expressed as one or more **postings**. Postings within a transaction must sum to zero.

```
   Charge Alice $100 for a $100 order + $2 fee → merchant:
   
   Transaction T1:
     Posting 1:  Alice_wallet             -100  (credit)   ← she paid
     Posting 2:  Merchant_receivable      +98   (debit)    ← merchant gets 98
     Posting 3:  Platform_fee_income      +2    (debit)    ← platform earns 2
                                          ────
                                            0             ← MUST sum to zero
```

**Schema**

```
   accounts
   ────────
   account_id       PK
   owner_id
   account_type     (customer_wallet, merchant_receivable, platform_income,
                     tax_liability, chargeback_reserve, ...)
   currency
   created_at
   
   transactions   (business-level events — one charge, one refund, ...)
   ─────────────
   transaction_id   PK
   type             (charge | refund | payout | fee | adjustment)
   status           (pending | posted | failed | reversed)
   external_ref     (order_id, PSP tx id)
   idempotency_key  UNIQUE
   created_at
   
   postings   (immutable, append-only)
   ────────
   posting_id       PK (monotonic)
   transaction_id   FK
   account_id       FK
   amount           (signed integer, minor units — cents/paise)
   currency
   posted_at        TIMESTAMP  (as-of business time)
   created_at       TIMESTAMP  (when the row was written)
   
   INVARIANT: for every transaction, SUM(amount) OVER (transaction_id) = 0.
```

**Balances are derived by convention:**

```
   balance(account_id) = SUM(amount) FROM postings WHERE account_id = ?
   
   Options:
     Option 1 — compute on read (correct, slow at scale).
     Option 2 — materialize balances table, update in same txn as postings.
     Option 3 — periodic snapshots + delta-since-snapshot (bank pattern).
```

Materialized balance table is the pragmatic default:

```
   balances
   ────────
   account_id       PK
   currency
   balance          (integer, minor units)
   last_posting_id  (for consistency checks)
   updated_at
```

Update the balance and insert postings **in the same DB transaction**. Postgres, MySQL, or CockroachDB are ideal — you need ACID.

#### ⑤ High-level design

**Two subsystems:**
1. **Ledger** — internal source of truth, ACID, small services.
2. **Payment orchestrator** — talks to external PSPs, handles the messy real world.

```
   Customer / Order service
        │
        │ POST /payments (Idempotency-Key)
        ▼
   ┌─────────────────────────────────────┐
   │  Payment API (idempotent)           │
   │  ▸ Check idempotency store          │
   │      already exists? return cached  │
   │  ▸ Validate & persist "pending"     │
   │  ▸ Start Payment Saga               │
   └──────────────┬──────────────────────┘
                  │
                  ▼
   ┌─────────────────────────────────────┐
   │  Payment Saga (orchestrator)         │
   │                                      │
   │  ① Reserve customer balance          │
   │  ② Call PSP (Stripe / Adyen)         │
   │  ③ On success:                       │
   │       POST postings to Ledger        │
   │       Mark transaction "posted"      │
   │  ④ On failure:                       │
   │       Compensate (release reserve)   │
   │       Mark "failed"                   │
   └──────────────┬──────────────────────┘
                  │
      ┌───────────┼───────────────┐
      ▼           ▼               ▼
   ┌───────┐  ┌────────────┐  ┌─────────────────┐
   │Ledger │  │PSP Adapter │  │Idempotency store │
   │(SQL)  │  │(Stripe /   │  │(Redis + DB)      │
   │       │  │Adyen /     │  └─────────────────┘
   │       │  │Bank rails) │
   └───┬───┘  └─────┬──────┘
       │            │
       │            ▼
       │      ┌─────────────────────┐
       │      │  PSP callbacks       │
       │      │  (webhook receiver)  │
       │      └───────┬─────────────┘
       │              │
       ▼              ▼
   ┌─────────────────────────────────────┐
   │  Reconciliation service              │
   │  ▸ Compare our ledger vs PSP reports │
   │  ▸ Detect and alert on drift          │
   └─────────────────────────────────────┘
   
   Async: postings → Kafka → data warehouse (reporting)
```

**Traced flow — one successful charge**

```
   1. Order svc → POST /payments { amount:10000, order:123 }
                  Idempotency-Key: idem-uuid-A
   
   2. Payment API:
      ▸ Redis SETNX idem:idem-uuid-A → new.
      ▸ Persist transaction (status = pending) in Postgres.
      ▸ Emit "PaymentInitiated" to Kafka.
      ▸ Return 202 { payment_id } to caller.
   
   3. Payment Saga worker picks up:
      ▸ Call Stripe /charges with same idempotency_key.
        Stripe deduplicates on their side too → safe to retry.
      ▸ Stripe returns success + tx_id.
   
   4. Post to ledger (same DB transaction):
      BEGIN;
        INSERT postings (customer_wallet -10000);
        INSERT postings (merchant_receivable +9800);
        INSERT postings (platform_fee +200);
        UPDATE balances for all 3 accounts;
        UPDATE transactions SET status='posted';
      COMMIT;
   
   5. Emit "PaymentSucceeded" to Kafka → notifications, order fulfillment.
   
   6. Later — Stripe sends webhook for the same charge.
      Webhook handler idempotently marks it received (no-op if already posted).
```

#### ⑥ Deep dives

**Deep dive A — Idempotency at every layer**

The core of payments.

```
   Layer 1 — Client idempotency key:
     Client generates a UUID for each user action ("Pay now" click).
     Retries reuse the same key.
     Server dedupes → returns the ORIGINAL response.
   
   Layer 2 — Payment API:
     Store (idempotency_key → transaction_id, response) with TTL 24h.
     Duplicate: return the stored response without re-executing.
   
   Layer 3 — PSP call:
     Pass the same idempotency key to Stripe.
     Stripe dedupes on their side.
     Prevents double-charging if we time out and retry.
   
   Layer 4 — Ledger postings:
     Each posting has transaction_id + posting_id (unique).
     Duplicate insert fails on PK constraint. Safe by design.
   
   Layer 5 — Webhook handler:
     PSP might deliver the same webhook multiple times.
     Dedupe by (psp_event_id) or by matching to our transaction state.
   
   Belt and suspenders. Always.
```

**Deep dive B — Saga for cross-system consistency**

```
   You can't run a single ACID txn across Postgres + Stripe.
   Use a saga (Section 10.7):
   
   Steps:
     ① Reserve balance (mark "pending charge" in ledger).
     ② Call PSP with idempotency key.
     ③ Post the completed entries.
   
   Compensations:
     If ② fails → release the reserve.
     If ③ fails after ② succeeded → we're mid-flight; PSP holds the money.
       Retry ③ indefinitely; alert if stuck > threshold.
   
   Orchestration:
     Temporal / Camunda / Axon — durable workflow engines.
     Or hand-rolled: state machine in Postgres, worker polls "next step".
```

**Deep dive C — Handling PSP failures**

The messy real world.

```
   Case 1: PSP returns success.
     Post to ledger. Done.
   
   Case 2: PSP returns explicit failure (card declined).
     Mark transaction failed. Release any reserve. Notify customer.
     No retry (business error, not transient).
   
   Case 3: PSP times out (unknown outcome).
     The dangerous one.
     Rules:
       ▸ Never assume failure. Money might have moved.
       ▸ Retry with the SAME idempotency key. Stripe returns the original result.
       ▸ If still ambiguous, poll PSP via GET /charges/{id}.
       ▸ Escalate to manual review after N failed polls.
   
   Case 4: PSP returns 5xx.
     Retry with backoff + jitter.
     Circuit-break on sustained failure.
     Fallback: route to secondary PSP if configured.
   
   Case 5: PSP webhooks are delayed / out of order.
     Webhook handler is idempotent; state transitions are guarded.
     Trust our current state; use webhook for confirmation not command.
```

**Deep dive D — Reconciliation**

```
   Every night (or hour), PSP sends a settlement file:
     "Here are all the transactions we processed for you today."
   
   Reconciler:
     For each PSP row, find matching internal transaction (by psp_tx_id).
     Flag differences:
       ▸ Missing in ours → we lost a webhook. Backfill.
       ▸ Missing in theirs → we posted but they didn't (rare, investigate).
       ▸ Amount mismatch → serious. Alert immediately.
     
   Log every discrepancy. Never auto-fix without human sign-off for money moves.
   
   Also reconcile against the bank (payouts side) — deposits should match.
```

**Deep dive E — Multi-currency & FX**

```
   Store amount in minor units (integer) — never floats. Ever.
   Store currency per posting.
   
   FX conversion is a business transaction:
     Charge $100 USD → credit merchant €92 EUR.
     
     Postings:
       customer_wallet   -10000 USD
       fx_holding_USD    +10000 USD
       fx_holding_EUR    -9200  EUR
       merchant_EUR      +9200  EUR
   
   FX rate captured at posting time. Reference in transaction metadata.
   FX P&L accounts for platform's exposure.
```

**Deep dive F — Refunds**

```
   POST /payments/{id}/refund { amount, idempotency_key }
   
   Rules:
     ▸ Refund amount ≤ original charge amount minus prior refunds.
     ▸ Call PSP refund with own idempotency key.
     ▸ Post reversal entries:
         merchant_receivable  -amount
         customer_wallet      +amount
         platform_fee (partial return per policy)
     
     Balance invariant preserved.
     Refunded transaction linked to original via refund_of=payment_id.
```

**Deep dive G — Chargebacks & disputes**

```
   Customer disputes charge with their bank → chargeback.
   
   PSP notifies via webhook: "chargeback opened for charge X."
   
   Handling:
     Move funds to a chargeback_reserve account (hold pending outcome).
     Notify merchant, collect evidence.
     PSP resolves after 30-90 days:
       ▸ Won: release from reserve, credit back to merchant.
       ▸ Lost: post reversal, deduct from merchant.
   
   Fraud reserve accounts on the ledger track this as ongoing exposure.
```

**Deep dive H — PCI-DSS compliance (never touch card numbers)**

```
   Golden rule: NEVER store card PAN in your systems.
   
   Design:
     ▸ Client-side JS (Stripe.js / Adyen Web Component) posts card directly to PSP.
     ▸ PSP returns a tokenized reference (pm_xxxxxx).
     ▸ Our server only sees the token.
     ▸ Card details never touch our servers → dramatically reduces PCI scope.
   
   For high-touch cases (recurring billing), store only the token.
   Vault / tokenization services: Basis Theory, VGS, PSP-native.
   
   Logs & traces: strictly scrub PAN, CVV, expiry.
```

**Deep dive I — Availability strategies**

```
   Payments must NOT go down when a PSP does.
   
   Multi-PSP routing:
     ▸ Primary: Stripe.
     ▸ Secondary: Adyen.
     ▸ Rules engine picks based on: cost, success rate by BIN, currency, geo.
     ▸ Failure of primary → auto-retry via secondary (with new idempotency key
       since it's a new PSP call, but linked to same internal transaction).
   
   Ledger writes should always succeed:
     ▸ If PSP is down, mark transaction "pending" and retry later.
     ▸ Don't reject the request unless internal validation fails.
   
   Read path:
     Balance queries always served from local ledger — never from PSP.
```

**Deep dive J — Event sourcing / audit trail**

```
   Every state change is an immutable event:
     PaymentInitiated → PSPCallStarted → PSPCallSucceeded → PostingsWritten → PaymentSucceeded
   
   Store events in Kafka + long-term storage.
   Current state derivable from event history.
   Enables:
     ▸ Full audit log for compliance.
     ▸ Time-travel debug ("what did we know at 03:12 UTC?").
     ▸ Replay to fix bugs and regenerate derived state.
```

**Deep dive K — Ledger consistency checks**

```
   Every posting insert requires: SUM(amount) OVER transaction = 0.
     Enforce with DB CHECK constraint via trigger, or app-level assertion in the txn.
   
   Nightly job:
     For each transaction: verify sum-to-zero.
     For each account: verify balance = SUM(postings.amount).
   
   Any drift → immediate alert. Freeze account. Human investigation.
   
   Every posting immutable — corrections done via reversing entries, not updates.
```

**Deep dive L — Rate limiting & fraud on the payment side**

```
   Excessive attempts per card / IP → block.
   Velocity checks: N charges from same source in T minutes → require MFA.
   Card testing: many small failed attempts → likely stolen-card testing.
   
   ML fraud score at charge time (features from card + device + geo + history).
   Score high → step-up auth (3-D Secure).
```

#### ⑦ Bottlenecks & tradeoffs

**What breaks first at 10× scale?**
- **PSP throughput / rate limits** — sharded API keys across PSPs, quotas per merchant.
- **Ledger writes** — SQL scale via sharding by account_id; for very high scale, purpose-built ledgers (TigerBeetle, Aptos-style).
- **Reconciliation window** — high volume PSP files break naive nightly jobs; incremental streaming reconciliation.
- **Kafka event volume** — partition ledger events by account_id; separate topics for high-volume vs audit.

**Tradeoffs to state out loud**
- **ACID vs scale** — traditional Postgres handles a lot; move to sharded/purpose-built ledgers only when forced.
- **Sync webhook vs poll** — webhooks are faster but delivery isn't guaranteed; always have poll fallback.
- **Single vs multi PSP** — multi = resilience + cost optimization + complexity; usually worth it.
- **Materialize balance vs compute** — materialize is fast but adds consistency requirements; compute is safe but slow.
- **Event sourcing everywhere vs state store** — full ES is powerful but heavy; hybrid (state + audit log) is more common.
- **Freeze on drift vs auto-correct** — always freeze. Money moves need human sign-off.

**Interview one-liner**
> "A payment ledger is a double-entry, immutable, ACID data store wrapped in a saga that talks to PSPs. Idempotency at every layer (client → API → PSP → ledger → webhooks). Reconciliation catches drift. PCI scope is minimized by never touching PAN. Reads always from local ledger; PSP calls only mutate. The whole design is optimized for correctness under partial failure, not for speed."

---

*Say "next" for 14.22 Order matching / stock exchange, or name a specific one.*
