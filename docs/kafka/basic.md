**What is kafka ?**

    Apache Kafka is an open-source distributed event streaming platform, designed for high-throughput, low-latency data handling
    It was originally developed by LinkedIn and is now maintained by the Apache Software Foundation.

**High-throughput**

    Meaning: The system can process a large amount of data or a large number of operations in a given period of time.
    Focus: Maximizing volume of work done.
    Measured in: Data units per second (e.g., Mbps, GB/s, transactions per second).
    Example:
    A network link that can transfer 10 gigabits per second.
    A data processing pipeline that can handle millions of database writes per minute.

**Low-latency**

    Meaning: The system responds or processes requests very quickly — the delay between input and response is small.
    Focus: Minimizing time delay.
    Measured in: Time (e.g., milliseconds, microseconds).
    Example:
    Sending a request to a server and getting a response in 1 millisecond.
    Video conferencing systems needing minimal lag for real-time conversation.

**Key Difference**

    Throughput is about how much work you can do over time.
    Latency is about how fast each individual piece of work gets done.

Kafka can connect to external systems (for data import/export) via Kafka Connect, and provides the Kafka Streams libraries for stream processing applications.
Each record has an offset — its position in that partition.

**Guarantees ordering within individual partitions?**

    Inside one partition:
    The messages are stored and delivered in the exact same order they were written.
    If message A is written before message B in the same partition, any consumer reading that partition will always see A before B.
    
    Across multiple partitions:
    Kafka does not guarantee that messages from different partitions will be seen in the order they were produced.
    If message A is in partition 0 and message B is in partition 1, a consumer might receive B before A — even if A was produced first.

**Why order is this important?**

   If you care about processing events in exact order, you must put all related events in the same partition (by using a consistent “key” when producing messages).
   If related events are spread across partitions, your consumers may see them in a mixed order.

**Kafka uses manual offset management, giving consumers control over retries and failure handling.**

1. What is an “offset” in Kafka?
   In Kafka, each partition is like an append-only log of messages.

Every message has a unique offset — an integer that marks its position in the partition:
Partition 0: Offset 0 → Offset 1 → Offset 2 → Offset 3 → ...
Offset 0 = first message written to that partition
Offset 1 = second message, and so on
Consumers read messages in order of offsets.

2. "Manual offset management"
   When a consumer reads a message, Kafka does not automatically “mark it as read” forever.
   Instead:

The consumer application is responsible for telling Kafka the last offset it has successfully processed.
This "telling" is called committing the offset.

3. Why give consumers control?
   Control lets you handle failures and retries safely.

Example:

Your consumer reads offset 42 and processes the message.
Right before it completes, the process crashes.
If Kafka had auto-committed the offset, offset 42 would be marked as "done" and never re-read — even though processing didn’t finish.
With manual control, you only commit the offset after you’re certain the message was successfully processed (e.g., stored in a database, API called successfully).
On restart, the consumer starts again from the last committed offset, so offset 42 gets retried

5. Commit Strategies
   Manual, synchronous commit — commit after each processed message or batch (slower but safer).
   Manual, asynchronous commit — commit in background after processing (faster, riskier).
   Auto-commit (not truly manual) — Kafka commits automatically at a fixed interval (fast but risky if crashes happen before processing finishes).


In Simple term :
Kafka is like a messaging system (similar to a publish-subscribe system), but it’s built to handle massive amounts of real-time data from multiple sources and deliver it to multiple consumers reliably and quickly.

Key Concepts
Producer – An application that sends (publishes) data (messages/events) to Kafka.
Consumer – An application that reads (subscribes) data from Kafka.
Topic – A category or feed name to which records are stored and published.
Partition – Topics are split into partitions for scalability and parallelism.
Broker – A Kafka server that stores data and serves clients.
Cluster – A group of brokers working together.
Offset – A unique ID for each message in a partition; consumers track this to know their position.
Consumer Group → A group of consumers that share the work of reading from partitions.
Consumers – Read messages from Kafka.
Zookeeper / KRaft → Coordinates cluster metadata (newer versions use KRaft instead of ZooKeeper).

Why Use Kafka?
High throughput – Can handle millions of messages per second.
Scalable – Easily add more brokers to handle more data.
Durable – Messages are stored on disk and replicated across brokers.
Fault-tolerant – Can handle broker failures without data loss.
Real-time – Ideal for streaming and real-time analytics.

Use Cases
Real-time analytics (e.g., fraud detection in banking)
Log aggregation (collect logs from various apps into one stream)
Messaging (loosely coupled communication between services)
Event-driven architectures (microservices communication)
Data pipelines (ETL, data ingestion to storage systems like Hadoop or Elasticsearch)


Setup :
download kafka 
go to kafka folder 
we can start kfka using zookeper ot kraft
i can use kraft
extract zip file in a folder
genrate a unique id  : kafka_2.13-3.9.1 % sudo ./bin/kafka-storage.sh random-uuid
format the storage dir : sudo ./bin/kafka-storage.sh format -t KvEiUPnRRiaVVFEYn38bNQ -c ./config/kraft/server.properties
start the server : sudo ./bin/kafka-server-start.sh ./config/kraft/server.properties

Ex1 :
producer > kafka broker > consumer 
Create a topic
bin/kafka-topics.sh --create --topic test-topic --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1

Start a producer
bin/kafka-console-producer.sh --topic test-topic --bootstrap-server localhost:9092

start a consumer :
bin/kafka-console-consumer.sh --topic test-topic --from-beginning --bootstrap-server localhost:9092

**Ex2 :**
App A sends logs
App B consumes them
Another consumer group archives logs

**create a topic with partitions 2**
bin/kafka-topics.sh --create \
--topic app-logs \
--bootstrap-server localhost:9092 \
--partitions 2 \
--replication-factor 1

--partitions allows parallel processing [Scalability] ,
Higher Throughput : For example, 1 partition might handle 10k msgs/sec, but 4 partitions might handle 40k msgs/sec.
Fault Tolerance & Data Distribution : With multiple partitions, data and load is distributed

Ordering Control
Within a partition: Messages are strictly ordered by offset
Across partitions: Ordering is NOT guaranteed
You can choose partition keys if you want all events for the same customer to go to the same partition (preserves per-customer order)

--replication-factor is number of copies

start a producer (simulate logs)
bin/kafka-console-producer.sh \
--topic app-logs \
--bootstrap-server localhost:9092 

Start 1st consumer group (real-time monitoring)
bin/kafka-console-consumer.sh \
--topic app-logs \
--group monitoring-service \
--bootstrap-server localhost:9092

Start 2nd consumer group (archiving)
bin/kafka-console-consumer.sh \
--topic app-logs \
--group archive-service \
--bootstrap-server localhost:9092

Notice: Both groups get all messages individually → Kafka supports multiple independent consumers.

Parallelism Example
Let’s say 4,000 messages arrive:

With 1 partition → 1 consumer processes 4,000 sequentially
With 4 partitions → 4 consumers process 1,000 each = faster throughput

Rule
you don’t have to have the same number of consumers as partitions — but there’s a relationship between them that affects parallelism and throughput.
Max active consumers in a consumer group = number of partitions for that topic.
You can have less consumers than partitions (works fine).
You can have equal consumers to partitions (full parallelism).
You can have more consumers — but the extras will be idle.

You don’t need to match consumers = partitions, but:
Equal → maximum parallelism (balanced load)
Less → works, but each consumer does more work
More → wasted resources (idle consumers)

Kafka guarantees that:
Each partition is consumed by exactly one consumer in a consumer group at a time.

This means:
1 partition → max 1 active consumer in the group
4 partitions → max 4 active consumers in that group

But in simple terms:
Partitions ≥ max number of consumers in a single consumer group you’ll ever need

Why?
Partitions are the upper limit of active consumers per group.
Example: Need up to 6 consumers working in parallel → at least 6 partitions.

Other command 
**list of topic**
bin/kafka-topics.sh --list --bootstrap-server localhost:9092

**Discribe the topic**
bin/kafka-topics.sh --describe \
--topic orders \
--bootstrap-server localhost:9092

Create a consumer group
Kafka allows grouped consumption so messages are load balanced.
bin/kafka-console-consumer.sh \
--topic orders \
--group order-group \
--bootstrap-server localhost:9092

list consumer group
bin/kafka-consumer-groups.sh \
--bootstrap-server localhost:9092 \
--list

Check Consumer Group Offsets
bin/kafka-consumer-groups.sh \
--bootstrap-server localhost:9092 \
--describe \
--group order-group

Current Offset → Where the consumer is now
Log End Offset → Latest available message
Lag → Messages still to be consumed

Delete a Topic
bin/kafka-topics.sh \
--delete \
--topic orders \
--bootstrap-server localhost:9092
(Only works if delete.topic.enable=true in server.properties)




Component :
1. Topics
   A topic is like a named queue, grouped by message category (e.g., payments, orders, user_logs).
   Data in Kafka topics is split into partitions to enable parallelism and scalability.
2.  Partitions
    Each partition stores messages in an append-only log.
    Messages within a partition are ordered by an offset — a simple numerical ID.
    Different consumers can read different partitions in parallel.
    Topic: orders
    Partition 0: offset 0 —> offset 5
    Partition 1: offset 0 —> offset 5
3. Producers
   Push messages to a topic (and optionally specify the partition).
   Kafka ensures data is written to the partition log sequentially → making disk writes fast.
4. Consumers
   Organized into consumer groups.
   Kafka assigns partitions to consumers in the group, so each partition is consumed by exactly one consumer in a group.
   Consumers keep track of their last read offset so they can resume reading from where they left off.
5. Brokers
   A broker is a Kafka server instance.
   Stores partition data and serves producer/consumer requests.
   One broker can handle thousands of partitions.
   Kafka scales by adding more brokers into a cluster.
6. Replication
   Partition data is replicated across multiple brokers for fault tolerance.
   One broker is the leader for a partition; others are followers.
   Producers and consumers talk to the leader; followers replicate the data from it
7. message key
8. kafka Serilisation and Deserilisation 
9. consumer Group


**kafka UI:**
docker run --rm-d \
--name kafka-ui \
-p 8080:8080 \
-e KAFKA_CLUSTERS_0_NAME=local \
-e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092 \
provectuslabs/kafka-ui:latest

Change in server.property file in kraft
Note : advertised.listeners=PLAINTEXT://host.docker.internal:9092,CONTROLLER://host.docker.internal:9093

What is Kafka Connect? — 
Kafka Connect is just a tool that moves data in and out of Kafka automatically using ready-made connectors.
Without Kafka Connect → You have to write a custom application (producer/consumer) to read from a DB, API, or file, and write that data into Kafka topics (and the other way around).
With Kafka Connect → You configure a connector (a JSON file), and Connect service does all of that for you with no custom code.
Kafka Connect itself does not replace your brokers → it’s a service that connects Kafka to other systems reliably.
http://localhost:8083/connectors
http://localhost:8083/connector-plugins
http://localhost:8083/

A connector is essentially a job definition:
Source Connector → pulls data from an external system into Kafka topics.
Sink Connector → pushes data from Kafka topics into an external system.

Data Flows
Source Connector
External system → Kafka Connect → Kafka topic(s).
Sink Connector
Kafka topic → Kafka Connect → External system.
Kafka Connect = Kafka’s data integration framework.


**KafkaStream :**
Kafka Streams is a Java library (part of Apache Kafka) used to build real-time, event-driven applications that process data directly from Kafka topics.
It’s not a separate cluster or service — it runs inside your own application (as a JAR dependency).
It allows you to read, process, and write data back to Kafka in real time — basically a stream processing API on top of Kafka.
Kafka Streams is built on top of Kafka’s consumer/producer APIs and adds:
Data transformation
Joining streams
Aggregations

Kafka Streams vs Others
Tech	                Runs on	                        Use case
Kafka Streams	        Inside your app (Java library)	Lightweight, embedded processing
ksqlDB	                Server/service	                SQL-like stream processing
Apache Flink/Spark	    Separate processing clusters	Heavy, large-scale batch+stream

Kafka Streams is lightweight and ideal for microservices that consume Kafka and transform data on the fly.


**how we can Plan for future scaling ?**