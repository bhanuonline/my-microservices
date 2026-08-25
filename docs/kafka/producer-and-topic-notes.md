What is a Producer?

Producer = anyone who SENDS data to Kafka
Now What is a Consumer?

Consumer = anyone who READS data from Kafka
Producer & Consumer together
                    KAFKA BROKER
┌────────────┐
💳 Payment App ──▶ │   Topic:   │    ──▶ 🚨 Fraud Detector
(Producer)          │ "payments"   │         ──▶ 📊 Analytics
│            │                      ──▶ 📧 Notification Service
└────────────┘      (all Consumers)
One Producer can have many Consumers ✅

All reading the same data independently
Important rule — Consumer does NOT delete data
When consumer reads a message — message is NOT deleted from Kafka
Other consumers can still read the same message ✅

What is a Topic?
Topic = a category/folder where messages are stored
Producer sends to a specific topic

Consumer reads from a specific topic

Now What is a Partition?

Partition = Topic is split into smaller pieces

Why do we need Partition?
Imagine a single billing counter at a supermarket:
❌ Without Partition (1 counter):
🧑‍🤝‍🧑🧑‍🤝‍🧑🧑‍🤝‍🧑🧑‍🤝‍🧑🧑‍🤝‍🧑 ──▶ [Counter 1]
Everyone waits in ONE long queue → SLOW ❌
✅ With Partitions (3 counters):
🧑‍🤝‍🧑🧑‍🤝‍🧑 ──▶ [Counter 1]
🧑‍🤝‍🧑🧑‍🤝‍🧑 ──▶ [Counter 2]
🧑‍🤝‍🧑🧑‍🤝‍🧑 ──▶ [Counter 3]
Work split across counters → FAST ✅

Same idea in Kafka — Topic is split into partitions

Each partition handles a portion of messages → parallel processing!

How Topic & Partition look together
Topic: "payments"
┌─────────────────────────────────────┐
│  Partition 0: [msg1] [msg4] [msg7]  │
│  Partition 1: [msg2] [msg5] [msg8]  │
│  Partition 2: [msg3] [msg6] [msg9]  │
└─────────────────────────────────────┘

Messages are distributed across partitions

Each partition is an ordered sequence of messages


Key rule — Order is guaranteed WITHIN partition only
✅ Within Partition 0:  msg1 → msg4 → msg7  (always in order)
✅ Within Partition 1:  msg2 → msg5 → msg8  (always in order)

❌ Across partitions:   NO guaranteed order
If order matters → send related messages to same partition

How does Kafka decide which Partition to send to?
3 ways:
1. Round Robin (default)
   msg1 → Partition 0
   msg2 → Partition 1
   msg3 → Partition 2
   msg4 → Partition 0  (starts again)
2. By Key (most common)
   Producer sends message with a KEY

Key: "UserID-101" → always goes to Partition 0
Key: "UserID-202" → always goes to Partition 1
Key: "UserID-101" → always goes to Partition 0 ✅ (same user, same partition)
Same key = same partition = order maintained for that user ✅

3. Custom (you decide)
Write your own logic to decide which partition

Partition & Broker relationship
Each partition lives on a specific broker:
BROKER 1              BROKER 2              BROKER 3
────────────          ────────────          ────────────
Partition 0           Partition 1           Partition 2
[msg1]                [msg2]                [msg3]
[msg4]                [msg5]                [msg6]

Partitions are spread across brokers

This is how Kafka achieves scalability — more brokers = more partitions = more speed!
This is the beauty of Kafka 🎯
Producers and Consumers are completely independent
They don't know about each other
They only talk through the Topic

What is Offset?
Offset = position/sequence number of a message inside a partition
Every message gets a unique offset inside its partition
Offset always starts from 0
Offset always increases by 1
Offset is never reused

Each partition has its own offset starting from 0
Offset 0 in Partition 0 is different from Offset 0 in Partition 1

Why is Offset important?
Offset tells Consumer exactly where it stopped reading

Who tracks the Offset?

Consumer itself tracks its own offset

Consumer reads Offset 2
↓
Consumer saves:
"Topic: users, Partition 0, Offset: 2"
↓
This is called COMMITTING the offset
This saved position = Committed Offset
Stored in a special Kafka topic called __consumer_offsets

What if Consumer never commits offset?
Consumer reads Offset 0, 1, 2
But NEVER commits ❌
↓
Consumer crashes 💥
↓
Consumer restarts
↓
"Where did I stop?" 🤔
No record found!
↓
Starts from Offset 0 again ❌
Reads same messages again = DUPLICATES ❌
This is why committing offset is very important!

3 types of offset positions
Position	Meaning
Latest	Start reading only NEW messages (ignore old ones)
Earliest	Start reading from very beginning (Offset 0)
Specific	Start reading from a specific offset number

3 ways Producer decides Partition
Way 1️⃣ — You mention Partition directly
producer.send(topic="users", partition=2, message)
→ always goes to Partition 2 ✅

Way 2️⃣ — You send a Key
producer.send(topic="users", key="mob:1000", message)
→ Kafka hashes key → decides partition automatically
→ same key = always same partition ✅

Way 3️⃣ — Nothing (Round Robin)
producer.send(topic="users", message)
→ Kafka distributes across partitions automatically

When to use which?
Situation	Use
Order doesn't matter	Round Robin (Way 3)
Same user data must stay together	Key (Way 2)
You exactly know where data should go	Specific Partition (Way 1)

What is a Consumer Group?
Consumer Group = a team of consumers working together to read from a topic

Why do we need Consumer Group?
Imagine 1 consumer reading millions of messages:
Topic: "payments" (3 partitions)
┌─────────────┐
│ Partition 0 │ ──▶ ┐
│ Partition 1 │ ──▶ ├──▶ 😓 Consumer 1 (alone, overloaded)
│ Partition 2 │ ──▶ ┘
└─────────────┘
One consumer reading ALL partitions = slow & overloaded ❌

Solution — Consumer Group:
Topic: "payments" (3 partitions)
┌─────────────┐
│ Partition 0 │ ──▶ Consumer 1 ✅
│ Partition 1 │ ──▶ Consumer 2 ✅
│ Partition 2 │ ──▶ Consumer 3 ✅
└─────────────┘
Each consumer handles one partition = fast & balanced ✅
One partition can only be assigned to ONE consumer in a group

What if Consumers > Partitions?
Topic: "payments" (3 partitions)
Consumer Group (4 consumers)

Partition 0 ──▶ Consumer 1 ✅
Partition 1 ──▶ Consumer 2 ✅
Partition 2 ──▶ Consumer 3 ✅
✖️  Consumer 4 😴 (sits idle — no partition left)
Extra consumer sits idle
Not useful — don't add more consumers than partitions!

What if Consumers < Partitions?
Topic: "payments" (3 partitions)
Consumer Group (2 consumers)

Partition 0 ──▶ Consumer 1 ✅
Partition 1 ──▶ Consumer 1 ✅ (handles 2 partitions)
Partition 2 ──▶ Consumer 2 ✅

One consumer handles multiple partitions
Works fine but Consumer 1 does more work

What if a Consumer crashes? — Rebalancing
BEFORE CRASH:
Partition 0 ──▶ Consumer 1 ✅
Partition 1 ──▶ Consumer 2 ✅
Partition 2 ──▶ Consumer 3 ✅

Consumer 3 crashes 💥

AFTER REBALANCING (automatic):
Partition 0 ──▶ Consumer 1 ✅
Partition 1 ──▶ Consumer 2 ✅
Partition 2 ──▶ Consumer 2 ✅ (takes over)

Kafka automatically rebalances partitions among remaining consumers
No human needed ✅
No messages missed ✅

Multiple Consumer Groups — Independent reading
Topic: "payments"
┌─────────────┐
│ Partition 0 │ ──▶ Group A: Consumer 1 ✅
│ Partition 1 │ ──▶ Group A: Consumer 2 ✅
│ Partition 2 │ ──▶ Group A: Consumer 3 ✅
└─────────────┘
│
│ same topic!
▼
┌─────────────┐
│ Partition 0 │ ──▶ Group B: Consumer 1 ✅
│ Partition 1 │ ──▶ Group B: Consumer 2 ✅
│ Partition 2 │ ──▶ Group B: Consumer 3 ✅
└─────────────┘