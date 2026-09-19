# Flow — Saga compensation (refund)

## Goal

Force the notification step to fail after payment succeeded → orchestrator sends `RefundCommand` → payment-service logs the refund → order → CANCELLED.

## Trigger

Currently `NotifyUserCommandHandler` fails when the message body contains `"fail-me"`.

Options:

**Option 1 — add a test hook (recommended):**
Modify `OrderSagaOrchestrator.onPaymentReply()` temporarily to include `"fail-me"` in the message for testing:
```java
streamBridge.send(NOTIFY_COMMANDS,
    new NotifyUserCommand(saga.getId(), saga.getOrderId(),
        "Your order " + saga.getOrderId() + " has been paid. fail-me"));
```

Send a request:
```bash
curl -X POST http://localhost:8083/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'
```

**Option 2 — inject via header:**  TODO: extend NotifyUserCommand to accept a "force failure" flag.

## Expected trace

1. Order created → saga STARTED
2. Payment succeeds → saga PAID
3. Notify command sent with "fail-me"
4. notification-service replies failure ("smtp_unreachable")
5. Orchestrator receives failure reply → saga → COMPENSATING → FAILED
6. RefundCommand sent to `payment.commands.refund` topic
7. payment-service logs: `WARN Executing REFUND sagaId=... reason=smtp_unreachable`
8. Order marked CANCELLED

Verify:
```bash
curl http://localhost:8083/api/v1/orders/<orderId>   # → status CANCELLED
```

## What could go wrong

- RefundCommand sent but no consumer → refund lost (nobody consumes it). Verify `spring.cloud.function.definition` in payment-service includes `refundCommand`.
- Reply arrives after saga already terminal → orchestrator's state check prevents re-processing.
- Real prod: you'd want to await a `RefundReply` before marking terminal — currently fire-and-forget.

## Interview talking points

- Compensation is NOT rollback. It's a business action (issue refund).
- Real refund is asynchronous — payment gateways don't refund instantly. Saga might have a REFUNDING state that waits for confirmation.
- What if refund itself fails? → escalate to human review, don't loop forever.
