What Is a Consumer Group?
In Kafka:

A Consumer Group is a set of consumers that work together to consume data from a topic.
Kafka splits a topic into partitions, and each partition is consumed by only one consumer within a group at any given time.
Consumers in the same group share the workload.
Each group is identified by a group.id.
Key points:

1 group, multiple consumers → load-balanced consumption (high throughput).
Multiple groups → each group gets its own copy of the messages (good for fan-out scenarios).

Example
Let’s say:

Topic: order-events
3 partitions: P0, P1, P2
Consumer Group: inventory-group
Consumers: InventoryConsumer1, InventoryConsumer2
Partition assignment in the group:

InventoryConsumer1 → P0, P1
InventoryConsumer2 → P2
If one consumer dies, the remaining consumer will take over the lost partitions (rebalance).


1️⃣ Consumer Rebalance - The Definition
A consumer rebalance is the process by which Kafka redistributes partitions among the consumers in a consumer group.

This happens when:

A new consumer joins the group
An existing consumer leaves (crashes, stops)
The number of partitions changes for a topic
The group metadata refresh triggers due to timeouts or subscription changes

Rebalance is Kafka’s way of making sure all topic partitions are fairly assigned to the available consumers in a group, ensuring parallel and fault-tolerant message consumption.

3️⃣ How does Kafka Trigger a Rebalance?
Kafka uses a Group Coordinator broker:

Each consumer sends heartbeat signals.
If a heartbeat is missed and session.timeout.ms expires → coordinator assumes consumer is dead → triggers rebalance.
Also triggered on join or leave group events.

4️⃣ Effects of Rebalance
✅ Pros:

Keeps work balanced across consumers.
Ensures failover — if a consumer dies, others take over its partitions.
⚠️ Cons:

Downtime during rebalance: No processing happens until ownership of partitions is reassigned.
If rebalance happens too frequently → unstable consumer group and throughput drops.


1️⃣ What is a Consumer Offset?
In Kafka, messages in a partition are stored with a sequential ID called the offset.

Think of it as the row number in a log file.

Example for a single topic partition (P0):

Offset:  0    1    2    3    4    5
Message: M0   M1   M2   M3   M4   M5

2️⃣ Why Do We Need Offsets?
Guarantees at-least-once or exactly-once message delivery.
Allows consumers to resume reading after a failure without losing data or re-reading old messages (unless you want to).
Each consumer group has its own offsets, so different services can process the same topic independently.


3️⃣ Where Are Offsets Stored?
Kafka stores committed offsets in an internal topic called:
__consumer_offsets
