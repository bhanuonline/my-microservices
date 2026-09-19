# Flow — DLQ (poison message quarantine)

## Goal

Publish a malformed JSON to a consumed topic, watch consumer retry 3 times, then route to `<topic>.DLT`.

## Trigger — poison message

```bash
docker exec -it kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic user.registered
```

Type:
```
this-is-not-json
```
Press Enter, then Ctrl+D.

## Expected timeline

| t | Event |
|---|---|
| 0 | notification-service attempts to deserialize → throws |
| ~500ms | retry 1 → throws |
| ~1s | retry 2 → throws |
| ~2s | retry 3 → throws |
| ~2.1s | Cloud Stream publishes to `user.registered.DLT` |
| ~2.2s | order-service `DlqObserver` logs it |
| ~2.2s | Main topic offset advanced; new messages resume flowing |

## Verify

```bash
# 1. DLT topic has the message
docker exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic user.registered.DLT --from-beginning --max-messages 1

# 2. DlqObserver logged it (in order-service logs)
docker compose logs order-service | grep "DLQ received"
```

Expected log line:
```
ERROR ... DlqObserver : DLQ received | originalTopic=user.registered originalOffset=... exception=<deserialization error> payload=this-is-not-json
```

## What could go wrong

- **No DLQ arrival:** `autoCommitOnError` not set → offset stuck → replays forever, never DLT.
- **Deserialization failure bypasses handler retry** — Cloud Stream behavior varies; some binder versions require a `ListenerFailedException` handler bean.
- **DLT topic never created** — Kafka should auto-create; check broker's `auto.create.topics.enable=true` (bitnami default: true).

## Replay from DLT (after fixing the cause)

```bash
# One-shot replay
docker exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic user.registered.DLT --from-beginning --max-messages 10 | \
docker exec -i kafka kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic user.registered
```

Real prod: build a small admin tool for selective replay.

## Interview talking points

- DLQ prevents "silent starvation" from poison messages.
- Retries handle transient; DLQ handles poison. Both together = comprehensive.
- Retry topics pattern (retry.5s, retry.30s, retry.5m, then DLT) is a common enhancement.
- Kafka has no native DLQ — this is a Spring feature.
- Set retention on DLT — never store forever, but long enough to investigate.
