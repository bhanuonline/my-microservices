# Apache Kafka — Complete Notes

---

## What is Apache Kafka?

Apache Kafka is an **open-source distributed event streaming platform** used for building real-time data pipelines and streaming applications.

---

## Key Concepts

### Stream vs Pipeline vs Data

| Term | Meaning | Analogy |
|---|---|---|
| Pipeline | The structure/channel | The pipe itself 🔧 |
| Stream | The continuous flow inside it | Water flowing 💧 |
| Data | The actual content | The apples 🍎 |

> **Data ≠ Stream**
> Stream is a specific **behaviour** of data — not data itself.
> A stream = apples moving continuously on a conveyor belt 🚛

---

## Core Capabilities

---

### 1. ⚡ High Throughput & Low Latency

**Throughput = How MUCH data can flow**

- Delivers messages at network-limited throughput
- Latencies as low as **2ms**

```
Normal Road:  🚗 🚗 🚗            → Low Throughput
Highway:      🚗🚗🚗🚗🚗🚗🚗🚗    → High Throughput
```

**Latency = How FAST one message travels**

```
You send message → [Kafka] → Receiver gets it
         |_________________________|
                   ⏱️ this time = Latency
```

**Why is Kafka so fast? 3 reasons:**

1. **Sequential Disk Writes** — writes data in one straight line, no jumping around
2. **Batching** — groups multiple messages and sends them together
3. **Zero Copy** — skips unnecessary memory copies, sends data directly

---

### 2. 📈 Scalability

Kafka can scale to handle massive growth:

- Up to **1000 brokers** in a single cluster
- **Trillions of messages** per day
- **Petabytes of data**
- Hundreds of thousands of partitions
- Elastically expand and contract storage and processing as needed

```
Small Setup:    [Broker 1]
                ↓ grow
Medium Setup:   [Broker 1] [Broker 2] [Broker 3]
                ↓ grow
Large Setup:    [Broker 1] ... [Broker 1000]
```

---

### 3. 🌍 High Availability

Kafka keeps running even across regions:

- Stretch clusters efficiently over **availability zones**
- Connect separate clusters across **geographic regions**

```
Region: India     🏢 Cluster A
Region: US        🏢 Cluster B  ──▶ connected
Region: Europe    🏢 Cluster C
```

> Even if one entire region goes down — Kafka keeps running from another region ✅

---

### 4. 🛡️ Fault Tolerance & Durability

**Fault Tolerance** = System keeps working even if something breaks

**Durability** = Data is NOT lost even if something crashes

#### How Kafka achieves this — 2 key concepts:

**1. Replication**
Kafka copies your message to **multiple brokers (servers)**

```
Your Message arrives
        ↓
Broker 1 💾  ← LEADER
Broker 2 💾  ← replica (copy)
Broker 3 💾  ← replica (copy)
```

**2. Leader & Follower**

Every topic partition has:
- **1 Leader** → handles all reads & writes
- **2+ Followers** → silently copy everything from leader

**What happens when a broker crashes?**

```
Step 1: Broker 1 crashes 💥
Step 2: Kafka detects it (in seconds)
Step 3: Kafka promotes Broker 2 as new Leader 👑
Step 4: Producers & Consumers switch to Broker 2
Step 5: Everything continues normally ✅
```

**Where is data stored?**

| Storage | Result |
|---|---|
| RAM (Memory) | Data disappears when server restarts ❌ |
| Disk | Data stays even after crash ✅ |

> Kafka writes every message to **disk** — so data survives crashes.

---

### 5. ⚙️ Stream Processing (Kafka Streams)

**Stream Processing** = doing something to data **AS IT FLOWS**

#### 4 main operations:

| Operation | What it does | Example |
|---|---|---|
| Filter | Keep only relevant events | Only payments above ₹10,000 |
| Transform | Change data format/shape | Fahrenheit to Celsius |
| Aggregation | Summarize many into one | Total sales per minute |
| Join | Combine two streams | Order + Product details |

#### Important — Who does the processing?

```
❌ Topic does NOT do these operations
❌ Broker does NOT do these operations
✅ Kafka Streams (separate layer) does these operations
```

#### What is Kafka Streams?

- A **Java library** that runs inside **YOUR application**
- Runs on **YOUR machine** — NOT inside the broker
- Acts as **Consumer + Processor + Producer** combined

```
┌─────────────┐     ┌─────────────┐     ┌──────────────────┐
│  PRODUCER   │────▶│   BROKER    │────▶│  KAFKA STREAMS   │
│ sends data  │     │ stores data │     │ filter/transform │
└─────────────┘     └─────────────┘     └──────────────────┘
```

#### Is Kafka Streams part of Broker?

> ❌ NO — Kafka Streams is completely SEPARATE from Broker

```
┌─────────────────────────────────────────────────┐
│                 KAFKA BROKER                    │
│         (separate server/machine)               │
│  ┌─────────────┐       ┌─────────────────────┐  │
│  │   Topic A   │       │      Topic B         │  │
│  │  (storage)  │       │     (storage)        │  │
│  └─────────────┘       └─────────────────────┘  │
└─────────────────────────────────────────────────┘
            ▲                       │
            │ reads                 │ writes
            │                       ▼
┌─────────────────────────────────────────────────┐
│              KAFKA STREAMS                      │
│         (YOUR application code)                 │
│         (runs on YOUR machine)                  │
│      filter / transform / aggregate             │
└─────────────────────────────────────────────────┘
```

> Kafka Streams is just a **Java library**
> Runs inside **YOUR application** on **YOUR machine**
> **NOT** inside the broker

---

#### Full Kafka Streams flow:

```
Producer         →   sends data
Broker / Topic   →   stores data temporarily
Kafka Streams    →   reads, processes, writes back  (Consumer + Processor + Producer)
Broker / Topic   →   stores processed result
Consumer         →   final receiver
```

#### Key feature — Exactly Once Processing ✅

| Guarantee | Meaning | Problem |
|---|---|---|
| At most once | May miss messages | Data loss ❌ |
| At least once | May repeat messages | Duplicates ❌ |
| **Exactly once** | No loss, no duplicate | ✅ Perfect |

---

### 6. 🔌 Kafka Connect (Integrations)

**Kafka Connect** = a **two-way bridge** between Kafka and the outside world

#### Why do we need it?

Kafka stores data **temporarily** (default 7 days). For permanent storage, data needs to move to a database.

| Storage | Duration |
|---|---|
| Kafka Broker | Temporary (7 days default) |
| Database | Permanent (forever) |

#### Two types of Connectors:

**Source Connector — brings data INTO Kafka**

```
MySQL       ──▶ Source Connector ──▶ Kafka Topic
PostgreSQL  ──▶ Source Connector ──▶ Kafka Topic
AWS S3      ──▶ Source Connector ──▶ Kafka Topic
```

**Sink Connector — sends data OUT of Kafka**

```
Kafka Topic ──▶ Sink Connector ──▶ Elasticsearch
Kafka Topic ──▶ Sink Connector ──▶ AWS S3
Kafka Topic ──▶ Sink Connector ──▶ PostgreSQL
```

#### Memory trick:

```
SOURCE = water SOURCE = water comes FROM source → data comes INTO Kafka
SINK   = kitchen SINK = water goes INTO sink    → data goes OUT of Kafka
```

#### Full Connect picture:

```
MySQL / PostgreSQL / AWS S3
        ↓ (Source Connectors)
    KAFKA BROKER (Topics)
        ↓ (Sink Connectors)
Elasticsearch / AWS S3 / PostgreSQL
```

---

## Broker vs Topic — Quick Clarification

| | Broker | Topic |
|---|---|---|
| What is it? | Physical server | Logical category/label |
| Does it store data? | ✅ YES — on disk | Organized inside broker |
| Analogy | Library building 🏢 | Book section name 📚 |

> **Broker** = actually stores data (real server)
> **Topic** = organizes data inside the broker (logical label)

---

## One-line Summaries

| Concept | One line |
|---|---|
| Stream | Continuous flow of data |
| High Throughput | Handles millions of messages per second |
| Low Latency | Delivers messages in as low as 2ms |
| Scalability | Grows from 1 broker to 1000 brokers easily |
| High Availability | Runs across regions — never goes fully down |
| Fault Tolerance | Kafka keeps working even after a crash |
| Durability | Data is never lost even after a crash |
| Kafka Streams | Smart consumer that processes and produces back |
| Kafka Connect | Two-way bridge between Kafka and external systems |