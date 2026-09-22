# System Design — Messaging & Streaming

Section 10 of the study series. Continues from [02-databases.md](./02-databases.md).

Once you have more than one service, they need to communicate. **Synchronous** calls (REST, gRPC) couple them tightly. **Asynchronous** messaging decouples them in time, space, and failure mode. Section 10 is about async messaging — the platforms, the patterns, and the traps.

---

## 10. Messaging & Streaming

### 10.1 Why messaging exists — sync vs async

**The problem with sync everywhere**

```
   Order service ──sync HTTP──► Inventory ──sync──► Payment ──sync──► Email
   
   If Email is down → whole chain fails.
   If Payment is slow → Order thread blocks.
   To scale Email 10× → have to scale everyone.
   To add a "Loyalty points" step → change Order code.
```

Sync = tight coupling. Everyone must be up, fast, and known upfront.

**The async idea**

```
   Order service ──publish "OrderPlaced"──► [Broker]
                                              │
                             ┌────────────────┼────────────────┐
                             ▼                ▼                ▼
                         Inventory        Payment           Email
                         (consumes)       (consumes)        (consumes)
                         
   ✅ Order returns immediately after publishing.
   ✅ Email can be down for hours — messages wait in the queue.
   ✅ Add Loyalty later — just subscribe to same event, no Order change.
```

**Sync vs Async — head to head**

| Property | Sync (REST/gRPC) | Async (Queue/Bus) |
|---|---|---|
| Coupling | Tight (caller knows callee) | Loose (both know the topic) |
| Latency of caller | Waits for full chain | Returns after publish |
| Failure blast | Cascades upstream | Isolated per consumer |
| Ordering | Natural | Requires partitioning |
| Backpressure | Native (TCP) | Broker buffers, may drop |
| Debuggability | Easy (traces) | Harder (event flows) |
| Best for | User-facing reads, direct commands | Background work, integration, fan-out |

**When to reach for async**
- Fan-out to many consumers.
- Work that can be delayed (email, indexing, analytics).
- Smoothing bursty writes into steady processing.
- Cross-service integration in microservices.
- Any time "fire and forget" is acceptable.

**Interview one-liner**
> "Sync ties services together in time; async lets them fail, scale, and evolve independently. Use sync for user-facing commands, async for everything else."

---

### 10.2 The three shapes of async messaging

Three canonical patterns. Every messaging system is a variant of one.

**A. Point-to-point queue (work distribution)**

```
   Producer ──► [Queue] ──► Consumer 1
                        └─► Consumer 2
                        └─► Consumer 3
   
   Each message goes to ONE consumer (competing consumers).
   Used to distribute work across a pool of workers.
   
   Example: 100 image resize jobs, 10 workers → each worker gets ~10.
```

Classic queue semantics: **RabbitMQ, SQS, ActiveMQ**.

**B. Pub/Sub (broadcast to multiple subscribers)**

```
   Producer ──► [Topic] ──► Subscriber A  (all get every message)
                        ──► Subscriber B
                        ──► Subscriber C
   
   Each message goes to EVERY subscriber.
   Used for fan-out — many services react to the same event.
   
   Example: "OrderPlaced" → Inventory + Email + Analytics + Loyalty.
```

Classic pub/sub: **Redis Pub/Sub, SNS, NATS, MQTT**.

**C. Log / Stream (durable, replayable)**

```
                    ┌── Consumer A (offset=100, "live")
   Producer ──► [Append-only log] ── Consumer B (offset=42, catching up)
                    │                     
                    └── Consumer C (offset=0, replaying from start)
   
   Messages are STORED, indexed by position (offset).
   Consumers track their own position; multiple can read at different rates.
   
   Example: Kafka, Kinesis, Pulsar, EventStoreDB.
```

Streams are pub/sub **+ durable, replayable log**. Best of both worlds.

**The three shapes side-by-side**

```
   Queue                Pub/Sub               Log/Stream
   ──────────           ──────────            ──────────────────
   
   [-][-][-]           [-][-][-]              [0][1][2][3][4]...
                              ↘                        ↑     ↑
      ↓                      ↘  ↘                    A     B     C
   pop 1                   copy for all          each reads by offset
   → 1 consumer            → all subs             independently
   
   Message vanishes       Message vanishes       Message stays for TTL
   after processing       after fan-out          (days, weeks, forever)
```

**Interview one-liner**
> "Queues do work distribution (one message → one worker). Pub/sub fans out (one message → many subs). Streams are durable append-only logs — pub/sub with replay. Kafka is a stream; SQS is a queue; SNS is pub/sub."

---

### 10.3 Kafka — the reference stream platform

Kafka is worth its own section — it's the industry default for streaming, and interviewers love it.

**Core concepts**

```
   ┌─────────────────────────────────────────────────────────┐
   │  Broker cluster (many machines)                         │
   │                                                          │
   │   ┌────────── Topic: "orders" ─────────┐                │
   │   │                                     │                │
   │   │   Partition 0    Partition 1        │                │
   │   │   [0][1][2][3]   [0][1][2][3][4]    │                │
   │   │   ↑                                 │                │
   │   │   log is append-only; offsets       │                │
   │   │   uniquely identify records         │                │
   │   └─────────────────────────────────────┘                │
   │                                                          │
   └─────────────────────────────────────────────────────────┘
   
   Producer ──► partition (via key hash) ──► append at end
   
   Consumer ──► reads from committed offset ──► advances offset
```

**Partitions — the unit of parallelism**

```
   Topic "orders" with 3 partitions:
   
      Partition 0     Partition 1     Partition 2
      [k=A,B,C ...]   [k=D,E ...]     [k=F,G,H ...]
   
   Producer with key K → partition = hash(K) % 3
   
   Ordering guarantee:
     Within one partition: strict order.
     Across partitions:    no ordering guarantee.
   
   So all events for user_42 go to same partition → user's events in order.
```

**Consumer groups — the parallelism model**

```
   Topic has 3 partitions.
   
   Consumer group "billing" with 3 instances:
     Instance-1 → assigned partition 0
     Instance-2 → assigned partition 1
     Instance-3 → assigned partition 2
     
     Every message read exactly ONCE by the group (competing consumers).
   
   Consumer group "analytics" with 1 instance:
     Instance-1 → assigned all 3 partitions
     
     Reads INDEPENDENTLY of the "billing" group.
   
   Rule: partitions >= consumers  (else consumers sit idle)
```

**Offsets — the position bookmark**

```
   Log:  [0][1][2][3][4][5][6][7]
                         ↑
               committed offset = 5 for this consumer group
   
   Consumer reads 5, 6, 7 → commits new offset 8.
   
   On restart: resume from last committed offset.
   
   Reset options:
     earliest → replay from beginning (offset 0)
     latest   → skip to newest, ignore backlog
     specific → seek to a timestamp or offset
```

**Replication — how Kafka survives broker failure**

```
   Each partition has N replicas (typically 3).
   
   Partition 0:
     Leader:    Broker-A       (handles reads/writes)
     Follower:  Broker-B, Broker-C
   
   ISR (In-Sync Replicas): replicas caught up with the leader.
   
   Leader dies → controller elects new leader from ISR.
   
   Producer acks:
     acks=0  → fire and forget (may lose data)
     acks=1  → wait for leader ack (data may be lost if leader dies)
     acks=all → wait for all ISR (safest)
```

**Retention — how long messages live**

```
   Retention by time:  keep for 7 days
   Retention by size:  keep last 100GB
   Compacted topic:    keep only the latest value per key (like a KV store)
   
   Kafka is a durable log, not a queue. Old messages exist for replay.
```

**Kafka vs traditional broker (RabbitMQ)**

| Feature | Kafka | RabbitMQ |
|---|---|---|
| Model | Log-based | Queue-based |
| Retention | Days/weeks/forever | Until acked |
| Consumer speed | Independent, offset-tracked | Broker pushes |
| Throughput | Very high (millions/sec) | High (100k/sec) |
| Ordering | Per-partition | Per-queue |
| Complex routing | Limited | Rich (exchanges, bindings) |
| Best for | Streaming, event sourcing | Traditional messaging, RPC |

**Interview one-liner**
> "Kafka is a distributed, replicated, partitioned commit log. Producers append to partitions; consumers track offsets. Partitioning gives parallelism and per-key ordering. Consumer groups make it a competing-consumers queue AND a broadcast bus at the same time."

---

### 10.4 Delivery semantics — at-most, at-least, exactly-once

The single most important concept in async messaging. **What guarantee does the broker (or your code) provide?**

**At-most-once**

```
   Producer sends → fire and forget.
   Consumer processes → doesn't ack, or acks before processing.
   
   Result:  Every message delivered 0 or 1 times.
   Failure mode: message may be LOST.
```

Fast, cheap, but data can vanish. Use for metrics/telemetry where losing a data point is fine.

**At-least-once (the default in most systems)**

```
   Producer sends → retries until acked.
   Consumer processes → acks AFTER success.
   
   Result:  Every message delivered 1 or more times.
   Failure mode: message may be DUPLICATED.
```

The most common and practical setting. But: **consumers must be idempotent** because duplicates happen.

**Exactly-once — the holy grail**

```
   Every message delivered EXACTLY once.
   No loss, no duplicates.
   
   Requires: producer idempotence + transactional writes to broker
             + transactional read/process/commit on consumer.
```

Kafka supports it via:
- **Idempotent producer** — each producer + partition + sequence gets deduped by the broker.
- **Transactional API** — atomically write to multiple partitions AND commit consumer offsets.
- Only works **within Kafka**. External side effects (sending emails, calling HTTP APIs) can still duplicate.

**The "exactly-once" reality check**

```
   Consumer:
     ① Read message
     ② Do side effect (send email, charge card)
     ③ Commit offset
   
   Crash between ② and ③  → next start re-reads, does effect AGAIN.
   
   True exactly-once for external side effects requires:
     Idempotent side effects (safe to repeat), OR
     Transactional outbox (atomic with the DB write).
```

So in practice: **at-least-once + idempotent consumers ≈ exactly-once**.

**Delivery semantics comparison**

| Semantic | Guarantees | Cost | When to use |
|---|---|---|---|
| At-most-once | May lose | Cheapest, fastest | Metrics, non-critical logs |
| At-least-once | May duplicate | Standard | Almost everything (with idempotency) |
| Exactly-once | Neither | Complex, some overhead | Ledgers, payments, dedupe-critical |

**Idempotency — the way through**

Most robust systems pick "at-least-once + idempotent." Common idempotency techniques:

```
   Idempotency key:
     Every message has a unique ID.
     Consumer records processed IDs (in Redis/DB) → skip if seen.
   
   Natural idempotence:
     "Set balance to 500" is idempotent.
     "Add 100 to balance" is NOT.
   
   Upsert vs increment:
     UPDATE ... SET x=... WHERE id=...   (idempotent)
     UPDATE ... SET x=x+1 WHERE id=...   (NOT idempotent)
```

**Interview one-liner**
> "At-most-once may lose. At-least-once may duplicate. Exactly-once is achievable within Kafka but not across external side effects — so real systems use at-least-once + idempotent consumers, which behave like exactly-once."

---

### 10.5 Event Sourcing — the DB as a log

Traditional apps store **current state**. Event sourcing stores **every change** as an event, and derives state by replaying them.

**Traditional approach**

```
   Table: accounts
     id | balance
     ───────────
      1 | 500
   
   Update balance to 400 → row updated. Old value gone.
```

**Event-sourced approach**

```
   Log: account-1 events
     1. AccountOpened     { balance: 0 }
     2. Deposited         { amount: 500 }
     3. Withdrew          { amount: 100 }
     4. Withdrew          { amount: 200 }
     5. Deposited         { amount: 200 }
   
   Current state = replay all events → balance = 400.
```

**Why event sourcing wins for some systems**
- **Audit trail is free** — the log IS the history.
- **Time travel** — "what was the balance on March 5?" → replay to that point.
- **Debug replays** — reproduce production bugs by replaying events.
- **New views** — add a new consumer that projects the log into a new shape (analytics, search index).

**Why it's not universal**
- Complex to reason about.
- Schema evolution of events is tricky (old events must remain valid).
- Reads are expensive without snapshots (imagine replaying 10 years of events on every read).

**Snapshots — the read optimization**

```
   Every N events, save a snapshot of the current state.
   
   To read: latest_snapshot + replay events after it.
   
   snapshot@event-100 → replay 101..142 → current state
```

**Interview one-liner**
> "Event sourcing stores every change as an immutable event; current state is a projection of the log. You gain audit, replay, and time travel. You pay in complexity — needs snapshots and careful schema evolution."

---

### 10.6 CQRS — separate reads from writes

CQRS = **Command Query Responsibility Segregation**. Reads and writes go through different models.

**The problem it solves**
One data model that's good for both writes and reads is often a compromise. Denormalized read views are fast but hard to keep consistent; normalized tables are safe for writes but slow for reads.

**The pattern**

```
                     ┌──────────────┐
   Commands ────────►│  Write model │──► DB (source of truth)
   (Create, Update)  │              │        │
                     └──────────────┘        │
                                             │ events / CDC
                                             ▼
                     ┌──────────────┐   ┌────────────────┐
                     │  Read model  │◄──│  Projection    │
   Queries ─────────►│  (optimized) │   │  (denormalized)│
                     └──────────────┘   └────────────────┘
```

**Real example — e-commerce**

```
   Write side (Postgres):
     orders (id, user_id, status, ...)
     order_items (order_id, product_id, qty)
     users (id, name, ...)
     products (id, name, price)
   
   Read side (Elasticsearch / Mongo):
     order_details: {
       id, user: {name, email},
       items: [{name, price, qty}],
       total, status
     }
     
   All the joins done once at write time (or via CDC),
   stored denormalized, read blazingly fast.
```

**CQRS + Event Sourcing = powerful combo**

```
   Command → Event → Log ──► Projection 1 (orders view)
                          ──► Projection 2 (analytics)
                          ──► Projection 3 (search index)
   
   Add new projection later → replay log to backfill.
```

**When NOT to use CQRS**
- Simple CRUD apps. It's overkill.
- Strong consistency across read/write. CQRS is inherently eventual.

**Interview one-liner**
> "CQRS splits the write model (optimized for correctness) from the read model (optimized for query patterns), kept in sync via events. Combined with event sourcing, it's the backbone of many high-scale systems."

---

### 10.7 Sagas — distributed transactions without 2PC

You saw this in Section 9.5. Now the full picture.

**The problem**
In microservices, one business operation touches multiple services, each with its own DB. You can't run a single ACID transaction across them.

**Example: booking a trip**
```
   Reserve flight  → Flight service (own DB)
   Reserve hotel   → Hotel service  (own DB)
   Charge card     → Payment service (own DB)
   
   Payment fails → must UNDO flight + hotel.
```

**The Saga pattern**
Break the operation into a **sequence of local transactions**, each with a **compensating action** to undo it if a later step fails.

**Two flavors**

**A. Choreography — event-driven, no coordinator**

```
   Booking svc ──"BookingRequested"──► Kafka
                                          │
                                          ▼
                  Flight svc ──"FlightBooked"──► Kafka
                                          │
                                          ▼
                  Hotel svc  ──"HotelBooked"──► Kafka
                                          │
                                          ▼
                  Payment svc ──"PaymentFailed"──► Kafka
                                          │
                                          ▼
                  Hotel svc  ──compensate: cancel hotel
                                          │
                                          ▼
                  Flight svc ──compensate: cancel flight
   
   Each service reacts to events. No central controller.
```

- ✅ Fully decoupled, no single coordinator.
- ❌ Hard to reason about — the flow is spread across many event handlers.
- ❌ Hard to add global timeouts or observability.

**B. Orchestration — central coordinator**

```
                     ┌─────────────────────┐
                     │  Booking Saga        │
                     │  Orchestrator        │
                     └──────────┬──────────┘
                                │
                    ① BookFlight   │
                                │◄── success
                    ② BookHotel    │
                                │◄── success
                    ③ ChargeCard   │
                                │◄── FAIL
                    ④ CancelHotel  │
                                │◄── ack
                    ⑤ CancelFlight │
                                │◄── ack
                    ⑥ Mark saga FAILED
```

- ✅ Central place to see the full flow.
- ✅ Easier to add retries, timeouts, monitoring.
- ❌ Orchestrator becomes a component to maintain (may be a service or a workflow engine like Temporal / Camunda / Axon).

**Compensating actions — the design challenge**

```
   Not all actions are reversible!
   
   Book flight → reversible: cancel reservation.
   Send email → NOT reversible: apology email as best effort.
   Charge card → reversible: refund.
   Print physical ticket → NOT reversible: manual process.
```

**Design rule:** put the **irreversible steps last**. That way if something fails earlier, you compensate the reversible ones and never do the irreversible.

**Sagas vs 2PC**

| Aspect | 2PC | Saga |
|---|---|---|
| Coordination | Blocking prepare + commit | Local txns + events |
| Consistency | Immediate | Eventual |
| Blocking | Yes (holds locks) | No |
| Complexity | Framework handles | You design compensations |
| Failure mode | Coordinator dies → blocked | Independent recovery |
| Fits microservices? | ❌ | ✅ |

**Interview one-liner**
> "Sagas replace distributed transactions with a chain of local ones plus compensating actions. Choreography = events, decentralized, hard to observe. Orchestration = one workflow, easier to reason about. Use orchestration once flows get non-trivial."

---

### 10.8 Stream processing — computing on the log

Consuming a stream one message at a time is easy. Doing **aggregations, joins, and windowed computations** at scale is stream processing.

**The problem**
```
   Kafka stream of clicks:  { user_id, ad_id, timestamp }
   
   Question: "count of clicks per ad per 1-minute window"
   
   You can't do this with a naive consumer — you need:
     - state (running counters)
     - windowing (which events belong to which minute)
     - fault tolerance (state survives crashes)
     - out-of-order handling (late events)
```

**Enter stream processors**

- **Kafka Streams** — library, runs in your app, uses Kafka as the state backend.
- **Apache Flink** — dedicated cluster, best for complex/large workloads.
- **Spark Structured Streaming** — micro-batch, fits Spark ecosystems.
- **ksqlDB** — SQL on top of Kafka Streams.

**Key concepts**

**Windowing**

```
   Tumbling window:  [1min][1min][1min][1min]     non-overlapping
   Hopping window:   [1min]                       overlap
                       [1min]                      "every 30s, look at last 1min"
                         [1min]
   Session window:   groups events with < N-min gap
   Global window:    everything, no boundary (use with triggers)
```

**Watermarks — handling late events**

```
   Real time:  ───────────────────►
                                  ↑
                            watermark T
                            "no events with time < T will arrive anymore"
   
   Event with timestamp T-2min arrives NOW → treated as late.
   
   Options:
     Drop late events.
     Update the window's result (side output).
```

**State stores**

```
   Aggregations require state (running counts, joined records).
   
   Stream processors keep state locally (RocksDB) + back it up
   to Kafka via changelog topic → survives crashes.
```

**Stream-Table duality**

```
   A stream of updates IS a table over time:
   
     Stream:  (key=A, value=1) → (A, 2) → (A, 3) → (B, 5)
     Table:   { A: 3, B: 5 }   (latest value per key)
   
   Kafka's compacted topics are literally tables stored as streams.
```

**Interview one-liner**
> "A stream is a table changing over time; a table is a snapshot of a stream. Stream processors let you aggregate, join, and window continuous data with fault-tolerant state — the log is the source of truth and the processor is a derivation."

---

### 10.9 The Outbox pattern (revisited) — the dual-write killer

You saw this in Section 9.9. It's the most important pattern for reliable event publishing.

**The problem**

```
   Naive approach:
   
   ① INSERT INTO orders (...) VALUES (...);
   ② producer.send("OrderPlaced", ...);
   
   What if ② fails after ① succeeds?  → DB says yes, downstream missed it.
   What if ① fails after ② sends?     → downstream reacts to nonexistent order.
```

Two systems, no atomicity → data drift.

**The Outbox pattern**

```
   In ONE local ACID transaction:
   
   BEGIN;
     INSERT INTO orders (...) VALUES (...);
     INSERT INTO outbox (id, topic, payload) VALUES (...);
   COMMIT;
   
   Then, separately:
     CDC (Debezium) or a poller reads outbox → publishes to Kafka → deletes.
```

- ✅ Guaranteed atomic with the business write.
- ✅ Guaranteed at-least-once delivery to Kafka (dedupe downstream).
- ❌ Adds an outbox table + relay component.

**The inverse: Inbox pattern**

```
   Consumer receives message.
   
   BEGIN;
     INSERT INTO inbox (message_id) VALUES (...); -- dedupe check
     -- ... business logic that updates other tables ...
   COMMIT;
   
   If message_id already exists → skip (idempotency).
```

**Interview one-liner**
> "Never dual-write. Use the outbox to publish events atomically with your DB changes, and CDC to relay them. This is the plumbing behind reliable event-driven microservices."

---

### 10.10 The broker landscape — choosing one

**RabbitMQ**
- Traditional message broker. AMQP.
- Rich routing (topic exchanges, headers, fanout).
- Push-based delivery.
- **Use for:** classical queues, RPC over messaging, complex routing, moderate throughput.

**Apache Kafka**
- Distributed log. Pull-based.
- High throughput (millions/sec), retention.
- Great for streaming, event sourcing, log aggregation.
- **Use for:** high-scale event backbone, streaming pipelines, replayable event stores.

**AWS SQS**
- Managed queue. Simple, cheap.
- Standard (at-least-once) or FIFO (exactly-once, lower throughput).
- No streaming; short retention.
- **Use for:** simple background jobs on AWS.

**AWS SNS**
- Managed pub/sub. Fan-out to SQS, Lambda, HTTP, email.
- **Use for:** notifications, cross-service fan-out on AWS.

**AWS Kinesis**
- Managed Kafka-alternative on AWS.
- Similar model: shards, retention, consumer groups.
- **Use for:** streaming on AWS when you don't want to run Kafka.

**Apache Pulsar**
- Newer, unified queue + stream. Segmented storage.
- Multi-tenant, geo-replication built in.
- **Use for:** Kafka-like use cases needing multi-tenancy or tiered storage.

**Redis Streams / Pub-Sub**
- Lightweight, in-memory. Streams add durability.
- Great for latency-sensitive low-scale workloads.

**NATS / MQTT**
- Very lightweight pub/sub.
- **Use for:** IoT, edge, ultra-low overhead.

**When to pick which — the shortest guide**

```
   Traditional queue, complex routing         → RabbitMQ
   High-scale stream, event sourcing, log     → Kafka
   Simple queue on AWS                        → SQS
   Fan-out on AWS                             → SNS (+ SQS)
   Streaming on AWS                           → Kinesis
   IoT / edge                                 → MQTT
   Low-latency simple pub/sub                 → Redis / NATS
```

---

### 10.11 Common failure modes

- **Poison messages** — a message consistently fails processing. Loops forever without a **dead-letter queue (DLQ)**.
- **Consumer lag** — consumers can't keep up. Monitor lag, auto-scale consumers.
- **Head-of-line blocking** — one slow message in a partition delays all after it. Break into multiple partitions or async-worker fan-out.
- **Ordering vs parallelism trap** — more partitions = more parallel, but order only guaranteed within a partition. If you need per-user order, use user_id as key.
- **Rebalance storms** — consumer groups reshuffle when a consumer joins/leaves. Cooperative rebalancing (Kafka 2.4+) helps.
- **Skewed partitions** — hot key → one partition overloaded. Fix key hashing or shard the hot key.
- **Schema evolution** — old consumers can't parse new events. Use schema registry (Avro/Protobuf) with backward-compatible changes.
- **Exactly-once illusion** — thinking Kafka EOS covers your HTTP side effects. It doesn't. Idempotency is still your job.

---

### 10.12 Java angle — putting it in a Spring stack

**Kafka**
- `spring-kafka` — `@KafkaListener`, `KafkaTemplate`.
- Idempotent producer: `enable.idempotence=true`.
- Transactional producer + consumer for EOS within Kafka.
- Schema registry via `io.confluent:kafka-avro-serializer` or Protobuf.

**RabbitMQ**
- `spring-amqp` — `@RabbitListener`, `RabbitTemplate`.
- Declarative queue/exchange bindings.

**Outbox**
- `INSERT INTO outbox` in same `@Transactional` method as your business INSERT.
- Debezium reads Postgres WAL → publishes → deletes outbox rows.

**Consumer idempotency**
- Store processed `messageId` in Redis with TTL or a DB `inbox` table.
- Check before doing side effects.

**Stream processing**
- Kafka Streams — write topology in Java, deploys as normal Spring Boot app.
- Flink / ksqlDB — separate infra.

**Sagas**
- Axon Framework — full CQRS + event sourcing + sagas.
- Temporal — durable workflow engine (recommended for orchestration).
- Or roll your own with a state machine in a service.

---

### 10.13 Putting it together — an event-driven order flow

```
   ┌─────────────┐
   │   Client    │ POST /orders
   └──────┬──────┘
          │
          ▼
   ┌─────────────────────────────────────────────┐
   │  Order service                              │
   │                                             │
   │  ① Validate                                  │
   │  ② @Transactional:                          │
   │       INSERT INTO orders                    │
   │       INSERT INTO outbox("OrderCreated")   │
   │     COMMIT                                  │
   │  ③ Return 201 to client                     │
   └──────┬──────────────────────────────────────┘
          │
          ▼
   ┌─────────────────────────────────────────────┐
   │  Debezium (CDC)                             │
   │    reads outbox → publishes to Kafka        │
   └──────┬──────────────────────────────────────┘
          │
          ▼
   ┌─────────────────────────────────────────────┐
   │  Kafka: topic "orders.events"               │
   └──────┬───────────┬────────────┬─────────────┘
          │           │            │
          ▼           ▼            ▼
   ┌────────────┐ ┌──────────┐ ┌────────────┐
   │ Inventory  │ │  Email   │ │ Analytics  │
   │ (reserve)  │ │  (send)  │ │  (indexed) │
   │            │ │          │ │            │
   │ idempotent │ │idempotent│ │ idempotent │
   │ by orderId │ │by orderId│ │ by orderId │
   └─────┬──────┘ └──────────┘ └────────────┘
         │
         ▼ if reservation fails
   ┌────────────────────────┐
   │  Publish "OrderFailed" │  → orchestrator or choreography
   │  → Saga compensates    │    cancels payment, notifies user
   └────────────────────────┘
```

**Interview one-liner for the whole section**
> "Async messaging decouples services in time and failure. Kafka provides a durable, replayable event backbone; delivery is at-least-once + idempotent in practice. Outbox atomizes DB and event publish. Sagas replace distributed transactions with compensations. CQRS separates write and read models. Together, they're the substrate of modern event-driven architecture."

---

*Next up: Section 11 — Distributed Systems Theory: clocks (Lamport, vector, HLC), consensus (Paxos, Raft), leader election, membership (SWIM, gossip), 2PC/3PC/TCC/Saga formal treatment, idempotency, fencing tokens.*
