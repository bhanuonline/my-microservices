# Payment gateway — Phase 5 design

**Status:** Draft — awaiting approval before implementation.
**Author:** proposed design, open for review.
**Scope:** Add Checkout.com as the first provider that uses an explicit
two-phase (authorize → capture) payment model. Introduce the shared
infrastructure (interface + commands + saga states) so any future
split-capture provider can plug in the same way.

---

## 1. Problem statement

The four providers shipped in Phases 1-4 all use the *combined* model:
one `PaymentCommand` triggers both the authorization and the capture
inside the provider's `initiate()` call. The money moves immediately.

That is correct for immediate-charge e-commerce (what the saga was
built for). It's wrong for business flows that need a time gap between
"customer committed" and "we take the money":

- Hotels, car rentals, Airbnb — authorize at booking, capture at check-in
- Marketplaces with escrow — hold funds while seller fulfills
- Ship-then-charge — authorize at order time, capture when package ships
- High-value / fraud-sensitive flows — authorize, run risk check, then
  capture or void
- Partial shipments — one auth, multiple captures for what shipped

Phase 5 adds the vocabulary (interface + commands + saga states) for
these flows, with Checkout.com as the first provider to implement them.

### Non-goals

- Multi-capture per auth (one auth → one capture only, this phase).
- Auto-capture on a timer (operator-triggered via admin API).
- Saga for refunds (refund stays synchronous, same as today).
- Breaking any existing Mock / Stripe / Razorpay / PayPal flow.

---

## 2. Why Checkout.com

Three reasons it's the right pick for demonstrating split-capture:

1. **API shape is explicitly two-phase.** `POST /payments` with
   `capture: false` returns a `Pending` or `Authorized` payment. A
   subsequent `POST /payments/{id}/captures` moves the money. The two
   steps map cleanly onto Authorize + Capture commands with no
   provider-side cleverness required.
2. **First-class void.** `POST /payments/{id}/voids` lets you release
   the authorization without charging. Many providers conflate void with
   a zero-amount refund (which incurs fees). Checkout.com's explicit
   void is what we want to demonstrate.
3. **Clean REST + HMAC webhook signature.** No bespoke SDK required.
   Learning value is high.

---

## 2b. Flow charts

### End-to-end happy path (split-capture)

```
┌────────┐           ┌─────────────┐    ┌──────────────────┐   ┌─────────┐
│ Client │           │ api-gateway │    │  order-service   │   │  Kafka  │
└───┬────┘           └──────┬──────┘    └────────┬─────────┘   └────┬────┘
    │                       │                    │                   │
    │ POST /api/v1/checkout │                    │                   │
    │ provider=checkoutcom  │                    │                   │
    │──────────────────────►│                    │                   │
    │                       │ route /checkout    │                   │
    │                       │───────────────────►│                   │
    │                       │                    │ create Order      │
    │                       │                    │ create Saga       │
    │                       │                    │─────┐             │
    │                       │                    │     │ provider is │
    │                       │                    │     │ split-capable
    │                       │                    │◄────┘             │
    │                       │                    │                   │
    │                       │                    │ AuthorizePaymentCommand
    │                       │                    │──────────────────►│
    │                       │                    │                   │
    │ 201 orderId+sagaId+redirectUrl             │                   │
    │◄──────────────────────│◄───────────────────│                   │
    │                       │                    │                   │
    │  (meanwhile, async…)                                           │
    │                                                                │
    │                              ┌─────────────────┐               │
    │                              │ payment-service │               │
    │                              └────────┬────────┘               │
    │                                       │ consumes               │
    │                                       │◄───────────────────────│
    │                                       │                        │
    │                                       │ PaymentService         │
    │                                       │  .handleAuthorize()    │
    │                                       │                        │
    │                                       │─┐  WebClient           │
    │                                       │ │ POST /payments       │
    │                                       │ │ capture=false        │
    │                                       │ ▼                      │
    │                                       │ ┌─────────────────┐    │
    │                                       │ │ Checkout.com    │    │
    │                                       │ │ api.sandbox     │    │
    │                                       │ └────────┬────────┘    │
    │                                       │          │ Authorized  │
    │                                       │◄─────────┘             │
    │                                       │                        │
    │                                       │ Payment=AUTHORIZED     │
    │                                       │ PaymentAuthorizedReply │
    │                                       │───────────────────────►│
    │                                                                │
    │   OrderSagaOrchestrator.onPaymentAuthorizedReply(reply)        │
    │                              │                                 │
    │                              │ saga state → AUTHORIZED         │
    │                              │ *** WAIT ***                    │
    │                              │  (no next command sent)         │
    │                                                                │
    │   ────────── TIME GAP (hours / days) ──────────                │
    │                                                                │
    │ POST /admin/orders/{id}/capture                                │
    │──────────────────────►│                    │                   │
    │                       │───────────────────►│                   │
    │                       │                    │ check saga state  │
    │                       │                    │ (must be          │
    │                       │                    │  AUTHORIZED)      │
    │                       │                    │                   │
    │                       │                    │ CapturePaymentCommand
    │                       │                    │──────────────────►│
    │                       │                    │                   │
    │ 202 action=capture_requested                                   │
    │◄──────────────────────│◄───────────────────│                   │
    │                                                                │
    │                              payment-service consumes          │
    │                                       │◄───────────────────────│
    │                                       │                        │
    │                                       │─┐  WebClient           │
    │                                       │ │ POST /payments/{id}/captures
    │                                       │ ▼                      │
    │                                       │ Checkout.com → Captured│
    │                                       │                        │
    │                                       │ Payment=CAPTURED       │
    │                                       │ PaymentCapturedReply   │
    │                                       │───────────────────────►│
    │                                                                │
    │   OrderSagaOrchestrator.onPaymentCapturedReply(reply)          │
    │                              │                                 │
    │                              │ saga state → PAID               │
    │                              │ NotifyUserCommand               │
    │                              │────────────────────────────────►│
    │                                                                │
    │   (standard notification flow — same as combined providers)    │
    │   → saga = NOTIFIED, order = PAID                              │
    │                                                                │
```

### Alternative — void path (operator cancels before capture)

```
                    After AUTHORIZED reply has arrived…

    │                                                                │
    │ POST /admin/orders/{id}/void                                   │
    │   {reason: "customer cancelled"}                               │
    │──────────────────────►│                    │                   │
    │                       │───────────────────►│                   │
    │                       │                    │ check saga state  │
    │                       │                    │  = AUTHORIZED     │
    │                       │                    │                   │
    │                       │                    │ VoidPaymentCommand│
    │                       │                    │──────────────────►│
    │                       │                    │                   │
    │ 202 action=void_requested                                      │
    │◄──────────────────────│◄───────────────────│                   │
    │                                                                │
    │                              payment-service consumes          │
    │                                       │◄───────────────────────│
    │                                       │                        │
    │                                       │─┐  WebClient           │
    │                                       │ │ POST /payments/{id}/voids
    │                                       │ ▼                      │
    │                                       │ Checkout.com → Voided  │
    │                                       │                        │
    │                                       │ Payment=VOIDED         │
    │                                       │ PaymentVoidedReply     │
    │                                       │───────────────────────►│
    │                                                                │
    │   OrderSagaOrchestrator.onPaymentVoidedReply(reply)            │
    │                              │                                 │
    │                              │ saga state → FAILED             │
    │                              │ Order → CANCELLED               │
    │                              │ metrics.terminal("compensated", │
    │                              │   "voided_by_operator")         │
    │                              │ (NO refund fired — nothing to   │
    │                              │  undo; auth is released, free.) │
```

### Decision tree — which path does the orchestrator take?

```
                       OrderSagaOrchestrator.start()
                                  │
                                  ▼
                   ┌──────────────────────────────┐
                   │  providerName in              │
                   │  splitCaptureProviders set?  │
                   └──────────────┬───────────────┘
                                  │
                     ┌────────────┴────────────┐
                     │                         │
                    YES                        NO
                     │                         │
                     ▼                         ▼
         AuthorizePaymentCommand         PaymentCommand
         (new Phase 5 path)              (existing combined path)
                     │                         │
                     ▼                         ▼
         saga: STARTED → AUTHORIZED   saga: STARTED → PAID
             (waits for operator)         (immediate)
                     │                         │
         admin capture / void                  │
                     │                         │
                     ▼                         ▼
         saga: AUTHORIZED → PAID               ▼
                              └─────┬──────────┘
                                    ▼
                           NotifyUserCommand
                                    ▼
                              saga: NOTIFIED
```

### Payment entity state machine (updated)

```
                      ┌────────────┐
                      │ INITIATED  │
                      └─────┬──────┘
                            │
            ┌───────────────┼────────────────┬──────────────┐
            │               │                │              │
     provider.authorize()   provider.initiate()  (sync fail)
     (split path)           (combined path)
            │               │                │              │
            ▼               ▼                ▼              ▼
     ┌────────────┐   ┌──────────┐    ┌──────────┐   ┌─────────┐
     │ AUTHORIZED │   │ CAPTURED │    │ DECLINED │   │ FAILED  │
     └─────┬──────┘   └────┬─────┘    └──────────┘   └─────────┘
           │               │
    ┌──────┴──────┐        │ provider.refund()
    │             │        │
    ▼             ▼        ▼
 capture()     void()    REFUNDED
    │             │
    ▼             ▼
 CAPTURED      ┌────────┐
    │          │ VOIDED │   NEW terminal — no money moved, free of fees
    │          └────────┘
    │
    │ provider.refund()
    ▼
 REFUNDED

 Terminal states: CAPTURED (success before refund),
                  REFUNDED, DECLINED, FAILED, VOIDED
```

### OrderSaga state machine (updated)

```
                                 STARTED
                                    │
         ┌──────────────────────────┼─────────────────────────┐
         │                          │                         │
     Authorize cmd              Payment cmd                 (sync fail)
     (split-capable)            (combined)
         │                          │                         │
         ▼                          ▼                         ▼
   ┌────────────┐             ┌─────────┐              ┌──────────┐
   │ AUTHORIZED │             │  PAID   │              │  FAILED  │
   └─────┬──────┘             └────┬────┘              └──────────┘
         │                         │
   ┌─────┴──────┐                  │ NotifyUserCommand
   │            │                  │
   ▼            ▼                  │
 capture       void                │
   │            │                  │
   ▼            ▼                  │
  PAID       VOIDED (terminal)     │
   │                               │
   └────────────┬──────────────────┘
                │
                ▼
          NotifyUserCommand
                │
     ┌──────────┴────────────┐
     │                       │
   reply ok               reply fail
     │                       │
     ▼                       ▼
  NOTIFIED              COMPENSATING
  (terminal)                 │
                      RefundCommand
                             │
                             ▼
                          FAILED
                        (compensated)

 Terminal states: NOTIFIED, FAILED, VOIDED
```

### Error flow — authorize declines

```
   Client                                payment-service
     │                                          │
     │ POST /checkout provider=checkoutcom      │
     │─────────────────────►[gateway]──────────►│
     │                                          │
     │ 201 orderId+sagaId                       │
     │◄─────────────────────────────────────────│
     │                                          │
     │   AuthorizePaymentCommand → Kafka → payment-service
     │                                          │
     │                                          │─┐ provider.authorize()
     │                                          │ ▼
     │                                          │ Checkout.com returns
     │                                          │ status=Declined
     │                                          │
     │                                          │ Payment=DECLINED
     │                                          │ PaymentAuthorizedReply(
     │                                          │   success=false,
     │                                          │   failureReason="card_declined")
     │                                          │
     │   orchestrator.onPaymentAuthorizedReply(reply)
     │                                          │
     │                                          │ saga state → FAILED
     │                                          │ Order → CANCELLED
     │                                          │ metrics.terminal(
     │                                          │   outcome="failed",
     │                                          │   reason="auth_failed:card_declined")
     │
     │   (saga tail — same as any auth-fail today, nothing new to debug)
```

### Error flow — capture fails after authorize

```
         Operator                             payment-service
            │                                        │
            │ POST /admin/orders/{id}/capture        │
            │─────────────►[gateway]────────────────►│
            │                                        │
            │ 202 action=capture_requested           │
            │◄───────────────────────────────────────│
            │                                        │
            │   CapturePaymentCommand → Kafka → payment-service
            │                                        │
            │                                        │─┐ provider.capture()
            │                                        │ ▼
            │                                        │ Checkout.com returns
            │                                        │ error (e.g. 422
            │                                        │ validation_failed)
            │                                        │
            │                                        │ Payment stays AUTHORIZED
            │                                        │  (no state change —
            │                                        │   bank didn't move money)
            │                                        │
            │                                        │ PaymentCapturedReply(
            │                                        │   success=false,
            │                                        │   failureReason="validation_failed")
            │                                        │
            │   orchestrator.onPaymentCapturedReply(reply)
            │                                        │
            │                                        │ saga STAYS at AUTHORIZED
            │                                        │ Prometheus:
            │                                        │   orders_saga_capture_failed_total
            │                                        │   increments. Alert fires if
            │                                        │   this persists.
            │
            │   Operator can:
            │     a) Investigate + retry capture
            │     b) Decide to void
            │     c) Contact support
            │
            │   Importantly: saga is NOT in a broken state.
            │   AUTHORIZED is a legitimate holding state;
            │   nothing in the saga is wedged.
```

---

## 3. Architecture — current vs proposed

### Current (combined, 4 providers)

```
POST /api/v1/checkout {provider: "stripe"}
     ▼
order-service CheckoutController
  ├── OrderService.create(..., provider)
  │     └── OrderSagaOrchestrator.start(orderId, amount, provider)
  │           └── PaymentCommand → Kafka: payment.commands
  │
  └── (if provider != "mock") PaymentClient.initiate() (Feign)
                                  └── sync returns redirectUrl

payment-service PaymentCommandProcessor
   └── PaymentService.handlePayment()
         └── provider.initiate(payment)   ← one shot
               ├── returns CAPTURED → publish PaymentReply(success)
               └── returns INITIATED (await webhook)

Later: webhook → applyWebhook() → publishes PaymentReply

Saga states:  STARTED → PAID → NOTIFIED (success)
                      → FAILED  (payment denied)
                      → COMPENSATING → FAILED  (notify fail → refund)
```

### Proposed (adds split-capture branch for Checkout.com)

```
POST /api/v1/checkout {provider: "checkoutcom"}
     ▼
order-service CheckoutController
  └── OrderService.create(..., provider)
        └── OrderSagaOrchestrator.start(orderId, amount, provider)
              │
              ├── IF provider name is in splitCaptureProviders set:
              │     AuthorizePaymentCommand → Kafka
              │
              └── ELSE (existing behavior unchanged):
                    PaymentCommand → Kafka

payment-service PaymentCommandProcessor
   ├── (existing) handlePayment(PaymentCommand)
   ├── (NEW)      handleAuthorize(AuthorizePaymentCommand)
   │                └── PaymentService.handleAuthorize()
   │                      └── provider.authorize(payment)
   │                            └── returns AUTHORIZED → publish PaymentAuthorizedReply
   ├── (NEW)      handleCapture(CapturePaymentCommand)
   │                └── PaymentService.handleCapture()
   │                      └── provider.capture(payment)
   │                            └── returns CAPTURED → publish PaymentCapturedReply
   └── (NEW)      handleVoid(VoidPaymentCommand)
                    └── PaymentService.handleVoid()
                          └── provider.void(payment)
                                └── returns VOIDED → publish PaymentVoidedReply

OrderSagaOrchestrator
   ├── onPaymentAuthorizedReply(reply)
   │     ├── saga state → AUTHORIZED
   │     └── WAIT — orchestrator does NOT send next command.
   │             Operator or scheduled job triggers capture.
   │
   ├── onPaymentCapturedReply(reply)
   │     ├── saga state → PAID (same state as combined-flow PAID)
   │     └── NotifyUserCommand → Kafka  (merges back into existing flow)
   │
   └── onPaymentVoidedReply(reply)
         └── saga state → FAILED (reason: voided_by_operator)
               and Order → CANCELLED

New admin endpoints on order-service:
   POST /admin/orders/{orderId}/capture
   POST /admin/orders/{orderId}/void
     └── looks up saga, publishes Capture/Void command

Saga states (expanded):
   STARTED → PAID → NOTIFIED                            (combined path)
   STARTED → AUTHORIZED → PAID → NOTIFIED               (split path, happy)
   STARTED → FAILED                                     (auth denied)
   STARTED → AUTHORIZED → FAILED                        (voided)
   STARTED → AUTHORIZED → PAID → COMPENSATING → FAILED  (notify fail → refund)
```

The crucial design property: **after `AUTHORIZED`, the two paths
converge.** `PaymentCapturedReply` from the split-capture path triggers
exactly the same `NotifyUserCommand` the combined path triggers from
`PaymentReply(success=true)`. The saga tail is shared.

---

## 4. Design patterns in play

```
┌─ Existing (unchanged) ───────────────────────────────────────────────┐
│  Strategy          PaymentProvider + 4 impls                         │
│  Registry          PaymentProviderRegistry (name → impl)             │
│  State Machine     Payment entity (INITIATED → … → terminal)         │
│  Facade            PaymentService wraps provider calls               │
│  Saga              OrderSagaOrchestrator                             │
│  Idempotency       DB uniqueness + provider idempotency keys         │
└──────────────────────────────────────────────────────────────────────┘

┌─ Added in Phase 5 ───────────────────────────────────────────────────┐
│  Capability pattern   New sub-interface SplitCapturePaymentProvider  │
│                       extends PaymentProvider. Providers opt in by   │
│                       implementing it. Code checks via instanceof.   │
│                                                                      │
│                       Why not just add 3 methods to PaymentProvider  │
│                       with default UOE?                              │
│                         - Opt-in via interface is clearer intent.    │
│                         - `provider instanceof SplitCapture...`      │
│                           is explicit; no runtime surprises.         │
│                         - Lets static analysis (and future test      │
│                           coverage reports) tell you which providers │
│                           have split-capture.                        │
│                                                                      │
│  Command pattern      3 new command records in common-lib:           │
│                         AuthorizePaymentCommand                      │
│                         CapturePaymentCommand                        │
│                         VoidPaymentCommand                           │
│                       Each is an immutable value object representing │
│                       a verb the saga asks the payment service to    │
│                       perform. Classic Command.                      │
│                                                                      │
│                       Why not reuse PaymentCommand with an "action"  │
│                       field?                                         │
│                         - Loses type safety (consumer must branch    │
│                           on a string).                              │
│                         - Each command carries different data        │
│                           (CapturePaymentCommand doesn't need        │
│                           amount; VoidPaymentCommand carries reason).│
│                         - Separate Kafka bindings = independent      │
│                           retry/DLQ behavior per operation.          │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 5. Interface + command shapes

### SplitCapturePaymentProvider (new)

```java
package com.example.paymentservice.provider;

/**
 * Marker + method set for providers that support an explicit two-phase
 * (authorize → capture) payment lifecycle plus an unused-authorization
 * void. Combined-flow providers (Mock, Stripe Checkout, Razorpay,
 * PayPal CAPTURE intent) do NOT implement this; their initiate() is a
 * one-shot authorize+capture.
 *
 * Lifecycle:
 *   1. authorize(payment)  → money reserved; Payment=AUTHORIZED
 *   2a. capture(payment)   → money moves; Payment=CAPTURED
 *   2b. OR void(payment)   → release reservation; Payment=VOIDED (free)
 *   3. refund(payment)     → reverse a CAPTURE (inherited from PaymentProvider)
 */
public interface SplitCapturePaymentProvider extends PaymentProvider {

    /** Reserve funds without moving them. Returns AUTHORIZED on success. */
    ProviderSession authorize(Payment payment);

    /** Convert a prior authorization into an actual charge. */
    ProviderSession capture(Payment payment);

    /** Release a prior authorization without charging. */
    ProviderSession voidPayment(Payment payment);

    // initiate() + refund() + handleWebhook() inherited from PaymentProvider.
    // initiate() typically calls authorize() internally so this provider
    // STILL works via the combined PaymentCommand path for any caller that
    // doesn't want the two-phase flow.
}
```

### Commands + replies (common-lib)

```java
// AuthorizePaymentCommand — sent on new saga start when provider is split-capable.
public record AuthorizePaymentCommand(
        UUID sagaId,
        String orderId,
        BigDecimal amount,
        String currency,
        String provider
) {}

// CapturePaymentCommand — sent when operator (or future scheduler) decides to charge.
public record CapturePaymentCommand(
        UUID sagaId,
        String paymentId,   // the payment-service's internal Payment.id
        String reason
) {}

// VoidPaymentCommand — sent when operator cancels before capture.
public record VoidPaymentCommand(
        UUID sagaId,
        String paymentId,
        String reason
) {}

// Replies
public record PaymentAuthorizedReply(
        UUID sagaId,
        String orderId,
        String paymentId,
        String authId,
        boolean success,
        String failureReason
) {}

public record PaymentCapturedReply(
        UUID sagaId,
        String orderId,
        String paymentId,
        boolean success,
        String failureReason
) {}

public record PaymentVoidedReply(
        UUID sagaId,
        String orderId,
        String paymentId,
        boolean success,
        String failureReason
) {}
```

### Payment entity — new state

```
Added: VOIDED (terminal, no money moved)

Full state machine:
  INITIATED ──► AUTHORIZED ──► CAPTURED ──► REFUNDED
      │             │
      │             └──► VOIDED  (NEW — operator released the auth)
      │
      ├──► DECLINED  (synchronous rejection)
      └──► FAILED    (system error)
```

### OrderSaga entity — new states

```
Added: AUTHORIZED, VOIDED (terminal)

STARTED
  ├── combined path:  STARTED → PAID → NOTIFIED  (success)
  │                           ↳ FAILED           (payment denied)
  │                           ↳ COMPENSATING → FAILED (notify fail → refund)
  │
  └── split path:     STARTED → AUTHORIZED → PAID → NOTIFIED
                              ↳ FAILED (auth denied)
                              ↳ VOIDED (operator voided)   NEW
                      AUTHORIZED → PAID → COMPENSATING → FAILED
```

---

## 6. HTTP client choice — WebClient

```
┌─ Why WebClient (not HttpClient or RestTemplate) ─────────────────────┐
│                                                                      │
│  Checkout.com integration makes 4-5 outbound calls per flow:         │
│    POST /payments               (authorize)                          │
│    POST /payments/{id}/captures (capture)                            │
│    POST /payments/{id}/voids    (void)                               │
│    POST /payments/{id}/refunds  (refund)                             │
│    GET  /payments/{id}          (status check, optional)             │
│                                                                      │
│  All need:                                                           │
│    ✓ Automatic Zipkin spans (observability stays coherent)           │
│    ✓ Resilience4j CircuitBreaker on a flaky external dep             │
│    ✓ Correlation ID header propagation                               │
│    ✓ Idempotency key per call (Cko-Idempotency-Key)                  │
│    ✓ Jackson in/out without boilerplate                              │
│                                                                      │
│  WebClient gives you all five with Spring auto-config.               │
│  HttpClient makes you wire each one manually.                        │
│  RestTemplate is sync-only and in maintenance mode.                  │
│                                                                      │
│  Non-blocking is a bonus, not a requirement — WebClient.block()      │
│  works fine inside the servlet-based PaymentService.                 │
└──────────────────────────────────────────────────────────────────────┘
```

### WebClient wiring (sketch)

```java
@Configuration
class CheckoutDotComConfig {

    @Bean
    @ConditionalOnProperty(value = "payment.checkoutcom.enabled", havingValue = "true")
    WebClient checkoutcomWebClient(
            @Value("${payment.checkoutcom.base-url}") String baseUrl,
            @Value("${payment.checkoutcom.secret-key}") String secretKey) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .filter(ExchangeFilterFunction.ofRequestProcessor(req -> {
                    // Auto-add correlation id from MDC if present
                    String cid = MDC.get("correlationId");
                    if (cid != null) {
                        return Mono.just(ClientRequest.from(req)
                                .header("X-Correlation-Id", cid).build());
                    }
                    return Mono.just(req);
                }))
                .build();
    }
}
```

---

## 7. Checkout.com API — concrete request/response shapes

### Authorize

```http
POST https://api.sandbox.checkout.com/payments
Authorization: Bearer sk_sbox_...
Cko-Idempotency-Key: saga-<sagaId>
Content-Type: application/json

{
  "source": {
    "type": "token",
    "token": "tok_mbabizu24mvu3mela5njyhpit4"
  },
  "amount": 9999,
  "currency": "USD",
  "reference": "<orderId>",
  "capture": false,
  "metadata": {
    "sagaId": "<sagaId>",
    "paymentId": "<Payment.id>"
  }
}
```

Response (success):
```json
{
  "id": "pay_mbabizu24mvu3mela5njyhpit4",
  "status": "Authorized",
  "approved": true,
  "_links": {
    "capture": { "href": "https://api.sandbox.checkout.com/payments/pay_.../captures" },
    "void":    { "href": "https://api.sandbox.checkout.com/payments/pay_.../voids" }
  }
}
```

### Capture

```http
POST https://api.sandbox.checkout.com/payments/pay_.../captures
Cko-Idempotency-Key: capture-<sagaId>

{
  "amount": 9999,
  "reference": "capture-<orderId>"
}
```

### Void

```http
POST https://api.sandbox.checkout.com/payments/pay_.../voids
Cko-Idempotency-Key: void-<sagaId>

{
  "reference": "void-<orderId>"
}
```

### Webhook

Checkout.com POSTs to our configured URL with:
- Header `Cko-Signature: <hex>` = HMAC-SHA256 of body with signing secret
- Body: `{ "id": "evt_...", "type": "payment_captured", "data": {...} }`

Event types we handle:
- `payment_approved`   → AUTHORIZED
- `payment_captured`   → CAPTURED
- `payment_declined`   → DECLINED
- `payment_voided`     → VOIDED (new terminal)
- `payment_refunded`   → REFUNDED

---

## 8. Backwards compatibility

Three guarantees:

1. **Existing providers unchanged.** Mock, Stripe, Razorpay, PayPal
   don't implement `SplitCapturePaymentProvider`. They continue to work
   via `PaymentCommand` → `provider.initiate()`.
2. **Existing saga paths unchanged.** `STARTED → PAID → NOTIFIED` still
   fires for any combined-flow provider. The new `AUTHORIZED` state is
   only entered when the orchestrator sent an `AuthorizePaymentCommand`.
3. **Existing Testcontainers suite stays green.** The 4 saga tests use
   the mock-ish path; none of their assertions touch `AUTHORIZED`.

The orchestrator decides which path to take at `start()` time:

```java
boolean splitCapable = splitCaptureProviderNames.contains(providerName);
if (splitCapable) {
    streamBridge.send(AUTHORIZE_COMMANDS,
        new AuthorizePaymentCommand(sagaId, orderId, amount, currency, providerName));
} else {
    streamBridge.send(PAYMENT_COMMANDS,
        new PaymentCommand(sagaId, orderId, amount, providerName));
}
```

`splitCaptureProviderNames` is injected from config (initially: just
`"checkoutcom"`). Keeps the orchestrator from needing to know what each
provider can do.

---

## 9. Admin endpoints (new, order-service)

```
POST /admin/orders/{orderId}/capture
  Body (optional): { "reason": "shipment confirmed" }
  → Looks up saga for orderId
  → Verifies saga state = AUTHORIZED (409 otherwise)
  → Publishes CapturePaymentCommand(sagaId, paymentId, reason)
  → Returns 202 Accepted with { sagaId, action: "capture_requested" }

POST /admin/orders/{orderId}/void
  Body (optional): { "reason": "customer cancelled" }
  → Same shape, publishes VoidPaymentCommand
  → Returns 202
```

Security: these go behind the same JWT check as other admin endpoints
(`/admin/**` already requires authentication).

---

## 10. Error paths

```
┌─ Scenario                      │ What happens ────────────────────────┐
│────────────────────────────────┼─────────────────────────────────────┤
│ Authorize fails (card declined)│ provider.authorize() returns         │
│                                │ DECLINED → publish Reply(fail) →     │
│                                │ saga FAILED → order CANCELLED        │
│                                │                                      │
│ Authorize fails (system error) │ provider throws → caught → publish  │
│                                │ Reply(fail reason=system_error)      │
│                                │                                      │
│ Capture fails after authorize  │ saga stays AUTHORIZED. Admin can     │
│                                │ retry capture OR void. Prometheus    │
│                                │ metric orders_saga_capture_failed    │
│                                │ increments for visibility.           │
│                                │                                      │
│ Void a non-authorized payment  │ PaymentService short-circuits with   │
│                                │ "nothing to void" — admin endpoint  │
│                                │ returns 409 earlier via saga state  │
│                                │ check.                               │
│                                │                                      │
│ Timeout — authorized but never │ New alert: SagaStuckInAuthorized —  │
│ captured for > X hours         │ fires on `(now - saga.updatedAt) >  │
│                                │ 24h AND state=AUTHORIZED`. Operator  │
│                                │ decides capture or void.             │
│                                │                                      │
│ Duplicate capture command      │ Idempotent via Checkout.com's        │
│                                │ Cko-Idempotency-Key = capture-<id>. │
│                                │ Also guarded by Payment.markCaptured │
│                                │ throwing on non-AUTHORIZED state.   │
└────────────────────────────────┴─────────────────────────────────────┘
```

---

## 11. Observability additions

```
Metrics (Micrometer):
  orders_saga_authorized_total      counter   total successful auths
  orders_saga_captured_total        counter   total successful captures
  orders_saga_voided_total          counter   total voids
  orders_saga_time_to_capture       timer     AUTHORIZED → CAPTURED duration
                                              (reveals how long operators
                                               take to confirm shipment)

Alerts (Grafana provisioned):
  SagaStuckInAuthorized  warning  state=AUTHORIZED older than 24h

Dashboard panels (order-saga.json):
  "Authorized vs captured rate" — spot a growing gap
  "Average time to capture" — SLA for operators
  "Voided outcomes" — stat with monthly tally
```

---

## 12. Checkout.com sandbox setup

```
1. Sign up: https://www.checkout.com/get-test-account
   OR: https://dashboard.sandbox.checkout.com/

2. Get keys from Dashboard → Developers → Keys:
   - Processing channel ID
   - Secret key (sk_sbox_...)
   - Public key (pk_sbox_...)
   - Webhook signing secret

3. Create a webhook:
   Dashboard → Developers → Webhooks → New webhook
   URL:    https://<ngrok-or-tunnel>/webhooks/checkoutcom
   Events: payment_approved, payment_captured, payment_declined,
           payment_voided, payment_refunded

4. Base URL (sandbox): https://api.sandbox.checkout.com
   Base URL (prod):    https://api.checkout.com
   Switch via config, no code change.

5. Test card tokens (Checkout.com provides pre-made):
   tok_card_visa_1          → always approves
   tok_card_decline         → always declines
   tok_card_3ds             → triggers 3DS challenge
```

---

## 13. Changes required — file list

```
New files:
  common-lib/.../saga/AuthorizePaymentCommand.java
  common-lib/.../saga/CapturePaymentCommand.java
  common-lib/.../saga/VoidPaymentCommand.java
  common-lib/.../saga/PaymentAuthorizedReply.java
  common-lib/.../saga/PaymentCapturedReply.java
  common-lib/.../saga/PaymentVoidedReply.java
  paymentservice/.../provider/SplitCapturePaymentProvider.java   (interface)
  paymentservice/.../provider/CheckoutDotComPaymentProvider.java (~400 LOC)
  paymentservice/.../config/CheckoutDotComConfig.java            (WebClient bean)
  order-service/.../controller/CheckoutAdminController.java      (capture/void)
  docs/microservices/payment-gateway-phase5-design.md            (this file)

Modified files:
  pom.xml                                               (no Checkout.com SDK)
  paymentservice/pom.xml                                (no new dep — webflux already via common-lib)
  paymentservice/.../model/Payment.java                 (+VOIDED state + markVoided())
  paymentservice/.../service/PaymentService.java        (+handleAuthorize/Capture/Void)
  paymentservice/.../saga/PaymentCommandProcessor.java  (+3 new handlers)
  paymentservice/src/main/resources/application.yml     (+payment.checkoutcom.* + binding names)
  order-service/.../saga/OrderSaga.java                 (+AUTHORIZED, VOIDED states + hooks)
  order-service/.../saga/OrderSagaOrchestrator.java     (+branch logic + 3 new reply handlers)
  order-service/.../saga/SagaReplyHandlers.java         (+3 new reply bindings)
  order-service/.../saga/SagaMetrics.java               (+authorized/captured/voided counters + time-to-capture timer)
  order-service/src/main/resources/application.yml      (+binding names)
  common-lib/.../common/saga/*                          (no changes to existing records)
  .env.example + .env                                   (+payment.checkoutcom.* placeholders)
  observability/grafana/dashboards/order-saga.json      (+3 panels)
  observability/grafana/provisioning/alerting/saga-alerts.yml  (+SagaStuckInAuthorized rule)
  postman/microservices.postman_collection.json         (+F8 flow)
  docs/microservices/payment-gateway.md                 (+Phase 5 section)
  docs/microservices/troubleshooting.md                 (+SagaStuckInAuthorized runbook)

Rough LOC total: 1800-2200 across ~25 files.
```

---

## 14. Rollout plan

Three sequential commits, each independently buildable + testable:

```
Commit 1 — Contracts + domain model (~400 LOC)
  Adds commands, replies, interface, Payment VOIDED state, OrderSaga
  states, SagaMetrics counters. No wiring, no provider impl, no routing.
  All existing tests pass.
  Nothing visible to a user yet — pure contract plumbing.

Commit 2 — CheckoutDotComPaymentProvider + wiring (~1000 LOC)
  Provider impl via WebClient. PaymentService + PaymentCommandProcessor
  handle Authorize/Capture/Void. Orchestrator branch + reply handlers.
  Config + .env placeholders. Compile + existing saga regression tests.
  Mock-mode is possible: can set payment.checkoutcom.enabled=true with
  dummy keys and run the saga tests against a WireMock Checkout.com.
  (Deferred to Commit 3's test suite.)

Commit 3 — Admin endpoints + Postman F8 + docs + alerts (~400 LOC)
  CheckoutAdminController (capture/void endpoints).
  New Testcontainers test: SplitCaptureSagaIntegrationTest with WireMock.
  F8 Postman flow. troubleshooting.md updates. Grafana dashboard panels.
  Alert rule.
```

Each commit is a checkpoint for review.

---

## 15. Open questions — your call

```
A. New Postman flow F8 scope:
   (A1) Just happy path: authorize → admin capture → NOTIFIED
   (A2) A1 + void path: authorize → admin void → VOIDED
   (A3) A2 + decline path: authorize fails → FAILED

B. WireMock for Testcontainers test:
   WireMock would simulate Checkout.com's responses, so the test runs
   without real sandbox credentials. Adds ~1 dep (wiremock-standalone)
   and ~200 lines of stub config. Worth it for CI, YES/NO?

C. Alert on `AUTHORIZED` → `CAPTURED` latency SLO:
   Operator SLA is a business decision. Default proposal: 24h.
   Agree, or different number?

D. Admin endpoint auth:
   /admin/orders/*/capture needs auth. Easiest: same JWT as other admin
   routes. Alternative: separate role "payment-operator". Default to the
   first unless you'd like finer-grained roles.

E. Scheduler alternative: should the orchestrator also support an
   OPTIONAL auto-capture after N minutes? Useful for e-comm flows that
   want a "cooling-off period" (user can cancel for 5 min before we
   charge). Default proposal: NO, admin-only. Easy to add later.
```

---

## 16. Not in this design — leave for later

- Multi-capture per auth (one auth → N partial captures). Checkout.com
  supports it, but the saga would need a per-shipment command, which is
  out of scope.
- Automatic capture retry on timeout. For now, operator reissues the
  admin capture request manually.
- Refund as a saga. Refund stays synchronous and sync-returns a
  success/fail, same as today.
- Timeline UI showing saga state transitions over time. Nice-to-have,
  not blocking.

---

## 17. Approval needed

Answer the five open questions in section 15, confirm this design, and
I'll start building Commit 1. Nothing in `src/main` changes until you
say go.
