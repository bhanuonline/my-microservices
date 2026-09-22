# Event-driven basics — Spring Cloud Stream (functional model)

## The problem it solves

- Sync HTTP call couples caller to callee tightly. New consumer = modify producer.
- Events flip it: producer publishes facts, N consumers react. Zero producer changes when adding a consumer.

## How it works

```
<!-- ASCII: producer → Kafka topic → 1..N consumers (each in own group) -->
```

## Two programming models

- `spring-kafka` — low-level: `KafkaTemplate`, `@KafkaListener`
- `spring-cloud-stream` — higher-level: `StreamBridge`, `Consumer<T>` bean. **We use this.**

## Naming convention

- Consumer bean `foo` → binding `foo-in-0`
- Producer via `streamBridge.send("foo-out-0", ...)` → binding `foo-out-0`
- Each binding maps to a topic in yml

## Consumer groups

- Same group name = **load balance** (only one instance receives each message).
- Different group names = **fan out** (each service gets a copy).
- Topic naming: `<domain>.<event-past-tense>` — e.g. `user.registered`, `order.created`.

## How it's wired in THIS project

- **Publisher:** `StreamBridge` in `user-service`, `order-service`, `payment-service`, `notification`.
- **Consumer:** `@Bean Consumer<T>` in the service that reacts.
- **Config keys:**
  ```yaml
  spring.cloud.function.definition: bean1;bean2   # comma-separated bean names
  spring.cloud.stream.bindings.<bean>-in-0.destination: <topic>
  spring.cloud.stream.bindings.<bean>-in-0.group: <consumer-group>
  ```

## Event flows in this project

| Flow | Producer | Topic | Consumer(s) |
|---|---|---|---|
| User registration | user-service | `user.registered` | notification |
| Order created (choreo) | order-service | `order.created` | payment-service |
| Payment result (choreo) | payment-service | `payment.completed` / `payment.failed` | order-service |
| Payment command (saga) | order-service | `payment.commands` | payment-service |
| Payment reply (saga) | payment-service | `payment.replies` | order-service |
| Notify command (saga) | order-service | `notification.commands` | notification |
| Notify reply (saga) | notification | `notification.replies` | order-service |

## How to observe it running

```bash
# List topics
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# Peek at a topic
docker exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic user.registered --from-beginning
```

## Common failure modes

- `spring.cloud.function.definition` missing your bean → silently not wired.
- Binding name mismatch — must be exactly `<beanName>-in-0` / `<beanName>-out-0`.
- Consumer group with old offsets — messages you thought would be consumed were already committed.

## Interview talking points

- Choreography vs orchestration (see [saga-orchestration.md](saga-orchestration.md)).
- Fat events vs thin events.
- Delivery semantics: at-most-once, at-least-once, exactly-once.
- Backpressure (concept — Kafka handles differently than reactive streams).
