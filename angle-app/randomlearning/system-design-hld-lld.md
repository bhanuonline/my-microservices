# System Design — HLD & LLD Complete Reference

A ready-to-quiz reference on High-Level Design (HLD) and Low-Level Design (LLD) — what they are, how they differ, what to include in each, worked examples, and interview questions.

---

## One-liner definitions

- **HLD (High-Level Design)** = the **architecture** — *what* components exist and *how* they talk to each other. Diagrams, not code.
- **LLD (Low-Level Design)** = the **detailed design** — *how* each component works inside. Classes, methods, database schemas.

## The mental model

```
HLD  →  "What are the buildings and roads in the city?"
LLD  →  "What are the rooms and wiring inside each building?"
```

---

## Side-by-side comparison

| Aspect | HLD | LLD |
|---|---|---|
| **Scope** | Whole system | One component / module |
| **Audience** | Architects, PMs, senior devs | Implementing developer |
| **Abstraction** | Services, databases, queues | Classes, methods, tables |
| **Diagrams** | Architecture, deployment, data flow | Class, ER, sequence |
| **Time spent** | Days-weeks (upfront) | Hours-days per component |
| **Changes often?** | Rarely (major decisions) | Yes (each feature) |
| **Code-adjacent?** | No | Yes |
| **Answers question** | "How does the system work?" | "How is X implemented?" |
| **Written in** | Confluence/notion/whiteboard | UML tools, code comments, ADRs |
| **Interview level** | Senior / Staff | Mid / Senior |

---

# PART 1 — HLD (HIGH-LEVEL DESIGN)

## What HLD covers

1. **Overall architecture** — services, databases, message queues, caches, load balancers
2. **How components interact** — REST, gRPC, event streams, database reads
3. **Technology choices** — "we use PostgreSQL for orders, Redis for sessions, Kafka for events"
4. **Non-functional requirements (NFRs)** — scalability, availability, latency, throughput
5. **Data flow** — how a request travels end-to-end
6. **Deployment topology** — regions, availability zones, redundancy
7. **Capacity estimation** — QPS, storage, bandwidth

## HLD building blocks — know these components

| Component | Purpose | Example tech |
|---|---|---|
| **Load balancer** | Distribute traffic across servers | NGINX, AWS ALB, HAProxy |
| **API gateway** | Auth, routing, rate limiting, single entry point | Kong, AWS API Gateway |
| **CDN** | Cache static content close to users | Cloudflare, Akamai, CloudFront |
| **Cache** | Fast lookup of frequently accessed data | Redis, Memcached |
| **Relational DB** | Structured data with strong consistency | PostgreSQL, MySQL |
| **NoSQL DB** | Flexible schema, horizontal scale | MongoDB, DynamoDB, Cassandra |
| **Message queue** | Async communication between services | Kafka, RabbitMQ, SQS |
| **Object store** | Large blobs (images, videos, files) | S3, GCS, Azure Blob |
| **Search engine** | Full-text search | Elasticsearch, OpenSearch |
| **Job scheduler** | Batch and cron jobs | Airflow, Quartz, K8s CronJobs |
| **WebSocket layer** | Real-time bidirectional communication | Socket.io, AWS API Gateway WS |

## Core concepts every HLD interview tests

### 1. Scalability

- **Vertical scaling** (scale up) — bigger machine. Simple, limited, expensive at the top end
- **Horizontal scaling** (scale out) — more machines. Complex but essentially unlimited. Requires stateless services

### 2. Availability

Measured in "nines":
- **99% (2 nines)** — 3.65 days downtime/year
- **99.9% (3 nines)** — 8.7 hours downtime/year
- **99.99% (4 nines)** — 52 minutes downtime/year
- **99.999% (5 nines)** — 5 minutes downtime/year (rarely achievable, very expensive)

Techniques: redundancy, failover, multi-region, health checks, circuit breakers.

### 3. Consistency

- **Strong consistency** — every read sees the latest write. Slow, easy to reason about.
- **Eventual consistency** — reads may see stale data briefly. Fast, scales well, needs conflict resolution.
- **Read-your-writes** — a user's own writes are always visible to their subsequent reads.
- **Session consistency** — within a session, consistent view.

### 4. CAP theorem

You can only pick 2 of 3:
- **Consistency** — all reads see the same latest data
- **Availability** — every request gets a response
- **Partition tolerance** — system continues despite network partitions

In practice: partition tolerance is a given (networks fail), so it's really CP or AP. Real systems tune per operation.

### 5. Latency vs throughput

- **Latency** = time for a single request (P50, P95, P99)
- **Throughput** = requests per second (RPS)

Optimizing one often hurts the other. Cache reduces latency; batching increases throughput.

### 6. Database scaling patterns

- **Read replicas** — one primary write, N read replicas. Great for read-heavy workloads.
- **Sharding** — split data across databases by key (user_id, geographical region). Solves write scaling.
- **Vertical partitioning** — split tables (columns) into hot and cold data.
- **Federation** — split by feature/service (users DB, orders DB, catalog DB).

### 7. Caching strategies

- **Cache-aside** (most common) — app checks cache first; on miss, reads DB and populates cache
- **Read-through** — cache library reads DB on miss transparently
- **Write-through** — writes go through cache to DB synchronously
- **Write-behind** — writes go to cache; DB updated asynchronously
- **Refresh-ahead** — cache proactively refreshes before expiry

Eviction: LRU, LFU, TTL-based.

### 8. Messaging patterns

- **Pub/sub** (Kafka topics, SNS) — one publisher, many subscribers, decoupled
- **Point-to-point** (SQS, RabbitMQ queues) — one producer, one consumer per message
- **Request-reply** — async request with correlation ID
- **Event sourcing** — store events, not state; derive current state by replay
- **CQRS** — separate read model from write model

### 9. Communication patterns

- **Synchronous** — REST, gRPC. Simple, tight coupling, cascading failures
- **Asynchronous** — message queues. Loose coupling, harder to debug
- **Streaming** — Kafka, WebSockets. Continuous data flow

## Capacity estimation (back-of-the-envelope math)

Interviewers **always** expect this. Learn the powers of 10:

| Metric | Value |
|---|---|
| 1 day | 86,400 seconds ≈ 100k seconds |
| 1 month | 2.5M seconds |
| 1 year | ~30M seconds |
| Read/write ratio (typical) | 100:1 |
| SSD read | ~1 ms |
| RAM read | ~100 ns |
| Round-trip within DC | ~500 μs |
| Round-trip cross-region | ~150 ms |

**Example — Twitter timeline:**
- 500M active users, 1 tweet/user/day = 500M tweets/day
- 500M / 100k sec = **5000 writes/sec** average
- Peak = 3× average = **15k writes/sec**
- Reads: users check timeline 10×/day = 5B reads/day = **50k reads/sec** average, **150k peak**
- Tweet ~300 bytes → 500M × 300 B = **150 GB/day** storage growth

Now you can size databases, cache RAM, network bandwidth.

## Common HLD trade-offs (interview signals)

- **SQL vs NoSQL** — SQL for structured, transactional data; NoSQL for scale, schema flexibility, specific access patterns
- **Monolith vs microservices** — monolith is simpler; microservices scale teams, not necessarily systems
- **Sync vs async** — sync is simple but couples services; async decouples but adds complexity
- **Strong vs eventual consistency** — pick per operation, not per system
- **Replicate everywhere vs single source of truth** — replication improves latency, costs consistency

---

## HLD worked example — URL Shortener (bit.ly)

### Requirements

**Functional:**
- Shorten a long URL to `short.ly/abc123`
- Redirect short URL to original
- Custom aliases (optional)
- Analytics: click count, referrer

**Non-functional:**
- **100M new URLs/month, 10B redirects/month**
- 99.9% availability
- Redirects < 100ms P99
- 5 years of data retention

### Capacity estimation

- **Writes:** 100M / (30 × 100k sec) = ~40 URLs/sec
- **Reads:** 10B / (30 × 100k sec) = ~3333 redirects/sec (100× read/write ratio)
- **Storage:** 100M/month × 60 months × 500 bytes/URL = **~3 TB**
- **Cache:** hot 20% of URLs handle 80% of traffic → cache ~20% = 600 GB (too big for RAM) → cache top-N based on click count → **~50 GB RAM**

### Architecture

```
                     ┌────────────┐
                     │    CDN     │  (caches redirects)
                     └──────┬─────┘
                            │
                     ┌──────▼─────┐
                     │Load Balancer│
                     └──────┬─────┘
                            │
              ┌─────────────┼─────────────┐
              │             │             │
       ┌──────▼──────┐┌─────▼──────┐┌────▼─────┐
       │  Shortener  ││  Redirect  ││Analytics │
       │   Service   ││  Service   ││ Service  │
       └──────┬──────┘└─────┬──────┘└────┬─────┘
              │             │             │
       ┌──────▼──────┐┌─────▼──────┐┌────▼─────┐
       │   Postgres  ││   Redis    ││  Kafka   │
       │  (source of ││  (hot URL  ││ (click   │
       │   truth)    ││  cache)    ││  events) │
       └─────────────┘└────────────┘└────┬─────┘
                                         │
                                    ┌────▼─────┐
                                    │Clickhouse│
                                    │(analytics)│
                                    └──────────┘
```

### Data flow

**Shortening (write path):**
1. Client posts long URL to `POST /api/shorten`
2. Shortener Service generates unique short code (base62 hash of counter, or MD5 collision-avoidance)
3. Save `(short_code, long_url, user_id, created_at)` to Postgres
4. Return `short.ly/abc123`

**Redirect (read path):**
1. Client requests `GET short.ly/abc123`
2. Redirect Service checks Redis cache first
3. On cache hit → 302 redirect immediately (< 5ms)
4. On cache miss → query Postgres, populate cache, redirect (< 50ms)
5. Emit `{short_code, timestamp, user_agent, referrer}` to Kafka async
6. Analytics service consumes Kafka events into Clickhouse

### Key design decisions with justification

1. **Short code generation:** base62 (0-9, a-z, A-Z) = 62^7 = 3.5T combinations, enough for 5 years. Alternatives: MD5 hash + collision detection (slower), or pre-generated pool (simpler but requires infrastructure).
2. **Postgres for source of truth:** ACID needed for uniqueness constraint on short codes.
3. **Redis for cache:** millisecond reads, LRU eviction. Cache size ≈ 50 GB fits in a single Redis cluster.
4. **CDN in front:** short URLs are mostly public → CDN caches redirects globally.
5. **Kafka for analytics:** decouples the read path from analytics processing. Redirect stays fast even if analytics goes down.
6. **Clickhouse for analytics:** columnar DB, orders-of-magnitude faster than Postgres for aggregations over billions of rows.

### Trade-offs discussed

- **Consistency:** eventual OK — if a user clicks a link seconds after creation and hits a stale replica → cache miss, fetches primary, populates
- **Availability:** 99.9% via multi-region deploy, DB read replicas, CDN failover
- **Consistency of counters:** eventual — analytics show counts within seconds, not milliseconds
- **Custom aliases:** unique index on Postgres, `INSERT ON CONFLICT` for atomic creation

---

# PART 2 — LLD (LOW-LEVEL DESIGN)

## What LLD covers

1. **Class diagrams** — classes, methods, fields, relationships
2. **Database schemas** — tables, columns, indexes, foreign keys
3. **API contracts** — exact endpoint signatures, request/response payloads
4. **Design patterns** — which pattern where and why
5. **Algorithms** — pseudocode for non-trivial logic
6. **Error handling strategy**
7. **Data validation rules**
8. **Sequence diagrams** — method-level interactions

## LLD principles — SOLID

Every LLD interview probes SOLID:

- **S**ingle Responsibility — one class, one reason to change
- **O**pen/Closed — open to extension, closed to modification
- **L**iskov Substitution — subclasses must be substitutable for their parent
- **I**nterface Segregation — many small interfaces > one big one
- **D**ependency Inversion — depend on abstractions, not concretions

## Object-oriented design guide

### Identify actors and use cases

- Who uses the system?
- What can they do?
- What are the constraints?

### Identify nouns → potential classes

"A user places an order for a product from a menu at a restaurant."
→ Candidate classes: User, Order, Product, Menu, Restaurant

### Identify verbs → potential methods

"Places an order" → `User.placeOrder(product, quantity)`

### Identify relationships

- Association (uses)
- Aggregation (has-a, weak ownership)
- Composition (has-a, strong ownership — dies together)
- Inheritance (is-a)

### Apply design patterns

Use the right pattern for the right problem — not because it's cool. See the `java-design-patterns.md` file.

## Class diagram notation (UML lite)

```
┌─────────────────────┐
│    ClassName        │      ← name
├─────────────────────┤
│ - field1: Type      │      ← private field
│ + field2: Type      │      ← public field
├─────────────────────┤
│ + method(): Type    │      ← public method
│ - helper(): void    │      ← private method
└─────────────────────┘

Relationships:
─────────►    association (uses)
◇──────►    aggregation (has-a, weak)
♦──────►    composition (has-a, strong)
─────△     inheritance (is-a)
- - - - -►   dependency
```

## Database design essentials

### Normalization (know 1NF, 2NF, 3NF)

- **1NF** — no repeating groups; every cell atomic
- **2NF** — 1NF + no partial dependencies on composite keys
- **3NF** — 2NF + no transitive dependencies

Rule of thumb: normalize until it hurts, denormalize until it works.

### Indexes

- **Primary key index** — automatic, unique, clustered
- **Secondary index** — for frequently queried non-PK columns
- **Composite index** — multiple columns (order matters — leftmost prefix rule)
- **Unique index** — enforce uniqueness
- **Partial index** — indexes rows matching a predicate

### When to denormalize

- Read-heavy workloads
- JOIN-heavy queries are slow
- Analytics (dimensional modeling)
- Materialized views

---

## LLD worked example — Parking Lot System

### Requirements

**Functional:**
- Vehicles enter, get a ticket, park in an available spot
- Vehicles exit, pay based on time parked
- Multi-floor lot with different spot sizes (Motorcycle, Compact, Large)
- Handle multiple gates
- Track available spot count in real-time

**Non-functional:**
- Multi-threaded (multiple gates simultaneously)
- Extensible (add new vehicle types, new pricing strategies)

### Actors identified

- Customer (drives vehicle in/out)
- Attendant (rarely — for payment support)
- System (auto-assigns spots)

### Classes

```
Vehicle (abstract)
├── Motorcycle
├── Car
└── Truck

ParkingSpot (abstract)
├── MotorcycleSpot
├── CompactSpot
└── LargeSpot

ParkingLot
├── Floors: List<ParkingFloor>
└── EntryGates, ExitGates: List<Gate>

ParkingFloor
├── Spots: Map<SpotType, List<ParkingSpot>>

Ticket
├── vehicle, spot, entryTime, exitTime, amount

PricingStrategy (interface)
├── HourlyPricingStrategy
└── FlatRatePricingStrategy

PaymentProcessor (interface)
├── CashPaymentProcessor
└── CardPaymentProcessor
```

### Class-by-class LLD

**Vehicle:**
```java
public enum VehicleType { MOTORCYCLE, CAR, TRUCK }

public abstract class Vehicle {
    private final String licensePlate;
    private final VehicleType type;

    protected Vehicle(String plate, VehicleType type) {
        this.licensePlate = plate;
        this.type = type;
    }
    public String getLicensePlate() { return licensePlate; }
    public VehicleType getType()    { return type; }
}

public class Car extends Vehicle {
    public Car(String plate) { super(plate, VehicleType.CAR); }
}
```

**ParkingSpot:**
```java
public enum SpotType { MOTORCYCLE, COMPACT, LARGE }

public class ParkingSpot {
    private final String id;
    private final SpotType type;
    private final int floor;
    private volatile Vehicle occupant;   // volatile for cross-thread visibility

    public ParkingSpot(String id, SpotType type, int floor) {
        this.id = id;
        this.type = type;
        this.floor = floor;
    }

    public synchronized boolean tryPark(Vehicle v) {
        if (occupant != null) return false;
        if (!canFit(v))       return false;
        occupant = v;
        return true;
    }

    public synchronized void vacate() { occupant = null; }
    public boolean isOccupied()       { return occupant != null; }

    private boolean canFit(Vehicle v) {
        return switch (v.getType()) {
            case MOTORCYCLE -> true;
            case CAR        -> type != SpotType.MOTORCYCLE;
            case TRUCK      -> type == SpotType.LARGE;
        };
    }
}
```

**Ticket:**
```java
public record Ticket(
    String ticketId,
    Vehicle vehicle,
    ParkingSpot spot,
    Instant entryTime,
    Instant exitTime,
    BigDecimal amount
) {}
```

**PricingStrategy (Strategy pattern):**
```java
public interface PricingStrategy {
    BigDecimal calculate(Vehicle v, Duration parked);
}

public class HourlyPricing implements PricingStrategy {
    private final Map<VehicleType, BigDecimal> hourlyRates = Map.of(
        VehicleType.MOTORCYCLE, new BigDecimal("1.00"),
        VehicleType.CAR,        new BigDecimal("2.50"),
        VehicleType.TRUCK,      new BigDecimal("5.00")
    );
    public BigDecimal calculate(Vehicle v, Duration parked) {
        long hours = Math.max(1, parked.toHours());
        return hourlyRates.get(v.getType()).multiply(BigDecimal.valueOf(hours));
    }
}
```

**ParkingLot (Singleton + Facade):**
```java
public class ParkingLot {
    private static final ParkingLot INSTANCE = new ParkingLot();
    public static ParkingLot getInstance() { return INSTANCE; }

    private final List<ParkingFloor> floors = new ArrayList<>();
    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();
    private final PricingStrategy pricingStrategy = new HourlyPricing();

    private ParkingLot() { /* init floors */ }

    public Ticket enter(Vehicle vehicle) {
        ParkingSpot spot = findSpot(vehicle)
            .orElseThrow(() -> new NoSpotAvailableException(vehicle.getType()));
        if (!spot.tryPark(vehicle)) throw new SpotRaceException();
        Ticket ticket = new Ticket(UUID.randomUUID().toString(),
                                   vehicle, spot, Instant.now(), null, null);
        activeTickets.put(ticket.ticketId(), ticket);
        return ticket;
    }

    public BigDecimal exit(String ticketId) {
        Ticket t = activeTickets.remove(ticketId);
        if (t == null) throw new InvalidTicketException(ticketId);
        Duration parked = Duration.between(t.entryTime(), Instant.now());
        BigDecimal amount = pricingStrategy.calculate(t.vehicle(), parked);
        t.spot().vacate();
        return amount;
    }

    private Optional<ParkingSpot> findSpot(Vehicle vehicle) {
        return floors.stream()
                     .flatMap(f -> f.getAvailableSpots(vehicle.getType()).stream())
                     .findFirst();
    }
}
```

### Design patterns used

- **Singleton** — `ParkingLot`
- **Strategy** — `PricingStrategy`, `PaymentProcessor`
- **Factory** — could add `VehicleFactory` for entry gates
- **State** — could add `SpotState` (Available, Occupied, Reserved)

### Database schema (for persistence)

```sql
CREATE TABLE vehicles (
    license_plate VARCHAR(20) PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE parking_spots (
    id VARCHAR(50) PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    floor INTEGER NOT NULL,
    is_occupied BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_spots_available ON parking_spots(type, is_occupied) WHERE is_occupied = FALSE;

CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    vehicle_plate VARCHAR(20) NOT NULL REFERENCES vehicles(license_plate),
    spot_id VARCHAR(50) NOT NULL REFERENCES parking_spots(id),
    entry_time TIMESTAMPTZ NOT NULL,
    exit_time TIMESTAMPTZ,
    amount NUMERIC(10,2)
);
CREATE INDEX idx_tickets_active ON tickets(exit_time) WHERE exit_time IS NULL;
```

### Concurrency considerations

- Multiple gates run in parallel — race on `tryPark`
- Solution: `synchronized` block on the spot, or `AtomicReference<Vehicle>` for lock-free
- `activeTickets` is `ConcurrentHashMap` for concurrent lookup
- `pricingStrategy` is stateless (thread-safe by immutability)

---

# INTERVIEW APPROACH

## HLD interview — the recipe

**~45-60 minutes.** Do NOT jump straight to boxes and arrows.

### Step 1: Clarify requirements (5-10 min)

- Ask about functional requirements
- Ask about non-functional requirements (scale, latency, availability)
- Ask about constraints (existing tech, team size, budget)
- Estimate scale explicitly (users, requests/sec, data size)

### Step 2: High-level architecture (10 min)

- Draw the coarse architecture: gateway, services, DBs, caches, queues
- Explain the request flow end-to-end
- Justify each component ("why a queue here? decoupling read from write")

### Step 3: Deep dive into 1-2 components (15-20 min)

- Pick the most interesting or difficult component
- Discuss database schema, caching strategy, algorithms
- Discuss failure modes

### Step 4: Scaling and reliability (10 min)

- What breaks at 10× scale? 100×?
- How do you handle a DB going down? A region going down?
- What about DDoS, hot keys, thundering herd?

### Step 5: Trade-offs (5 min)

- Wrap up with what you'd change if you had more time / constraints changed
- Show you understand there's no perfect answer

## LLD interview — the recipe

**~45-60 minutes.**

### Step 1: Clarify requirements (5 min)

- Ask about specific use cases
- Ask about constraints (thread safety, memory, extensibility)

### Step 2: Identify entities and relationships (10 min)

- List nouns → classes
- List verbs → methods
- Sketch relationships (has-a, is-a)

### Step 3: Class diagram (15 min)

- Draw classes with public methods
- Show relationships
- Identify design patterns you'd use

### Step 4: Code the important pieces (15 min)

- Interviewer picks 1-2 methods → write them out
- Focus on correctness, encapsulation, error handling

### Step 5: Extensibility discussion (5 min)

- "What if we needed to add X?"
- Show your design accommodates change without rewriting

---

# COMMON INTERVIEW PROBLEMS

## LLD problems (practice these)

**Easy:**
- Parking Lot
- Vending Machine
- Snake and Ladder
- Rate Limiter
- LRU Cache
- Splitwise (expense sharing)
- Meeting Scheduler

**Medium:**
- Elevator system
- Library management
- Movie Ticket Booking (BookMyShow)
- Chess game
- ATM machine
- Cache with TTL
- Notification system

**Hard:**
- Distributed lock
- Job scheduler (like Airflow)
- Blackjack / Poker
- Restaurant order management
- File system (like Google Drive)

## HLD problems (practice these)

**Common:**
- URL shortener (bit.ly)
- Rate limiter at scale
- Twitter feed / Instagram
- WhatsApp / chat system
- YouTube / Netflix
- Uber / food delivery
- Payment gateway
- Notification system (push, email, SMS)
- Search autocomplete
- Distributed cache (Redis clone)
- Distributed message queue (Kafka clone)
- E-commerce site (Amazon-lite)

---

# INTERVIEW-STYLE QUESTIONS

## HLD questions

**Q1. What's the difference between horizontal and vertical scaling?**
Vertical = bigger single machine (limited, expensive). Horizontal = more machines (unlimited, complex, requires stateless services).

**Q2. Explain CAP theorem.**
In a distributed system, you can only guarantee 2 of: Consistency, Availability, Partition tolerance. Since partitions are inevitable, real systems choose between CP (strong consistency, may reject writes during partition) and AP (always available, may return stale data).

**Q3. When would you use NoSQL over SQL?**
When you need horizontal scale for writes, schema flexibility, or specific access patterns (key-value lookup, time-series, graph traversal). NoSQL sacrifices SQL's joins and full ACID for these gains.

**Q4. Explain read-through vs write-through cache.**
Read-through: on cache miss, cache reads from DB and returns. Write-through: writes go to cache and DB synchronously. Both provide strong consistency between cache and DB but hurt latency compared to cache-aside.

**Q5. How do you handle a hot key in Redis?**
Options: (1) shard the key across multiple instances, (2) local in-process cache, (3) request coalescing (single request to backend, others wait), (4) TTL randomization to avoid stampede.

**Q6. What's the difference between Kafka and RabbitMQ?**
Kafka is a distributed log (persistent, high-throughput, ordered per partition, replay possible). RabbitMQ is a broker (routing, message priorities, per-message ack). Kafka for event streaming/analytics; RabbitMQ for task queues/RPC.

**Q7. How does DNS-based load balancing differ from L4/L7 load balancing?**
DNS returns different IPs based on geography/load — cheap but slow to react (TTL). L4 (transport) balances by TCP; L7 (application) balances by HTTP metadata (URL, headers). L4 is faster; L7 is smarter.

**Q8. What is a circuit breaker?**
A pattern that stops calling a failing downstream service after N consecutive failures, "opens" the circuit, waits, then "half-opens" to test if service recovered. Prevents cascading failures. Implementations: Resilience4j, Hystrix (deprecated).

**Q9. How would you design idempotent APIs?**
Client sends an idempotency key with the request. Server stores it briefly (Redis TTL). If a duplicate request arrives with the same key, return the cached previous response instead of re-executing.

**Q10. What is eventual consistency and when is it acceptable?**
Data replicas will converge to the same value given enough time without new writes. Acceptable when: (a) reads don't need to see the absolute latest write, (b) low latency matters more than perfect consistency (e.g., timeline views, cache counts).

## LLD questions

**Q11. What is SOLID?**
Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion. Five OO design principles for maintainable, extensible code.

**Q12. How do you decide between composition and inheritance?**
Prefer composition. Inheritance for genuine "is-a" relationships (Circle is-a Shape). Composition for "has-a" (Car has-a Engine). Inheritance couples tightly; composition allows runtime swapping.

**Q13. Design a thread-safe LRU cache.**
`LinkedHashMap` with `accessOrder=true` gives you LRU behavior, `Collections.synchronizedMap` for thread safety. Or use `Caffeine` library. For high concurrency, prefer segmented locking (`ConcurrentLinkedHashMap`).

**Q14. Design a rate limiter (LLD).**
Token bucket: refill at fixed rate, each request consumes a token, reject if empty. Sliding window: count requests in a rolling window. Fixed window: simpler but has boundary issues. For distributed: Redis + Lua script for atomic decrement.

**Q15. How do you handle concurrent modification in a shared resource?**
Options: (1) `synchronized` block, (2) `Lock` (with `tryLock` for timeouts), (3) `AtomicReference` + CAS, (4) immutable copies + swap, (5) `ConcurrentHashMap` for maps. Pick based on contention profile.

**Q16. Why would you use an interface over an abstract class?**
Interface: pure contract, multiple inheritance, no state, best for defining capabilities (`Comparable`, `Runnable`). Abstract class: shared implementation, single inheritance, has state. Java 8+ default methods blur the line — but interfaces are still preferred for extensibility.

**Q17. Explain dependency injection.**
Objects receive their dependencies from an external source rather than creating them. Enables testability (inject mocks), configurability (inject different implementations), single responsibility (no factory logic in business classes). Frameworks: Spring, Guice, Dagger.

**Q18. How do you handle exceptions in a service layer?**
Catch low-level exceptions (SQL, I/O), translate to domain exceptions (`OrderNotFoundException`), let them propagate. Global exception handler at the boundary converts to HTTP status codes. Never swallow exceptions silently. Log with context.

---

# DEBUG SCENARIOS

## D1. The over-optimized HLD (premature scaling)

Team designs system for "future 100M users" — 3 microservices, Kafka, Kubernetes, service mesh. Current traffic is 1000 users. Development takes 8 months instead of 2.

**Bug:** designing for hypothetical future scale instead of actual current needs.

**Fix:** start with a monolith. Split when it hurts (team autonomy, deployment isolation, scale requirements).

---

## D2. The hot partition

Sharding by `user_id` → most traffic goes to a "power user" (celebrity, popular content). One shard is overloaded; others idle.

**Bug:** shard key doesn't distribute uniformly.

**Fix:** consistent hashing, virtual buckets, or shard-key that includes multiple attributes (`user_id + timestamp_hour`). For celebrities specifically: cache aggressively at edge.

---

## D3. Cache stampede

Cache expires for a hot key. 10,000 requests hit the DB simultaneously to repopulate → DB melts.

**Bug:** no coalescing — every miss triggers a DB call.

**Fix:** (1) probabilistic early expiration (refresh before TTL), (2) request coalescing (mutex — first request populates, others wait), (3) stale-while-revalidate (return old value while refreshing async).

---

## D4. LLD: mutable state exposed through getter

```java
public class Portfolio {
    private final List<Trade> trades = new ArrayList<>();
    public List<Trade> getTrades() { return trades; }
}
```

Callers can `portfolio.getTrades().add(fakeTrade)`. Class invariants can be violated externally.

**Bug:** exposing internal mutable state.

**Fix:** return `Collections.unmodifiableList(trades)` or `List.copyOf(trades)`. Or make `Portfolio` a record with `List.copyOf` defensive copy.

---

## D5. God Object anti-pattern

`OrderService` class has 5000 lines, 50 public methods, handles orders, payments, notifications, refunds, reporting.

**Bug:** violates Single Responsibility Principle. Hard to test, hard to change without breaking unrelated flows.

**Fix:** split into `OrderPlacementService`, `OrderQueryService`, `PaymentService`, `NotificationService`, etc. Compose via interfaces.

---

## D6. Cascading failure — no circuit breaker

Service A → Service B → Service C. Service C becomes slow. Service B's threads pile up waiting. Service A's threads pile up waiting for B. Whole stack goes down.

**Bug:** no timeout, no circuit breaker, no backpressure.

**Fix:** (1) short timeouts on every network call, (2) circuit breaker (open after N failures), (3) bulkheads (isolated thread pools per downstream), (4) fallback responses.

---

## D7. Distributed transaction anti-pattern

Order placement needs to write to Orders DB and Payments DB atomically. Team implements two-phase commit (2PC).

**Bug:** 2PC is fragile, blocks resources during prepare phase, and has poor availability.

**Fix:** use the **Saga pattern** — chain of local transactions with compensating actions on failure. Or the **Outbox pattern** — write event to same DB in same tx, poll and publish. Both give eventual consistency without distributed transaction fragility.

---

## D8. Thread-unsafe Singleton exposed to concurrent users

```java
public class Counter {
    private static Counter INSTANCE = new Counter();
    private int count;
    public static Counter getInstance() { return INSTANCE; }
    public void increment() { count++; }
    public int get() { return count; }
}
```

Multiple threads call `increment()` — lost updates.

**Bug:** Singleton is thread-safe for retrieval, but its methods aren't. Shared state without synchronization.

**Fix:** `AtomicInteger count`, or `synchronized` methods, or use `LongAdder` for high-contention counters.

---

# QUICK-FIRE RAPID ROUND

| Question | Answer |
|---|---|
| HLD or LLD: class diagram? | LLD |
| HLD or LLD: deployment topology? | HLD |
| Which pattern isolates failing downstream services? | Circuit Breaker |
| CAP: what does an AP system sacrifice? | Strong consistency |
| What replaces 2PC in modern systems? | Saga pattern |
| What is the leftmost prefix rule? | Composite index only helps if query filters use its leftmost columns |
| What is idempotent? | Same operation multiple times has same effect as once |
| What is a bloom filter used for? | Fast "definitely not present" check with false positives possible |
| Kafka partition ordering guarantee? | Ordered within a partition, not across |
| What is a materialized view? | Precomputed query result stored as a table |
| RDBMS or NoSQL for social feed? | Usually NoSQL (Cassandra) for scale + denormalized reads |
| Redis persistence options? | RDB snapshots + AOF (append-only log) |
| What is horizontal partitioning? | Sharding — split rows across DBs |
| What is vertical partitioning? | Split columns across tables/DBs |
| Read replicas — strong or eventual consistency? | Eventual (replication lag) |
| gRPC vs REST — which is faster? | gRPC (HTTP/2, binary, streaming, code-gen) |

---

# THE ONE-SENTENCE SUMMARIES

- **HLD** — architecture-level design (services, databases, queues); answers "how does the system work"
- **LLD** — component-level design (classes, methods, schemas); answers "how is X implemented"
- **Scalability** — vertical (bigger box) or horizontal (more boxes); prefer horizontal for growth
- **CAP** — pick 2 of Consistency, Availability, Partition tolerance; partition is a given, so it's CP or AP
- **SOLID** — 5 OO design principles that make code maintainable and extensible
- **Load balancer** — distributes traffic across servers for scale and redundancy
- **Cache** — reduce read latency by storing hot data in memory
- **Message queue** — decouple producers from consumers, add async processing
- **Sharding** — horizontal DB partitioning by a key
- **Read replica** — copies of DB for read scaling; eventual consistency
- **Circuit breaker** — stop calling failing services to prevent cascading failures
- **Idempotency** — same operation repeated has same effect; safe for retries
- **Saga** — chain of local transactions with compensations; replacement for 2PC
- **CQRS** — separate read and write models for scale and clarity
- **Event sourcing** — store events, not state; replay to derive current state

---

## The interview-safe summary you can drop verbatim

> High-Level Design (HLD) is the system-wide architecture — services, databases, queues, and how they communicate — driven by non-functional requirements like scale, availability, and latency. Low-Level Design (LLD) is the class-and-method-level detail inside each component, driven by OO principles like SOLID, appropriate design patterns, and clean data models. HLD interviews test your ability to reason about distributed systems, capacity, and trade-offs (SQL vs NoSQL, sync vs async, strong vs eventual consistency). LLD interviews test your OO design skills — can you turn requirements into a well-factored class hierarchy that's testable, extensible, and thread-safe. Both start with clarifying requirements before drawing anything, and both end with a discussion of trade-offs and what you'd change with more time.
