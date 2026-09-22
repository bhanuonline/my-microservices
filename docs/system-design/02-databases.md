# System Design — Databases

Section 9 of the study series. Continues from [01-fundamentals.md](./01-fundamentals.md).

Databases are the persistence layer of every system design. Section 9 covers **which type to pick, how they store data, how they stay consistent, how they scale, and where they break.**

---

## 9. Databases

### 9.1 The big picture — the DB landscape

Databases fall into families based on **data model** and **access pattern**.

```
                            Databases
                                │
        ┌───────────────────────┴───────────────────────┐
        │                                               │
    Relational                                       NoSQL
    (SQL, ACID,                        (schemaless, horizontal scale first)
     schema-first)                            │
        │                    ┌─────────────┬──┴──┬──────────────┬─────────────┐
        │                    │             │     │              │             │
    ┌───┴────┐        Key-Value      Document  Column-family   Graph      Time-series
    │Postgres│        Redis          MongoDB   Cassandra       Neo4j      InfluxDB
    │MySQL   │        DynamoDB       Couchbase HBase           Neptune    Timescale
    │Oracle  │        Memcached      Elastic    ScyllaDB        JanusGraph Prometheus
    └────────┘
    
    Also cross-cutting: Search (Elasticsearch), Object storage (S3),
    Wide-column analytics (BigQuery, Snowflake), NewSQL (Spanner, CockroachDB)
```

**When to pick which (rough guide)**

| Family | Best for | Weakness |
|---|---|---|
| Relational (SQL) | Transactions, joins, constraints, reporting | Horizontal scale is hard |
| Key-Value | Session, cache, simple lookups by key | No queries beyond key |
| Document | JSON-shaped data, flexible schema | Complex joins are awkward |
| Column-family | Time-series, event logs, huge writes | Query patterns must be known upfront |
| Graph | Relationships (social, fraud rings, recommendations) | Not for tabular workloads |
| Time-series | Metrics, sensor data, financial ticks | Not general-purpose |
| Search | Full-text, faceted search, log search | Not source of truth |
| Object storage | Blobs, media, backups | Not queryable |
| NewSQL | SQL semantics + horizontal scale | New, more expensive |

**Interview one-liner**
> "Pick the storage that matches your access pattern, not your comfort zone. A social feed is column-family (Cassandra); a bank ledger is relational (Postgres); a session store is KV (Redis)."

---

### 9.2 SQL vs NoSQL — the real differences

The name is misleading. NoSQL doesn't mean "no SQL query language" — many NoSQL DBs have SQL-like languages (CQL, N1QL). It means **not a traditional relational database**.

**Core contrasts**

```
   SQL (Relational)                      NoSQL (varies by type)
   ──────────────────                    ──────────────────────
   
   Schema:      strict, upfront          flexible, per-document
   Joins:       first-class               denormalize / avoid
   Transactions: ACID across rows        limited (per-doc, per-partition)
   Consistency: strong                    tunable (usually eventual)
   Scaling:     vertical first            horizontal-native
   Query:       SQL (declarative)         varies (CQL, MQL, KV, GraphQL)
   Best for:    complex queries          simple queries at huge scale
```

**The row-based vs schemaless mental model**

```
   Relational (rows in tables)
   ─────────────────────────────
   users table:
     id | name  | email          | age
   ────┼───────┼────────────────┼─────
      1| Alice | a@x.com        | 30
      2| Bob   | b@x.com        | 25
   
   Every row: same columns, same types. Schema enforced.
   
   Document (JSON docs in a collection)
   ──────────────────────────────────────
   users collection:
     { "_id": 1, "name": "Alice", "email": "a@x.com", "age": 30 }
     { "_id": 2, "name": "Bob", "phones": ["+1", "+91"] }   ← different shape OK
   
   Each doc: independent shape. App is responsible for consistency.
   
   Key-Value (opaque values)
   ──────────────────────────
   session:abc123 → "{\"userId\":42,\"role\":\"admin\"}"
   session:def456 → "..."
   
   DB doesn't parse the value. Fastest lookups.
   
   Column-family (rows keyed, columns wide)
   ──────────────────────────────────────────
   users
     row-key: user_42
       col: name       → Alice
       col: email      → a@x.com
       col: login:2025-09-16 → true
       col: login:2025-09-17 → true
     row-key: user_43
       col: name       → Bob
       col: phone      → +1
   
   Each row can have its own columns. Great for wide sparse data.
   
   Graph
   ──────
   (Alice)──FRIENDS_WITH──►(Bob)──WORKS_AT──►(AcmeCo)
      │                       ▲
      └─LIVES_IN──►(Delhi)◄──LIVES_IN─┘
   
   Query: "friends of friends who work at AcmeCo" — trivial.
```

**When SQL still wins in the modern era**
- **Financial systems** — you want strong ACID and joins across accounts.
- **Small-to-medium scale** — a Postgres instance handles most real apps.
- **Reporting / analytics** — SQL is *the* query language.
- **Data with lots of relationships** — foreign keys, joins.

**When NoSQL wins**
- **Massive write throughput** — Cassandra, DynamoDB.
- **Schema flexibility** — a rapidly evolving product where every user has different fields.
- **Simple lookups at huge scale** — session stores, catalogs.
- **Global multi-region** — some NoSQL systems (DynamoDB Global Tables) do this natively.

**Interview one-liner**
> "SQL trades scale for correctness and expressiveness; NoSQL trades correctness and expressiveness for scale. Modern reality: use both, one for each subsystem."

---

### 9.3 How databases actually store data

Two dominant storage engines: **B-Tree** (SQL, most engines) and **LSM Tree** (Cassandra, RocksDB, LevelDB).

**B-Tree — the SQL default**

Balanced tree of pages on disk. Reads and writes are `O(log N)` in page-count terms.

```
                     ┌──────────────────┐
                     │  Root: [50 | 100]│
                     └────┬───────┬─────┘
                          │       │       
             ┌────────────┘       └────────────┐
             ▼                                 ▼
        ┌─────────────┐                 ┌─────────────┐
        │ Internal    │                 │ Internal    │
        │ [20|35]     │                 │ [70|85|120] │
        └──┬──┬───┬───┘                 └──┬──┬───┬───┘
           │  │   │                        │  │   │
           ▼  ▼   ▼                        ▼  ▼   ▼
       Leaf..Leaf..Leaf                Leaf..Leaf..Leaf
       (rows)                          (rows)
   
   Sorted by primary key.
   Range queries: walk leaves sequentially.
```

- ✅ Random reads: fast (~3-4 disk seeks even for huge tables).
- ✅ Range scans: fast (leaves linked in order).
- ❌ Writes: update pages in place → random I/O expensive on HDDs.
- **Used by: Postgres, MySQL/InnoDB, Oracle, SQL Server.**

**LSM Tree — the NoSQL default for write-heavy loads**

Instead of updating pages, **append writes** to an in-memory sorted structure, then flush to immutable sorted files (SSTables) on disk. Merge them periodically ("compaction").

```
   Write path
   ──────────
   
   Write ──► [Memtable]  in-memory sorted map (fast writes)
                │
                │ flushes when full
                ▼
              SSTable-0   (immutable, sorted, on disk)
              SSTable-1
              SSTable-2
              ...
              │
              ▼
   Compaction merges older SSTables into fewer bigger ones.
   
   Read path
   ─────────
   
   Query ──► check Memtable ──► if miss, check SSTables (newest → oldest)
                                   using Bloom filters + indexes
```

- ✅ **Writes are sequential** — massive write throughput on SSD/HDD.
- ✅ Great for time-series, logs, event data.
- ❌ Reads may check many SSTables (mitigated by Bloom filters).
- ❌ Compaction uses I/O; can spike latency.
- **Used by: Cassandra, ScyllaDB, RocksDB, LevelDB, HBase, InfluxDB.**

**B-Tree vs LSM head-to-head**

| Property | B-Tree | LSM Tree |
|---|---|---|
| Write cost | Random I/O per update | Sequential append (fast) |
| Read cost | Fewer seeks | May check many SSTables |
| Space | Smaller (in-place update) | Larger (old versions kept) |
| Compaction | None | Background, I/O-heavy |
| Best for | Read-heavy, transactions | Write-heavy, time-series |

**Interview one-liner**
> "B-Trees update in place — great for reads, slower for writes. LSM Trees append and compact — great for writes, more read work. SQL DBs pick B-Tree; wide-column NoSQL picks LSM."

---

### 9.4 Indexes — the read accelerator

Without an index, a query is `O(N)` — full table scan. An index is a **separate data structure** mapping key values back to rows, letting the DB find data in `O(log N)`.

**Primary vs secondary index**

```
   Primary key index (clustered in InnoDB/SQL Server, unclustered in Postgres):
   
      PK ──► row (physical location)
   
   Secondary index:
   
      indexed_col ──► PK  (then PK ──► row)
   
   Two lookups for secondary (extra hop).
```

**Index types**

**B-Tree index (the default)**
```
   Great for equality (=) and range (<, >, BETWEEN, ORDER BY).
   
      SELECT * FROM users WHERE age BETWEEN 20 AND 30;   ✅ fast
      SELECT * FROM users WHERE email = 'x';             ✅ fast
      SELECT * FROM users WHERE email LIKE '%@gmail.com';❌ no prefix, full scan
```

**Hash index**
```
   O(1) lookup by exact key. No range support.
   
      Used by: MEMORY tables, some Postgres cases, KV stores.
```

**Composite / multi-column index**
```
   INDEX (last_name, first_name)
   
   Sorted by (last_name, first_name) tuple.
   
      WHERE last_name = 'Smith'                    ✅ uses index
      WHERE last_name = 'Smith' AND first_name = 'A'  ✅ uses both
      WHERE first_name = 'A'                       ❌ can't use (left-prefix rule)
```

**Covering index**
```
   Index contains all columns the query needs.
   → Query answered from the index alone, no row fetch. Blazingly fast.
   
      INDEX (email) INCLUDE (name, age)   ← Postgres INCLUDE
      SELECT name, age FROM users WHERE email = 'x';  ← answered by index
```

**Full-text / inverted index**

Used for text search. Maps **words → list of docs containing them**.

```
   Documents:
     doc1: "the quick brown fox"
     doc2: "the lazy dog"
     doc3: "quick foxes"
   
   Inverted index:
     "the"      → [doc1, doc2]
     "quick"    → [doc1, doc3]
     "brown"    → [doc1]
     "fox"      → [doc1]
     "foxes"    → [doc3]
     "lazy"     → [doc2]
     "dog"      → [doc2]
   
   Query "quick fox" → intersect posting lists → doc1.
```

**Used by: Elasticsearch, Solr, Lucene, Postgres `tsvector`.**

**Special indexes**
- **GIN / GiST** (Postgres) — arrays, JSONB, full-text, geospatial.
- **Spatial (R-Tree)** — bounding-box queries (Postgis, MySQL Spatial).
- **Geohash / S2** — location proximity (covered in the Uber design later).
- **Bitmap index** — analytics, low-cardinality columns (Oracle, some warehouses).

**The cost of indexes**
- **Write amplification** — every INSERT/UPDATE also updates the index.
- **Disk space** — indexes can outsize the table.
- **Rule of thumb** — index by query pattern, not "just in case."

**Query planner and EXPLAIN**
The DB decides *whether* to use each index based on estimated cost.

```
   EXPLAIN SELECT * FROM users WHERE age > 30;
   
   Seq Scan on users  (cost=0..1234 rows=800)   ← full scan
   
   vs
   
   Index Scan using users_age_idx  (cost=0..12 rows=800) ← index used
```

Read your plans. Missing/misused indexes are behind most slow queries.

**Interview one-liner**
> "Indexes are `O(log N)` lookups on top of `O(N)` tables — but every index is a write-time cost. Match indexes to query patterns; use covering indexes for read-hot paths; use inverted indexes for text."

---

### 9.5 Transactions and isolation levels

You touched this in Section 5.3 (ACID). Here's the full story.

**A transaction is a group of ops treated atomically.**

```
   BEGIN;
     UPDATE accounts SET balance = balance - 100 WHERE id = 1;
     UPDATE accounts SET balance = balance + 100 WHERE id = 2;
   COMMIT;
   
   Either BOTH happen or NEITHER.
```

Rollback on error preserves invariants (never lose money in transit).

**The four ACID properties**

```
   Atomicity   — all or nothing
   Consistency — DB invariants preserved
   Isolation   — concurrent txns don't see each other's uncommitted state
   Durability  — committed data survives crashes
```

**Isolation is where the interesting problems live.**

Fully serial execution is safest but slow. Real DBs allow **concurrency with tradeoffs**.

**The anomalies (climbing severity)**

```
   Dirty read           T1 reads data T2 wrote but hasn't committed.
                        If T2 rolls back → T1 saw phantom data.
   
   Non-repeatable read  T1 reads row X. T2 updates X and commits.
                        T1 reads X again → different value!
   
   Phantom read         T1 runs SELECT ... WHERE age > 20 → 5 rows.
                        T2 inserts a matching row and commits.
                        T1 re-runs → 6 rows appear (phantoms).
   
   Lost update          T1 reads x=5. T2 reads x=5. Both write x=6.
                        Expected x=7 (two +1's) but got x=6.
   
   Write skew           T1 and T2 each read a snapshot, each writes
                        something valid alone. Combined result violates
                        an invariant. (E.g., both doctors go off-call
                        because each sees the other still on-call.)
```

**Isolation levels (SQL standard)**

```
   Level                Prevents                             Allows still
   ───────────────────  ───────────────────────────────      ─────────────────
   READ UNCOMMITTED     nothing                              all anomalies
   READ COMMITTED       dirty reads                          non-rep read, phantoms
   REPEATABLE READ      dirty + non-repeatable reads         phantoms (mostly)
   SERIALIZABLE         all anomalies (fully isolated)       —
```

**Real defaults**
- **Postgres:** READ COMMITTED (default), REPEATABLE READ, SERIALIZABLE.
- **MySQL InnoDB:** REPEATABLE READ (default, with gap locks that also block phantoms).
- **Oracle:** READ COMMITTED (default) or SERIALIZABLE.
- **SQL Server:** READ COMMITTED (default).

**How isolation is implemented — 2 techniques**

**Locking**
Reads/writes take shared or exclusive locks.
- Simple, historical (SQL Server default).
- ❌ Contention → slow, deadlocks.

**MVCC — Multi-Version Concurrency Control**
Every write creates a new version tagged by transaction ID. Readers see a **snapshot** as of their start time. Writers don't block readers.

```
   Time
     ▼
   T1: BEGIN                     (snapshot @ t=1: x=5)
   T2: BEGIN                     (snapshot @ t=2: x=5)
   T2: UPDATE x=10, COMMIT       (v2 of x created)
   T1: SELECT x                  → returns 5 (its snapshot)
   T1: COMMIT
   
   New readers now see x=10.
```

- **Postgres, Oracle, MySQL InnoDB, SQL Server (with RCSI)** all use MVCC.
- Trade: more storage for old versions (Postgres needs VACUUM).

**Optimistic vs Pessimistic concurrency**

```
   Pessimistic:
      Lock rows on read. Others wait.
      Good when contention is high.
   
   Optimistic:
      Don't lock. Check on commit: "did anyone modify this since I read?"
      If yes → abort and retry.
      Good when contention is low.
   
   SQL: SELECT ... FOR UPDATE     (pessimistic row lock)
   ORM: @Version column           (optimistic — checks version on save)
```

**Distributed transactions — the hard mode**
When data lives on multiple DBs / services, ACID gets *hard*.

**Two-Phase Commit (2PC)**
```
   Coordinator
       │
       │ ① PREPARE ─────────► all participants
       │      ◄── vote yes/no ── each
       │
       │ ② COMMIT (if all yes) ──► all participants
       │      OR
       │    ABORT ─────────► all participants
```
- ✅ Atomic across nodes.
- ❌ Blocking: if coordinator dies mid-commit, participants hang.
- Rarely used for high-scale systems; more for enterprise (XA, JTA).

**Sagas — the modern approach**
Instead of one big transaction, chain **compensating actions**.

```
   Book flight ─► Book hotel ─► Charge card ─► Confirm
                                    │
                                    ▼ (fails)
   Refund card ◄ Cancel hotel ◄ Cancel flight ◄──── compensations
```
- ✅ No global locks. Each service manages its own transaction.
- ❌ Compensations are business logic — you write them.
- **The standard for microservice transactions.**

**Java angle**
- `@Transactional` (Spring) — declarative ACID transactions on one DB.
- JPA `@Version` — optimistic locking.
- Distributed txns: JTA/XA rarely used; Sagas via Axon, Camunda, or manual choreography.

**Interview one-liner**
> "Isolation levels trade correctness for concurrency. MVCC makes 'REPEATABLE READ' cheap. Distributed transactions are hard — use sagas with compensations instead of 2PC in microservices."

---

### 9.6 Replication — one primary, many copies

Replication = keeping copies of data on multiple nodes. **Why?**
- **Read scale** — reads served by replicas.
- **High availability** — failover if primary dies.
- **Disaster recovery** — a copy in another region.
- **Latency** — replica close to the user.

**Three topologies**

**A. Leader-Follower (primary-replica) — the SQL default**

```
                    ┌──────────────┐
       writes ─────►│    Leader    │
                    │              │
                    └──────┬───────┘
                           │ replication stream
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌─────────┐  ┌─────────┐  ┌─────────┐
        │Follower1│  │Follower2│  │Follower3│
        └─────────┘  └─────────┘  └─────────┘
              ▲            ▲            ▲
              └────────────┴────────────┘
                         reads
```

- ✅ Simple. All writes serialized through leader.
- ✅ Read scale via followers.
- ❌ Writes bottlenecked at leader.
- ❌ Failover complexity: elect a new leader, promote a follower.

**Sync vs Async replication**

```
   Async (default in most SQL DBs):
      Leader commits, returns to client, THEN sends to followers.
      ✅ Fast writes.
      ❌ If leader dies before replication, followers miss it → data loss.
   
   Sync:
      Leader waits for at least one follower to confirm before returning.
      ✅ No data loss on leader failure.
      ❌ Write latency = slowest sync follower.
   
   Semi-sync (MySQL):
      At least ONE follower acked. Compromise.
```

**Replication lag**

```
   Leader: x=1                  (t=0)
   Leader: x=2 committed         (t=1)
   Follower: still x=1           (t=1.2)
   Client reads follower → sees x=1  (stale — 200ms lag)
```

This is the source of the classic "I posted a comment, but I don't see it after redirect" bug. Fixes: read-your-writes routing (read from leader after write) or session stickiness.

**B. Multi-Leader**

Multiple nodes accept writes. Each replicates to the others.

```
      ┌──────────┐         ┌──────────┐         ┌──────────┐
      │ Leader-A │◄───────►│ Leader-B │◄───────►│ Leader-C │
      │ (DC-US)  │         │ (DC-EU)  │         │ (DC-IN)  │
      └──────────┘         └──────────┘         └──────────┘
        writes/reads         writes/reads         writes/reads
```

- ✅ Writes accepted in every region → low latency for users.
- ❌ **Write conflicts** — same row updated in two regions. Resolve how?
  - Last-write-wins (data loss risk).
  - Application-level merge (CRDTs, custom logic).
- Used by: multi-region CouchDB, some Cassandra topologies (via leaderless).

**C. Leaderless (Dynamo-style)**

**Every** node accepts reads and writes. Use quorum to coordinate.

```
   Write to K → send to N=3 replicas
                need W=2 acks → success
   
   Read of K  → query N=3 replicas
                need R=2 responses → return latest
   
   For consistency:  W + R > N
```

```
   Nodes for key K:  R1, R2, R3
   
   Client ──write("v2")──► R1  ACK
                        ──► R2  ACK        (W=2 satisfied)
                        ──► R3  (still old, gossip fixes later)
   
   Client ──read K──► R1 → v2
                   ──► R3 → v1               (R=2)
                                 latest wins → v2
```

- ✅ No leader = no single point of failure.
- ✅ Tunable consistency per query (`ONE`, `QUORUM`, `ALL`).
- ❌ Reconciliation is complex: **read repair**, **hinted handoff**, **anti-entropy** (Merkle trees).
- Used by: **Cassandra, DynamoDB, Riak**.

**Quorum math cheat**

```
   Total replicas:     N = 3
   Writes need:        W = 2
   Reads need:         R = 2
   
   W + R > N  →  2 + 2 > 3  ✅ strong consistency
   
   N=3, W=1, R=1 → fastest but stale reads possible
   N=3, W=3, R=1 → slow writes, fastest strong reads
```

**Interview one-liner**
> "Leader-follower is the SQL default: simple, scales reads, needs failover. Multi-leader gets write locality at the cost of conflicts. Leaderless (Dynamo) drops the leader entirely and uses quorum + read-repair for tunable consistency."

---

### 9.7 Sharding — the horizontal-scale hammer

Replication makes copies of data. **Sharding** splits data across nodes.

**Why shard?**
- Data doesn't fit on one machine.
- Writes exceed one machine's IOPS.
- One machine's connection pool exhausted.

**The core idea**

```
   Before:            After sharding by user_id:
   
   ┌──────────┐       ┌──────────┐  ┌──────────┐  ┌──────────┐
   │   ALL    │       │ shard-1  │  │ shard-2  │  │ shard-3  │
   │   DATA   │  ───► │ user_id  │  │ user_id  │  │ user_id  │
   │          │       │  1-1M    │  │  1M-2M   │  │  2M-3M   │
   └──────────┘       └──────────┘  └──────────┘  └──────────┘
```

**Sharding strategies**

**A. Range-based**
```
   Shard by ranges of the key.
   
   user_id 0        ──► shard-1
   user_id 1000000  ──► shard-2
   user_id 2000000  ──► shard-3
   
   ✅ Range queries efficient (contiguous data).
   ❌ Hot-spot: if IDs are time-based, all recent writes go to one shard.
```

**B. Hash-based**
```
   shard = hash(key) % N
   
   ✅ Even distribution.
   ❌ Range queries fan out to all shards.
   ❌ Resharding when N changes moves 100% of data.
```

**C. Consistent hashing**
```
   Nodes and keys hashed onto a ring. Adding a node moves ~1/N of keys.
   
                    0
                ┌───┴───┐
              ▓ │       │ ▓
        (Node A)│       │(Node B)     Add Node D between A and B?
                │       │              → only keys between A and D move.
                │       │
        (Node C)│       │
              ▓ │       │
                └───────┘
                   180
   
   Adds virtual nodes ("vnodes") for balance.
   ✅ Minimal rebalance on scale changes.
   ✅ Standard for Cassandra, DynamoDB.
```

(You've seen this in Section 6.2 for LBs — same idea.)

**D. Directory-based (lookup table)**
```
   Central service records "user_42 → shard-7".
   
   ✅ Flexible: move data anywhere.
   ❌ Directory is a single point of failure; extra hop.
```

**Choosing a shard key — the hardest part**
The shard key determines locality, hot spots, and query patterns.

- **Good key**: high cardinality, evenly distributed, matches common query filter.
- **Bad keys**: `country` (uneven), `timestamp` (all writes on newest shard), single tenant IDs where one tenant is 90% of traffic.

**Hot shard problem**
```
   Twitter shards by user_id. Then @justinbieber posts.
   His user_id's shard gets slammed.
```

Fixes:
- **Sub-sharding** — split hot key with a suffix (`user:42:0..9`).
- **Read caching** — hot user's timeline in Redis.
- **Different shard key** — sometimes needs a redesign.

**Rebalancing**
Adding shards moves data. Strategies:
- **Fixed number of virtual shards** (hundreds), map many-to-one to physical nodes. Adding a node = reassign some virtual shards. (DynamoDB, Cassandra vnodes.)
- **Consistent hashing** — natural, minimal movement.
- **Coordinated migration** — slow, careful, dual-write during cutover.

**Cross-shard queries — the pain**
```
   SELECT * FROM users WHERE country='IN' ORDER BY signup_date;
   
   Sharded by user_id → this query hits ALL shards → scatter-gather.
```

- Fan out to every shard, gather + merge in coordinator.
- Or maintain a **secondary index** in a separate service (denormalize).
- Or accept eventual consistency (search index like Elasticsearch).

**Sharding vs partitioning vocabulary**
- **Partition** — logical split (e.g., Kafka topic partitions, Postgres native partitions).
- **Shard** — physical split across machines.
- Often used interchangeably.

**Interview one-liner**
> "Sharding scales writes horizontally by splitting data by key. Range keys support range queries but hot-spot easily. Hash and consistent-hash spread evenly but hurt range queries. Choose the shard key by the dominant access pattern."

---

### 9.8 Replication + Sharding — the full architecture

Real DBs combine both.

```
             ┌────────────────────────────────────────────┐
             │                                            │
             │      Sharded, replicated cluster           │
             │                                            │
             │   Shard 1                Shard 2           │
             │  ┌────────────┐         ┌────────────┐    │
             │  │  Primary   │         │  Primary   │    │
             │  │ (writes)   │         │ (writes)   │    │
             │  └─────┬──────┘         └─────┬──────┘    │
             │        │                       │           │
             │        ▼                       ▼           │
             │  ┌────────────┐         ┌────────────┐    │
             │  │ Replica 1a │         │ Replica 2a │    │
             │  └────────────┘         └────────────┘    │
             │  ┌────────────┐         ┌────────────┐    │
             │  │ Replica 1b │         │ Replica 2b │    │
             │  └────────────┘         └────────────┘    │
             │                                            │
             └────────────────────────────────────────────┘
   
   Key idea:
     Sharding    = scale WRITES (partition data across shards)
     Replication = scale READS + tolerate failures (copy within shard)
```

**Example scale-up path (Postgres)**
1. Start: 1 Postgres box. Scale vertically.
2. Read heavy: add read replicas (leader-follower).
3. Write heavy: shard by tenant / user (Citus, Vitess).
4. Cross-region: multi-leader with conflict resolution.

**Example NoSQL path (Cassandra)**
Starts sharded + replicated from day one. `RF=3`, `QUORUM` for both R and W. Add nodes → automatic vnode rebalance.

---

### 9.9 Change Data Capture (CDC) — the modern integration pattern

Sometimes you don't want to *query* the DB — you want to **know when it changes** and stream that to other systems.

**The idea**
Read the DB's replication log (WAL / binlog / oplog) and turn every row change into an event.

```
   ┌──────────┐   binlog stream   ┌────────────┐   Kafka topic   ┌──────────────┐
   │Database  │─────────────────►│ Debezium /  │──"user updated"─►│ Consumers:   │
   │(Postgres,│                   │ Maxwell     │                  │ - search idx │
   │ MySQL)   │                   │ CDC connector│                 │ - cache invld│
   └──────────┘                   └────────────┘                   │ - analytics  │
                                                                    │ - other svcs │
                                                                    └──────────────┘
```

**Why it beats app-level dual writes**
- App writes to DB *and* publishes to Kafka → two systems, easy to get out of sync.
- CDC: the DB *is* the source of truth. Everything downstream derives from it.

**The Outbox pattern**
For microservices that need to publish events atomically with a DB write:

```
   BEGIN;
     INSERT INTO orders (...) VALUES (...);
     INSERT INTO outbox (event_type, payload) VALUES ('OrderCreated', {...});
   COMMIT;
   
   ─── CDC on outbox table → Kafka → consumers
   
   (Or a poller reads outbox and marks as sent.)
```

Both writes in one ACID transaction → no dual-write race.

**Common consumers of CDC**
- **Search index sync** — DB → Elasticsearch.
- **Cache invalidation** — DB → Redis DEL.
- **Data warehouse ETL** — DB → Snowflake/BigQuery.
- **Cross-service events** — one service's changes trigger others.
- **Audit logs** — every change captured.

**Interview one-liner**
> "CDC turns the DB's WAL into an event stream. Combined with the outbox pattern, it solves the dual-write problem cleanly and is how modern event-driven systems keep data stores in sync."

---

### 9.10 Choosing a database — a design checklist

When designing a system, walk this decision tree per data type.

```
   ① What are the reads like?   point lookup / range / analytics / search / graph?
   ② What are the writes like?  bursty / steady / massive / rare?
   ③ What's the scale?          GB / TB / PB?
   ④ Consistency needed?        strong / read-your-writes / eventual?
   ⑤ Availability target?       always-on across regions? or single-region OK?
   ⑥ Query flexibility?         known access patterns / ad-hoc queries?
   ⑦ Transactions?              single-row / multi-row / cross-service?
   
   Then map to a family:
```

**Common combos**

| System | Storage layer |
|---|---|
| E-commerce orders | Postgres (ACID) + Redis cache + Elasticsearch search |
| Social feed | Cassandra (writes) + Redis (hot timeline) + Postgres (users) |
| IoT telemetry | InfluxDB / Timescale + object storage for cold data |
| Chat app | Cassandra (messages) + Redis (presence) + S3 (attachments) |
| Analytics | Kafka → Snowflake / BigQuery |
| Recommendation | Neo4j / graph + Redis for hot user features |
| Session store | Redis |

**A common mistake in interviews**
Picking one DB for the whole system. Real designs use **many**, each for what it's good at. **Polyglot persistence** is the modern norm.

---

### 9.11 Java angle — putting the concepts together

- **JPA / Hibernate** with **HikariCP** — most Spring apps. Works with any relational DB.
- **Spring Data Repositories** — abstract common CRUD.
- **`@Transactional`** — ACID transactions on one relational DB.
- **`@Version`** — optimistic locking.
- **Spring Data Redis + Lettuce** — for KV/cache.
- **Spring Data MongoDB** / **Spring Data Cassandra** — document / column-family DBs.
- **Debezium** in a Kafka Connect cluster — CDC out of Postgres/MySQL to Kafka.
- **`server.connection-pool.maximum-pool-size`** — always cap. See Section 1.3 for why (connection pooling = keep-alive at DB layer).

**Common Java DB pitfalls**
- N+1 queries — fetch join, `@EntityGraph`.
- Long-running txns — hold locks, contention.
- Auto-flush surprises — Hibernate flushes at query boundaries.
- Connection leaks — always close in `try-with-resources`.

---

### 9.12 Summary — one-liners for every sub-topic

- **SQL vs NoSQL** — correctness+expressiveness vs scale+flexibility. Use both.
- **Storage engine** — B-Tree for reads; LSM for writes.
- **Indexes** — read accelerators with write cost; match to access patterns.
- **Isolation** — MVCC snapshot reads are the modern default; sagas beat 2PC.
- **Replication** — leader-follower for SQL; leaderless for Dynamo-style scale.
- **Sharding** — split writes by key; choose the shard key by dominant query.
- **CDC + Outbox** — modern glue between the DB and everything downstream.
- **Polyglot persistence** — one storage per data type, not one for all.

**The top-level insight**
> "Every system-design question has a storage decision inside it. The answer is rarely 'just Postgres' or 'just Cassandra' — it's usually 'Postgres for these tables, Redis for those keys, Cassandra for that stream, Elasticsearch for search, and Kafka+CDC to keep them consistent.'"

---

*Next up: Section 10 — Messaging & Streaming: queues vs pub/sub vs streams, Kafka internals, delivery semantics (at-most-once, at-least-once, exactly-once), event sourcing, CQRS, Sagas, stream processing.*
