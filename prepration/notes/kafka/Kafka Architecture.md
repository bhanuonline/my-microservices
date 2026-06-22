# Apache Kafka — Architecture & Beyond

---

## 1. Kafka Architecture

---

### Basic Concepts

#### Producer
> **Producer = anyone who SENDS data to Kafka**

| Term | Meaning |
|---|---|
| Producer | Sends data INTO Kafka |
| Direction | Your App → Kafka Broker |

Real world examples:
- 📱 Mobile App → sends user click events
- 💳 Payment System → sends every transaction
- 🌡️ IoT Sensor → sends temperature every second
- 🛒 E-commerce App → sends every order placed

---

#### Consumer
> **Consumer = anyone who READS data from Kafka**

| Term | Meaning |
|---|---|
| Consumer | Reads data FROM Kafka |
| Direction | Kafka Broker → Your App |

Real world examples:
- 📧 Email Service → reads new user events → sends welcome email
- 📊 Analytics System → reads all events → builds reports
- 🚨 Fraud Detector → reads payment events → checks for fraud

**Important rule — Consumer does NOT delete data:**
```
Topic: "payments"
[msg1] [msg2] [msg3]

Consumer A reads msg1 ✅ → msg1 still in topic
Consumer B reads msg1 ✅ → msg1 still in topic
Consumer C reads msg1 ✅ → msg1 still in topic
```

---

#### Broker & Topic

| | Broker | Topic |
|---|---|---|
| What is it? | Physical server | Logical category/label |
| Stores data? | ✅ YES — on disk | Organized inside broker |
| Analogy | Library building 🏢 | Book section name 📚 |

```
📁 Topic: "payments"   → only payment events
📁 Topic: "orders"     → only order events
📁 Topic: "users"      → only user events
```

---

#### Partition
> **Partition = Topic split into smaller pieces for speed & scalability**

```
Topic: "payments"
┌─────────────────────────────────────┐
│  Partition 0: [msg1] [msg4] [msg7]  │
│  Partition 1: [msg2] [msg5] [msg8]  │
│  Partition 2: [msg3] [msg6] [msg9]  │
└─────────────────────────────────────┘
```

**Key rule — Order guaranteed WITHIN partition only:**
```
✅ Within Partition 0:  msg1 → msg4 → msg7  (always in order)
❌ Across partitions:   NO guaranteed order
```

**3 ways Producer decides Partition:**

```
Way 1 — Direct Partition:
producer.send("users", partition=0, message)
→ always goes to Partition 0 ✅

Way 2 — By Key:
producer.send("users", key="mob:1000", message)
→ Kafka hashes key → same key = always same partition ✅

Way 3 — Round Robin:
producer.send("users", message)
→ Partition 0, 1, 2, 0, 1, 2 ... (Kafka decides) 🔄
```

**Partition & Broker relationship:**
```
BROKER 1 🏢          BROKER 2 🏢          BROKER 3 🏢
────────────         ────────────         ────────────
Partition 0          Partition 1          Partition 2
[msg1]               [msg2]               [msg3]
[msg4]               [msg5]               [msg6]
```

---

#### Offset
> **Offset = position/sequence number of a message inside a partition**

```
Partition 0
┌─────────────────────────────────────────┐
│ Offset 0 → {name: xyz, age: 25}        │
│ Offset 1 → {name: pqr, age: 40}        │
│ Offset 2 → {name: abc, age: 30}        │
│ Offset 3 → {name: def, age: 22}        │
└─────────────────────────────────────────┘
```

- Offset always starts from **0**
- Increases by **1** each message
- Never reused
- Scope is **per partition** (not global)

**Committed Offset = bookmark that saves where consumer stopped:**
```
Consumer reads Offset 0, 1, 2
Consumer commits: "Topic: users, Partition 0, Offset: 2"
Saved in Kafka's special topic: __consumer_offsets
Consumer crashes 💥 → restarts → continues from Offset 2 ✅
```

**3 types of offset positions:**

| Position | Meaning |
|---|---|
| Latest | Start reading only NEW messages |
| Earliest | Start reading from very beginning (Offset 0) |
| Specific | Start reading from a specific offset number |

---

#### Consumer Group
> **Consumer Group = a team of consumers working together to read from a topic**

```
Topic: "payments" (3 partitions)

Partition 0 ──▶ Consumer 1 ✅
Partition 1 ──▶ Consumer 2 ✅
Partition 2 ──▶ Consumer 3 ✅
```

**Key rules:**

```
✅ 1 Partition → 1 Consumer only (within a group)

Consumers > Partitions:
Partition 0 ──▶ Consumer 1 ✅
Partition 1 ──▶ Consumer 2 ✅
Partition 2 ──▶ Consumer 3 ✅
            ✖️  Consumer 4 😴 (idle — no partition left)

Consumers < Partitions:
Partition 0 ──▶ Consumer 1 ✅
Partition 1 ──▶ Consumer 1 ✅ (handles 2 partitions)
Partition 2 ──▶ Consumer 2 ✅
```

**Rebalancing — when consumer crashes:**
```
Consumer 3 crashes 💥
        ↓
Kafka automatically rebalances:
Partition 0 ──▶ Consumer 1 ✅
Partition 1 ──▶ Consumer 2 ✅
Partition 2 ──▶ Consumer 2 ✅ (takes over)
```

**Multiple Consumer Groups — Independent reading:**
```
Topic: "payments"
        ↓
Group A (Fraud Team)     → reads independently ✅
Group B (Analytics Team) → reads independently ✅
Group C (Notification)   → reads independently ✅

Each group has its OWN offset ✅
```

---

### Kafka Cluster
> **Cluster = a group of brokers working together as ONE system**

```
┌─────────────────────────────────────────────┐
│              KAFKA CLUSTER                  │
│                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ Broker 1 │  │ Broker 2 │  │ Broker 3 │  │
│  │ Topic A  │  │ Topic A  │  │ Topic A  │  │
│  │ P0 👑    │  │ P1 👑    │  │ P2 👑    │  │
│  │ P1 copy  │  │ P2 copy  │  │ P0 copy  │  │
│  └──────────┘  └──────────┘  └──────────┘  │
└─────────────────────────────────────────────┘
          ▲                    ▲
       Producer            Consumer
```

| Single Broker | Cluster |
|---|---|
| Crashes = everything down ❌ | One crashes = others take over ✅ |
| Limited storage | Grows with brokers ✅ |
| Limited speed | Speed grows with brokers ✅ |

---

## 2. How Producer Writes Data (Deep Dive)

### Full Producer journey — 7 steps

**Step 1️⃣ — Create Record**
```
Record = {
  topic     : "users"
  partition : 0                (optional)
  key       : "mob:1000"
  value     : {name: abc, age: 30, mob: 1000}
  timestamp : "2024-01-01"
}
```

**Step 2️⃣ — Serialization**
> Converts data into BYTES (Kafka only understands bytes)
```
{name: abc, age: 30, mob: 1000}
        ↓ Serializer
01001010 11001010 10101010 ...
```

**Step 3️⃣ — Partitioner decides Partition**
```
Key exists?
YES → hash("mob:1000") → 42 → 42 % 3 = 0 → Partition 0
NO  → Round Robin → Partition 0, 1, 2, 0 ...
```

**Step 4️⃣ — Record Accumulator (Buffer)**
```
Buffer:
┌─────────────────────────────────┐
│  Partition 0: [r1, r2, r3]     │
│  Partition 1: [r4, r5]         │
│  Partition 2: [r6, r7, r8]     │
└─────────────────────────────────┘
↓ when batch full OR time limit reached → sends to Broker
```

**Step 5️⃣ — Sender Thread sends to Broker**
```
Partition 0 batch ──▶ Broker 1 (P0 Leader) ✅
Partition 1 batch ──▶ Broker 2 (P1 Leader) ✅
Partition 2 batch ──▶ Broker 3 (P2 Leader) ✅
```

**Step 6️⃣ — Acknowledgement (acks)**

| acks value | Meaning | Speed | Safety |
|---|---|---|---|
| 0 | No wait for reply | ⚡ Fastest | ❌ May lose data |
| 1 | Wait for leader only | Medium | ⚠️ Risky if leader crashes |
| all | Wait for ALL replicas | 🐢 Slowest | ✅ 100% safe |

**Step 7️⃣ — Retry on failure**
```
No ack received ❌ → retry after 100ms → retry again → max retries → error ❌
```

### Full Producer flow summary:
```
Data → Create Record → Serialize → Partitioner → Buffer → Sender → Broker → Ack
```

---

## 3. How Consumer Reads Data (Deep Dive)

### Full Consumer journey — 6 steps

**Step 1️⃣ — Subscribe to Topic**
```
Consumer: "I want to read Topic: users"
        ↓
Kafka assigns partitions:
Consumer 1 ──▶ Partition 0
Consumer 2 ──▶ Partition 1
Consumer 3 ──▶ Partition 2
```

**Step 2️⃣ — Poll (Pull Model)**
```
Consumer asks: "Give me new messages" ← POLL
        ↓
Kafka returns batch
        ↓
Consumer processes
        ↓
Consumer asks again → repeats forever 🔄
```

**Why Pull model (not Push)?**

| Push Model | Pull Model |
|---|---|
| Kafka sends whenever it wants | Consumer asks when ready |
| Consumer may get overwhelmed ❌ | Consumer controls speed ✅ |

**Step 3️⃣ — Deserialization**
```
Bytes: 01001010 11001010 ...
        ↓ Deserializer
{name: abc, age: 30, mob: 1000} ✅
```

**Step 4️⃣ — Process the data**
```
{name: abc, age: 30, mob: 1000}
        ↓
→ Save to Database 💾
→ Send email 📧
→ Trigger alert 🚨
→ Update dashboard 📊
```

**Step 5️⃣ — Commit Offset**

| Type | How | Risk |
|---|---|---|
| Auto Commit | Every 5 seconds automatically | May lose data if crash before processing ⚠️ |
| Manual Commit | After successfully processing | Safe — full control ✅ |

**Step 6️⃣ — Consumer crash → Rebalancing**
```
Consumer 1 crashes 💥 (last offset: 5)
        ↓
Kafka detects → Rebalancing
        ↓
Consumer 2 takes over from offset 5 ✅
No messages missed ✅
```

### Producer vs Consumer comparison:

| | Producer | Consumer |
|---|---|---|
| Direction | INTO Kafka | FROM Kafka |
| Serialization | Data → Bytes | Bytes → Data |
| Confirmation | Waits for ack | Commits offset |
| Model | Push to Kafka | Pull from Kafka |
| On failure | Retries automatically | Rebalances automatically |

---

## 4. Replication Deep Dive

### Replication Factor
> How many copies of data exist

| Factor | Copies | Can survive |
|---|---|---|
| 1 | No backup | 0 broker crashes ❌ |
| 2 | 1 backup | 1 broker crash ✅ |
| 3 (recommended) | 2 backups | 2 broker crashes ✅✅ |

### How replication works — step by step:

```
Step 1️⃣ Producer sends to Leader (Broker 1)
Step 2️⃣ Leader replicates to Followers (Broker 2, 3)
Step 3️⃣ Followers confirm to Leader
Step 4️⃣ Leader sends ack to Producer ✅
```

### ISR — In Sync Replicas
> List of brokers fully caught up with leader

```
Normal: ISR = [Broker 1 👑, Broker 2, Broker 3]

Broker 3 falls behind:
ISR = [Broker 1 👑, Broker 2]  (Broker 3 removed)

Broker 3 catches up again:
ISR = [Broker 1 👑, Broker 2, Broker 3] ✅
```

> acks = all only waits for ISR brokers — does NOT wait for slow/dead brokers ✅

### Leader Election:
```
ISR = [Broker 1 👑, Broker 2, Broker 3]
Broker 1 crashes 💥
        ↓
New leader = first broker in ISR = Broker 2 👑
ISR = [Broker 2 👑, Broker 3]
```

### Unclean Leader Election:
```
ISR = [Broker 1, Broker 2] → both crash 💥
Only Broker 3 alive (NOT in ISR)

Option 1 — Wait for ISR: No data loss ✅ but unavailable ❌
Option 2 — Elect Broker 3: Available ✅ but may lose data ❌
```

### Replication across Data Centers:
```
Mumbai DC 🏙️          Delhi DC 🏙️
──────────────         ──────────
Broker 1 👑            Broker 4
Broker 2               Broker 5
Broker 3               Broker 6
        ↕️ MirrorMaker (replicates between DCs)
```

---

## 5. Kafka APIs

### 5 Kafka APIs overview:

```
📤 Producer API  → send data INTO Kafka
📥 Consumer API  → read data FROM Kafka
⚙️ Streams API   → process data inside Kafka
🔌 Connect API   → connect external systems
🛠️ Admin API     → manage Kafka resources
```

### Full picture:
```
┌─────────────────────────────────────────────────────┐
│                  KAFKA CLUSTER                      │
│  ┌─────────┐   ┌─────────┐   ┌─────────┐           │
│  │Broker 1 │   │Broker 2 │   │Broker 3 │           │
│  └─────────┘   └─────────┘   └─────────┘           │
└─────────────────────────────────────────────────────┘
      ▲         ▲          ▲          ▲         ▲
  Producer  Consumer   Streams    Connect    Admin
   API        API        API        API       API
  (send)    (read)   (process)  (integrate) (manage)
```

### When to use which API?

| Situation | Use |
|---|---|
| App needs to send events | Producer API |
| App needs to read events | Consumer API |
| Filter/transform in real time | Streams API |
| Connect database/S3 to Kafka | Connect API |
| Create/delete/manage topics | Admin API |

### Admin API capabilities:
```
✅ Create Topics        → AdminAPI.createTopic("payments", partitions=3)
✅ Delete Topics        → AdminAPI.deleteTopic("old-topic")
✅ List all Topics      → AdminAPI.listTopics()
✅ Check Broker status  → AdminAPI.describeBrokers()
✅ Change configs       → AdminAPI.changeConfig(retention="7days")
```

---

## 6. Kafka Use Cases & Real World Examples

---

### 1. 📨 Messaging System
> Kafka as replacement for traditional message queues

```
User places order (Flipkart)
        ↓
Order Service ──▶ Kafka ──▶ Payment Service
                        ──▶ Inventory Service
                        ──▶ Notification Service
                        ──▶ Analytics Service
```

---

### 2. 📊 Real Time Analytics
> Process and analyze data as it arrives

```
Uber — Every driver sends location every second
        ↓
Kafka Streams processes:
→ Calculate ETA ✅
→ Find nearest driver ✅
→ Detect surge pricing areas ✅
All in real time!
```

---

### 3. 🚨 Fraud Detection
> Detect suspicious activity instantly

```
User pays ₹50,000 from new location (PayPal)
        ↓
Kafka Streams checks pattern in milliseconds
        ↓
Unusual! → Block transaction 🚨
         → Send OTP 📱
         → Alert fraud team 👮
All in under 100ms! ✅
```

---

### 4. 📝 Log Aggregation
> Collect logs from many servers into one place

```
Server 1   ──▶ ┐
Server 2   ──▶ ├──▶ Kafka ──▶ Elasticsearch ──▶ Kibana Dashboard 📊
Server 100 ──▶ ┘
✅ All logs in ONE place
✅ Real time error alerts
```

---

### 5. 🔄 Event Sourcing
> Store every change as an event — never update, only append

```
Account Balance (Banking):
Offset 0: ₹10,000 (opening balance)
Offset 1: -₹2,000 (ATM withdrawal)
Offset 2: +₹5,000 (salary credit)
Offset 3: -₹1,000 (bill payment)

✅ Full history preserved
✅ Full audit trail
```

---

### 6. 🔗 Microservices Communication
> Connect multiple microservices through Kafka

```
Customer places order (Swiggy)
        ↓
Order Service ──▶ Kafka ──▶ Restaurant Service  🍕
                        ──▶ Delivery Service    🛵
                        ──▶ Payment Service     💳
                        ──▶ Notification Service 📱
                        ──▶ Analytics Service   📊
✅ Services don't know each other
✅ Easy to add new services
✅ Each service scales independently
```

---

## Summary — One line for everything

| Concept | One line |
|---|---|
| Producer | Sends data INTO Kafka |
| Consumer | Reads data FROM Kafka |
| Broker | Physical server that stores data |
| Topic | Logical category/label for messages |
| Partition | Topic split into pieces for scalability |
| Offset | Position/page number of message in partition |
| Consumer Group | Team of consumers sharing work |
| Kafka Cluster | Group of brokers as ONE system |
| Replication | Same data copied to multiple brokers |
| ISR | List of brokers fully synced with leader |
| Producer API | Send data into Kafka |
| Consumer API | Read data from Kafka |
| Streams API | Process data in real time |
| Connect API | Bridge to external systems |
| Admin API | Manage Kafka resources |