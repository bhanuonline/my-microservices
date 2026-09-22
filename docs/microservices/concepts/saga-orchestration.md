# Saga — Orchestration + Compensation

## Two flavors

| | Choreography | Orchestration |
|---|---|---|
| Coordination | None. Each service reacts to events. | Central orchestrator sends commands. |
| Coupling | Loose. Zero central authority. | Orchestrator knows the flow. |
| Visibility | Emergent — hard to read the process. | One place = one flow. |
| Best for | 2-3 hops, simple flows. | 4+ hops, complex failure paths. |

We built BOTH in this project. Choreography for `user→notification` and the initial order→payment; orchestration for the full order→payment→notify→refund flow.

## Compensation — the key insight

You cannot roll back a distributed transaction. You must define **compensating actions** — the inverse of each forward step.

```
   Forward:  reserve inventory → charge payment → ship
   Compensating: release inventory ← refund ← cancel shipment
```

If step 3 fails, saga runs backwards through completed steps, executing compensations.

## State machine

```
                    STARTED
                       │
                       ▼ payment OK
                     PAID
                       │
                       ▼ notify OK
                    NOTIFIED  (terminal — success)

   Failure at any step:
                       ▼
                  COMPENSATING
                       ▼
                    FAILED (terminal)
```

## How it's wired in THIS project

- **Entity:** `order-service/.../saga/OrderSaga.java` — persisted state machine
- **Orchestrator:** `order-service/.../saga/OrderSagaOrchestrator.java` — @Service, transactional
- **Command/Reply DTOs:** `common-lib/.../saga/*.java`
- **Command topics:** `payment.commands`, `notification.commands`, `payment.commands.refund`
- **Reply topics:** `payment.replies`, `notification.replies`
- **Participants:**
  - payment-service: `PaymentCommandHandler` (consumes command, publishes reply)
  - notification: `NotifyUserCommandHandler`

## How to observe it running

```bash
# Happy path — quantity=2, amount=$19.98 < $50 threshold, notify succeeds
curl -X POST http://localhost:8083/api/v1/orders -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'
# check order status
curl http://localhost:8083/api/v1/orders/<orderId>   # → PAID

# Compensation path — force notify to fail
# TODO: modify NotifyUserCommand.message to include "fail-me" via a test hook
# Watch payment-service logs for "Executing REFUND"
```

## Common failure modes

- Duplicate replies flip state back and forth → orchestrator MUST check current state before advancing.
- Command sent, reply lost → saga stuck in intermediate state forever. Fix: timeout + retry, or scheduled state audit.
- Orchestrator crashes mid-flow → restart, query DB for RUNNING sagas, resume from state.

## Interview talking points

- Choreography vs orchestration tradeoff.
- Compensation is NOT rollback — it's a business-logic inverse.
- Saga is a state machine — should be persisted, survives crashes.
- Related pattern: Process Manager (heavier saga with correlation).
- Alternatives: 2PC (avoid — slow, complex), transactional messaging (limited scope).
