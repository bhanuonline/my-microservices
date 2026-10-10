# Tier 2 Architecture — Data & Messaging Depth

> **📸 Architecture snapshot** — module paths in diagrams use the flat
> pre-reorg layout (e.g. `paymentservice`, `user-service`). After the
> Oct 2026 reorg those live under `services/payment-service`,
> `services/user-service`, etc. The architecture itself is unchanged.
> For current paths, see [`topologies.md`](../setup/topologies.md) or run
> `./start-stack.sh --list`.

Companion to [tier2-roadmap.md](tier2-roadmap.md). The roadmap tells you
*what to build*; this doc explains *how it fits together*, with diagrams
of every data path and failure mode.

Audience: you, building this to learn and to demo in interviews.

- [1. The complete stack after Tier 2](#1-the-complete-stack-after-tier-2)
- [2. Lifecycle of one order through every Tier 2 feature](#2-lifecycle-of-one-order)
- [3. Schema Registry + Avro](#3-schema-registry--avro)
- [4. Exactly-once semantics](#4-exactly-once-semantics)
- [5. DLQ persistence + admin](#5-dlq-persistence--admin)
- [6. CQRS read model](#6-cqrs-read-model)
- [7. Event sourcing](#7-event-sourcing)
- [8. Debezium CDC](#8-debezium-cdc)
- [9. Operational cheat sheet](#9-operational-cheat-sheet)
- [10. Interview script](#10-interview-script)

---

## 1. The complete stack after Tier 2

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           OBSERVABILITY (Tier 1)                             │
│   Zipkin · Prometheus · Grafana · Loki · Promtail                            │
└──────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                          MESSAGING INFRA (Tier 2)                            │
│                                                                              │
│   ┌─────────────────────┐    ┌──────────────────────┐   ┌─────────────────┐  │
│   │  Schema Registry    │    │  Kafka Connect       │   │     Kafka       │  │
│   │  :8085              │    │  (Debezium)   :8083  │   │  :9092 / :29092 │  │
│   │  Avro schemas       │    │  reads WAL →         │   │  topics:        │  │
│   │  compat enforced    │    │  publishes to Kafka  │   │   order.created │  │
│   └──────────┬──────────┘    └──────────┬───────────┘   │   payment.*     │  │
│              │                          │               │   notif.*       │  │
│              │ register/lookup schema   │ CDC events    │   *.DLT         │  │
│              ▼                          ▼               │   *.retry.5s    │  │
│   ┌────────────────────────────────────────────────────┼─   dlq_events   │  │
│   │          every producer/consumer talks here        │   outbox stream │  │
│   └────────────────────────────────────────────────────┘─────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                           APPLICATION PLANE                                  │
│                                                                              │
│  ┌─────────┐  ┌─────────────────────────────────┐   ┌─────────────────────┐  │
│  │ API GW  │─▶│  Order-service (WRITE SIDE)     │   │  Order-query        │  │
│  │  :8080  │  │  :8083                          │   │  (READ SIDE) :8086  │  │
│  └─────────┘  │                                 │   │                     │  │
│               │  ┌──────────────┐ ┌──────────┐  │   │  OrderProjector     │  │
│               │  │ State-based  │ │Event     │  │   │   ← kafka consumer  │  │
│               │  │ (default)    │ │sourced   │  │   │                     │  │
│               │  │ orders table │ │aggregate │  │   │  OrderDoc (ES)      │  │
│               │  └──────┬───────┘ └────┬─────┘  │   │  index: orders_v1   │  │
│               │         ▼              ▼        │   │                     │  │
│               │  ┌─────────────┐ ┌──────────┐   │   │  GET /orders/search │  │
│               │  │outbox_events│ │order_    │   │   └──────────▲──────────┘  │
│               │  └──┬────┬─────┘ │events +  │   │              │             │
│               │     │    │       │snapshots │   │              │ consumes    │
│               │     │    │       └──────────┘   │              │ order.      │
│               │     │    │                      │              │  created    │
│               │ (A) │ (B)│ Debezium CDC         │              │             │
│               │     │    │ reads WAL            │              │             │
│               │     ▼    ▼                      │              │             │
│               │ Outbox   kafka-connect──────────┼──────────────┤             │
│               │ Relay    publishes to Kafka     │              │             │
│               │ (polling,                       │              │             │
│               │  disabled                       │              │             │
│               │  under                          │              │             │
│               │  docker-cdc)                    │              │             │
│               │                                 │              │             │
│               │  DlqObserver ─▶ dlq_events ─▶ /admin/dlq       │             │
│               └─────────────────────────────────┘              │             │
│                                                                │             │
│  ┌──────────┐                                                  │             │
│  │ Payment  │─saga─▶ Order    (Kafka)                          │             │
│  │ Product  │─evts─▶          (Kafka)──────────────────────────┘             │
│  │ Notif    │                                                                │
│  └──────────┘                                                                │
│                                                                              │
│       Infra:   MySQL (user/product/auth)   Postgres-order (docker-cdc only)  │
│                H2 (order default)          Elasticsearch :9200               │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Two outbox-dispatch paths now coexist, pick with a flag:**
- (A) `OutboxRelay` polling — default, `outbox.polling-enabled=true`, works with H2.
- (B) Debezium CDC — `docker-cdc` profile, flag off, Postgres required.

---

## 2. Lifecycle of one order through every Tier 2 feature

Walk through a `POST /api/v1/orders` that creates an order, pays for it, and
eventually surfaces in the search index. Each stage below exercises a
different Tier 2 feature.

```
Browser          Order (write)       Kafka            Order-query (read)
   │                 │                  │                   │
   │ POST /orders    │                  │                   │
   ├────────────────▶│                  │                   │
   │  Idemp-Key K    │                  │                   │
   │                 │                  │                   │
   │                 │ IdempotencyFilter (Tier 1)           │
   │                 │ miss → forward                       │
   │                 │                  │                   │
   │                 │ ○ OrderService.create                │
   │                 │  @NewSpan "order.create"             │
   │                 │  ┌─ INSERT orders            (SQL)   │
   │                 │  ├─ INSERT outbox_event      (SQL) ──┼── outbox row
   │                 │  └─ COMMIT (one tx)                  │
   │                 │  AND / OR                            │
   │                 │  ┌─ append order_events[](Tier 2 #5) │
   │                 │  │  (if using event-sourced path)    │
   │                 │  └─ snapshot every 50 events         │
   │                 │                  │                   │
   │                 │ OUTBOX DISPATCH                      │
   │                 │ ─────────────────                    │
   │                 │ path A: OutboxRelay poll (500 ms)    │
   │                 │   kafkaTemplate.executeInTransaction │
   │                 │   batch of up to 50 rows             │
   │                 │   atomic send ────────────▶ topic order.created
   │                 │                              │       │
   │                 │ path B: Debezium reads WAL   │       │
   │                 │   latency < 50 ms            │       │
   │                 │   routes by `destination` column     │
   │                 │                              │       │
   │                 │                  │ KafkaAvroSerializer│
   │                 │                  │ fetches schemaId  │
   │                 │                  │ from registry     │
   │                 │                  │ (if Avro, #3)     │
   │                 │                  │                   │
   │                 │                  │                   │ OrderProjector consumer
   │                 │                  │                   │   groupId=order-query-projector
   │                 │                  │                   │   isolation=read_committed  ← Tier 2 #4
   │                 │                  │                   │   decodes OrderCreatedEvent
   │                 │                  │                   │   → OrderDoc upsert in ES
   │                 │                  │                   │
   │                 │                  │                   │ If handler THROWS:
   │                 │                  │                   │   in-process retry ×3
   │                 │                  │                   │   → error.<topic>.<group>
   │                 │                  │                   │   DlqObserver persists row (#5)
   │                 │                  │                   │   /admin/dlq surfaces it
   │                 │                  │                   │
   │ 201 Created     │                  │                   │
   │◀────────────────│                  │                   │
   │ (write-side     │                  │                   │
   │  response)      │                  │                   │
   │                 │                                      │
   │        [100 ms – 2 s later]        │                   │
   │                                                        │
   │ GET /orders/search?status=CREATED → :8086              │
   ├───────────────────────────────────────────────────────▶│ hit ES
   │                                                        │ return doc
   │◀───────────────────────────────────────────────────────│
```

If the client **retries with the same `Idempotency-Key`**, step 1 short-circuits
(Tier 1). If the Kafka transaction aborts mid-batch (Tier 2 #4), consumers
with `isolation.level=read_committed` see nothing — on next poll, outbox rows
still PENDING are re-sent. Consumer-side `IdempotencyGuard` ensures no
duplicate projection.

---

## 3. Schema Registry + Avro

### Wire format

```
Every Avro record on the wire looks like this:

   ┌──────┬───────────────────┬──────────────────────────────────┐
   │ 0x00 │  4-byte schema id │  binary Avro payload             │
   └──────┴───────────────────┴──────────────────────────────────┘
    magic   int32 big-endian    serialized per the schema at id

Producer side:
   1. On first send, serializer registers the schema → gets id.
   2. Caches { subject → id } locally — zero network cost per message.
   3. Prepends magic + id, encodes payload.

Consumer side:
   1. Reads magic (0x00) + id (4 bytes).
   2. Looks up schema by id in registry → caches.
   3. Decodes payload using (writer schema, reader schema) → generated class.
```

### Compatibility rules enforced before publish

```
                    ┌──────────────────────────────────────┐
                    │  Set per subject (one subject/topic) │
                    └──────────────────────────────────────┘

BACKWARD   new consumer can read old data         ← default. producer evolves after consumer.
FORWARD    old consumer can read new data         ← producer evolves first.
FULL       both                                   ← hardest to meet; frozen contracts.
NONE       anything goes                          ← greenfield only; disable before shipping.

Add nullable field (type: ["null", X], default: null)  → BACKWARD OK
Add required field                                     → BACKWARD BROKEN  (old payloads lack it)
Remove field with default                              → BACKWARD OK
Rename field                                           → BROKEN (both directions)
Change type (int → string)                             → BROKEN
```

The registry **rejects publishes** that would break the active compat rule —
you fail at CI/deploy time, not when a consumer explodes at 3am.

### Where it lives in this repo

```
pom.xml                                       Confluent repo + pinned versions
docker-compose.yml                            schema-registry service on :8085
common-lib/pom.xml                            avro-maven-plugin + kafka-avro-serializer
common-lib/src/main/avro/OrderCreatedEvent.avsc
    → generated class: com.example.common.event.avro.OrderCreatedEventAvro
      lives in target/generated-sources/avro, added to compile path by the plugin.
```

### Opt-in pattern (not mass-migration)

```yaml
# any service that wants to publish Avro:
spring.kafka.producer:
  value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
  properties:
    schema.registry.url: http://schema-registry:8081
    auto.register.schemas: false    # production: schemas registered via CI
```

Keep JSON for the services that already work. Migrate topics one at a time;
Avro and JSON can coexist on different topics.

### Trade-offs

| Decision | Why |
|---|---|
| Confluent registry + Avro over Apicurio + Protobuf | Confluent is default on JVM/Spring; batteries included. Protobuf wins on gRPC/polyglot. |
| `auto.register.schemas=false` in prod | Prevents a buggy producer from publishing a schema that fails future compat checks. Register via CI. |
| Schema per topic (one subject) | Default and simplest. Multi-type-per-topic needs union schemas; avoid until needed. |
| Decimal as `bytes` with `logicalType=decimal` | Correct money representation. Never float. |

### Interview talking points

- **Why not just JSON** — no enforced contract; schema drift is silent until a consumer crashes.
- **Why not just the generated class across services** — then every schema change requires a lock-step rebuild/redeploy of every consumer. Registry decouples versioning.
- **Schema evolution rules in interview form** — "Add optional field, OK. Rename, no. Changing type, no. Remove required, no." You should be able to recite that cold.
- **The id trick** — the first 5 bytes of every message tell the consumer which schema it was written with. Consumers never need to pre-know schemas; they fetch on demand and cache.

---

## 4. Exactly-once semantics

### The three ingredients

```
                 ┌──────────────────────────────────────────────┐
                 │       Effectively-once delivery              │
                 │     = at-least-once + idempotent consumer    │
                 └──────────────────────────────────────────────┘

PRODUCER                         BROKER                       CONSUMER
────────                         ──────                       ────────
enable.idempotence=true          dedup producer retries       isolation.level=read_committed
    │                            on broker side                   │
    ▼                                                             ▼
transactional.id=                stores + aborts/commits      skips aborted/in-flight tx records
  <app>-tx-<hostname>            transaction markers              │
    │                                                             ▼
    │                                                        business dedup
    │                                                        (IdempotencyGuard by eventId)
    │
    └── kafkaTemplate.executeInTransaction {
            send(topic, k1, v1)
            send(topic, k2, v2)
        }  → atomic: all commit or none
```

### Why **each** of the three matters

```
Scenario A — only enable.idempotence set
   producer.send() A → B → C. network blip. producer retries C.
   broker sees seq=3 twice, dedups one. Result: no dup on the wire.
   BUT: your APP called send() twice itself? Idempotence doesn't catch that.

Scenario B — only transactional.id set, consumer not read_committed
   batch of 10 commits atomically. Consumer with default isolation.level
   reads 7 records from the in-flight tx, then the tx aborts.
   7 "ghost" records processed. Duplicates.

Scenario C — all three + outbox but no consumer-side dedup
   DB tx commits (outbox row + domain row), then JVM dies BEFORE marking
   outbox row SENT. Next poll re-sends. Kafka tx commits again. Consumer
   sees the event twice. Needs IdempotencyGuard.
```

**You need all three + consumer-side dedup.** "Exactly-once" is really
"at-least-once that converges" — the mental model to carry into interviews.

### Fencing (the per-instance-prefix fix)

```
Two order-service instances with the same transactional.id
    pod-A              broker                 pod-B
      │                   │                     │
      │ initProducerId    │                     │
      ├──────────────────▶│                     │
      │ ← epoch=5         │                     │
      │                   │                     │
      │ send batch...     │                     │
      │                   │ initProducerId      │
      │                   │◀────────────────────┤
      │                   │ epoch=6 → send to A │
      │                   │ ← epoch=6           │
      │                   │                     │
      │ send batch        │                     │
      │ (uses epoch 5)    │                     │
      │                   │                     │
      │   ← ProducerFenced │                    │
      │                   │                     │
      │ halt              │                     │
      │                   │                     │

Fix (what we applied):
    transaction-id-prefix: ${spring.application.name}-tx-${HOSTNAME:local}-
    → pod-A gets  order-service-tx-pod-a-N
    → pod-B gets  order-service-tx-pod-b-N
    No shared id, no fencing.
```

### What runs where today

```
order-service    ✓ enable.idempotence     ✓ transactional producer (per-host prefix)
                 ✓ outbox + atomic batch via executeInTransaction
                 ✓ IdempotencyGuard on consumer handlers
                 ✓ read_committed consumer (via cloud-stream default)

product-service  ✓ same (used for the user-registered flow)
notification     ✓ read_committed consumer
paymentservice   ✓ IdempotencyGuard in saga command processor
```

### Interview talking points

- **"Exactly-once" is politely restated as effectively-once.** You get: at-least-once + atomic batches + consumer dedup = convergence.
- **Idempotence alone** dedups producer retries only — not app-level double-sends.
- **Transactions alone** need `read_committed` consumers to matter.
- **Why outbox + EOS instead of 2PC** — 2PC needs cross-system distributed-tx coordinators. Outbox makes it "did it also publish" into a DB question answered by the next poll (or Debezium).
- **Cost** — transactions add ~1 ms coordinator round-trip per batch; `read_committed` disables zero-copy on the broker read path. Measure before enabling globally.

---

## 5. DLQ persistence + admin

### Message flow with DLQ handling

```
Topic T  ─────▶  consumer handler
                      │
                   throws
                      │
                      ▼
                in-process retry (Cloud Stream max-attempts=3, expo backoff)
                      │
                   still fails
                      │
                      ▼
                binder routes to error.T.<group>  (DLQ topic)
                      │
                      ▼
                DlqObserver (KafkaListener on error.*  | *.DLT)
                      │
                      ├─ parses headers:  x-original-topic, x-original-offset,
                      │                   x-exception-fqcn, x-exception-message
                      │
                      ├─ INSERT dlq_events (status=NEW)
                      │
                      └─ log at ERROR with id, topic, exception
                                         │
                                         ▼
                                 operator paged (future: alert on dlq_events{status=NEW} count)
                                         │
                                         ▼
                                 GET /admin/dlq?status=NEW   → list rows
                                 GET /admin/dlq/{id}         → full payload
                                 POST /admin/dlq/{id}/replay → republish to original topic
                                                                 (consumer idem catches dup)
                                 POST /admin/dlq/{id}/ack    → drop (keep row for audit)
```

### Schema

```sql
dlq_events
  id                 BIGSERIAL PRIMARY KEY
  dlq_topic          VARCHAR(255)  NOT NULL     error.<topic>.<group> or <topic>.DLT
  original_topic     VARCHAR(255)
  original_partition INT
  original_offset    BIGINT
  message_key        VARCHAR(512)
  payload            CLOB
  exception_class    VARCHAR(255)
  exception_message  CLOB
  received_at        TIMESTAMP     NOT NULL
  status             VARCHAR(16)   NOT NULL     NEW | REPLAYED | ACKNOWLEDGED
  status_updated_at  TIMESTAMP
  status_updated_by  VARCHAR(128)              operator JWT subject

  idx_dlq_status, idx_dlq_received
```

### What was built in this repo

```
common-lib/.../dlq/
  DlqEvent.java              @Entity, enum Status
  DlqEventRepository.java    Spring Data JPA
order-service/.../consumer/
  DlqObserver.java           was log-only; now persists each poison
order-service/.../web/
  DlqAdminController.java    GET list, GET by id, POST replay, POST ack
```

Scanned by `OrderServiceApplication`'s updated `scanBasePackages` /
`@EntityScan` / `@EnableJpaRepositories` (now include `com.example.common.dlq`).

### Replay safety

```
operator clicks replay
    │
    ▼
kafka.send(originalTopic, messageKey, payload)
    │
    ▼
consumer sees event with same eventId (producer embedded it)
    │
    ▼
IdempotencyGuard.claim(eventId, consumer)
    │
    ├─ first time: process + mark done   ← healthy replay path
    │
    └─ already-processed: no-op          ← operator mis-click or double-replay
```

### Interview talking points

- **"Retry forever" is the wrong default.** Caps + tiers + persistent DLQ is the pattern.
- **Why not sleep in the consumer** — blocks the thread; blocks every partition the consumer owns.
- **Headers over payloads for metadata** — attempt count, original topic, exception survive serialization changes.
- **Replay = republish to original topic**, not re-run the handler directly. The second route exercises the full pipeline including dedup.
- **Future tiers (next):** `*.retry.5s → *.retry.1m → *.retry.5m → *.DLQ`. Use `@RetryableTopic` for the Spring built-in. Mention this.

---

## 6. CQRS read model

### Split responsibilities

```
WRITES                                     READS
──────                                     ─────
POST /orders                               GET /orders/search?status=…&productId=…&page=…
     │                                            │
     ▼                                            ▼
order-service :8083                        order-query :8086
 ┌─────────────────────────┐                ┌─────────────────────────┐
 │ JPA to Postgres/H2      │                │ Spring Data ES          │
 │   ACID                  │                │ ElasticsearchRepository │
 │   normalized            │                │   denormalized          │
 │   strict schema         │                │   searchable + filterable
 │                         │                │                         │
 │   outbox_events ────────┼─ Kafka ──────▶ │ OrderProjector consumer │
 │     order.created       │                │   upserts OrderDoc      │
 │                         │                │                         │
 └─────────────────────────┘                └─────────────────────────┘
```

### Index per schema generation

```
index name              purpose
──────────────────      ───────────────────────────────────────
orders_v1               current write target; projector upserts here
orders_v2 (future)      new mapping; backfill in parallel; flip an alias
                        when caught up — zero-downtime schema change

alias:  orders  ─────▶  orders_v1   (today)
alias:  orders  ─────▶  orders_v2   (after flip)
```

### Projector shape (what we built)

```
@KafkaListener(topics = "order.created",
               groupId = "order-query-projector",
               containerFactory = "orderCreatedListenerFactory")

   ┌──────────────────────────────┐
   │ KafkaConsumerConfig          │
   │  - StringDeserializer        │
   │  - JsonDeserializer trusted  │
   │  - isolation.level=          │
   │    read_committed            │ ← pairs with Tier 2 #4 producer
   │  - enable.auto.commit=false  │ ← Spring commits after handler returns
   └──────────────────────────────┘

   handler:  repo.save(OrderDoc.from(event))
             (save = upsert by @Id so replays are safe)
```

### Query shapes

```
GET /orders/search
    │ no filter → repo.findAll(page)
GET /orders/search?status=CREATED
    │ repo.findByStatus(status, page)   ← derived query
GET /orders/search?productId=42
    │ repo.findByProductId(productId, page)
GET /orders/search/{orderId}
    │ repo.findById(id)
```

For richer queries (fuzzy match, multi-field, aggregations) add
`ElasticsearchOperations` with a `NativeQueryBuilder` — leave it until a
query exercises the muscle.

### Consistency

```
                time ──▶

   write-side: POST /orders       ✓ 201 Created
                    │                           │
                    │  outbox → relay → Kafka   │
                    │                   │       │
                    │   (10 – 500 ms)   │       │
                    │                   ▼       │
   read-side:                          projector consumes
                                        │       │
                                        │  (10 – 100 ms)
                                        ▼       │
                                        ES upsert
                                                │
                                                ▼
                                              GET returns it

   WINDOW where write visible but search returns stale: usually < 1 s.
```

- **Checkout totals, inventory holds** — don't put on the read model. Read-your-writes must hit the write store.
- **Dashboards, search, exploration** — perfect fit for the read model.

### Rebuild path (interview answer to drift)

```
POST /admin/projector/rebuild           (future endpoint)
    │
    ▼
read orders from Postgres write store
    │
    ▼
publish synthetic OrderCreatedEvent per row to a rebuild topic
    │
    ▼
projector consumes rebuild topic (same code path), upserts ES
    │
    ▼
flip alias from orders_v1 → orders_v1_new, drop old index
```

### Interview talking points

- **Why CQRS** — different access patterns. Writes want strict schema + ACID; reads want flexible, fast queries.
- **Trade-off** — eventual consistency; always ask "can this user flow tolerate a 1-second lag?"
- **Not every service needs CQRS.** Add when read path differs sharply from write path.
- **Why not just add indexes to the write DB** — ES does fuzzy, nested, aggregations, geo, autocomplete. SQL can't match without pain.
- **The alias dance** is the zero-downtime schema change technique. Learn it.

---

## 7. Event sourcing

### Two storage models side by side

```
STATE-BASED (default / existing)              EVENT-SOURCED (Tier 2 opt-in)
────────────────────────────────              ─────────────────────────────
orders table                                  order_events  (append-only)
┌────┬──────┬───────┐                         ┌─────┬──────┬───────────┬────────┬────┐
│ id │status│ amount│                         │ seq │order │event_type │payload │ver │
│ o1 │ PAID │ 29.97 │                         │  1  │ o1   │OrderCreated│{...}  │ 1  │
└────┴──────┴───────┘                         │  2  │ o1   │PayReserved│{...}   │ 2  │
                                              │  3  │ o1   │PayCompleted│{...}  │ 3  │
UPDATE on each state change                   └─────┴──────┴───────────┴────────┴────┘
history LOST                                                 ↑
                                              append-only; UNIQUE(order_id, version)
                                              → optimistic concurrency enforced by DB

                                              order_snapshots (every 50 events)
                                              ┌────────┬─────┬─────────────┐
                                              │order_id│ver  │ JSON state  │
                                              │  o1    │ 50  │ {status:…}  │
                                              └────────┴─────┴─────────────┘
```

### load() cycle

```
load("o1")
   │
   ▼
   latest snapshot? ─── yes ─── deserialize → OrderAggregate at version N
   │                                             │
   no                                            ▼
   │                              fetch events WHERE version > N
   ▼                                             │
   OrderAggregate at version 0                   │
   │                                             ▼
   fetch all events for o1                     agg.apply(each event)
   │                                             │
   ▼                                             │
   agg.apply(each event) ──────────────────── version in agg = N + applied count
```

### save() cycle

```
agg.handleReservePayment("pmt-7")        ← command handler
   │ validates (status == NEW)
   │ emits [EventPayload(PaymentReserved, {paymentId:pmt-7})]
   │ applies in-memory  (status → PAYMENT_RESERVED, version++)
   │ returns new events
   ▼
save(agg, newEvents)
   │ baseVersion = agg.version - newEvents.size()   ← what was in DB before this call
   │ for each ev: INSERT INTO order_events(order_id, type, payload, version = baseV+i+1)
   │
   │ UNIQUE(order_id, version) → race: one wins, loser throws
   │                              ConcurrentOrderModificationException
   │                              → caller must reload and retry
   │
   │ if version - lastSnapshotVersion >= 50:
   │     snapshotRepo.save(new OrderSnapshot(id, version, JSON state))
```

### Command-handler-event split (the hard discipline)

```
GOOD                                     BAD (looks like OOP, is a trap)
────                                     ───
public List<Event> handleX(cmd) {        public void handleX(cmd) {
  require(invariant);                      this.status = NEW_STATE;   // ← mutates directly
  Event e = new E(…);                      // ← history lost; apply() not the only mutator
  apply(e);                              }
  return List.of(e);
}

private void apply(Event e) {
  // the ONLY place state mutates
}
```

Why: `apply()` runs both for fresh writes and when replaying history. If writes
mutate directly, replays won't reproduce the same state.

### Payload polymorphism via Jackson

```
                 ┌──────────────────────────────────────┐
                 │ EventPayload = (type, Payload)       │
                 │ Payload sealed-ish by @JsonSubTypes  │
                 │   OrderCreatedPayload                │
                 │   PaymentReservedPayload             │
                 │   SimplePayload                      │
                 │   ReasonPayload                      │
                 │   FulfilledPayload                   │
                 └──────────────────────────────────────┘

   serialized   { "type":"PaymentReserved",
                  "payload":{ "@type":"PaymentReserved", "orderId":"o1", "paymentId":"pmt-7" } }

   @type discriminator ensures the right concrete class is picked on read.
```

### Where it lives in this repo

```
order-service/.../eventsourced/
  OrderEvent.java                        @Entity (append-only)
  OrderSnapshot.java                     @Entity (latest per aggregate)
  OrderEventRepository.java              findByOrderIdAndVersionGreaterThan…
  OrderSnapshotRepository.java
  OrderAggregate.java                    handle*/apply/Payload inner records
  EventSourcedOrderRepository.java       load + save + snapshot every 50
```

Not wired into any REST controller yet — Tier 2 scope was "add the capability
alongside, don't rip out working code." A follow-up task will add
`POST /api/v2/orders` using the event-sourced path.

### Interview talking points

- **When event sourcing wins** — audit trail, temporal queries, compensations, "why is this order in this state."
- **When it loses** — simple CRUD, teams unprepared for the mental shift. Debugging snapshots is a real cost.
- **Snapshots are not an optimisation** — at scale they're a requirement. Rule of thumb: snapshot every N events (50–100) OR when load exceeds budget.
- **Event migrations** — events are immutable. Fix bad events via an **upcaster** that transforms old events into the new shape on read.
- **CQRS + event sourcing** pair naturally — the event stream feeds projections. You can have CQRS without ES (we do: order-query consumes outbox JSON). You rarely have ES without some read model.
- **Compare to outbox** — outbox publishes current-state events as side effects. ES *is* the state. Different scopes.

---

## 8. Debezium CDC

### Polling outbox vs CDC

```
POLLING (default today)                    CDC (docker-cdc profile)
──────────────────────                     ─────────────────────────
OrderService.create()                       OrderService.create()
    JPA INSERT orders                           JPA INSERT orders
    JPA INSERT outbox_event                     JPA INSERT outbox_event
    COMMIT                                      COMMIT
       │                                            │
       ▼                                            │ (writes to WAL)
OutboxRelay @Scheduled 500ms                        ▼
    SELECT PENDING LIMIT 50                   Postgres WAL  ───────────────┐
    kafka.executeInTransaction {                                            │
        send(topic, k, v) × N                      Debezium kafka-connect  │
    }                                                 (polls WAL at native │
    UPDATE SENT                                       speed, ~ms latency)  │
       │                                                                   │
       ▼                                               applies Outbox       │
    Kafka                                              EventRouter SMT      │
       │                                                 - key: aggregate_id│
       │                                                 - route by:        │
       │                                                   destination col  │
       │                                                 - payload: payload │
       │                                                                   ▼
       │                                                 Kafka topic
       │                                                 (same as polling)
       │                                                   │
       └──────── identical consumer side ──────────────────┘

Latency: p99 ~ 500 ms            Latency: p99 ~ 50 ms
DB load:  continuous polling     DB load:  zero polling, logical repl.
```

### Why the outbox table remains

```
Business write already lives in a tx: INSERT orders + INSERT outbox_event.
Debezium just changes HOW the outbox row becomes a Kafka event; the row
still exists.

Benefits of keeping outbox:
  - Decouples business write from "which topic / what shape"
  - Routes by column (destination) → EventRouter SMT does the mapping
  - Audit trail: outbox_events is a log of what was emitted
  - Fallback: disable CDC flag, polling resumes without data loss
```

### Debezium Outbox Event Router SMT

```
Row in outbox_events (as CDC event):
   { id: uuid, aggregate_type: "order", destination: "order.created",
     payload: "{...json...}", created_at: ... }

                       │
                       ▼  EventRouter SMT

Kafka message:
   topic:   order.created      ← from `destination` column
   key:     "order"            ← from `aggregate_type`
   headers: { destination: "order.created" }
   value:   {...json...}       ← from `payload` column

The SMT UNWRAPS the row — downstream consumers see the business event, not
the DB row representation.
```

### Switching between modes

```yaml
# Default: polling path works, docker-cdc profile not active
outbox.polling-enabled: true

# Debezium path:
#   docker compose up -d postgres-order kafka-connect
#   SPRING_PROFILES_ACTIVE=docker-cdc  (sets outbox.polling-enabled=false)
#   ./observability/debezium/register-outbox-connector.sh
```

`OutboxRelay` is gated by `@ConditionalOnProperty(outbox.polling-enabled)` —
with the flag off the bean never registers; no scheduled job runs.

### What runs where today

```
Default (dev, H2):      path A — OutboxRelay polling, ~500 ms latency
docker-cdc profile:     path B — Debezium CDC, ~50 ms latency, Postgres required
```

Both paths produce the **same Kafka message shape** so downstream consumers
(order-query, product-service, notification) don't care which runs.

### Interview talking points

- **CDC is the modern replacement for polling outbox at scale.**
- **Why the outbox pattern survives CDC** — business write still needs atomic
  "domain row + event" in the same tx. CDC changes who publishes, not whether.
- **Debezium is restart-safe** — persists its WAL offset in a Kafka topic; on
  restart resumes from that offset. No duplicates, no misses.
- **Pitfalls**:
  - WAL retention — CDC can't catch events older than the WAL window.
  - Primary DB load — logical replication is cheap but non-zero; monitor.
  - Schema changes — DDL on the source may need the connector restarted.
- **Multi-tenant / sharded** — one connector per shard, one topic per shard.

---

## 9. Operational cheat sheet

### Ports

```
Application plane                     Observability / messaging
─────────────────                     ─────────────────────────
8080  api-gateway                     9411  zipkin
8081  user-service                    9090  prometheus
8082  product-service                 3100  loki
8083  order-service                   3000  grafana
8086  order-query (new, CQRS)         9092  kafka (external)
8095  auth-server                    29092  kafka (internal)
8096  resource-server                 8085  schema-registry (host → container :8081)
8761  eureka-server                   8083  kafka-connect / debezium
                                      9200  elasticsearch
                                      5433  postgres-order (docker-cdc)
```

### First-time boot (full Tier 2)

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home
mvn -DskipTests clean package

# Observability + messaging infra
docker compose up -d zipkin prometheus loki promtail grafana \
                     schema-registry elasticsearch

# App plane (default — polling outbox, H2)
docker compose up -d eureka-server auth-server api-gateway \
                     user-service product-service order-service

# New CQRS read service
cd order-query && mvn spring-boot:run &

# Open:
open http://localhost:9411          # Zipkin
open http://localhost:3000          # Grafana
open http://localhost:8085/subjects # Schema Registry
open http://localhost:9200          # Elasticsearch
```

### Switching to Debezium mode

```bash
docker compose up -d postgres-order kafka-connect

# Restart order-service with docker-cdc profile:
SPRING_PROFILES_ACTIVE=docker-cdc java -jar order-service/target/order-service-*.jar

# Register connector (requires jq):
./observability/debezium/register-outbox-connector.sh

# Verify:
curl -s http://localhost:8083/connectors/order-outbox-connector/status | jq .
```

### Verifying each feature

```
AVRO + SCHEMA REGISTRY
  curl http://localhost:8085/subjects
  # publish an order; expect a new subject to appear if that topic uses Avro

EOS
  grep "TransactionalId" order-service logs
  # should be unique per instance: order-service-tx-<hostname>-

DLQ ADMIN
  # make something fail (e.g. send malformed JSON to payment.commands)
  curl -u admin:admin123 http://localhost:8080/admin/dlq?status=NEW
  curl -u admin:admin123 -X POST http://localhost:8080/admin/dlq/1/replay

CQRS
  curl -u admin:admin123 -X POST http://localhost:8080/api/v1/orders \
       -H "Content-Type: application/json" -d '{"productId":1,"quantity":2}'
  # wait 1-2 s then
  curl http://localhost:8086/orders/search?status=CREATED

EVENT SOURCING
  # not wired to a REST endpoint yet — exercise via a unit test calling
  # EventSourcedOrderRepository.save() + load() and asserting status/version.

DEBEZIUM CDC
  # under docker-cdc profile:
  docker exec -it postgres-order psql -U order -d orderdb -c \
    "INSERT INTO outbox_events(id, aggregate_type, destination, payload, status, created_at, attempt_count) \
     VALUES (gen_random_uuid(), 'order', 'order.created', '{\"test\":true}', 'PENDING', NOW(), 0);"
  docker exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 \
     --topic order.created --from-beginning
  # expect the test event within ~50 ms
```

---

## 10. Interview script

**Q: How do you evolve an event schema without breaking consumers?**
> Every event is Avro-encoded with a magic byte + schema id prefix. The
> Confluent Schema Registry enforces a per-subject compatibility rule — we
> use BACKWARD, which means new consumers can read old data. The registry
> rejects publishes that would break that rule at CI/deploy time, so we fail
> fast instead of at 3am when a consumer explodes. Adding nullable fields
> with defaults is safe; renames and type changes are refused.

**Q: Does your system guarantee exactly-once delivery?**
> Nothing truly delivers exactly-once over an unreliable network. What we
> have is **effectively-once**: idempotent transactional producer + atomic
> outbox batches committed via Kafka transactions, read-committed consumers
> that skip aborted transactions, and a consumer-side `IdempotencyGuard`
> keyed on event id. The producer's `transactional.id` is suffixed with
> the pod hostname so parallel instances don't fence each other.

**Q: What happens to a message that keeps failing?**
> In-process retry 3× with exponential backoff. After that it lands on the
> binder's error topic — `error.<topic>.<group>`. A `DlqObserver` catches
> every DLQ topic by pattern and inserts a `dlq_events` row with the
> original offset, headers, exception, and payload. Operators list via
> `GET /admin/dlq?status=NEW`, inspect, and either replay (republishes to
> the original topic — consumer dedup catches it) or ack (keep for audit).
> Next step is tiered retry topics: 5 s, 1 m, 5 m, then DLQ.

**Q: Your reads are slow and need fuzzy search — what do you do?**
> Add CQRS: a separate `order-query` service with Elasticsearch as the read
> store. A `KafkaListener` on `order.created` upserts `OrderDoc` into the
> `orders_v1` index. Reads hit `/orders/search` on the read service.
> Trade-off is eventual consistency — the window is Kafka latency plus
> projection time, usually sub-second. The read model is rebuildable from
> the event stream, so index schema changes are a parallel-backfill + alias
> flip, zero downtime.

**Q: How would you reconstruct an order's state at an arbitrary past time?**
> With state-based storage you can't — UPDATE loses history. The Order
> aggregate also supports event sourcing: every state change appends a row
> to `order_events`. Current state = fold `apply(event)` over the history.
> We take a snapshot every 50 events to bound load time. The DB enforces
> optimistic concurrency via `UNIQUE(order_id, version)` — two concurrent
> writers can't both commit.

**Q: Your outbox polls every 500 ms. Can you do better?**
> Yes — Debezium CDC. We added a `docker-cdc` profile that switches
> order-service to Postgres and runs a Kafka Connect worker with the Debezium
> Postgres connector plus the Outbox Event Router SMT. Debezium reads the WAL
> directly and publishes outbox rows to Kafka in under 50 ms, with zero
> polling load on the DB. The outbox *table* stays — it keeps business write
> decoupled from publishing concerns. OutboxRelay is gated on an
> `outbox.polling-enabled` flag so the two paths are mutually exclusive.

---

## Related reading

- [tier2-roadmap.md](tier2-roadmap.md) — step-by-step build plan
- [tier1-architecture.md](tier1-architecture.md) · [tier1-roadmap.md](tier1-roadmap.md)
- [concepts/outbox-pattern.md](concepts/outbox-pattern.md)
- [concepts/dlq.md](concepts/dlq.md)
- [concepts/event-driven-basics.md](concepts/event-driven-basics.md)
- [concepts/saga-orchestration.md](concepts/saga-orchestration.md)
