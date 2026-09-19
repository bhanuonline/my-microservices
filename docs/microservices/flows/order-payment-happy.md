# Flow — Order → Payment (happy path)

## The request

```bash
# Even quantity → payment succeeds
curl -X POST http://localhost:8083/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":2}'
```

## Expected outcome

- 201 Created with `{orderId, status: "CREATED"}`
- Order row in H2 (order-service DB) — initially CREATED
- Saga row in `order_sagas` — STARTED
- Kafka topic `payment.commands` — one PaymentCommand
- payment-service consumes → publishes PaymentReply(success=true) to `payment.replies`
- Orchestrator consumes reply → saga → PAID
- Orchestrator sends NotifyUserCommand to `notification.commands`
- notification consumes → publishes NotifyUserReply(success=true) to `notification.replies`
- Orchestrator consumes reply → saga → NOTIFIED, order → PAID
- `GET /api/v1/orders/{id}` → status PAID

## Trace to fill in

<!-- Fill during actual test -->

### 1. Order created + saga started
```
INFO  [order-service,<traceId>,...] OrderSagaOrchestrator : Saga <id> STARTED for orderId=<orderId>
```

### 2. payment-service processes command
```
INFO  [payment-service,...] PaymentCommandHandler : Processing PaymentCommand sagaId=<id> orderId=<orderId> amount=19.98
```

### 3. Orchestrator receives reply → saga PAID → notify sent
```
INFO  [order-service,...] OrderSagaOrchestrator : Saga <id> → PAID, sending notify command
```

### 4. notification processes command
```
INFO  [notification,...] NotifyUserCommandHandler : Sending notification sagaId=<id>
```

### 5. Orchestrator receives notify reply → saga COMPLETED
```
INFO  [order-service,...] OrderSagaOrchestrator : Saga <id> COMPLETED
```

## Zipkin trace

Should see 5+ spans across order → payment → order → notification → order.

## What could go wrong

- Product-service down → the pre-check (`checkAvailability`) fails → order never created (correct).
- Payment amount >= $50 → payment-service replies failure → saga → FAILED, no compensation needed.
- Notify includes "fail-me" → notification fails → compensation (refund) → saga → FAILED.
