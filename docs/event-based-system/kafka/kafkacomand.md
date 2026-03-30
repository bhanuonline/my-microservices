start server :
    
    sudo ./bin/kafka-server-start.sh ./config/kraft/server.properties

create a topic:

    ./kafka-topics.sh \
    --create \
    --topic test-topic \
    --bootstrap-server localhost:9092 \
    --replication-factor 1 \
    --partitions 4

producer:

    ./kafka-console-producer.sh \
    --bootstrap-server localhost:9092 \
    --topic test-topic

consumner:

    ./kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic test-topic \
    --from-beginning

Consumer with partition info:

    ./kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic test-topic \
    --from-beginning \
    --property print.partition=true \
    --property print.offset=true \
    --property print.timestamp=true
    
    ./kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic test-topic \
    --from-beginning \
    --property print.key=true \
    --property key.separator=":" \
    --property print.partition=true \
    --property print.offset=true \
    --property print.timestamp=true

    Note :the default partitioner is called the UniformStickyPartitioner for no-key records.


Kafka CLI command with message key

    kafka-console-producer \
    --broker-list localhost:9092 \
    --topic test-topic \
    --property "parse.key=true" \
    --property "key.separator=:"


list of topic;
./kafka-topics.sh --bootstrap-server localhost:9092 --list
for docker ;
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list

consumer group:;
./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
./kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
--describe --group my-group

read commit log :
./kafka-console-consumer.sh \
--bootstrap-server localhost:9092 \
--topic __consumer_offsets \
--from-beginning \
--formatter "kafka.coordinator.group.GroupMetadataManager\$OffsetsMessageFormatter" \
--consumer.config config/consumer.properties

bin/kafka-run-class.sh kafka.tools.DumpLogSegments \
--files /Users/bhanupratap/Documents/my/kafka_2.13-3.9.1/kafkaLog/test-topic-0/00000000000000000000.log \
--print-data-log


Create broker configs
cp config/server.properties config/server-1.properties
cp config/server.properties config/server-2.properties
cp config/server.properties config/server-3.properties
Edit each config for all server property
broker.id=1
listeners=PLAINTEXT://:9092
log.dirs=/tmp/kafka-logs-1

Test replication
bin/kafka-topics.sh \
--create \
--bootstrap-server localhost:9092 \
--replication-factor 3 \
--partitions 1 \
--topic replicated-test

bin/kafka-topics.sh --describe --bootstrap-server localhost:9092 --topic replicated-test
bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic replicated-test
bin/kafka-console-consumer.sh --bootstrap-server localhost:9093 --topic replicated-test --from-beginning
