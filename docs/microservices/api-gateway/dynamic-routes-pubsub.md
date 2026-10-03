# API Gateway — Route Refresh via Redis Pub/Sub

Sub-second cross-replica sync for dynamic routes. When one gateway pod adds
or removes a route, every peer refreshes within milliseconds via a Redis
pub/sub notification — instead of waiting up to 30s for the next scheduled poll.

Companion to [dynamic-routes.md](dynamic-routes.md).

---

## 1. What this fixes

```
BEFORE (polling only):

  Pod A               Pod B                   Pod C
    │                   │                       │
    │  POST /admin/routes                       │
    │  save + local refresh                     │
    │                   │  ⏱ up to 30s...       │
    │                   │  refresh (poll)       │  ⏱ up to 30s...
    │                                           │  refresh (poll)
    │
  ⚠ For up to 30s, peers serve stale route table


AFTER (pub/sub + poll fallback):

  Pod A                 Redis                  Pod B, C
    │                     │                       │
    │  POST /admin/routes │                       │
    │  save + local       │                       │
    │  refresh            │                       │
    │  broadcast ─────────▶                       │
    │                     ├── notify ────────────▶│  ⏱ ~5-50ms
    │                                             │  refresh
  ✓ All pods converge within tens of milliseconds
  ✓ 5-minute poll still runs as safety net (missed messages)
```

---

## 2. Why Redis pub/sub

You already run Redis for the rate limiter, idempotency store, and API-key
lookups. Adding pub/sub is a **zero-infra** upgrade.

| Approach | Latency | Extra infra | Complexity |
|---|---|---|---|
| Scheduled poll only (before) | up to N sec | none | low |
| **Redis pub/sub** (this) | ~5-50 ms | none (Redis already there) | low |
| Kafka topic | ~50-200 ms | Kafka broker | medium |
| Consul events | ~100 ms | Consul | medium |
| Spring Cloud Bus | ~50 ms | Bus + broker | high |

For "notify a small number of gateway pods that a route table changed,"
pub/sub is the sweet spot.

---

## 3. Redis pub/sub properties

- **Fire-and-forget** — no persistence, no consumer groups, no offsets.
- **At-most-once delivery** — subscribers must be connected when published;
  a message lost is lost.
- **Every subscriber gets every message** — no partitioning, no exclusive
  consumption.
- **FIFO per publisher connection** — but no ordering guarantee across
  publishers.

Fits our need exactly: we're broadcasting "reload your routes" — the payload
is a trigger, not data.

---

## 4. Architecture

```
                                  ┌──────────────────────────────┐
POST /admin/routes ──────▶        │  RouteAdminController        │
DELETE /admin/routes/{id}         │  1. save to DB               │
                                  │  2. publish LOCAL RefreshEvent│
                                  │  3. RouteRefreshPublisher    │────┐
                                  │     .broadcast() (fire+forget)│    │
                                  └──────────────────────────────┘    │
                                             │                        │
                                             ▼                        │
                                  ┌──────────────────────────────┐    │
                                  │  JdbcRouteDefinitionRepo     │    │
                                  │  → H2/Postgres               │    │
                                  └──────────────────────────────┘    │
                                                                      ▼
                        ┌───────────────────────────────────────────────────────┐
                        │  Redis :6379                                          │
                        │                                                       │
                        │  PUBLISH gateway.routes.refresh                       │
                        │  msg = {sourceId, timestamp, action}                  │
                        └───────────────────────────────────────────────────────┘
                                              │
                            ┌─────────────────┼─────────────────┐
                            │                 │                 │
                            ▼                 ▼                 ▼
                       Pod A subscriber  Pod B subscriber  Pod C subscriber
                            │                 │                 │
                       sourceId==self?   sourceId==self?   sourceId==self?
                            │ yes             │ no              │ no
                            │ SKIP            │                 │
                            │                 ▼                 ▼
                            │        publishEvent(       publishEvent(
                            │        RefreshRoutesEvent) RefreshRoutesEvent)
                            │                 │                 │
                            │                 ▼                 ▼
                            │        CachingRouteLocator rebuilds on B, C
                            │
                            ▼
                     (Pod A already refreshed locally in step 2)
```

---

## 5. Design decisions

### 5a. Self-echo filter (why `sourceId`)

Every pod both publishes AND subscribes to the same channel. Without a source
ID, a pod would refresh twice — once from its local event, once from the
Redis message.

```
sourceId = UUID generated per JVM at startup
On receive: if msg.sourceId == mySourceId → skip
```

Cheap self-filtering. Interview point: *"how do you avoid echo in a pub/sub
broadcast to yourself?"*

Config lets you override the ID (e.g. `HOSTNAME` in Kubernetes) — makes logs
readable.

### 5b. Notification-only payload

Two schools:

**Notification-only** (chosen):
```json
{
  "sourceId": "gw-a3f5b8...",
  "timestamp": "2026-09-29T10:30:00Z",
  "action": "REFRESH"
}
```
- Subscribers do a full `RefreshRoutesEvent` → re-read all routes from DB
- Simple, always correct, tiny message
- Every refresh = one DB scan

**Delta / payload**:
- Subscribers can update just one route locally without a DB scan
- Faster refresh, but harder to keep consistent (missed messages = stale)

Picked notification-only. DB reads are cheap; correctness matters more than
shaving a Redis round-trip.

### 5c. Keep the scheduled poll (defense in depth)

Pub/sub is at-most-once delivery. Pod could be:
- Restarting during publish
- Network-partitioned briefly
- Reconnecting after Redis restart

The poll is the eventual-consistency backstop. We lengthened it from **30s to
5min** — pub/sub handles the fast path, poll handles the tail.

Interview soundbite: *"defense in depth — pub/sub is the fast path, polling
is the correctness floor."*

### 5d. Reconnect logic

Redis disconnects (network blip, restart) will kill the subscription. Reactor's
`Retry.backoff(...)` re-subscribes with exponential backoff:

```java
.retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
        .maxBackoff(Duration.ofSeconds(30)))
```

Long.MAX_VALUE = keep trying forever. Max 30s between retries — bounded
worst-case reconnect time.

### 5e. Optional subscription — @ConditionalOnProperty gate

`gateway.dynamic-routes.pubsub.enabled` — separate switch from the main
dynamic-routes toggle. Disable = fall back to poll-only. Useful for:
- Local dev (avoid Redis chatter)
- Debugging (does the issue disappear without pub/sub?)
- Diagnosing subscription problems

### 5f. Fire-and-forget publish

`broadcast().subscribe()` — no `await`, no error propagation to the client.

Rationale:
- Save-to-DB already succeeded → the write is safe
- Local refresh already fired → this instance is correct
- Pub/sub failure is best-effort noise; poll fallback will catch it
- Blocking the admin response on Redis health is wrong

Errors logged inside the publisher; nobody upstream cares.

---

## 6. Message flow (interview-friendly timeline)

```
T=0.000s  Client → Pod A: POST /admin/routes
T=0.010s  Pod A: writes to H2 DB
T=0.011s  Pod A: publishes LOCAL RefreshRoutesEvent → CachingRouteLocator rebuild
T=0.012s  Pod A: RouteRefreshPublisher.broadcast() → Redis PUBLISH
T=0.015s  Redis broadcasts to all subscribers (A, B, C)
T=0.017s  Pod A subscriber:  sourceId == self → SKIP
T=0.020s  Pod B subscriber:  sourceId ≠ self → publish local RefreshRoutesEvent
T=0.023s  Pod B: CachingRouteLocator rebuild (reads from DB)
T=0.021s  Pod C subscriber:  same as Pod B
T=0.024s  Pod C: rebuild

All three pods converged in ~25ms (vs up to 30s before).

If Redis is down / message lost:
T=5m00s   All pods run scheduled poll (RouteRefreshScheduler)
```

---

## 7. Config

```yaml
gateway:
  dynamic-routes:
    enabled: true
    refresh-interval: 5m                     # poll fallback (was 30s)
    pubsub:
      enabled: true                          # opt-in for pub/sub
      channel: gateway.routes.refresh
      instance-id: ${HOSTNAME:${random.uuid}} # per-JVM identity
```

Two independent gates:
- `dynamic-routes.enabled` — entire stack (admin API, repo, scheduler)
- `dynamic-routes.pubsub.enabled` — just pub/sub (publisher + subscriber beans)

---

## 8. Files added / changed

```
api-gateway/
└── src/main/
    ├── java/com/example/apigateway/
    │   ├── config/
    │   │   └── DynamicRoutesProperties.java                       (added PubSub nested class)
    │   └── dynamicroutes/
    │       ├── RouteRefreshMessage.java                           (NEW — pub/sub DTO)
    │       ├── RouteRefreshPublisher.java                         (NEW — Redis PUBLISH)
    │       ├── RouteRefreshSubscriber.java                        (NEW — Redis SUBSCRIBE)
    │       ├── RouteAdminController.java                          (broadcasts after each mutation)
    │       └── RouteRefreshScheduler.java                         (unchanged — reads new 5m default)
    └── resources/
        └── application.yml                                        (pubsub block + poll → 5m)

docs/microservices/api-gateway/
└── dynamic-routes-pubsub.md                                       (this file)
```

No new dependencies — Redis reactive is already in from the rate-limiter build.

---

## 9. Verification

### 9.1 Single instance — self-echo test

```bash
mvn -pl api-gateway clean spring-boot:run

# On boot, logs should show:
#   Subscribing to route-refresh channel 'gateway.routes.refresh' as instance '<uuid>'
```

Trigger a change:
```bash
TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/admin/routes \
  -d '{
    "id": "products-preview",
    "uri": "lb://product-service",
    "predicates": [{"name":"Path","args":{"_genkey_0":"/api/v1/products-preview/**"}}]
  }'
```

Gateway logs should show:
```
Published route refresh to 'gateway.routes.refresh' — 1 subscriber(s)
Skipping self-published refresh from '<uuid>'
```

### 9.2 Two instances — real cross-pod sync

**Note**: with H2 file mode, only one process can hold the DB lock. To
demo pub/sub between two instances, either:
1. Switch to Postgres, OR
2. Use H2 TCP server mode, OR
3. Have both pods share H2 in AUTO_SERVER mode:
   `r2dbc:h2:file:///./data/gateway-routes;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE`

Terminal 1 (Pod A):
```bash
mvn -pl api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8080 \
    --gateway.dynamic-routes.pubsub.instance-id=pod-a"
```

Terminal 2 (Pod B):
```bash
mvn -pl api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8081 \
    --gateway.dynamic-routes.pubsub.instance-id=pod-b"
```

Terminal 3 (make a change on Pod A):
```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/admin/routes \
  -d '{"id":"pubsub-demo","uri":"lb://product-service",
       "predicates":[{"name":"Path","args":{"_genkey_0":"/pubsub-demo/**"}}]}'

# Immediately (within ~50ms) check Pod B for the new route
curl -s http://localhost:8081/actuator/gateway/routes | jq '.[] | select(.route_id=="pubsub-demo")'
# → shows the route WITHOUT waiting for the 5m scheduled refresh
```

Pod A logs:
```
Published route refresh to 'gateway.routes.refresh' — 2 subscriber(s)
Skipping self-published refresh from 'pod-a'
```

Pod B logs:
```
Received route refresh from 'pod-a' (action=REFRESH)
```

### 9.3 Watch Redis pub/sub live

```bash
docker exec -it gateway-redis redis-cli SUBSCRIBE gateway.routes.refresh

# In another terminal:
curl -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/routes/refresh

# You'll see the raw published message:
# 1) "message"
# 2) "gateway.routes.refresh"
# 3) "{\"sourceId\":\"pod-a\",\"timestamp\":\"2026-...\",\"action\":\"REFRESH\"}"
```

### 9.4 Redis outage — verify reconnect

```bash
docker stop gateway-redis

# Gateway logs:
#   Route refresh subscription failed (attempt 1): ... — retrying
#   Route refresh subscription failed (attempt 2): ... — retrying

# Route table keeps serving traffic — cached, DB still accessible

docker start gateway-redis
# Subscription auto-reconnects within seconds
# Publisher also recovers on next .broadcast() call
```

### 9.5 Disable pub/sub — fall back to poll

```bash
mvn -pl api-gateway spring-boot:run \
  -Dspring-boot.run.arguments="--gateway.dynamic-routes.pubsub.enabled=false"

# Publisher + Subscriber beans absent. Multi-pod sync via 5m poll only.
```

---

## 10. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| Redis down at startup | Subscriber connect fails, retries with backoff | `Retry.backoff(...)` (built in) |
| Redis down mid-flight | Existing subscription errors, reconnects | Same retry logic |
| Redis restarts | Subscription drops, reconnects on retry | Same |
| Message lost (network blip) | Pod misses that refresh | 5-minute poll fallback catches it |
| Self-echo | Pod would refresh twice on its own publish | `sourceId` filter present |
| Bad JSON payload | Message dropped, next one still processes | `try/catch` in `handle()` |
| Two pods with same instance-id | They filter each other's messages as self | Use `${HOSTNAME}` in Kubernetes, or fall back to UUID |
| Publish before peer subscribed | Peer misses the message | Poll fallback |
| High-volume changes (100/sec) | Every change = full DB refresh | Debounce: `.buffer(Duration.ofMillis(100))` on receive |
| Bad DB state → refresh reads corrupted routes | Route not activated (existing `onErrorContinue` in repo) | Present in `JdbcRouteDefinitionRepository` |

---

## 11. Interview cheat-sheet

| Question | Answer |
|---|---|
| Why Redis pub/sub? | Fire-and-forget notification, low latency, zero extra infra (already have Redis). |
| Why not Kafka/Streams? | Overkill — we don't need durability, replay, consumer groups, or partitioning. |
| Why keep the poll fallback? | Pub/sub is at-most-once. Poll = eventual-consistency floor. Defense in depth. |
| How do you avoid self-echo? | Per-JVM `sourceId`; receivers skip messages matching their own id. |
| Payload strategy — notification or delta? | Notification only. Full DB re-read on each. Simpler and always correct. |
| What if Redis dies? | Subscribers reconnect with exponential backoff. Route table keeps serving (cached). Poll fallback keeps replicas eventually consistent. |
| Message ordering across pods? | Not guaranteed. Doesn't matter — every message is idempotent (reload everything). |
| Debouncing chatty admins? | Yes — `Flux.buffer(100ms)` on receive → coalesce multiple messages into one refresh. |
| Scale to 100 pods? | Each pod = one subscriber. Redis pub/sub linear. Fine. For thousands, switch to Redis Streams with consumer groups. |
| Latency vs polling? | 5-50ms vs 30s. ~600× faster on p50. |
| Message contract? | Small JSON DTO with `sourceId, timestamp, action`. Versionable — add fields freely. |
| Fire-and-forget publish — correct? | Yes. Save-to-DB already succeeded, local refresh already fired. Pub/sub is best-effort; poll picks up misses. |
| Why `ApplicationReadyEvent` instead of `@PostConstruct` for subscribe? | Ensures Spring context is fully up (including Redis connection) before we try to subscribe. |

---

## 12. Common pitfalls (interview probes)

1. **Missing self-echo filter** → double refreshes on every publish
2. **Subscribing in `@PostConstruct`** → Spring context not ready; Redis connection may not exist
3. **Forgetting `@PreDestroy` cleanup** → subscription leaks across hot reloads
4. **Blocking in subscriber `handle()`** → blocks Redis I/O thread. Publish local events; don't do DB calls inline.
5. **Assuming delivery guarantees** → pub/sub is at-most-once. If correctness depends on it, need Streams (or a fallback like our poll).
6. **Instance ID collision** → default to UUID unless you can guarantee uniqueness (e.g. Kubernetes pod name).
7. **Publish-before-subscribe race** → first-pod-up scenario. Poll fallback covers this.
8. **`broadcast().subscribe()` swallowing errors** → intentional (fire-and-forget), but errors must be logged inside the publisher so ops can see them.

---

## 13. Extensions (parked)

- **Debouncing** — coalesce N messages in 100ms into one refresh. Reduces DB scans when admins run bulk imports.
- **Structured payload** — include `routeId` + `action` (UPSERT/DELETE) for delta updates instead of full reloads.
- **Health check** — expose `/actuator/health` component reporting pub/sub connection status + subscriber count.
- **Metrics** — Micrometer counters: `route.refresh.published`, `route.refresh.received`, `route.refresh.self_skipped`; gauge for subscriber count from PUBSUB NUMSUB.
- **Backpressure protection** — cap refresh rate (e.g. 1/sec) even if Redis floods.
- **Multi-env channels** — separate channels per environment (`gateway.routes.refresh.prod`) to isolate blast radius.
- **Payload signing** — HMAC-sign messages so subscribers reject forged/replayed ones (only relevant if Redis is on an untrusted network).
- **Adaptive poll interval** — extend to 30min if pub/sub is healthy, drop to 30s if pub/sub errors are frequent.
