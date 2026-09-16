# System Design — Distributed Systems Theory

Section 11 of the study series. Continues from [03-messaging-streaming.md](./03-messaging-streaming.md).

Sections 1–10 gave you the *building blocks*. Section 11 goes one level deeper: the theory that explains **why** distributed systems are hard and **how** the tools you use survive it. Interviews rarely ask you to implement Raft — but understanding the concepts lets you reason confidently about consistency, leader election, and failure modes.

---

## 11. Distributed Systems Theory

### 11.1 Why distributed systems are hard — the fundamental problems

Before the algorithms, understand the problems they solve.

**The 8 fallacies of distributed computing** (Peter Deutsch, Sun Microsystems)

```
   ① The network is reliable          ← packets drop, cables cut
   ② Latency is zero                  ← minimum ~0.5ms same DC
   ③ Bandwidth is infinite            ← 1 Gbps ≈ 125 MB/s cap
   ④ The network is secure            ← MITM, eavesdrop
   ⑤ Topology doesn't change          ← nodes come and go
   ⑥ There is one administrator       ← multiple teams, clouds, regions
   ⑦ Transport cost is zero           ← CPU, encoding, serialization
   ⑧ The network is homogeneous       ← different protocols, versions
```

Any distributed algorithm that assumes any of these is broken.

**The three impossibilities you must know**

**FLP impossibility (Fischer, Lynch, Paterson, 1985)**
> In an **asynchronous** network, no deterministic consensus algorithm can guarantee both **safety** (never wrong) and **liveness** (always terminates) when even one node can fail.

Meaning: in the real world, consensus must relax one of them. Real algorithms (Raft, Paxos) sacrifice liveness in edge cases (they may loop) but preserve safety.

**CAP theorem (Section 5.1)**
> In a partition, you must sacrifice consistency OR availability.

**Two Generals Problem**
> Two generals must agree to attack at the same time via unreliable messengers. No protocol exists that guarantees agreement.

The implication for distributed systems: **you can never be perfectly sure the other side got your message**. Every distributed protocol has to handle this.

**Interview one-liner**
> "Distributed systems fail because networks are unreliable, clocks drift, and nodes crash independently. FLP says consensus can't be both safe and always-live; CAP says you can't have both consistency and availability during a partition. The rest of the field is coping strategies."

---

### 11.2 Time and clocks — why ordering is hard

**The problem with wall clocks**
Every server has a physical clock. They drift. NTP corrects them, but you get:
- **Clock skew** — nodes disagree by milliseconds to seconds.
- **Clock jumps** — NTP can move the clock backward or forward.
- **Different rates** — one server's second may be another's 1.001 second.

**Consequence:** you can't reliably order events across machines using wall-clock timestamps.

```
   Real timeline:                     What each server sees:
   ─────────────                      ──────────────────────
   
   Event A at Server-1 (t=100)        Server-1 clock: 100
   Event B at Server-2 (t=101)        Server-2 clock:  99   ← clock skew!
                                       
   Wall-clock ordering says B before A. Reality says A before B.
```

Distributed systems solve this with **logical clocks** — clocks that track *causality*, not real time.

---

### 11.3 Lamport clocks — the simplest logical clock

**The idea**
Every event gets a monotonic counter. Every message carries the sender's counter. On receipt, the receiver bumps its counter to `max(local, received) + 1`.

**The rules**

```
   Rule 1: On any local event, increment counter by 1.
   Rule 2: When sending a message, tag it with counter.
   Rule 3: On receipt of a message with counter K,
           set local = max(local, K) + 1.
```

**Example**

```
   Server A     counter:  1 → 2 →              6 →
                          ┌───┐    send M1     ┌───┐
                          │A1 │─────────────► │A3 │
                          └───┘  (val=2)       └───┘
                                                  ▲
                                                  │ receive M2
                                                  │ max(2, 5)+1 = 6
                                                  
   Server B     counter:                  4 → 5 →
                                          ┌───┐    send M2
                                          │B1 │─────────────►
                                          └───┘  (val=4)
```

**What it guarantees**
- If A happened-before B, then `clock(A) < clock(B)`.
- **But NOT the reverse:** two events with `clock(A) < clock(B)` may still be concurrent (no causal relation).

**Useful for:** total ordering when you don't care about causality vs concurrency (e.g., ordering events in Kafka logs before consumers).

**Limitation:** you can't tell if two events are truly concurrent or causally ordered from Lamport timestamps alone.

---

### 11.4 Vector clocks — tracking causality precisely

**The idea**
Instead of a single counter, each node maintains a **vector of counters** — one entry per node.

**The rules**

```
   Each node N maintains vector V[N] = [V_1, V_2, ..., V_n].
   
   Rule 1: On local event at N, increment V[N][N] by 1.
   Rule 2: On send, attach the whole vector.
   Rule 3: On receive of vector R, set
           V[N][i] = max(V[N][i], R[i]) for all i
           then increment V[N][N] by 1.
```

**Example (2 nodes)**

```
   Server A: [1,0] → [2,0] →              [3,2]
                    ┌──────┐    send       ┌──────┐
                    │[2,0] │───────────►  │[3,2] │
                    └──────┘               └──────┘
                                              ▲
                                              │ receive [1,1]
                                              │ merge → [max(2,1), max(0,1)] +1
                                              │        = [3, 2]
                                              
   Server B:                 [0,1] → [1,1]
                             ┌─────┐    send
                             │[0,1]│──────► ... to A
                             └─────┘  [1,1]
```

**Comparing vector clocks**
```
   V < V'  iff  every component of V ≤ V'  AND  V ≠ V'
   
   Concurrent  iff  neither V < V'  nor V' < V.
```

Example:
- `[2, 0]` < `[3, 2]` → happened-before
- `[2, 0]` and `[1, 3]` → concurrent (each has a component the other lacks)

**Where used**
- **Dynamo / Cassandra / Riak** — detect concurrent writes on a key. On read, return both versions if concurrent → client resolves.
- **Version vectors** in file sync (Dropbox internals).

**Lamport vs Vector clocks**

| | Lamport | Vector |
|---|---|---|
| Structure | Single integer | Vector, size = # nodes |
| Total order? | Yes | Partial |
| Detect concurrency? | ❌ | ✅ |
| Size | O(1) | O(N) |
| Use | Order events, no causality needed | Detect concurrent updates |

---

### 11.5 Hybrid Logical Clocks (HLC) — the practical compromise

Vector clocks are theoretically clean but O(N) is expensive at scale. Wall clocks are cheap but wrong. HLC combines them: **physical time + logical counter**, tightly bounded.

```
   HLC value = (physical_time, logical_counter)
   
   Local event:    hlc = (max(now, hlc.time), hlc.counter+1 if same time else 0)
   Send:           attach hlc
   Receive R:      hlc = (max(now, hlc.time, R.time),
                          counter based on which side is higher)
```

**Properties**
- Approximates real time (close to `now`).
- Preserves causality (like Lamport).
- Bounded drift from real clock.
- O(1) size.

**Used by:** CockroachDB, YugabyteDB, MongoDB (post-4.0) — for cross-node consistency without heavy coordination.

**Google Spanner** uses a different approach: **TrueTime**, backed by GPS + atomic clocks, giving *bounded* uncertainty windows. Very expensive infrastructure, but enables strict global consistency at scale.

**Interview one-liner**
> "Lamport clocks give total order but hide concurrency. Vector clocks detect concurrency at O(N) cost. HLC gets most of the benefit at O(1) using physical time as a base. Spanner cheats with atomic clocks."

---

### 11.6 Consensus — how nodes agree on a value

The core problem: **N nodes must agree on ONE value**, despite crashes and network delays.

**Applications**
- Electing a leader.
- Committing a distributed transaction.
- Replicating a state machine (state machine replication).
- Deciding which config to run.

**The state machine replication (SMR) trick**
If all replicas apply the *same* sequence of commands from the *same* starting state, they end in the same state. Consensus = agreeing on the next command in the sequence.

**Two algorithms dominate: Paxos and Raft.**

---

### 11.7 Paxos — the original consensus

Invented by Leslie Lamport (1998). Notoriously hard to understand.

**Roles**
- **Proposer** — proposes values.
- **Acceptor** — votes on proposals.
- **Learner** — learns the decided value.

Real systems fuse these into one process; the roles are logical.

**The two phases (Basic Paxos)**

```
   Phase 1: PREPARE
   ────────────────
   Proposer picks a proposal number N (higher than any seen).
   Proposer ─── PREPARE(N) ────► majority of acceptors
   
   Acceptor:
     If N > any previous prepare it has seen:
       promise not to accept lower N,
       return highest accepted value (if any).
     Else: ignore or nack.
   
   Phase 2: ACCEPT
   ────────────────
   Once proposer has majority of promises:
     If any acceptor returned a value → use it.
     Else → use own value.
   Proposer ─── ACCEPT(N, value) ────► acceptors
   
   Acceptor:
     If it hasn't promised a higher N:
       accept and record.
     Else: nack.
   
   Once majority accept → value is chosen.
```

**Why it works**
Any two majorities of acceptors overlap in at least one node. That intersecting node's promises prevent contradictory decisions.

**Why it's hard**
- Two proposers can dueling-propose forever (livelock).
- Multi-Paxos (running Paxos in a sequence for a log) adds a leader to avoid this.
- Papers use terminology (`ballot`, `quorum`) that obscures the mechanics.

**In practice**
- **Google Chubby** — Paxos-based lock service.
- **Google Spanner** — Multi-Paxos.
- Most infra teams pick Raft (simpler).

---

### 11.8 Raft — designed for understandability

Diego Ongaro (2014). Same guarantees as Paxos, simpler explanation. **What most modern systems use.**

**Node states**

```
                        (timeout)                (heartbeat)
              ┌──────────────────────► LEADER ──────┐
              │                                     │
   FOLLOWER ──┤                                     │
              │                                     │
              └── (majority votes for you) ──────CANDIDATE
                       ▲                            │
                       │  (see higher term)         │
                       └────────────────────────────┘
```

Every node is one of: Follower, Candidate, Leader. Time is divided into **terms** (monotonic).

**Leader election**

```
   ① Follower's election timeout (random 150-300ms) fires.
      No heartbeat received → become CANDIDATE.
   
   ② Candidate:
      - increment term
      - vote for itself
      - RequestVote RPC to all others
   
   ③ Others:
      - if their term is lower, update, vote yes
      - else vote no
   
   ④ If candidate gets majority → LEADER.
      Else → new election (with new randomized timeout).
   
   Randomized timeouts avoid perpetual ties.
```

**Log replication**

```
   Client ──► Leader
     │
     ▼
   Leader appends to its log (uncommitted)
     │
     ├─► Follower 1 (replicate via AppendEntries RPC)
     ├─► Follower 2
     └─► Follower 3
     
   Once majority acked → mark COMMITTED
   Apply to state machine.
   Reply to client.
   Followers apply once they see committed index.
```

**Safety properties Raft guarantees**
- **Election safety** — at most one leader per term.
- **Leader append-only** — leader never overwrites log entries.
- **Log matching** — if two logs contain an entry with same index+term, they're identical up to that point.
- **Leader completeness** — a committed entry is present in all future leaders' logs.
- **State machine safety** — no two servers apply different commands at the same index.

**Why Raft is easier**
- Strong leader model (only leader accepts writes).
- Randomized election timeouts break ties.
- Log is contiguous, no gaps.
- Split into 3 clean subproblems: leader election, log replication, safety.

**Where used**
- **Etcd** (Kubernetes control plane).
- **Consul**.
- **CockroachDB, TiKV, YugabyteDB** — per-range consensus.
- **RabbitMQ Quorum Queues**.
- **HashiCorp Nomad**.

**Interview one-liner**
> "Raft picks a strong leader via randomized-timeout elections, replicates a log to a majority, and only commits once a majority persists. It gives Paxos's safety with far less confusion — which is why it's everywhere."

---

### 11.9 Leader election — the practical primitive

Consensus and leader election are related but distinct. Leader election answers: **who's in charge right now?**

**Why elect a leader**
- Serialize writes through one node (simplifies concurrency).
- Coordinate distributed work (sharding, task assignment).
- Cache invalidation coordinator.
- Cron/scheduler with one owner.

**Common implementations**

**A. Consensus-based (Raft/Paxos)**
As above — strong guarantees, one leader per term.

**B. ZooKeeper / Etcd ephemeral node**

```
   Nodes race to create /leader (ephemeral, single-owner).
   Winner is the leader. Their session dying → node auto-deleted →
   others notice, re-race.
   
   Not full consensus by itself — relies on ZooKeeper's guarantees.
```

**C. Distributed lock in Redis (Redlock)**
```
   Nodes race to SETNX a key with TTL.
   Winner is leader for the TTL.
   Renews before expiry.
   
   ⚠️ Simple but has known safety issues under partial failures
   (see Martin Kleppmann's critique). Prefer Raft-backed systems.
```

**Split-brain — the classic failure**

```
   Network partition:
   
   ┌── DC-1 ──┐     ✗     ┌── DC-2 ──┐
   │ Leader A │           │ Leader B │  ← both elected!
   └──────────┘           └──────────┘
   
   Both accept writes → divergent state after healing.
```

**Prevention: quorum**
Require majority to elect. A partition can only have one side with majority. The minority side steps down.

```
   5 nodes, partition 3 | 2:
     Side with 3 → new leader elected.
     Side with 2 → cannot elect (no majority) → all followers.
   
   Never two leaders.
```

**Fencing tokens** — the belt-and-suspenders (next section).

---

### 11.10 Fencing tokens — surviving zombie leaders

**The problem: even with quorum, an old leader may not know it's been replaced.**

```
   Timeline:
   
   T0  Leader A is running.
   T1  A does long GC pause (10s).
   T2  Followers time out, elect B.
   T3  A wakes up, unaware.
   T4  A writes to storage.  ← ZOMBIE WRITE
   
   Storage now has A's write AND B's writes → corruption.
```

**The fix: monotonic fencing token**
Every leader gets a strictly increasing token. Downstream storage checks it.

```
   A is leader with token=5.
   Election happens.
   B is leader with token=6.
   
   Storage: last_seen_token = 6.
   
   A (zombie) attempts write with token=5.
   Storage rejects: "5 < 6, ignored."
   
   ✅ Old leader can never cause damage after being replaced.
```

**Used by**
- Any Raft-based system (term number IS the fencing token).
- Distributed locks (proper ones include a fence).
- Kafka epoch fences during leader elections.

**Interview one-liner**
> "A lock or leader election alone can't stop a zombie holder. Fencing tokens let the storage layer reject any write from an outdated leader — the ultimate safety net."

---

### 11.11 Membership and failure detection — gossip and SWIM

Consensus works if you know who the nodes are. But **who** is in the cluster? Machines join, leave, crash. Two families of algorithms manage this.

**Naive: heartbeats to a coordinator**

```
   All nodes ──heartbeat──► Coordinator
   
   ❌ Coordinator = SPOF, hot spot.
   ❌ Doesn't scale beyond few hundred nodes.
```

**Gossip protocol — epidemic communication**

```
   Every T seconds, each node picks a random peer and:
     - shares its membership + version info
     - receives the peer's
   
   Info spreads epidemically → O(log N) rounds to reach everyone.
```

```
   Round 1:  A ──► B                                  (A tells B "I've seen X")
   Round 2:  A ──► C     B ──► D                     (spreading)
   Round 3:  A ──► E     B ──► F     C ──► G     D ──► H
   ...
   
   After ~log(N) rounds, everyone knows.
```

- ✅ Scales to thousands of nodes.
- ✅ No SPOF; resilient to partial failures.
- ✅ Used by: Cassandra, Consul, Serf, HashiCorp Memberlist, Redis Cluster.

**SWIM — a specific gossip-based failure detector**
Scalable Weakly-consistent Infection-style Membership. Key idea: **indirect probing**.

```
   Node A wants to check B:
     ① A ──ping──► B
        If reply → alive, done.
        No reply → B might be dead OR network issue.
     
     ② A picks K random nodes, asks them to ping B:
        A ──"please ping B"──► C, D, E
        
        Any of them hears back from B → B is alive, network issue was A→B path.
        None hear back → mark B as suspected.
     
     ③ Suspected state gossiped. If no refutation in a timeout → confirmed dead.
```

Reduces false positives from single-path network issues.

**Anti-entropy — Merkle tree comparison**

For **data** membership (which nodes hold which keys), compare Merkle trees.

```
   Two replicas each build a Merkle tree over their keys.
   
   Root hashes match? → identical, no sync needed.
   
   Differ? → walk down, find divergent subtrees, sync only those.
```

**Used by:** Cassandra, DynamoDB anti-entropy repair, Riak.

**Interview one-liner**
> "Small clusters use heartbeats to a coordinator. Large clusters gossip: SWIM's indirect probing avoids false positives, Merkle-tree anti-entropy repairs replicas efficiently."

---

### 11.12 Distributed transactions — 2PC, 3PC, TCC (formal treatment)

You've seen these informally. Here's the mechanics.

**Two-Phase Commit (2PC)**

```
   Phase 1: PREPARE (voting)
   ─────────────────────────
   Coordinator ──PREPARE──► All participants
        ◄─ vote YES / NO ── each participant (durable log write on YES)
   
   If all YES → phase 2 COMMIT
   If any NO or timeout → phase 2 ABORT
   
   Phase 2: DECIDE
   ────────────────
   Coordinator ──COMMIT (or ABORT)──► All participants
        ◄── ACK ────────── each
```

**The blocking problem**

```
   If coordinator crashes AFTER participants voted YES but BEFORE sending COMMIT:
     Participants are blocked — they cannot abort (may have already committed
     according to some paths) and cannot commit (no confirmation).
     They wait for the coordinator to come back.
```

**Three-Phase Commit (3PC)**
Adds a **pre-commit** phase to avoid blocking.

```
   Phase 1: CAN_COMMIT?    (voting, same as 2PC)
   Phase 2: PRE_COMMIT     ← new phase: "we're going to commit"
   Phase 3: COMMIT
   
   If coordinator dies after Phase 2, participants know the decision was COMMIT
   (they can time out and proceed).
   
   ❌ In practice, 3PC has its own edge cases under network partitions
      and is rarely used. Consensus-based approaches (Raft) replace it.
```

**TCC — Try, Confirm, Cancel**
Application-level compensation-oriented pattern.

```
   Try:      Reserve resources (idempotent, tentative).
             Flight seat marked "held for 60s".
   
   Confirm:  Make the reservation permanent.
             Flight seat committed.
   
   Cancel:   Release the reservation.
             Seat freed.
   
   Coordinator makes sure either all services Confirm, or all Cancel.
   If a Try fails → cancel all previous Tries.
```

- ✅ No cross-DB transaction needed.
- ✅ Each service defines its own Try/Confirm/Cancel semantics.
- ❌ You design compensations correctly. Idempotency is non-negotiable.

Similar shape to Sagas (Section 10.7) but with an explicit "reserve" step.

**Comparison table**

| Protocol | Coordination | Blocking? | Consistency | Fit |
|---|---|---|---|---|
| 2PC | Central coordinator | Yes (on crash) | Strong (ACID) | Enterprise DBs (XA) |
| 3PC | Central coordinator | No, but fragile | Strong (theoretical) | Rarely used |
| TCC | Central + local | No | Eventual with compensation | Microservices |
| Saga | Decentralized events | No | Eventual with compensation | Modern microservices |
| Raft/Paxos | Consensus round | No (majority) | Strong (linearizable) | Metadata, config, KV stores |

**Interview one-liner**
> "2PC gives strong distributed ACID but blocks on coordinator failure. 3PC tries to fix that and mostly fails. TCC and Sagas replace ACID with compensation — better fits for microservices. Raft/Paxos handle metadata consensus directly."

---

### 11.13 Idempotency and exactly-once — the safety net

You've seen this in Sections 1.10, 8.6, 10.4. Here's the theory pass.

**The core insight**
In a distributed system, **the sender can't know if the receiver got the message.** Retries are inevitable. Only two protections make retries safe:
1. **Idempotent operations** — safe to apply N times.
2. **Deduplication by unique key** — record processed IDs, skip duplicates.

**Idempotent operation patterns**

```
   Naturally idempotent:
     SET x = 5
     PUT /users/42 { name: "Alice" }
     DELETE /orders/99
   
   Naturally NOT idempotent:
     INCREMENT x
     POST /orders { ... }  → creates a new order each time
     "Send $100"
```

**Dedup key strategies**

```
   Client-generated ID:  UUID sent with request, checked server-side.
   Server-generated ID:  return ID on success, client stores; retries re-use.
   Content hash:         idempotency by request payload hash.
   Idempotency key hdr:  standard HTTP header, Stripe pattern.
```

**Server-side dedup store**

```
   BEGIN;
     IF EXISTS (SELECT 1 FROM dedup WHERE key = ?):
       return cached_response
     ELSE:
       process request
       INSERT INTO dedup (key, response, expires_at) VALUES (...)
       return response
   COMMIT;
```

TTL prevents unbounded growth. Redis with TTL is a common lightweight impl.

**Exactly-once — a mirage or reality?**

Strict exactly-once end-to-end is impossible in the general case (FLP-adjacent). But:
- Kafka's exactly-once semantics **within Kafka** are real.
- End-to-end exactly-once requires **transactional side effects OR idempotent side effects**.

**In practice: at-least-once + idempotency = effectively-once**.

**Interview one-liner**
> "You can't build exactly-once on unreliable networks. But at-least-once delivery + idempotent handlers is functionally equivalent, and it's how the industry actually ships it."

---

### 11.14 The bigger picture — how the pieces compose

A modern distributed system uses these theory pieces like Lego blocks.

```
   ┌─────────────────────────────────────────────────────────────┐
   │  Layer                    Primitive                Example  │
   ├─────────────────────────────────────────────────────────────┤
   │  Ordering across nodes    Logical clocks           HLC       │
   │  Agreement on a value     Consensus                Raft      │
   │  Who's leader            Leader election + fence  Raft term │
   │  Who's in the cluster    Gossip / SWIM            Cassandra │
   │  Data consistency        Quorum reads/writes      Dynamo    │
   │  Transactions           2PC / Sagas / TCC        Saga       │
   │  Retries / dedup        Idempotency             UUID+dedup │
   └─────────────────────────────────────────────────────────────┘
```

**How they compose in a Kafka cluster**
- **Consensus (Raft/KRaft)** manages topic and partition metadata.
- **Leader per partition** for writes.
- **Fencing (leader epoch)** rejects zombie leaders.
- **ISR** = simple membership within a partition.
- **Idempotent producer** = client-side dedup.

**How they compose in Cassandra**
- **Gossip protocol** for membership.
- **Vector clocks / last-write-wins** for concurrent writes.
- **Quorum tunable consistency** for reads/writes.
- **Anti-entropy Merkle trees** for repair.
- **Hinted handoff** for transient downtime.

**How they compose in Kubernetes**
- **Etcd (Raft)** stores cluster state.
- **Leader election** for scheduler, controller-manager.
- **Watches** on etcd propagate state to all nodes.
- **Node heartbeats** for failure detection.

---

### 11.15 Java angle — what you actually touch

- **Curator / ZooKeeper client** — leader elections, distributed locks.
- **Etcd client** — same for etcd-based systems.
- **Kafka client** — offsets, transactions, idempotence flags.
- **Cassandra driver** — consistency levels per query, prepared statements dedupe protocol.
- **Redisson** — Redlock (with the caveats), distributed locks, atomic longs.
- **Spring Cloud** — Consul/Eureka clients using gossip semantics under the hood.
- **Application-level idempotency** — Redis or DB dedup stores keyed by request ID.

**Anti-patterns to avoid in Java code**
- Rolling your own consensus. Use etcd/ZooKeeper.
- Using `synchronized` for cross-instance coordination. It doesn't cross the JVM.
- Trusting wall-clock timestamps for distributed ordering. Use HLC or the DB's timestamp.
- Treating Redlock as a strong lock. It isn't. Fence tokens still matter.

---

### 11.16 Summary — one-liners you can defend

- **FLP** — no consensus in async networks with failures without giving up liveness OR safety.
- **CAP** — pick two, but P is forced.
- **Lamport clocks** — total order without concurrency detection.
- **Vector clocks** — precise causality, O(N) cost.
- **HLC** — physical + logical, O(1), close to real time.
- **Paxos / Raft** — consensus; Raft is easier and now standard.
- **Leader election** — with quorum + fencing = zombie-safe.
- **Gossip / SWIM** — scalable failure detection.
- **2PC** — strong but blocking.
- **Saga / TCC** — eventual consistency with compensation.
- **Idempotency** — the universal safety net.

**Top-level insight**
> "Every distributed system trades safety for progress somewhere. The tools you use (Kafka, Cassandra, etcd, Postgres replication) hide the theory, but the tradeoffs — consensus latency, replication lag, split-brain risk, retry duplication — surface as bugs when you ignore them."

---

*Next up: Section 12 — Microservices architecture: monolith → SOA → microservices → serverless, service discovery, config, API design (REST/GraphQL/gRPC), auth (OAuth 2.0/OIDC/JWT/mTLS), observability, deployment strategies.*
