🔧 1. Prerequisites
java -version(JDK 8 or higher)
    if not  sudo apt install openjdk-11-jdk (Ubuntu)
            brew install openjdk(mac)
📦 2. Download Kafka
wget https://downloads.apache.org/kafka/3.7.0/kafka_2.13-3.7.0.tgz
tar -xzf kafka_2.13-3.7.0.tgz
cd kafka_2.13-3.7.0

▶️ 3. Start Kafka (New Way - KRaft mode)
Kafka no longer requires Zookeeper (latest versions).

Start Kafka server:
bin/kafka-storage.sh format -t $(bin/kafka-storage.sh random-uuid) -c config/kraft/server.properties
bin/kafka-server-start.sh config/kraft/server.properties

🧪 4. Test Kafka
Create a topic:
bin/kafka-topics.sh --create --topic test-topic --bootstrap-server localhost:9092
Produce messages:
bin/kafka-console-producer.sh --topic test-topic --bootstrap-server localhost:9092
Consume messages:
bin/kafka-console-consumer.sh --topic test-topic --from-beginning --bootstrap-server localhost:9092

🐳 Alternative (Easiest): Docker Setup
If you want quick setup:
docker run -p 9092:9092 apache/kafka:latest

Note :
Default port: 9092
Logs stored in /tmp/kafka-logs
Use KRaft mode for new projects (no Zookeeper)

# docker based instalation
docker ps -a (check all container)
docker ps (check running container)
docker images
docker logs kafka
docker exec -it my-kafka bash (Enter container)
docker image prune -a
docker system prune -a --volumes (fully reset)


# error
The cluster.id you’re trying to use now ≠ the one already stored in meta.properties
Specifically:
Expected: HhKOyx7BR5iLW1zWAnRyxg
Found: Mw-y5wy4Rh6Sb3efKWOVNw

👉 This happens because:
You already formatted the storage earlier
Now you're trying to format again with a new cluster ID

🔥 Option 1: Clean reset (Recommended for local/dev)
Delete old Kafka data and reformat.
rm -rf /Users/bhanupratap/Documents/my/kafka_2.13-3.9.1/log/kraft-combined-logs

then
bin/kafka-storage.sh format -t $(bin/kafka-storage.sh random-uuid) -c config/kraft/server.properties

♻️ Option 2: Reuse existing cluster ID
Instead of generating a new UUID, use the existing one.
Open file:
vi log/kraft-combined-logs/meta.properties
Copy:
cluster.id=Mw-y5wy4Rh6Sb3efKWOVNw
Run:
bin/kafka-storage.sh format -t Mw-y5wy4Rh6Sb3efKWOVNw -c config/kraft/server.properties

🚫 Important rule
👉 Never reformat a directory with a different cluster ID
Kafka treats it as corruption.

🤔 Why do we need to “format” Kafka?
In Apache Kafka (KRaft mode), formatting initializes the storage directory before the broker starts.
Think of it like:
🧠 “Preparing Kafka’s brain and identity before it starts working.”

🧱 What happens during kafka-storage.sh format?

# When you run format, Kafka:
1. 🆔 Creates a Cluster ID
   Unique identifier for your Kafka cluster
Stored in:
meta.properties

2. 📁 Initializes log directories
Creates internal folders like:
log/kraft-combined-logs/
This is where Kafka stores:
Topics data
Partitions
Metadata

3. 🧾 Writes metadata (meta.properties)
Example:
cluster.id=Mw-y5wy4Rh6Sb3efKWOVNw
node.id=1
version=1

# use full command 
find . -name meta.properties
chmod -R 755 logs[Give permission to logs folder]
sudo chown -R $(whoami) logs [Change ownership (best fix)]
tmux split-window -h   # vertical
tmux split-window -v   # horizontal

Just remember: STPC

# Start Kafka
bin/kafka-server-start.sh config/kraft/server.properties

# Create topic
bin/kafka-topics.sh --create --topic test-topic --bootstrap-server localhost:9092

# List topics
bin/kafka-topics.sh --list --bootstrap-server localhost:9092

# Producer
bin/kafka-console-producer.sh --topic test-topic --bootstrap-server localhost:9092

# Consumer
bin/kafka-console-consumer.sh --topic test-topic --from-beginning --bootstrap-server localhost:9092

✅ Correct usage scenarios
✅ Case 1: Kafka in Docker, UI in Docker (same network)

👉 Use:
kafka:9092   (or your container name: my-kafka:9092)
✔️ Best practice

✅ Case 2: Kafka on your machine (not Docker), UI in Docker
👉 Use:
host.docker.internal:9092
✔️ Because container needs to reach your laptop

✅ Case 3: App on your machine, Kafka in Docker
👉 Use:
localhost:9092
✔️ Because port is exposed

# For ui
docker run -d \
-p 9000:9000 \
-e KAFKA_BROKERCONNECT=my-kafka:9092 \
--name kafdrop \
obsidiandynamics/kafdrop

docker run -d \
--name kafdrop \
--network container:my-kafka \
-e KAFKA_BROKERCONNECT=localhost:9092 \
obsidiandynamics/kafdrop

🧠 Simple memory trick
Same Docker → use container name
Docker → Host → use host.docker.internal
Host → Docker → use localhost

# Open a shell into the Kafka container
docker exec -it kafka bash

# Create a topic (3 partitions, replication factor 1 for local dev)
kafka-topics --create \
--topic order-events \
--partitions 3 \
--replication-factor 1 \
--bootstrap-server localhost:9092

# List all topics
kafka-topics --list --bootstrap-server localhost:9092

# Describe a topic (shows partition/replica layout)
kafka-topics --describe --topic order-events --bootstrap-server localhost:9092

# Produce messages manually (type a message, hit Enter, Ctrl+C to exit)
kafka-console-producer \
--topic order-events \
--bootstrap-server localhost:9092

# Produce with a key (key:value format)
kafka-console-producer \
--topic order-events \
--bootstrap-server localhost:9092 \
--property "key.separator=:" \
--property "parse.key=true"

# Consume from the beginning
kafka-console-consumer \
--topic order-events \
--from-beginning \
--bootstrap-server localhost:9092

# Consume showing keys and offsets
kafka-console-consumer \
--topic order-events \
--from-beginning \
--bootstrap-server localhost:9092 \
--property print.key=true \
--property print.offset=true

# Check consumer group lag
kafka-consumer-groups \
--describe \
--group fulfillment-group \
--bootstrap-server localhost:9092

# Change partition count (you can only increase, never decrease)
kafka-topics --alter \
--topic order-events \
--partitions 6 \
--bootstrap-server localhost:9092

# Delete a topic
kafka-topics --delete \
--topic order-events \
--bootstrap-server localhost:9092

# Describe a topic — shows partition/leader/replica layout
kafka-topics --describe \
--topic order-events \
--bootstrap-server localhost:9092

# Basic producer — type a message, hit Enter, Ctrl+C to exit
kafka-console-producer \
--topic order-events \
--bootstrap-server localhost:9092

# Produce with keys (key:value format, separator is ":")
kafka-console-producer \
--topic order-events \
--bootstrap-server localhost:9092 \
--property "parse.key=true" \
--property "key.separator=:"

# Example input for keyed producer:
# ord-11:{"qty":2,"item":"shoes"}
# ord-12:{"qty":1,"item":"hat"}

# Consume from the beginning
kafka-console-consumer \
--topic order-events \
--from-beginning \
--bootstrap-server localhost:9092

# Consume and print keys + offsets
kafka-console-consumer \
--topic order-events \
--from-beginning \
--bootstrap-server localhost:9092 \
--property print.key=true \
--property print.offset=true \
--property print.partition=true

# Consume only the latest messages (skip history)
kafka-console-consumer \
--topic order-events \
--bootstrap-server localhost:9092

# Consume as part of a named consumer group
kafka-console-consumer \
--topic order-events \
--from-beginning \
--bootstrap-server localhost:9092 \
--group fulfillment-group

# Consume only N messages then exit
kafka-console-consumer \
--topic order-events \
--from-beginning \
--bootstrap-server localhost:9092 \
--max-messages 10

# List all consumer groups
kafka-consumer-groups --list \
--bootstrap-server localhost:9092

# Describe a group — shows lag per partition
kafka-consumer-groups --describe \
--group fulfillment-group \
--bootstrap-server localhost:9092

# Reset offsets to beginning (re-read all messages)
# --dry-run first to preview, then remove it to apply
kafka-consumer-groups --reset-offsets \
--group fulfillment-group \
--topic order-events \
--to-earliest \
--dry-run \
--bootstrap-server localhost:9092

kafka-consumer-groups --reset-offsets \
--group fulfillment-group \
--topic order-events \
--to-earliest \
--execute \
--bootstrap-server localhost:9092

# Reset to latest (skip all existing messages)
kafka-consumer-groups --reset-offsets \
--group fulfillment-group \
--topic order-events \
--to-latest \
--execute \
--bootstrap-server localhost:9092

# Delete a consumer group (must have no active members)
kafka-consumer-groups --delete \
--group fulfillment-group \
--bootstrap-server localhost:9092

# List all brokers in the cluster
kafka-broker-api-versions \
--bootstrap-server localhost:9092

# Check the cluster ID and KRaft metadata
kafka-metadata-quorum \
--bootstrap-server localhost:9092 \
describe --status

# View internal topic offsets (useful for debugging lag)
kafka-get-offsets \
--topic order-events \
--bootstrap-server localhost:9092

# Tab 1: producer with keys
docker exec -it kafka kafka-console-producer \
--topic order-events \
--bootstrap-server localhost:9092 \
--property "parse.key=true" \
--property "key.separator=:"

# Tab 2: consumer showing everything
docker exec -it kafka kafka-console-consumer \
--topic order-events \
--from-beginning \
--bootstrap-server localhost:9092 \
--property print.key=true \
--property print.partition=true \
--property print.offset=true

# Tab 3: watch lag
docker exec -it kafka kafka-consumer-groups \
--describe \
--group my-group \
--bootstrap-server localhost:9092

