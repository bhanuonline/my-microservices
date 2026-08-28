listeners=PLAINTEXT://0.0.0.0:9092
PLAINTEXT — no SSL/TLS, plain TCP
0.0.0.0 — listen on all network interfaces
9092 — port number

listeners = "Where Kafka is listening."
advertised.listeners = "Where Kafka tells you to call back."

Kafka Producer config: acks and Broker/Topic-level config: min.insync.replicas

1. acks (Producer-side)
   This is a producer configuration that defines how many acknowledgments the Kafka leader must receive before it considers the write successful.

Options:

acks value	Meaning	                                                                                                                                                                            Durability / Latency
0	        Producer does not wait for any acknowledgment. The message is added to the socket buffer and the send is considered complete immediately. No guarantee that broker even got it.	    Fast, possible data loss.
1	        Leader broker writes message to its local log (no fsync guarantee yet in default config) and responds to producer without waiting for followers.	                                Low latency, risk of data loss if leader crashes before followers replicate.
all         (or -1)	Leader waits until all in-sync replicas (ISR) have successfully written the record before acknowledging.	                                                                Strong durability, higher latency.

2. min.insync.replicas (Broker/Topic-side)
   Broker or topic setting.
   Defines the minimum number of replicas that must acknowledge a write for it to be considered successful when acks=all is used.
   If the number of in-sync replicas (ISR) drops below min.insync.replicas, then:

The broker will reject produce requests with acks=all and return an error:
NOT_ENOUGH_REPLICAS or NOT_ENOUGH_REPLICAS_AFTER_APPEND.

ex:
# Example: Topic with replication factor 3, requiring at least 2 replicas in sync
bin/kafka-topics.sh --bootstrap-server localhost:9092 \
--create --topic my-topic \
--partitions 3 \
--replication-factor 3 \
--config min.insync.replicas=2

3. How They Work Together
   When you set:

acks=all   (Producer)
min.insync.replicas=2   (Broker/Topic)

Scenario:

Suppose the topic replication factor is 3.
ISR normally = leader + 2 followers (total 3 brokers).
When producer sends:
Leader writes the message locally.
Leader waits until at least 2 replicas (min.insync.replicas=2) have stored it, including itself (important: leader counts as one).
If fewer than 2 ISR exist (due to failures), the broker will not acknowledge, and producer gets an error.

Example Durability Settings for Strong Guarantees:
Replication factor: ≥ 3 (common is 3 in prod).
min.insync.replicas: 2 (meaning leader + at least one follower must ack).
Producer:

props.put(ProducerConfig.ACKS_CONFIG, "all");
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true"); // avoid duplicates on retry
Result: Message is only acknowledged if ≥ 2 brokers store it; exactly-once delivery per partition is possible.
