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
| 1 | Mock | Committed and working |
| 2 | Stripe | Code committed; needs real API keys to actually test |
| 3 | Razorpay | Code committed; needs real API keys + ngrok tunnel to test |
| 4 | PayPal | Code committed; needs sandbox credentials + webhook id + ngrok |
| 5 | Checkout.com | Code committed; needs sandbox credentials. Split-capture flow (authorize + operator-triggered capture/void). |

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
   mvn -pl services/payment-service spring-boot:run
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

## Phase 3 — Razorpay

Different model from Stripe: Razorpay does NOT host the checkout page.
We create a server-side Order, then the frontend uses Checkout.js to open
a modal with that order id. The user never leaves our domain.

For a backend-only study project, this project ships a minimal HTML page
served by order-service that renders Checkout.js — so you still get a
browser-click flow without needing a separate frontend project.

### What you'll need

1. A **Razorpay test account** — https://dashboard.razorpay.com/signup
   (India phone number for OTP).
2. **Test API keys** —
   https://dashboard.razorpay.com/app/keys → switch to "Test mode" → "Generate Test Key".
   You get a Key Id (`rzp_test_...`) and a Key Secret.
3. **A webhook configured** —
   https://dashboard.razorpay.com/app/webhooks → "Add New Webhook"
   - URL: `https://<your-ngrok-or-tunnel>/webhooks/razorpay`
     (Razorpay webhooks need a public URL; localhost won't do.
     Use ngrok: `ngrok http 8091` then paste the forwarded URL.)
   - Secret: pick a strong string, paste the SAME string in `.env` as
     `RAZORPAY_WEBHOOK_SECRET`.
   - Subscribe to: `order.paid`, `payment.failed`, `refund.processed`.

### Set your keys in `.env`

```bash
PAYMENT_RAZORPAY_ENABLED=true
RAZORPAY_KEY_ID=rzp_test_...            # from step 2
RAZORPAY_KEY_SECRET=...                 # from step 2
RAZORPAY_WEBHOOK_SECRET=...             # the string you chose in step 3
```

Then:
```bash
set -a; source .env; set +a
mvn -pl services/payment-service spring-boot:run
# AND in another terminal, if you don't have ngrok already:
ngrok http 8091
# Copy the https://xxxxx.ngrok.io URL into the Razorpay webhook config.
```

### Smoke test — payment-service only

```bash
curl -X POST http://localhost:8091/api/v1/payments/initiate \
  -H "Content-Type: application/json" \
  -d '{
    "sagaId": "'"$(uuidgen)"'",
    "orderId": "smoke-rzp-1",
    "amount": 500.00,
    "provider": "razorpay"
  }' | jq
```

Expected response:
```json
{
  "paymentId": "...",
  "providerRef": "order_abc123XYZ",
  "redirectUrl": "http://localhost:8080/razorpay/checkout?orderId=order_abc123XYZ&amount=50000&currency=INR&keyId=rzp_test_...",
  "status": "INITIATED"
}
```

Open the `redirectUrl` in a browser — a page appears with a "Pay" button
that opens the Razorpay modal.

### Full end-to-end

Postman: open folder **F6. Flow — Checkout (Razorpay)** and run top-to-bottom.

Manual version:
```bash
TOKEN=$(curl -s -u my-client:secret -X POST \
   http://localhost:8095/oauth2/token \
   -d 'grant_type=client_credentials&scope=orders.write' | jq -r .access_token)

curl -X POST http://localhost:8080/api/v1/checkout \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"productId":1,"quantity":2,"provider":"razorpay"}' | jq
```

Response includes `redirectUrl`. Open it, click Pay, use Razorpay's test
card and OTP (see table below). The modal closes, Razorpay sends the
`order.paid` webhook to payment-service (via your tunnel), which transitions
the Payment row to CAPTURED → saga → NOTIFIED → order → PAID.

### Razorpay test credentials

| Scenario | Card | OTP | Notes |
|---|---|---|---|
| Successful payment | `4111 1111 1111 1111` | `123456` | Default test card |
| Failed payment | `5267 3181 8797 5449` | — | Simulates `payment.failed` |
| Successful UPI | `success@razorpay` | — | Use "UPI" tab in modal |
| Failed UPI | `failure@razorpay` | — | Simulates failure |

CVC: any 3 digits. Expiry: any future month.

Full reference: https://razorpay.com/docs/payments/payments/test-card-upi-details/

### Common failure modes

**`Missing x-razorpay-signature header`:**
Something hit `/webhooks/razorpay` that wasn't Razorpay. Legit webhooks
always sign; service rejects with 400.

**`Razorpay webhook signature mismatch`:**
`RAZORPAY_WEBHOOK_SECRET` in `.env` doesn't match what you configured in
the Razorpay dashboard. Both must be the exact same string.

**Modal opens but says "Payment failed" even with the right test card:**
Most common cause — the `key` field in Checkout.js options doesn't match
the key your SERVER used to create the order. The HTML page is generated
by payment-service's provider so this should match automatically; if it
doesn't, check that paymentservice and the checkout page URL both use the
same `rzp_test_...` key id.

**Webhook never arrives at payment-service:**
Three things to check:
1. `ngrok` tunnel still running?
2. Razorpay dashboard webhook URL still points at the current ngrok URL?
   (ngrok free tier regenerates the URL every restart.)
3. Payment status "test" vs "live" — must match the key you're using.

**Webhook arrives but Payment stays INITIATED:**
Check paymentservice logs. Most likely the webhook event type is one we
don't handle (we only act on `order.paid`, `payment.failed`,
`refund.processed`). Other events return `ignored` — intentional.

### Razorpay vs Stripe differences recap

| Thing | Stripe | Razorpay |
|---|---|---|
| Hosted page | Stripe hosts | You host; use Checkout.js modal |
| Idempotency | native header | `receipt` field on Order |
| Signature | `Stripe-Signature` HMAC | `X-Razorpay-Signature` HMAC |
| Verify | `Webhook.constructEvent` | `Utils.verifyWebhookSignature` |
| Amount | smallest unit (×100 non-zero-decimal) | paise (always ×100) |
| Currency | lowercase | uppercase |
| Local tunnel | `stripe listen` built in | ngrok / cloudflared / etc. |

## Phase 4 — PayPal

Fundamentally different from Stripe/Razorpay in three ways:

1. **OAuth per request.** The SDK trades `client_id` + `client_secret` for
   a short-lived access token (~9h TTL) and refreshes transparently.
2. **Server-side webhook verification.** No HMAC. PayPal signs the webhook
   with its own certificate. We call PayPal back at
   `/v1/notifications/verify-webhook-signature` with the headers + body
   + our configured `webhook_id` to confirm authenticity.
3. **Two-step payment.** `CAPTURE` intent creates an Order in state
   `CREATED` with an "approve" link. User approves → PayPal auto-captures
   → `PAYMENT.CAPTURE.COMPLETED` webhook fires. The saga sees CAPTURED
   only after that final webhook.

### What you'll need

1. A **PayPal developer sandbox account** — https://developer.paypal.com/dashboard/
   (free, email + password).
2. A **sandbox REST app** — Dashboard → Apps & Credentials → Sandbox tab →
   Create App → copy Client ID and Secret.
3. A **webhook subscription** —
   Dashboard → My Apps → <your app> → scroll to Webhooks → Add Webhook.
   - URL: `https://<ngrok-url>/webhooks/paypal`
   - Events: `PAYMENT.CAPTURE.COMPLETED`, `PAYMENT.CAPTURE.DENIED`,
     `PAYMENT.CAPTURE.REFUNDED`, `CHECKOUT.ORDER.APPROVED`.
   - After save, PayPal shows the Webhook ID (`WH-XXXXXXXX...`).

### Set your keys in `.env`

```bash
PAYMENT_PAYPAL_ENABLED=true
PAYPAL_CLIENT_ID=...          # from step 2
PAYPAL_CLIENT_SECRET=...      # from step 2
PAYPAL_WEBHOOK_ID=WH-...      # from step 3
PAYPAL_ENV=sandbox            # leave as is for test mode
```

Then:
```bash
# ngrok tunnel for the webhook (PayPal needs a public URL)
ngrok http 8091
# Copy the https://xxx.ngrok.io into the webhook URL on the dashboard.

# Load env + restart paymentservice
set -a; source .env; set +a
mvn -pl services/payment-service spring-boot:run
```

### Smoke test — payment-service only

```bash
curl -X POST http://localhost:8091/api/v1/payments/initiate \
  -H "Content-Type: application/json" \
  -d '{
    "sagaId": "'"$(uuidgen)"'",
    "orderId": "smoke-paypal-1",
    "amount": 25.00,
    "provider": "paypal"
  }' | jq
```

Expected response:
```json
{
  "paymentId": "...",
  "providerRef": "5O190127TN364715T",
  "redirectUrl": "https://www.sandbox.paypal.com/checkoutnow?token=5O190127TN364715T",
  "status": "INITIATED"
}
```

Open the `redirectUrl` in a browser → log in with a sandbox buyer
account (create one at https://developer.paypal.com/dashboard/accounts).

### Full end-to-end

Postman folder: **F7. Flow — Checkout (PayPal)**.

### PayPal sandbox test accounts

PayPal doesn't use card numbers — use sandbox buyer accounts.
- Create at https://developer.paypal.com/dashboard/accounts.
- Default sandbox accounts: usually one "Personal (buyer)" and one
  "Business (merchant)" are created automatically.
- To force a failure, use any sandbox account with insufficient funds
  (set balance in the account editor), or dismiss the approval page.

### Common failure modes

**`Missing required header: paypal-auth-algo` (or similar):**
Something hit `/webhooks/paypal` that wasn't PayPal. We check all five
required `paypal-*` headers before even calling PayPal to verify.

**`verification_status=FAILURE`:**
Either `PAYPAL_WEBHOOK_ID` doesn't match the webhook subscription that
sent this payload, or your sandbox app vs. the webhook's app don't match.
Both the client id and the webhook id must come from the SAME sandbox
REST app.

**Webhook never arrives:**
PayPal's "Webhook simulator" (Dashboard → Webhooks Simulator) is the
fastest way to trigger an event without a real payment. Simulate
`PAYMENT.CAPTURE.COMPLETED` targeting your ngrok URL — the Payment row
won't exist for a simulated event (unknown provider_ref), so the
response is `WebhookResult.ignored(...)` + 200. If you don't see even
that in paymentservice logs, your ngrok URL on the dashboard is stale.

**`CHECKOUT.ORDER.APPROVED` fires but saga stays INITIATED:**
Expected. With CAPTURE intent, PayPal auto-captures after approval and
sends `PAYMENT.CAPTURE.COMPLETED` separately — the orchestrator advances
only on that second event. `APPROVED` returns `WebhookResult.ignored`.

### PayPal vs Stripe vs Razorpay recap

| Thing | Stripe | Razorpay | PayPal |
|---|---|---|---|
| Hosted page | Stripe hosts | Checkout.js modal | PayPal hosts |
| Idempotency | header | `receipt` field | `PayPal-Request-Id` header |
| Signature | HMAC local | HMAC local | PayPal API verify call |
| OAuth | no (static key) | no (static key) | yes (SDK handles) |
| Capture | 1 step | 1 step | 2 step (approve → capture) |
| Local tunnel | `stripe listen` | ngrok | ngrok |

---

## Phase 5 — Checkout.com (split-capture)

First provider in the project that supports an explicit two-phase lifecycle:

```
authorize → money reserved on card (not charged)
   │
   ├─► capture → money moves                 (operator says ship/charge)
   │
   └─► void    → authorization released      (operator says cancel)
                 nothing charged, usually free of fees
```

Why we built it: the project needed to demonstrate real-world flows where
"customer committed" and "we take the money" are separated by hours or days
— hotels, car rentals, Airbnb, marketplaces with escrow, pay-on-ship e-comm.
Checkout.com's API is cleanly two-phase so it was the natural choice.

### Architecture summary

- Interface: `SplitCapturePaymentProvider` extends `PaymentProvider` with
  `authorize()`, `capture()`, `voidPayment()`. Only providers that opt in
  implement it. Config list `orderservice.saga.split-capture-providers`
  (default: `checkoutcom`) tells the orchestrator which providers to route
  via the split path.
- Commands: `AuthorizePaymentCommand`, `CapturePaymentCommand`,
  `VoidPaymentCommand` (plus matching replies). Each has its own Kafka
  topic + consumer group + `IdempotencyGuard` consumer name so a saga can
  have an Authorize AND a later Capture without the guard swallowing one.
- Admin endpoints: `POST /admin/orders/{orderId}/capture` and `/void`
  (on order-service) publish the capture/void commands. Preconditions
  check saga state is `AUTHORIZED`; 409 Conflict otherwise.
- HTTP client: `WebClient` (Spring reactive, auto-Zipkin, MDC correlation
  propagation), configured in `CheckoutDotComConfig`.

See `payment-gateway-phase5-design.md` for the full design including flow
charts, state-machine diagrams, and the WebClient rationale.

### What you'll need

1. A **Checkout.com sandbox account** —
   https://www.checkout.com/get-test-account OR
   https://dashboard.sandbox.checkout.com/
2. **API keys** — Dashboard → Developers → Keys. You get:
   - Secret key: `sk_sbox_...`
   - Public key: `pk_sbox_...` (used by frontend; we don't need it here)
3. **A webhook signing secret** — Dashboard → Developers → Webhooks → New
   webhook. For sync-only testing with the admin flow, the webhook isn't
   strictly needed — the demo exercises the direct API via WebClient and
   the admin controller. For a realistic async setup, point the webhook
   URL at `https://<ngrok-tunnel>/webhooks/checkoutcom` and subscribe to:
   `payment_approved`, `payment_captured`, `payment_declined`,
   `payment_voided`, `payment_refunded`.

### Set your keys in `.env`

```bash
PAYMENT_CHECKOUTCOM_ENABLED=true
CHECKOUTCOM_BASE_URL=https://api.sandbox.checkout.com
CHECKOUTCOM_SECRET_KEY=sk_sbox_...
CHECKOUTCOM_WEBHOOK_SECRET=whsec_...
CHECKOUTCOM_TEST_TOKEN=tok_card_visa_1
```

Then:
```bash
set -a; source .env; set +a
mvn -pl services/payment-service spring-boot:run
# ...and leave order-service / Kafka / mysql-payment / notification running too.
```

### Checkout.com sandbox test tokens

Checkout.com provides pre-made tokens that let you exercise specific paths
without needing a tokenization frontend:

| Token | Behavior |
|---|---|
| `tok_card_visa_1` | Always authorizes (default in `.env.example`) |
| `tok_card_decline` | Always declines — exercises the auth-fail path |
| `tok_card_3ds` | Triggers 3DS challenge — out of scope for this demo |

Change `CHECKOUTCOM_TEST_TOKEN` and restart payment-service to switch.

### Full split-capture flow — through the gateway

Postman folder: **F8. Flow — Split-capture (Checkout.com)**.

Manual version:

```bash
TOKEN=$(curl -s -u my-client:secret -X POST \
   http://localhost:8095/oauth2/token \
   -d 'grant_type=client_credentials&scope=orders.write' | jq -r .access_token)

# 1. Checkout with provider=checkoutcom → saga enters AUTHORIZED after ~1-3s
RESP=$(curl -s -X POST http://localhost:8080/api/v1/checkout \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"productId":1,"quantity":2,"provider":"checkoutcom"}')
echo "$RESP" | jq
ORDER_ID=$(echo "$RESP" | jq -r .orderId)
SAGA_ID=$(echo "$RESP" | jq -r .sagaId)

# 2. Verify saga reaches AUTHORIZED
sleep 3
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/sagas/$SAGA_ID | jq '.state'
# "AUTHORIZED"

# 3a. HAPPY — capture
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason":"shipment confirmed"}' \
  http://localhost:8080/admin/orders/$ORDER_ID/capture | jq
# { "sagaId": "...", "action": "capture_requested", "paymentId": "..." }

# 4a. Verify saga reaches NOTIFIED
sleep 5
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/sagas/$SAGA_ID | jq '.state'
# "NOTIFIED"
```

Or the VOID path (step 3b, instead of 3a):

```bash
# 3b. CANCEL — void (releases the authorization, no money moved)
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason":"customer cancelled"}' \
  http://localhost:8080/admin/orders/$ORDER_ID/void | jq
# { "sagaId": "...", "action": "void_requested", ... }

sleep 3
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/admin/sagas/$SAGA_ID | jq '.state'
# "VOIDED"
```

### Observability — new metrics + panels

Three new Prometheus counters + one new timer:
- `orders_saga_authorized_total` — one per successful authorize reply
- `orders_saga_captured_total` — one per successful capture reply
- `orders_saga_voided_total` — one per successful void reply
- `orders_saga_time_to_capture_seconds` — operator SLA
  (AUTHORIZED → PAID duration)

Dashboard: three new panels on `Order Saga — business metrics`:
- "Split-capture — authorized / captured / voided per min"
- "Time to capture — operator SLA (p50/p95/p99)"
- "Currently AUTHORIZED (waiting for operator)" (stat with thresholds)

Alert: `SagaStuckInAuthorized` fires when any saga has been AUTHORIZED for
over 24h without capture/void. Default is a warning routed to mailhog.

### Common failure modes

**`401 Unauthorized` on authorize:**
`CHECKOUTCOM_SECRET_KEY` is wrong, or the dashboard key is a public key
(`pk_sbox_...`) instead of the secret (`sk_sbox_...`).

**`422 Unprocessable Entity` from Checkout.com:**
Usually a token issue — `CHECKOUTCOM_TEST_TOKEN` doesn't exist in your
sandbox account, or the amount currency doesn't match the token's test
rules. Our provider maps 422 to `DECLINED` so the saga transitions
`FAILED` cleanly. Check the response body in `paymentservice` logs.

**Saga stays STARTED past 3-5s:**
Either `PAYMENT_CHECKOUTCOM_ENABLED=false`, or the saga isn't routing to
the split path. Verify by hitting `/actuator/env` on both services and
confirming `payment.checkoutcom.enabled=true` and
`orderservice.saga.split-capture-providers` contains `checkoutcom`.

**409 Conflict on `/admin/orders/{id}/capture`:**
Saga is NOT in `AUTHORIZED` state. Response body says the current state.
Possibilities: still STARTED (authorize hasn't completed yet — wait a bit),
already PAID (someone else captured first), already VOIDED, or FAILED.

### Checkout.com vs other 4 providers recap

| Thing | Mock | Stripe | Razorpay | PayPal | Checkout.com |
|---|---|---|---|---|---|
| Authorize + capture | combined | combined | combined | combined (2-step but auto) | **separate** |
| Hosted page | n/a | Stripe | modal | PayPal | n/a (API-only here) |
| HTTP client | n/a | Stripe SDK | Razorpay SDK | PayPal SDK | **WebClient** |
| Webhook signature | n/a | HMAC local | HMAC local | PayPal API verify | HMAC local |
| Idempotency | sagaId guard | native header | `receipt` field | `PayPal-Request-Id` | `Cko-Idempotency-Key` |
| Operator capture API | n/a | n/a | n/a | n/a | **/admin/orders/{id}/capture** |

---

## Related

- `docs/microservices/debugging-flows.md` — Postman flows F1-F3 that exercise
  the saga under happy/fail/compensate paths. F4 (Mock checkout) and F5
  (Stripe checkout) extend that pattern through the new /checkout endpoint.
- `docs/microservices/troubleshooting.md#alert--order-saga` — what to look at
  when a payment-related saga alert fires.
- `docs/microservices/00-architecture-overview.md` — the big picture.
