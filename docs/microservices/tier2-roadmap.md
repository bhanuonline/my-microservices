# Tier 2 Roadmap — Data & Messaging Depth

Tier 1 (observability + resilience) made the system *visible* and *stable*.
Tier 2 makes it *correct under high event volume* — the data and messaging
patterns that come up in every senior-level system-design interview.

Audience: you, learning for interviews. Each section follows the same shape
as the Tier 1 doc: **what it is**, **why interviewers ask**, **current state**,
**implementation steps**, **verify**, **talking points**.

- [1. Kafka Schema Registry (Avro)](#1-kafka-schema-registry-avro)
- [2. Exactly-once semantics — Kafka transactions + outbox](#2-exactly-once-semantics)
- [3. DLQ retry topic with exponential backoff](#3-dlq-retry-topic)
- [4. CQRS + read model (Elasticsearch)](#4-cqrs--read-model)
- [5. Event sourcing on the Order aggregate](#5-event-sourcing)
- [6. Transactional outbox + Debezium CDC](#6-debezium-cdc)

---

## Target architecture after Tier 2

```
                ┌───────────────────────────────────────────────────────────┐
                │                 KAFKA + SCHEMA REGISTRY                   │
                │                                                           │
                │   Schema Registry :8081   ← Avro schemas versioned here   │
                │                                                           │
                │   Topics:                                                 │
                │     order.events.v1          (event-sourced stream)       │
                │     order.created.avro       (versioned business event)   │
                │     payment.commands/replies (saga)                       │
                │     *.retry.5s | *.retry.1m  (DLQ retry tiers)            │
                │     *.DLQ                    (poison store)               │
                └───────────────────────────────────────────────────────────┘
                           ▲                 │              ▲
                           │ produce          │ consume      │ consume
                           │ (Avro)           │ (Avro)       │ raw
    ┌────────────────┐     │                 ▼              │
    │ Order service  │─────┤          ┌──────────────┐      │
    │                │     │          │ Query API    │      │
    │  WRITE SIDE    │     │          │ (read model) │      │
    │  ──────────    │     │          └──────┬───────┘      │
    │  JPA write     │     │                 │              │
    │   ├─ orders    │     │                 ▼              │
    │   └─ outbox    │     │          ┌──────────────┐      │
    │                │     │          │Elasticsearch │      │
    │  Event-sourced │     │          │ order_search │      │
    │   ├─ events[]  │─────┘          └──────────────┘      │
    │   └─ snapshots │                        ▲             │
    │                │                        │             │
    └───────┬────────┘                        │             │
            │ WAL                 ┌───────────┴──────┐      │
            ▼                     │  Projector svc   │      │
    ┌────────────────┐            │  consumes events │      │
    │ Postgres       │            │  → Elasticsearch │      │
    │                │            └──────────────────┘      │
    └───────┬────────┘                                      │
            │ WAL stream                                    │
            ▼                                               │
    ┌────────────────┐                                      │
    │ Debezium       │─────── CDC events ──────────────────▶│
    │ connector      │        (replaces OutboxRelay)        │
    └────────────────┘                                      │
                                                   ┌────────┴────────┐
                                                   │ Retry Dispatcher│
                                                   │ 5s → 1m → 5m    │
                                                   │ → DLQ (poison)  │
                                                   └─────────────────┘
```

**New infra:**  Schema Registry · Debezium Connect · Elasticsearch · optionally Postgres (replaces H2).
**New services:**  a tiny `order-query` service (CQRS read side), a `retry-dispatcher`.

---

## 1. Kafka Schema Registry (Avro)

### What it is
A central HTTP service that stores schemas by subject (usually one per topic).
Producers send `(magicByte + schemaId + payload)`; consumers look up the
schema by id, deserialize into generated code. Confluent and Red Hat both ship
registries speaking the same wire protocol.

Avro gives you:
- **Compact binary** (smaller than JSON on the wire).
- **Schema evolution rules** — add nullable field = backward compatible; rename
  = incompatible. Registry rejects bad changes at *publish* time, not at 3 a.m.
  when a consumer explodes.

### Why interviewers ask
"How do you evolve an event schema without breaking 12 downstream consumers?"
JSON gives you no answer. Avro + registry gives you a *contract* enforced
before deploy.

### Current state in this repo
- ❌ No registry, no Avro. Events are JSON strings over Kafka
  (`OrderCreatedEvent`, `PaymentCompletedEvent`).
- ❌ A rename in `common-lib` can silently break any consumer not updated in
  lock-step.

### Implementation steps

**1.1 Add Schema Registry to docker-compose**
```yaml
schema-registry:
  image: confluentinc/cp-schema-registry:7.6.1
  container_name: schema-registry
  depends_on: [kafka]
  ports: ["8081:8081"]
  environment:
    SCHEMA_REGISTRY_HOST_NAME: schema-registry
    SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: PLAINTEXT://kafka:29092
    SCHEMA_REGISTRY_LISTENERS: http://0.0.0.0:8081
  networks: [backend]
```

**1.2 Add Avro Maven plugin + runtime deps to common-lib**
```xml
<dependency>
  <groupId>io.confluent</groupId>
  <artifactId>kafka-avro-serializer</artifactId>
  <version>7.6.1</version>
</dependency>
<plugin>
  <groupId>org.apache.avro</groupId>
  <artifactId>avro-maven-plugin</artifactId>
  <version>1.11.3</version>
  <executions>
    <execution>
      <goals><goal>schema</goal></goals>
      <configuration>
        <sourceDirectory>src/main/avro</sourceDirectory>
        <outputDirectory>target/generated-sources/avro</outputDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```
Add Confluent's repository to the parent pom (not on Maven Central):
```xml
<repositories>
  <repository>
    <id>confluent</id>
    <url>https://packages.confluent.io/maven/</url>
  </repository>
</repositories>
```

**1.3 Define the first Avro schema**
```json
// common-lib/src/main/avro/OrderCreatedEvent.avsc
{
  "type": "record",
  "name": "OrderCreatedEvent",
  "namespace": "com.example.common.event.avro",
  "fields": [
    {"name": "eventId", "type": "string"},
    {"name": "orderId", "type": "string"},
    {"name": "productId", "type": "long"},
    {"name": "quantity", "type": "int"},
    {"name": "amount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2}},
    {"name": "createdAt", "type": {"type": "long", "logicalType": "timestamp-millis"}}
  ]
}
```

**1.4 Switch the producer**
```yaml
spring.kafka.producer:
  key-serializer: org.apache.kafka.common.serialization.StringSerializer
  value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
  properties:
    schema.registry.url: http://schema-registry:8081
    auto.register.schemas: false        # production: schemas registered via CI, NOT by apps
```

Switch consumer to `KafkaAvroDeserializer` + `specific.avro.reader=true`.

**1.5 Set compatibility rules per subject**
```bash
curl -X PUT -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data '{"compatibility":"BACKWARD"}' \
  http://localhost:8081/config/order.created-value
```
Modes:
- `BACKWARD` — new consumer can read old data. Default. Most common.
- `FORWARD` — old consumer can read new data (producers evolve first).
- `FULL` — both. Hardest to meet.
- `NONE` — anything goes. Only for a greenfield topic you haven't shipped yet.

### Verify
- `curl http://localhost:8081/subjects` lists `order.created-value`.
- Add a nullable field, redeploy producer, old consumer still works.
- Rename a field, registry rejects the publish with 409.

### Interview talking points
- **Why Avro over Protobuf** — Avro's registry integration and JSON-shaped
  schemas win on JVM. Protobuf wins on gRPC and polyglot shops.
- **Who owns the schema** — teams, not platform. Registry is a lint check,
  not permission.
- **Schema-first vs code-first** — Avro schema is the source of truth; the
  Java class is generated. Code-first (POJO → schema) looks easier until you
  evolve.
- **The id trick** — the first few bytes of every message are the schema id.
  Consumers never need to pre-know schemas; they fetch on demand and cache.
- **Pitfall:** `auto.register.schemas=true` in dev is lovely; in prod it lets
  a buggy producer publish a schema that fails compat later. Register via CI.

---

## 2. Exactly-once semantics

### What it is
Three delivery guarantees:
- **At-most-once** — fire and forget. Fast. Loses messages on failure.
- **At-least-once** — retry on failure. Can duplicate.
- **Exactly-once** — never lose, never duplicate.

Kafka exactly-once requires **three** things working together:
1. **Idempotent producer** (`enable.idempotence=true`) — producer retries don't duplicate on the broker.
2. **Transactions** (`transactional.id` set) — a batch of sends commits atomically.
3. **Read-committed consumer** (`isolation.level=read_committed`) — consumer skips aborted/in-flight transactions.

### Why interviewers ask
"If an order is created, must the OrderCreated event appear in Kafka —
exactly once?" Right answer: outbox + transactional producer, and understand
why "exactly-once" is really "effectively-once with dedup on the consumer."

### Current state in this repo
- ✅ Idempotent producer: `enable.idempotence=true` set.
- ✅ Transactional producer: `transaction-id-prefix: order-service-tx-`.
- ✅ Transactional outbox: `OutboxRelay.drain()` wraps batch in `executeInTransaction`.
- ✅ Consumer dedup: `IdempotencyGuard` claims `(eventId, consumer)` before processing.
- ✅ `isolation.level: read_committed` on cloud-stream consumers.
- ⚠️ **Missing documentation** of why each piece is needed; interviewers will
  grill you on this. Build a one-page diagram.

### Implementation steps (hardening, not greenfield)

**2.1 Verify transaction prefix uniqueness per instance**
```yaml
# PROBLEM TODAY — all instances share "order-service-tx-"
spring.kafka.producer.transaction-id-prefix: order-service-tx-

# FIX — suffix by pod/host name so two instances don't fence each other
spring.kafka.producer.transaction-id-prefix: order-service-tx-${HOSTNAME:local}-
```
Why: two producers with the same `transactional.id` cause the newer one to
fence the older. On rolling deploys that means outbox throughput halves,
then quarters. In K8s, use the pod name; locally, hostname is fine.

**2.2 Add a smoke test that proves atomicity**
```java
@Test
void outboxBatchIsAtomic() {
  // Arrange: inject a broker fault after 3 of 10 sends.
  // Act: insert 10 outbox rows, trigger drain().
  // Assert: 0 messages visible to a read_committed consumer.
  //         Rows still PENDING (not SENT).
  //         Second drain() with no fault → all 10 appear atomically.
}
```

**2.3 Prove consumer dedup is required even with EOS**
Document the "DB tx commits but Kafka tx aborts" window: outbox rows marked
SENT get re-sent next poll. Dedup catches it.

**2.4 Add a metrics panel for EOS health**
```promql
# transactions committed vs aborted
rate(kafka_producer_record_send_total[1m])
rate(kafka_producer_transaction_abort_total[1m])
# consumer stuck at an aborted tx
kafka_consumer_fetch_manager_records_lag_max
```

### Verify
- Kill the service mid-batch (`docker kill order-service` during a load test).
  No partial batches visible to consumers. On restart, batches resume.
- Grep logs for `TransactionalId .* is already in use` — if you see it, two
  instances are sharing a prefix. Fix 2.1.

### Interview talking points
- **"Exactly-once" is a lie, politely restated.** You get: at-least-once
  delivery + idempotent consumers + atomic producer batches = effectively-once.
- **Why idempotence alone isn't enough** — it dedups producer *retries* on the
  same broker partition. It does NOT dedup across your code calling `send()`
  twice.
- **Why transactions ≠ exactly-once alone** — a consumer not using
  `read_committed` sees aborted-tx records.
- **Cost** — transactions add ~1 ms per batch (coordinator round-trip) and
  disable zero-copy on the broker's read path for `read_committed` consumers.
  Measure before enabling globally.
- **The outbox is the real EOS story.** DB tx + Kafka tx are independent; the
  outbox turns "did it also publish" into a DB question you can answer.

---

## 3. DLQ retry topic

### What it is
When a consumer fails to process a message (poison pill, downstream outage),
instead of dropping it on the floor, send it to a **DLQ** (dead-letter queue).
Smart DLQ handling isn't "log and page" — it's **tiered retries**:

```
Topic T  → consumer fails →  T.retry.5s   (sleeps, re-publishes to T)
                             T.retry.1m   (next tier)
                             T.retry.5m
                             T.DLQ        (poison; human review)
```

Each retry topic has a consumer that waits `delay` then republishes to the
original topic. Attempts tracked in headers.

### Why interviewers ask
"Payment gateway returns 500. What happens to the message?" Novice answer:
"we retry." Senior answer: "in-process retry 3× with backoff, then to a 5-second
retry topic, then 1-minute, then 5-minute, then DLQ. Operator can replay the
DLQ. Attempt count in headers, with a cap."

### Current state in this repo
- ✅ Spring Cloud Stream in-process retry: `max-attempts: 3`, exponential backoff.
- ✅ DLQ enabled: `enableDlq: true`, topics named `error.<topic>.<group>`.
- ✅ `DlqObserver` logs anything landing in DLQ.
- ❌ No **retry topics** between in-process retry and DLQ.
- ❌ No **admin UI** to inspect / replay DLQ.
- ❌ No persistence of DLQ messages; they're just logged.

### Implementation steps

**3.1 Add a `dlq_events` table**
```sql
CREATE TABLE dlq_events (
  id              BIGSERIAL PRIMARY KEY,
  original_topic  VARCHAR(255) NOT NULL,
  partition       INT NOT NULL,
  offset          BIGINT NOT NULL,
  key             VARCHAR(255),
  payload         TEXT,
  headers         JSONB,
  exception_class VARCHAR(255),
  exception_msg   TEXT,
  received_at     TIMESTAMPTZ DEFAULT NOW(),
  status          VARCHAR(32) NOT NULL DEFAULT 'NEW'  -- NEW / REPLAYED / ACKNOWLEDGED
);
```

**3.2 Promote `DlqObserver` to a persister**
Instead of logging, insert a row. Expose `GET /admin/dlq`, `POST /admin/dlq/{id}/replay`, `POST /admin/dlq/{id}/ack`.

**3.3 Add retry topics**
```yaml
spring.cloud.stream.bindings:
  paymentCompleted-in-0:
    destination: payment.completed
    group: order-service
    consumer:
      max-attempts: 3                   # in-process first
      back-off-initial-interval: 500
  # After in-process retries exhaust, poison → payment.completed.retry.5s
  # A dedicated consumer sleeps 5s then publishes back to payment.completed
```

Approach A — **spring-retry topics** (built into Spring Kafka >= 3):
```java
@RetryableTopic(
    attempts = "4",
    backoff = @Backoff(delay = 5_000, multiplier = 10),  // 5s, 50s, 500s
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    autoCreateTopics = "true"
)
@KafkaListener(topics = "payment.completed", groupId = "order-service")
public void onPaymentCompleted(PaymentCompletedEvent ev) { ... }
```
Spring auto-creates `payment.completed-retry-0`, `-retry-1`, `-dlt`.

Approach B — **manual retry consumer** (more control). One service `retry-dispatcher` with three listeners, each sleeping then forwarding.

**3.4 Attempt counter in headers**
Every retry increments `x-attempt-count`. The dispatcher checks it; above cap → send to DLQ instead of next retry tier.

**3.5 Admin UI**
Tiny Thymeleaf page: table of DLQ rows, columns = topic/key/exception, buttons = replay / acknowledge.

### Verify
- Push a message that always throws. Watch it move: `payment.completed` → retry (3 in-process) → `payment.completed.retry.5s` → wait 5s → back to original → retry → `.retry.1m` → eventually DLQ.
- `GET /admin/dlq` returns the row. `POST .../replay` publishes it back to the original topic.
- Metrics panel: `kafka_consumer_dlq_messages_total` increments by one.

### Interview talking points
- **"Retry forever" is the wrong default.** It masks real bugs and clogs
  partitions. Caps + tiers + DLQ is the pattern.
- **Why not just sleep in the consumer?** Blocking the consumer thread blocks
  every partition it owns. A separate retry topic lets other messages flow.
- **Headers over payloads for metadata.** Attempt count, original topic,
  first-seen timestamp live in headers so they survive serialization changes.
- **DLQ ≠ failure dumping ground.** It's a human-review queue. If it fills up
  silently it's just as bad as dropping messages.
- **Replay safety requires consumer idempotency.** You already have it
  (`IdempotencyGuard`). Say so when asked.

---

## 4. CQRS + read model

### What it is
**Command Query Responsibility Segregation** — split the write side (strict
schema, transactions, business rules) from the read side (denormalized,
query-optimized, eventually consistent).

```
Writes                           Reads
─────                            ─────
POST /orders                     GET /orders?customerName=…&status=…&dateRange=…
     │                                    │
     ▼                                    ▼
Postgres orders table             Elasticsearch order_search index
  normalized                        denormalized: order + customer + products
  ACID                              nested, searchable, aggregatable
     │                                    ▲
     └─── OrderCreatedEvent ──────────────┘
          via Kafka
          projector consumes
          and upserts into ES
```

### Why interviewers ask
"Your product catalog has 10M rows and your search is slow. What do you do?"
A real answer involves a read model. "How do you keep them in sync?" is the
follow-up — events.

### Current state in this repo
- ❌ No read model. Reads hit the same JPA entity as writes.
- ✅ Events already published for the key aggregates — so a projector is just a consumer.

### Implementation steps

**4.1 Add Elasticsearch to compose**
```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.14.3
  container_name: elasticsearch
  environment:
    - discovery.type=single-node
    - xpack.security.enabled=false
    - ES_JAVA_OPTS=-Xms512m -Xmx512m
  ports: ["9200:9200"]
  networks: [backend]
```

**4.2 Create a tiny `order-query` service**
```
order-query/
  pom.xml                          spring-boot-starter-data-elasticsearch + common-lib
  OrderQueryApplication.java
  model/OrderDoc.java              @Document(indexName="orders_v1")
  repo/OrderSearchRepository.java  extends ElasticsearchRepository<OrderDoc,String>
  web/OrderQueryController.java    GET /orders/search?q=…&status=…
  projection/OrderProjector.java   @KafkaListener(topics="order.created") → upsert OrderDoc
```

**4.3 Projector shape**
```java
@KafkaListener(topics = "order.created", groupId = "order-query-projector")
public void on(OrderCreatedEvent ev) {
  if (!guard.claim(ev.eventId(), "order-query")) return;  // dedup
  OrderDoc doc = OrderDoc.builder()
      .orderId(ev.orderId()).productId(ev.productId())
      .status("CREATED").createdAt(ev.createdAt())
      .build();
  repo.save(doc);  // upsert
}
```

**4.4 Query endpoint**
```
GET /orders/search?q=widget&status=CREATED&from=2025-01-01&to=2025-12-31
```
Backed by `NativeQueryBuilder` doing a multi-match + status filter + date range.

**4.5 Resync job**
Projector can be behind for N reasons (DLQ, Kafka outage). Add
`POST /admin/projector/rebuild` that reads the WRITE DB, publishes synthetic
events to a rebuild topic, projector consumes, index is rebuilt. Interview
answer to "how do you handle a drifted read model?"

### Verify
- Create 10 orders via `POST /orders`.
- Query `GET /orders/search?q=widget` on port 8085 (the new service) returns them.
- Stop the projector, create 5 more, start projector — those 5 appear within seconds.
- Rebuild endpoint: delete the index, hit `/admin/projector/rebuild`, index refills from Postgres.

### Interview talking points
- **Why CQRS** — read and write have different access patterns. Writes want
  strict schema; reads want fast, flexible queries. Splitting lets each side
  pick its best storage.
- **Consistency** — reads are *eventually consistent*. The gap is Kafka
  latency + projection time (usually < 1 s). Products / dashboards tolerate
  it; checkout totals don't.
- **Why not just add indexes to the write DB** — ES does fuzzy, nested,
  aggregations, geo, autocomplete. SQL can't match without serious pain.
- **Read-your-writes** — right after `POST /orders`, a `GET /orders/search`
  may not see it. Options: write-through to both (loses async benefit),
  session-sticky caching, or just UX ("your order was received, will appear
  in search shortly").
- **Rebuild from events ≠ event sourcing.** CQRS reuses the event stream but
  the write side is still state-based (JPA). Section 5 is where state is
  fully derived from events.

---

## 5. Event sourcing

### What it is
Instead of storing current state (`orders.status = PAID`), store the
**sequence of events** that produced it. Current state = replay events.

```
Traditional (state-based)
    orders table
    ┌─────────┬────────┬────────┐
    │ id      │ status │ amount │
    │ o-1     │ PAID   │ 29.97  │
    └─────────┴────────┴────────┘
     ↑ one row, lose history on UPDATE

Event sourcing
    order_events table
    ┌─────┬──────────┬─────────────────┬─────────┐
    │ seq │ order_id │ event_type      │ payload │
    │ 1   │ o-1      │ OrderCreated    │ {...}   │
    │ 2   │ o-1      │ PaymentReserved │ {...}   │
    │ 3   │ o-1      │ OrderPaid       │ {...}   │
    └─────┴──────────┴─────────────────┴─────────┘
     ↑ append-only, every state change preserved

To get current state: SELECT ... WHERE order_id='o-1' ORDER BY seq;
                      fold events through Order.apply(event) → aggregate state
```

Add **snapshots** every N events so loads don't replay 10,000 events every time.

### Why interviewers ask
Fintech, trading, healthcare, auditable workflows. "Can you prove the state
of this order on 2024-03-14?" Only event sourcing answers that cleanly.

### Current state in this repo
- ❌ Pure state-based. `Order` has `status`; old statuses are lost on UPDATE.

### Implementation steps (narrow scope — Order aggregate only)

**5.1 Design the event stream**
```
OrderCreated      {orderId, productId, quantity, amount}
PaymentReserved   {orderId, paymentId}
PaymentFailed     {orderId, reason}
PaymentCompleted  {orderId, chargedAt}
OrderCancelled    {orderId, reason}
OrderFulfilled    {orderId, trackingNo}
```

**5.2 Event store table**
```sql
CREATE TABLE order_events (
  seq         BIGSERIAL PRIMARY KEY,
  order_id    VARCHAR(64) NOT NULL,
  event_type  VARCHAR(64) NOT NULL,
  payload     JSONB NOT NULL,
  version     INT NOT NULL,              -- aggregate version for optimistic concurrency
  occurred_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (order_id, version)              -- prevent concurrent writes clashing
);
CREATE INDEX idx_order_events_order ON order_events(order_id, seq);

CREATE TABLE order_snapshots (
  order_id    VARCHAR(64) PRIMARY KEY,
  version     INT NOT NULL,
  state       JSONB NOT NULL,
  taken_at    TIMESTAMPTZ DEFAULT NOW()
);
```

**5.3 Aggregate shape**
```java
public class OrderAggregate {
  private String id;
  private OrderStatus status;
  private int version;

  public static OrderAggregate loadFromEvents(List<OrderEvent> events) {
    OrderAggregate agg = new OrderAggregate();
    events.forEach(agg::apply);
    return agg;
  }
  public List<OrderEvent> handle(CreateOrderCmd cmd) { … returns new events … }
  public List<OrderEvent> handle(ReservePaymentCmd cmd) { … }
  private void apply(OrderEvent event) { … state transition … }
}
```

**5.4 Repository**
```java
public class EventSourcedOrderRepository {
  public OrderAggregate load(String orderId) {
    Optional<Snapshot> snap = snapshotRepo.findLatest(orderId);
    List<OrderEvent> events = eventRepo.findSinceVersion(
        orderId, snap.map(Snapshot::version).orElse(0));
    OrderAggregate agg = snap.map(OrderAggregate::fromSnapshot)
                             .orElseGet(OrderAggregate::new);
    events.forEach(agg::apply);
    return agg;
  }
  public void save(OrderAggregate agg, List<OrderEvent> newEvents) {
    // optimistic concurrency: INSERTs will fail unique(order_id, version) on race
    eventRepo.appendAll(newEvents);
    if (newEvents.size() + agg.version() - lastSnapVersion >= 50) {
      snapshotRepo.save(new Snapshot(agg.id(), agg.version(), toJson(agg)));
    }
  }
}
```

**5.5 Publish the events to Kafka too**
Append to event store AND to the outbox in the same tx. The outbox relay
publishes to Kafka. Projectors (CQRS read side) build the read model from
those events.

### Verify
- Create → pay → cancel sequence yields 3 rows in `order_events`.
- `GET /orders/{id}` loads the aggregate from events; returns `CANCELLED`.
- Insert a snapshot at version 2; load now reads snapshot + 1 event instead of 3.
- Force a concurrent write (two threads append from version 2) — one wins, the other gets `UniqueConstraintViolationException`.

### Interview talking points
- **When event sourcing wins** — audit trail, temporal queries, undo /
  compensating actions, systems where "why" matters as much as "what."
- **When it loses** — simple CRUD, teams not ready for the mental model
  shift. The cost is real: migrations, schema evolution of events, replay
  performance, debugging snapshots.
- **Snapshots** — not an optimization, a requirement at scale. Rule of thumb:
  snapshot every N events (50–100) OR when load time exceeds budget.
- **Event migrations** — once published, events are immutable. If you need
  to "fix" an event, publish an **upcaster** that transforms old events into
  the new shape on read.
- **CQRS fits naturally** — the event stream feeds projections. The two
  patterns reinforce each other; you don't need both, but together they're a
  common pairing.
- **Compare to outbox** — outbox publishes current-state events as side
  effects. Event sourcing *is* the state. Different scopes.

---

## 6. Debezium CDC

### What it is
**Change Data Capture** — read the database's write-ahead log (WAL in
Postgres) and stream every row change as a Kafka event. Debezium is the
canonical implementation; runs as a Kafka Connect source connector.

```
Current (polling outbox)
    OrderService.create()
       │
       ▼
    JPA INSERT orders         ─── one DB round-trip per outbox poll
    JPA INSERT outbox_event   ─── OutboxRelay polls every 500 ms
                                   batches, publishes to Kafka
                                   latency: avg 250 ms, max 500 ms
                                   DB load: constant polling

CDC (Debezium)
    OrderService.create()
       │
       ▼
    JPA INSERT orders         ─── Postgres WAL
    JPA INSERT outbox_event   ───   │
                                    ▼
                              Debezium reads WAL in real time
                              publishes to Kafka
                              latency: < 50 ms typical
                              DB load: zero polling
```

### Why interviewers ask
It's the modern replacement for polling outboxes at scale. Also used for
database replication, cache invalidation, data warehouse ingestion.

### Current state in this repo
- ✅ Outbox exists (polling via `OutboxRelay`).
- ❌ No Debezium. Polling works but has latency floor + DB load.

### Implementation steps

**6.1 Switch order-service from H2 to Postgres**
Debezium needs WAL. H2 doesn't do CDC.
```yaml
# docker-compose.yml addition
postgres-order:
  image: debezium/postgres:15        # Debezium's variant has logical replication enabled
  environment:
    POSTGRES_DB: orderdb
    POSTGRES_USER: order
    POSTGRES_PASSWORD: order
  ports: ["5433:5432"]
  networks: [backend]
  command: ["postgres", "-c", "wal_level=logical"]
```
Point order-service DataSource at it; drop H2.

**6.2 Kafka Connect + Debezium**
```yaml
kafka-connect:
  image: debezium/connect:2.7
  container_name: kafka-connect
  depends_on: [kafka, schema-registry, postgres-order]
  ports: ["8083:8083"]
  environment:
    BOOTSTRAP_SERVERS: kafka:29092
    GROUP_ID: connect
    CONFIG_STORAGE_TOPIC: connect_configs
    OFFSET_STORAGE_TOPIC: connect_offsets
    STATUS_STORAGE_TOPIC: connect_statuses
    KEY_CONVERTER: io.confluent.connect.avro.AvroConverter
    VALUE_CONVERTER: io.confluent.connect.avro.AvroConverter
    CONNECT_KEY_CONVERTER_SCHEMA_REGISTRY_URL: http://schema-registry:8081
    CONNECT_VALUE_CONVERTER_SCHEMA_REGISTRY_URL: http://schema-registry:8081
  networks: [backend]
```

**6.3 Register the connector**
```bash
curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d '{
  "name": "order-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres-order",
    "database.port": "5432",
    "database.user": "order",
    "database.password": "order",
    "database.dbname": "orderdb",
    "topic.prefix": "orderdb",
    "schema.include.list": "public",
    "table.include.list": "public.outbox_events",
    "plugin.name": "pgoutput",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.route.by.field": "destination",
    "transforms.outbox.table.field.event.payload": "payload"
  }
}'
```
The `EventRouter` SMT reads outbox rows and routes them to topics by the
`destination` column — meaning your outbox *table shape stays the same*,
but the publishing mechanism changes from `OutboxRelay` to Debezium.

**6.4 Retire `OutboxRelay`**
Keep the code behind a feature flag:
```yaml
outbox:
  polling-enabled: ${OUTBOX_POLLING:false}
```
Delete when CDC has been stable in prod for a month.

### Verify
- Create an order; `outbox_events` row appears; within 100 ms the Kafka topic
  gets the event.
- `kill -9` the Debezium connector mid-run; restart; it resumes from the WAL
  offset — no duplicates, no misses.
- Latency metrics: p99 drops from ~500 ms (polling) to ~50 ms (CDC).

### Interview talking points
- **Why CDC over polling** — real-time, no DB load from polls, no "polling
  interval vs latency" tradeoff.
- **Debezium replays from WAL offset** — survives restarts; the connector
  persists its position in Kafka itself.
- **Outbox pattern + CDC is the modern standard.** The outbox table remains
  because it decouples business write from publishing concerns; CDC just
  removes the polling.
- **Pitfalls** — WAL on the primary; CDC can lag during peak writes.
  Debezium can't catch events older than the WAL retention window — set that
  deliberately.
- **Multi-tenant / sharded DBs** — one connector per shard, one topic per
  shard. Keep ordering by (shardId, aggregateId) if you need it.

---

## Suggested build order (3 weekends)

**Weekend 1 — contracts & correctness**
1. (3 h) Schema Registry up, migrate `OrderCreatedEvent` to Avro.
2. (2 h) Document existing EOS story + add instance-safe transaction prefix.

**Weekend 2 — resilience**
3. (3 h) Retry topics + `dlq_events` table + admin UI for inspect/replay.

**Weekend 3 — data patterns**
4. (3 h) CQRS: new `order-query` service + Elasticsearch + projector.
5. (3 h) Event sourcing on `Order` aggregate (snapshots included).
6. (3 h) Replace polling outbox with Debezium (Postgres migration included).

Expect weekend 3 to overflow — these are the big-ticket interview topics.

---

## Interview-grade one-liner summary

> *"We publish Avro-encoded events through a Schema Registry so consumers can
> evolve independently under backward-compatibility rules. The producer is
> transactional and idempotent; the outbox gives us effective exactly-once
> semantics. Poisons go to tiered retry topics (5 s, 1 m, 5 m) then a DLQ
> persisted in Postgres with a replay admin UI. Reads hit an Elasticsearch
> read model fed by a projector that consumes the same event stream.
> The Order aggregate is event-sourced with snapshots every 50 events for
> load performance. In production we'd replace polling with Debezium CDC on
> the outbox table so new events reach Kafka in under 50 ms with no DB polling
> load."*

If you can say that and point to running code for each clause, you've aced
the data + messaging portion of any staff-level microservices interview.

---

## Related docs

- [tier1-roadmap.md](tier1-roadmap.md) · [tier1-architecture.md](tier1-architecture.md) — observability & resilience
- [concepts/outbox-pattern.md](concepts/outbox-pattern.md) — current polling outbox
- [concepts/dlq.md](concepts/dlq.md) — current DLQ setup
- [concepts/event-driven-basics.md](concepts/event-driven-basics.md)
- [concepts/saga-orchestration.md](concepts/saga-orchestration.md)
