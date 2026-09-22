# Dead Letter Queue (DLQ / DLT)

## The problem it solves

- A poison message (malformed, unhandled exception) blocks a Kafka partition forever.
- Every good message behind it starves.
- Service *looks* healthy while silently broken.

## Two failure categories

| Transient | Poison pill |
|---|---|
| DB blip, network glitch, rate limit | Malformed JSON, bug, invalid data |
| RETRY (a few times) | Don't retry — set aside |

## How it works

```
   Kafka → consumer → success ✓
                    → throws → retry 1 (backoff 500ms)
                             → throws → retry 2 (backoff 1s)
                                      → throws → retry 3 (backoff 2s)
                                               → throws → publish to <topic>.DLT
                                                        → advance offset ✓
                                                        → main topic unblocked
```

## How it's wired in THIS project

- **Retries:** 3 attempts with exponential backoff (`spring.cloud.stream.default.consumer`)
- **DLQ:** `enableDlq: true` + `autoCommitOnError: true` at `spring.cloud.stream.kafka.default.consumer`
- **Naming:** `<original-topic>.DLT` (Spring default suffix)
- **Observer:** `order-service/.../consumer/DlqObserver.java` — `@KafkaListener(topicPattern = ".*\\.DLT")`

## How to observe it running

```bash
# 1. Publish garbage directly to a topic
docker exec -it kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic user.registered
# Type: not-a-real-event
# Ctrl+D

# 2. Wait ~5 seconds

# 3. Check DLT topic
docker exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic user.registered.DLT --from-beginning --max-messages 1

# 4. DlqObserver should log the arrival
docker compose logs -f order-service | grep DLQ
```

## Common failure modes

- Forgot `autoCommitOnError` → poisoned offset stays, message replays on restart.
- Application throws on deserialization, not in handler → some Cloud Stream versions bypass DLQ (fix: catch in a global `ListenerFailedException` handler).
- DLQ topic itself fills up → set retention, monitor depth.

## Replay strategy

To move DLT messages back to main topic after fixing:
```bash
# Simple version: use kafka console tools
docker exec -it kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic user.registered.DLT --from-beginning --max-messages N | \
docker exec -i kafka kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic user.registered
```

Real prod: build a small admin UI or MirrorMaker job.

## Interview talking points

- DLQ solves the "silent starvation" problem.
- `autoCommitOnError` — subtle but critical.
- DLQ observer pattern → alert / persist for review.
- Related: retry topics pattern (retry.5s, retry.30s, retry.5m, then DLT).
- Kafka has no native DLQ — this is a Spring/Cloud Stream feature.
