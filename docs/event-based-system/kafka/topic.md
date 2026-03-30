A Kafka topic is a named category or feed into which Kafka stores events (messages).

Think of it like a folder in Kafka where producers write messages and consumers read them.
Every message in Kafka must belong to a topic.
Topics are append-only logs — new messages get added to the end.

📦 Example
If you have an e-commerce app:

You might have a topic named "orders" where every placed order is stored.
Another topic "payments" for payment transactions.
Another "shipments" for delivery updates.

1️⃣ Producers write to a topic

Producers ---> Kafka Topic ---> Consumers
A Kafka Producer sends a record to a specific topic.
The topic name is required when sending messages.

2️⃣ Consumers read from a topic
One or more Consumers subscribe to the topic and get events in order per partition.

Partitioning
A topic is divided into partitions:

Each partition is an ordered, immutable sequence of events.
Events inside a single partition have a strict order, but across different partitions the order is not guaranteed.
Partitions allow horizontal scaling — more partitions = more parallel consumers.
partition are continuously grow as a new record inserted
all record are persisted in a commit log in the file where kafka insatalled
producer has a complete control of which partition the messgae going to go

Retention
Kafka keeps messages for a configured period (e.g., 7 days), even after they’ve been read.
Retention can be time-based or size-based.

Create a topic :
bin/kafka-topics.sh \
--create \
--topic my-topic \
--bootstrap-server localhost:9092 \
--partitions 3 \
--replication-factor 1

Key Takeaways
More partitions → more throughput (more consumers can read in parallel).
Messages in Kafka topics are retained even after they’re consumed (configurable retention).
Topics allow 1-to-1, 1-to-many, and many-to-many message patterns.

Kafka Topic Diagram
┌──────────────────────────────┐
│        Kafka Cluster          │
│   (multiple brokers/servers)  │
└──────────────────────────────┘
▲          ▲
│          │
Writes │          │ Reads
│          │

      ┌───────────────┐           ┌────────────────┐
      │   Producer 1  │ --------> │                │
      └───────────────┘           │                │
                                   │   TOPIC: orders│
      ┌───────────────┐           │ (3 partitions) │
      │   Producer 2  │ --------> │                │
      └───────────────┘           │                │
                                   └────────────────┘
                                         │    │    │
                                         ▼    ▼    ▼
                 Partition 0:  [Msg1, Msg4, Msg7, …]
                 Partition 1:  [Msg2, Msg5, Msg8, …]
                 Partition 2:  [Msg3, Msg6, Msg9, …]
                     ▲                ▲
                     │                │
           ┌────────────────┐   ┌────────────────┐
           │  Consumer 1    │   │  Consumer 2    │
           └────────────────┘   └────────────────┘

Explanation
Producers
Applications that send messages to a topic.
Topic
A named log — in this example named "orders".
Internally split into partitions for scalability and parallelism.
Partitions
Each partition is an ordered sequence of messages.
Each message gets a unique offset within a partition.
Consumers
Read messages from one or more partitions.
Kafka ensures order within each partition, but NOT across the whole topic.

**Kafka Consumer Groups Diagram**

Kafka Cluster

                ┌─────────────────────────┐
                │        Topic: orders     │
                │ (3 partitions: P0, P1, P2)│
                └─────────────────────────┘
                │   │   │
                ▼   ▼   ▼
                P0: [Msg1, Msg4, Msg7, ...]
                P1: [Msg2, Msg5, Msg8, ...]
                P2: [Msg3, Msg6, Msg9, ...]
    
        =========================================================
        Consumer Group A  (group.id = "order-processors")
        =========================================================
        
        ┌───────────────┐      ┌───────────────┐
        │  Consumer A1  │      │  Consumer A2  │
        └───────────────┘      └───────────────┘
        ▲                        ▲
        │                        │
        Reads P0 & P1           Reads P2

Explanation of Consumer Groups
Consumer Group = one or more consumers sharing the same group.id.
Kafka divides partitions among consumers in the same group (load balancing).
One partition → only one consumer in the same group can read from it at a time.
If you add more consumers than partitions, some consumers will stay idle.
If a consumer dies, Kafka rebalances and assigns its partitions to the remaining consumers.

Multiple Groups = Multiple Independent Reads
Kafka supports fan-out — different consumer groups can read the same data independently:

                Topic: orders
                  /   |   \
                 /    |    \
                ▼     ▼     ▼
        Group A         Group B
    (order-processors)  (order-analytics)
    
    P0 -> C1 (A)         P0 -> C1 (B)
    P1 -> C2 (A)         P1 -> C2 (B)
    P2 -> C2 (A)         P2 -> C1 (B)

Group A could be your order processing microservice.
Group B could be a BI/dashboard analytics job.
They don’t affect each other — offsets are tracked per group.

Key Points to Remember
One topic can have multiple consumer groups.
Inside a group: Partitions are split among consumers.
Between groups: Each group gets all messages.
Offsets (position in partition) are tracked per group.

Full Kafka Flow: Producers → Topic → Consumer Groups

    ┌──────────────────────────────────────────┐
    │           Kafka Cluster                   │
    │    (Brokers store topics in partitions)   │
    └──────────────────────────────────────────┘

       ┌─────────────┐           ┌──────────────────┐
       │ Producer 1  │  ──────►  │                  │
       └─────────────┘           │                  │
                                  │   Topic: orders  │
       ┌─────────────┐           │ (3 partitions)   │
       │ Producer 2  │  ──────►  │                  │
       └─────────────┘           └──────────────────┘
                                        │   │   │
                         ┌──────────────┘   │   └───────────────┐
                         ▼                  ▼                   ▼
                     Partition 0       Partition 1         Partition 2
                   [Msg1, Msg4...]   [Msg2, Msg5...]     [Msg3, Msg6...]

    ==================================================================================
    Consumer Group A (order-processors)
    ==================================================================================
    ┌───────────────┐       ┌───────────────┐
    │  Consumer A1  │       │  Consumer A2  │
    └───────────────┘       └───────────────┘
    ▲   ▲                   ▲
    │   └───────────────────┘
    │
    P0 → Consumer A1
    P1 → Consumer A1
    P2 → Consumer A2
    
    ==================================================================================
    Consumer Group B (order-analytics)
    ==================================================================================
    ┌───────────────┐       ┌───────────────┐
    │  Consumer B1  │       │  Consumer B2  │
    └───────────────┘       └───────────────┘
    ▲                         ▲
    │                         │
    P0 → Consumer B1          P1 → Consumer B2
    P2 → Consumer B2

**Why Partitions in Kafka?**
A partition is a subset of a Kafka topic's messages, stored in order, and spread across different Kafka brokers.
A partition is a subset of a Kafka topic's messages, stored in order, and spread across different Kafka brokers.

🔹 1. Scalability (Parallelism)
If a topic had only one partition, a single broker and consumer could process it at a time.
With multiple partitions, you can split the data among brokers and consume in parallel.
Kafka achieves this using partitions — allowing:

Parallel writes from producers
Parallel reads by multiple consumers

Example:
If a topic has 3 partitions and 3 consumers in the same group:

Each consumer handles one partition.
You triple your processing throughput compared to 1 partition

2. Load Balancing Across Brokers
   Kafka stores partitions across multiple brokers in the cluster.
   This avoids bottlenecks and spreads the load.
   Broker 1: P0
   Broker 2: P1
   Broker 3: P2
3. Fault Tolerance with Replication
  Each partition can have replicas on multiple brokers.
  If one broker goes down, another broker with the replica takes over.
   P0 (Leader: Broker1, Replica: Broker2)
   P1 (Leader: Broker2, Replica: Broker3)
   P2 (Leader: Broker3, Replica: Broker1)
4. Ordering Guarantees
   Order is guaranteed only within a partition, not across the whole topic.
   This means you can still have ordered data per key by using a partitioning key.
   Kafka uses key-based partitioning: all events with the same key go to the same partition → order is preserved for that key.
5. High Throughput
   Producers can write to different partitions in parallel.
   Consumers can read from different partitions simultaneously.
   More partitions → more parallel processing.


**Scaling Limits**
Max active consumers per group = # of partitions.
More partitions → more consumer instances possible → higher processing speed.
Adding more consumers than partitions gives no benefit (extras will be idle).


command :
List topics from your host machine-> bin/kafka-topics.sh --list --bootstrap-server localhost:9092


you don’t specify a key, the partitioner is triggered in round‑robin fashion
Ordering is only guaranteed per partition, not across the topic.
If you have multiple consumers in the same group, they will each receive messages from a subset of partitions.

Cosumer-offset ;
Cosumer has 3 option to reda the  message 
from-begeniing
latest
specify offset [via program]

consumer group:
What is a Consumer Group in Kafka?
A consumer group is a set of Kafka consumers that work together under one logical name to consume messages from topics
    Every consumer in a group is assigned partitions from the topic(s), so no message is processed by more than one consumer in the group.
    This allows parallel processing and load balancing.
    Consumer group is used for scalable message consumption
who manage the consumer group? > kafka broker  manage
kafka broker work as group coordinator
There’s no pre-create step — group is created when a consumer connects with a new group.id
The consumer group will remain in Kafka even if idle, until Kafka’s configured offsets.retention.minutes expires
By default, offsets are stored for 7 days if the group is inactive, then the group disappears

Commit log and retention policy :
kafka cluster:

how topic are disibuted?
how kafka handle data loss?
what is ISR?

**Kafka Producer**
A Kafka Producer is a client application in Apache Kafka responsible for publishing (sending) records (messages) to Kafka topics. Kafka is a distributed event streaming platform used for high-throughput, fault-tolerant, real-time data streams.
Asynchronous: Kafka producer is asynchronous by default but can be forced to be synchronous.

Kafka Producer — Sync vs Async
Synchronous send — you call .send(record).get(), which blocks until Kafka acknowledges the message.
Asynchronous send — you call .send(record, callback) and your code continues without waiting.
The callback is invoked when Kafka responds (success or failure) — useful for high throughput.

How data flow into kafak?
Key Properties Influencing Flow
Acknowledgments (acks): higher = better durability, lower = better latency.
Async vs Sync send: Async uses callbacks, doesn't block; Sync waits for ACK.
Batching: improves throughput but may add milliseconds of delay.
Replication: protects against broker failures, but each message is written to multiple brokers.

Send() Method:

    App Thread (You)
    producer.send(record, callback)
    |
    ├──> validate, serialize key/value
    ├──> choose partition
    ├──> enqueue record in RecordAccumulator (per partition batch)
    |
    +-- returns immediately to your code (non-blocking!)
    
    Sender Thread (Background)
    ├──> waits for batch full OR linger.ms timeout
    ├──> builds ProduceRequest
    ├──> gets broker leader from metadata cache (request metadata if needed)
    ├──> send over TCP via NetworkClient
    |
    Broker Leader
    ├──> append to commit log (fsync depending on settings)
    ├──> replicate to followers if needed
    ├──> send ProduceResponse (ACK)
    |
    Sender Thread
    ├──> read response
    ├──> execute your callback

Step 1 — Validation and Serialization
KafkaProducer first checks:
Is the topic name valid?
Does it allow null keys/values?
It uses the configured serializers (StringSerializer, ByteArraySerializer, etc.) to convert your key and value into byte arrays.
Step 2 — Partition Selection
KafkaProducer calls the configured Partitioner:
If you provided a key: hash(key) % numPartitions
If no key: uses a round-robin counter.
Result: topic + partition are determined for this record.
Step 3 — Append to RecordAccumulator
The producer has a component RecordAccumulator → in-memory buffer.
The byte-serialized message is appended to a batch for that partition:
Batching is key — multiple records bound for the same partition are combined into one Produce Request to improve throughput.
Parameters influencing this:
batch.size → max bytes per batch
linger.ms → how long to wait before sending, even if batch is not full
Step 4 — Asynchronous Sender Thread
Once the record is added to the RecordAccumulator, send() returns quickly to your application.
In the background:
The Sender Thread continuously checks the RecordAccumulator, groups messages into batches, and creates ProduceRequest objects.
Uses Kafka’s binary protocol to send these over TCP to the broker(s).
Step 5 — Network Send
The Sender Thread talks to a Kafka Broker leader for the partition.
Metadata cache inside KafkaProducer keeps track of which broker is the leader for each partition (from cluster metadata).
If leader info is missing or stale, the producer first requests metadata from Kafka brokers.
Step 6 — Broker Processing
On the broker side:

Broker receives ProduceRequest.
Appends the message to the commit log for the partition (disk write — optimized as sequential write).
If replication is enabled and acks=all, leader waits for in-sync replicas to confirm write.
Sends ProduceResponse with status back to producer.
Step 7 — Callback Execution
The producer I/O layer receives the ProduceResponse.
The Callback you supplied is executed in an I/O thread context, not your main thread.
Contains metadata:
topic
partition
offset
timestamp

**Kafka Producer configuration**

1. Minimum Required Producer Configs
   When creating a KafkaProducer in Java, you must set at least:

Property	        Description	                                                                                            Example
bootstrap.servers	Comma-separated list of broker addresses (host:port), used to bootstrap initial cluster metadata.	    "localhost:9092,localhost:9093"
key.serializer	    Class name of serializer to convert the key object into bytes.	                                        org.apache.kafka.common.serialization.StringSerializer
value.serializer	Same as above but for the value object.	                                                                org.apache.kafka.common.serialization.StringSerializer


2. Common Performance / Throughput Configs
   Property	                        Default	            Purpose
   batch.size	                    16384 (16 KB)	    Max bytes per batch per partition before sending. Larger batches → better throughput but more latency.
   linger.ms	                    0	                Wait time before sending a batch even if it’s not full. >0 allows more records per batch → higher throughput, slight extra latency.
   buffer.memory	                33554432 (32 MB)	Total memory available to producer for buffering unsent records.
   compression.type	                none	            Compress messages before sending (gzip, snappy, lz4, zstd). Smaller payloads, less network usage, possibly higher CPU.

   3. Reliability and Delivery Semantics
      Property	                                Default	                            Description
      acks	                                    1	                                How many acknowledgments from broker before producer considers a request complete: <br>0 – no ack; <br>1 – leader ack only; <br>all – all in-sync replicas ack.
      retries	                                2147483647 (∞ in new versions)	    Number of retries before giving up on sending a batch.
      retry.backoff.ms	                        100	                                Wait time before retrying send.
      max.in.flight.requests.per.connection	    5	                                Max unacknowledged requests per connection. For strict ordering with retries, set to 1.
      enable.idempotence	                    false	                            If true, ensures exactly-once delivery to a partition (no duplicates). Must be used with specific values of acks and retries.

Idempotence combo:

enable.idempotence=true
acks=all
retries set to max (default in modern clients)
max.in.flight.requests.per.connection ≤ 5 (default is fine)

Producer Config Strategy Tips
For low latency, keep linger.ms small (0–5ms) and batches small.
For high throughput, increase linger.ms (5–50ms) and batch.size (32–128 KB).
For strong reliability, use acks=all, enable.idempotence=true.
For throughput & savings, enable compression (lz4 or zstd).