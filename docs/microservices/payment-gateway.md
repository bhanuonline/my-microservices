# Payment gateway

How the payment subsystem fits together, phase by phase, with the exact
commands to run each provider end-to-end.

## Architecture (end state)

```
   Client (shop-ui, Postman, …)
      │ POST /api/v1/checkout
      │ { productId, quantity, provider: "mock" | "stripe" | "razorpay" | "paypal",
      │   returnUrl, cancelUrl }
      ▼
   api-gateway  :8080
      │ routes /api/v1/checkout/** → order-service
      ▼
   order-service CheckoutController
      │
      ├── creates Order + starts saga
      │       │
      │       │ PaymentCommand(..., provider) → Kafka: payment.commands
      │       ▼
      │   payment-service PaymentCommandProcessor
      │       │
      │       │ delegates to PaymentService
      │       ▼
      │   PaymentProviderRegistry.require(name)
      │       │
      │   ┌───┼───────────┬───────────────┬──────────────┐
      │   ▼   ▼           ▼               ▼              ▼
      │   Mock         Stripe         Razorpay        PayPal
      │  (sync)       (hosted)       (Phase 3)       (Phase 4)
      │                   │
      │                   │ Stripe.Session.create() with
      │                   │   Idempotency-Key = "saga-<sagaId>"
      │                   ▼
      │              checkout.stripe.com → user pays
      │                   │
      │                   │ webhook: POST /webhooks/stripe
      │                   │  signed by Stripe
      │                   ▼
      │              payment-service WebhookController
      │                   │ Webhook.constructEvent(body, sig, secret)
      │                   ▼
      │              PaymentService.applyWebhook → publishes PaymentReply
      │                   │
      │                   ▼  (back to the saga)
      │
      └── for provider != "mock":
          synchronously calls payment-service /api/v1/payments/initiate
          to get the redirect URL back in the HTTP response
```

The Kafka path and the HTTP /initiate path are redundant on purpose: both
converge on the same (sagaId, provider) Payment row, and
`PaymentService.handlePayment` short-circuits the second one when it sees
`providerRef` is already set. Stripe's own idempotency key is a third
safety net.

## Status

| Phase | Provider | Status |
|---|---|---|
| 1 | Mock | Committed (`cc64f00`) and working |
| 2 | Stripe | Code committed; needs real API keys to actually test |
| 3 | Razorpay | Stub only (`UnsupportedOperationException`) |
| 4 | PayPal | Stub only (`UnsupportedOperationException`) |

The stubs are `@ConditionalOnProperty` so they do NOT register as beans unless
you flip their enable flag. That means a service with no Stripe/Razorpay/PayPal
config boots clean on just the Mock.

---

## Phase 2 — Stripe

### What you'll need

1. A **Stripe test account** — https://dashboard.stripe.com/register
2. **Stripe CLI** installed and logged in — https://stripe.com/docs/stripe-cli
   ```bash
   brew install stripe/stripe-cli/stripe
   stripe login
   ```

### Set your keys in `.env`

The `.env` file ships with placeholders:
```bash
PAYMENT_STRIPE_ENABLED=false
STRIPE_SECRET_KEY=sk_test_REPLACE_ME
STRIPE_WEBHOOK_SECRET=whsec_REPLACE_ME
```

Fill them in:
1. **Secret key** — https://dashboard.stripe.com/test/apikeys → copy
   "Secret key" (starts with `sk_test_`).
2. **Webhook secret** — in a separate terminal, run:
   ```bash
   stripe listen --forward-to localhost:8091/webhooks/stripe
   ```
   The CLI prints:
   ```
   > Ready! Your webhook signing secret is whsec_abc123...
   ```
   Copy that value. It regenerates every time you restart `stripe listen`,
   so update `.env` whenever you restart the CLI.
3. **Enable the provider**:
   ```bash
   PAYMENT_STRIPE_ENABLED=true
   ```
4. Re-source and re-start payment-service:
   ```bash
   set -a; source .env; set +a
   mvn -pl paymentservice spring-boot:run
   ```

If you forget to set `STRIPE_SECRET_KEY` and `PAYMENT_STRIPE_ENABLED=true`,
`StripePaymentProvider` fails fast at construction with a clear error.

### Smoke test — payment-service only

```bash
curl -X POST http://localhost:8091/api/v1/payments/initiate \
  -H "Content-Type: application/json" \
  -d '{
    "sagaId": "'"$(uuidgen)"'",
    "orderId": "smoke-test-1",
    "amount": 999.00,
    "provider": "stripe"
  }' | jq
```

Expected response:
```json
{
  "paymentId": "...",
  "providerRef": "cs_test_a1XYZ...",
  "redirectUrl": "https://checkout.stripe.com/c/pay/cs_test_...",
  "status": "INITIATED"
}
```

Open the `redirectUrl` in a browser. You should see Stripe's hosted checkout page.

### Full end-to-end — through the gateway

Prereq: the full stack running (`docker compose up -d` + payment-service
locally with Stripe config).

Postman: open folder **F5. Flow — Checkout (Stripe)** and run it top-to-bottom.
Each step chains state into env vars so you can watch the flow progress.

Manual version:
```bash
# 1. Get a bearer token from auth-server
TOKEN=$(curl -s -u my-client:secret -X POST \
   http://localhost:8095/oauth2/token \
   -d 'grant_type=client_credentials&scope=orders.write' | jq -r .access_token)

# 2. Checkout — amount is quantity × ₹9.99 under the hood
curl -X POST http://localhost:8080/api/v1/checkout \
   -H "Authorization: Bearer $TOKEN" \
   -H "Content-Type: application/json" \
   -H "Idempotency-Key: $(uuidgen)" \
   -d '{
     "productId": 1,
     "quantity": 2,
     "provider": "stripe"
   }' | jq
```

Response includes `redirectUrl`. Open it, pay with test card
`4242 4242 4242 4242`, any future expiry, any CVC. Stripe redirects back to
`STRIPE_SUCCESS_URL`.

Meanwhile, your `stripe listen` terminal shows the webhooks arriving at
payment-service. Within a few seconds:
- payment-service persists Payment row → `CAPTURED`
- publishes PaymentReply → orchestrator advances saga → `NOTIFIED`
- order → `PAID`

### Stripe test cards

Use these to exercise specific outcomes without creating real charges.

| Scenario | Card number | Notes |
|---|---|---|
| Successful payment | `4242 4242 4242 4242` | The default happy-path card |
| Card declined | `4000 0000 0000 0002` | Generic decline |
| Insufficient funds | `4000 0000 0000 9995` | Decline reason specifically |
| 3D-Secure required | `4000 0025 0000 3155` | Authentication challenge |
| Processing error | `4000 0000 0000 0119` | Transient failure — saga should see FAILED |

Full catalogue: https://docs.stripe.com/testing#cards

### Common failure modes

**`Missing stripe-signature header` on /webhooks/stripe:**
Something hit the endpoint without Stripe signing it. Probably curl, not
Stripe itself. Legitimate Stripe webhooks always include this header; the
service correctly rejects anything else with 400.

**`Stripe signature verification failed`:**
Your `STRIPE_WEBHOOK_SECRET` doesn't match what `stripe listen` is using.
Restart `stripe listen` (secret changes every start), copy the new
`whsec_...`, update `.env`, re-source, re-start payment-service.

**`Payment already linked at provider, waiting for webhook`:**
Not an error — PaymentService detected that the Payment row already has a
Stripe session id and short-circuited to avoid a duplicate charge. This is
the Kafka+HTTP race guard working as designed.

**`cs_test_...` opens an error page:**
Usually `STRIPE_SECRET_KEY` doesn't match the account that owns the webhook
secret. Both must come from the same Stripe test account.

**Service boot fails with `NullPointerException` in `StripePaymentProvider.configureStripe`:**
`STRIPE_SECRET_KEY` is still the placeholder. Either disable Stripe
(`PAYMENT_STRIPE_ENABLED=false`) or fill in a real key.

### What runs where

| Component | Port | Role |
|---|---|---|
| api-gateway | 8080 | Routes /checkout, /orders, /payments |
| order-service | 8083 | CheckoutController + saga |
| payment-service | 8091 | /initiate, /webhooks/*, saga command processor |
| `stripe listen` | — | Tunnels Stripe → http://localhost:8091/webhooks/stripe |
| mysql-payment | 3310 | Payments table (used under `docker` profile) |
| kafka-ui | 8090 | Browse messages during debugging |

---

## Phase 3 — Razorpay (not yet implemented)

Scaffolding only. `RazorpayPaymentProvider` throws on invocation.
Phase 3 will mirror the Stripe pattern:
- `com.razorpay:razorpay-java` SDK
- Order + Checkout URL via `RazorpayClient.Orders.create(...)`
- Webhook via `X-Razorpay-Signature` HMAC-SHA256

## Phase 4 — PayPal (not yet implemented)

Different model:
- OAuth per request (cache the access token)
- Order with `CAPTURE` intent
- Webhook verification via POST to PayPal's `/v1/notifications/verify-webhook-signature`

---

## Related

- `docs/microservices/debugging-flows.md` — Postman flows F1-F3 that exercise
  the saga under happy/fail/compensate paths. F4 (Mock checkout) and F5
  (Stripe checkout) extend that pattern through the new /checkout endpoint.
- `docs/microservices/troubleshooting.md#alert--order-saga` — what to look at
  when a payment-related saga alert fires.
- `docs/microservices/00-architecture-overview.md` — the big picture.
