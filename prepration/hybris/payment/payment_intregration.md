# Payment-Specific Business Logic

API Integration gets data from the payment gateway.  
Payment Business Logic decides what to do with that data.

---

## 1. Create Order Before Payment

Customer clicks Place Order.

```
Order Number : ORD123
Amount       : 1000
Status       : PENDING_PAYMENT
```

Save order in DB.

---

## 2. Create Payment Transaction

Create payment transaction record.

```
Transaction Id : TXN123
Order Number   : ORD123
Amount         : 1000
Status         : INITIATED
```

Save transaction.

---

## 3. Call Payment Gateway API

**Send:**

```json
{
  "orderId": "ORD123",
  "amount": 1000
}
```

**Receive:**

```json
{
  "paymentId": "PAY123",
  "status": "PENDING"
}
```

Update transaction.

---

## 4. Redirect Customer

Redirect customer to gateway page.

Customer completes payment.

---

## 5. Handle Callback / Webhook

Gateway sends:

```json
{
  "paymentId": "PAY123",
  "status": "SUCCESS"
}
```

or

```json
{
  "paymentId": "PAY123",
  "status": "FAILED"
}
```

---

## 6. Verify Payment Status

Do not trust callback blindly.

Call payment status API.

```
GET /payment/PAY123
```

**Response:**

```json
{
  "paymentId": "PAY123",
  "status": "SUCCESS"
}
```

---

## 7. Update Order Status

| Gateway Result | Order Status    |
|----------------|-----------------|
| SUCCESS        | PAID            |
| FAILED         | PAYMENT_FAILED  |
| CANCELLED      | CANCELLED       |

---

## 8. Update Payment Transaction

Transaction moves through:

```
INITIATED → SUCCESS / FAILED / CANCELLED
```

Save latest status.

---

## 9. Trigger Business Actions

After successful payment:

- ✓ Generate Invoice
- ✓ Reserve Inventory
- ✓ Create Shipment
- ✓ Send Email
- ✓ Send SMS
- ✓ Start Fulfillment

---

## 10. Handle Refunds

Customer cancels order. Call Refund API.

**Response:**

```json
{
  "refundId": "REF123",
  "status": "SUCCESS"
}
```

**Update:**

```
Order Status   : REFUNDED
Payment Status : REFUNDED
```

---

## 11. Handle Duplicate Callbacks

Gateway may send callback twice.

Before processing:

1. Check transaction status.
2. If already `SUCCESS` → ignore callback.

---

## 12. Reconciliation

Daily job compares:

```
Internal Records
vs
Gateway Records
```

Detects:

- ✓ Missing payments
- ✓ Failed updates
- ✓ Amount mismatches

---

## Real Project Flow

```
Place Order
↓
Create Order
↓
Create Payment Transaction
↓
Call Payment API
↓
Customer Pays
↓
Callback/Webhook
↓
Verify Payment
↓
Update Transaction
↓
Update Order
↓
Invoice
↓
Shipment
↓
Customer Notification
```

---

## Authorization vs Purchase — The Core Concept

> This is not a pattern — it's a payment industry standard for how money moves.

---

### Authorization (Pre-Auth)

```
Customer pays → Bank RESERVES the money → Money is NOT yet taken
↓
(Later, during fulfillment)
↓
Merchant sends CAPTURE → Bank actually TAKES the money
```

**Two-step process:**

1. `AUTH` — "Can this customer pay 500 AED?" → Bank says yes, blocks 500 AED on the card
2. `CAPTURE` — "Actually take the 500 AED now" → Bank transfers the money

**Why use it:**

- You can cancel the order before capture (no refund needed, just void the auth)
- You can capture a partial amount (e.g., if one item is out of stock)
- Common for credit cards, debit cards where order fulfillment takes time

**In this codebase:**

```
Controller → authorizePayment()
  → paymentService.authorizeRequest()
  → FortAuthorizationCommand (command = "AUTHORIZATION")
  → Fort API
  → Creates AUTHORIZATION PaymentTransactionEntry

... later, during order fulfillment (sale posting) ...

  → paymentService.captureRequest()
  → FortCaptureCommand (command = "CAPTURE")
  → Fort API
  → Creates CAPTURE PaymentTransactionEntry
```

---

### Purchase (Direct Capture)

```
Customer pays → Bank TAKES the money immediately → Done
```

**Single-step process:**

1. `PURCHASE` — "Take 500 AED from this customer now" → Bank transfers immediately

**Why use it:**

- Some providers don't support pre-auth (SADAD, KNET, NAPS, Benefit)
- MADA cards (Saudi debit network) require direct purchase
- Simpler flow, but no partial capture or void — only refund

**In this codebase:**

```
Controller → purchasePayment()
  → paymentService.purchaseRequest()
  → FortPurchaseCommand (command = "PURCHASE")
  → Fort API
  → Creates SESSION_INITIATED + AUTHORIZATION PaymentTransactionEntries
  (No separate CAPTURE needed — money is already taken)
```

---

### Visual Comparison

**Authorization flow (two-step):**

```
Place Order          Ship Order           Done
│                    │                    │
▼                    ▼                    │
[AUTH]              [CAPTURE]             │
Reserve ₹500        Take ₹500             │
│                    │                    │
Can VOID here        Money moves here     │
(no refund needed)  (refund if returned)  │
```

**Purchase flow (one-step):**

```
Place Order                               Done
│                                         │
▼                                         │
[PURCHASE]                                │
Take ₹500 immediately                     │
│                                         │
Can only REFUND from here                 │
(money already taken)                     │
```

---

### Comparison Table

| Feature          | Authorization       | Purchase          |
|------------------|---------------------|-------------------|
| Steps            | 2 (auth + capture)  | 1 (immediate)     |
| Can void?        | Yes                 | No                |
| Partial capture? | Yes                 | No                |
| To cancel        | Void (no refund)    | Refund only       |
| Use case         | Cards, delayed fulfillment | MADA, KNET, SADAD |

---

## Design Patterns

---

### 1. Command Pattern

**What:** Encapsulates a payment gateway request as an object. Each payment operation (authorize, purchase, refund, capture) is a separate Command class.

**Why:** Different payment providers (Fort, Checkout.com) have different APIs, request formats, and response formats. The command pattern lets you swap providers without changing business logic.

**How it works in this codebase:**

```
AbstractCommand<RequestType, ResponseType>
    │
    ├── FortAuthorizationCommand      → command = "AUTHORIZATION"
    ├── FortPurchaseCommand           → command = "PURCHASE"  (extends FortAuthorizationCommand)
    ├── FortCaptureCommand            → command = "CAPTURE"
    ├── FortRefundCommand             → command = "REFUND"
    ├── EMIPurchaseCommand            → command = "PURCHASE" (with installment params)
    └── EMIPlansCommand               → fetches installment plans
```

Every command does 3 things:

1. `populateCommandQueryParams()` — Build the request params (merchant_reference, amount, currency, etc.)
2. `perform()` — POST to the payment gateway URL and get JSON response
3. `translateResponse()` — Convert the gateway's response into `LMGAuthorizationResult`

**The `CommandFactory` creates the right command based on the provider:**

```java
CommandFactory factory = commandFactoryRegistry.getFactory("payfort");
AuthorizationCommand cmd = factory.createCommand(AuthorizationCommand.class);  // → FortAuthorizationCommand
PurchaseCommand cmd      = factory.createCommand(PurchaseCommand.class);       // → FortPurchaseCommand
```

> **Key point:** If tomorrow you switch from Fort to a new gateway, you only write new command classes — the facade/service/controller layers don't change.