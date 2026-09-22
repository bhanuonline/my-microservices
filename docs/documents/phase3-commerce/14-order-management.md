# 14. Order Management

## What is Order Management in SAP Hybris?

Order Management covers everything that happens **after a customer places an order** — from payment authorization to delivery to returns. It's driven by the **Business Process Engine**, which orchestrates workflows as a series of automated steps.

---

## 1. The Order Model

### OrderModel (extends AbstractOrderModel)

When a customer places an order, the `CartModel` is cloned into an `OrderModel`:

```
CartModel  ──placeOrder()──►  OrderModel
```

### Key Fields

```
OrderModel
├── code: "LM-00012345"              ← unique order number
├── user: → CustomerModel            ← who placed it
├── date: 2026-08-21                 ← when placed
├── status: OrderStatus              ← current status
├── entries: List<OrderEntryModel>   ← line items
├── totalPrice: 350.00
├── totalDiscounts: 50.00
├── deliveryCost: 15.00
├── totalTax: 18.75
├── net: false                       ← prices include tax?
├── currency: AED
├── deliveryMode: → DeliveryModeModel
├── deliveryAddress: → AddressModel
├── paymentInfo: → PaymentInfoModel
├── paymentStatus: PaymentStatus
├── site: → BaseSiteModel
├── store: → BaseStoreModel
├── vouchersApplied: [VoucherModel...]
└── consignments: [ConsignmentModel...]  ← fulfillment groups
```

### OrderEntryModel

Each line item in the order:

```
OrderEntryModel
├── entryNumber: 0
├── product: → ProductModel
├── quantity: 2
├── basePrice: 150.00
├── totalPrice: 300.00
├── unit: → UnitModel
├── deliveryMode: → DeliveryModeModel   ← entry-level delivery
├── deliveryPointOfService: → POS       ← for click-and-collect
└── order: → OrderModel                 ← parent order
```

---

## 2. Order Status Lifecycle

### Status Flow

```
                    CREATED
                       │
                       ▼
              PAYMENT_AUTHORIZED
                       │
                 ┌─────┼─────┐
                 ▼     ▼     ▼
            FRAUD_   PAYMENT  PROCESSING
            CHECK    CAPTURED
                       │
                       ▼
                   READY_FOR_PICKUP / SHIPPED
                       │
                       ▼
                   COMPLETED
                       │
              ┌────────┼────────┐
              ▼                 ▼
        RETURN_REQUESTED    CANCELLED
              │
              ▼
        RETURNED / PARTIALLY_RETURNED
```

### OrderStatus Enum

| Status | Meaning |
|--------|---------|
| `CREATED` | Order just placed |
| `CHECKED_VALID` | Passed fraud check |
| `CHECKED_INVALID` | Failed fraud check |
| `PAYMENT_AUTHORIZED` | Payment reserved but not captured |
| `PAYMENT_NOT_AUTHORIZED` | Payment failed |
| `PAYMENT_CAPTURED` | Money taken |
| `PAYMENT_NOT_CAPTURED` | Capture failed |
| `READY` | Ready for fulfillment |
| `SHIPPING` | Being shipped |
| `COMPLETED` | Delivered |
| `CANCELLED` | Cancelled |
| `CANCELLING` | Cancellation in progress |

### PaymentStatus

```
NOTPAID → PAID → PARTIALLY_REFUNDED → REFUNDED
```

### DeliveryStatus (per Consignment)

```
NOT_SHIPPED → READY_FOR_PICKUP → SHIPPED → DELIVERED
```

---

## 3. Business Process Engine

### What is It?

The Business Process Engine is Hybris's **workflow engine**. It runs predefined processes made up of steps (called **actions** and **waits**). It's used for:

- Order fulfillment
- Return/refund workflows
- Email notifications
- Consignment tracking

### Process Definition (XML)

Processes are defined in XML files:

```xml
<process xmlns="http://www.hybris.de/xsd/processdefinition"
         name="order-process"
         start="checkOrder"
         onError="error">

    <action id="checkOrder"
            bean="checkOrderAction">
        <transition name="OK" to="authorizePayment"/>
        <transition name="NOK" to="error"/>
    </action>

    <action id="authorizePayment"
            bean="authorizePaymentAction">
        <transition name="OK" to="fraudCheck"/>
        <transition name="NOK" to="paymentFailed"/>
        <transition name="WAIT" to="waitForPayment"/>
    </action>

    <wait id="waitForPayment"
          then="authorizePayment"
          prependProcessCode="true">
        <event>PaymentConfirmed</event>
    </wait>

    <action id="fraudCheck"
            bean="fraudCheckAction">
        <transition name="OK" to="sendOrderConfirmation"/>
        <transition name="FRAUD" to="notifyFraud"/>
    </action>

    <action id="sendOrderConfirmation"
            bean="sendOrderConfirmationAction">
        <transition name="OK" to="waitForShipment"/>
    </action>

    <wait id="waitForShipment"
          then="completeOrder"
          prependProcessCode="true">
        <event>ShipmentConfirmed</event>
    </wait>

    <action id="completeOrder"
            bean="completeOrderAction">
        <transition name="OK" to="success"/>
    </action>

    <end id="success" state="SUCCEEDED">Order completed.</end>
    <end id="error" state="ERROR">Order processing error.</end>
    <end id="paymentFailed" state="FAILED">Payment failed.</end>

</process>
```

### Visual Flow

```
┌─────────────┐
│ checkOrder  │──NOK──► [error]
└──────┬──────┘
       │ OK
       ▼
┌──────────────────┐
│authorizePayment  │──NOK──► [paymentFailed]
└──────┬───────────┘
       │ OK          │ WAIT
       ▼             ▼
┌──────────────┐  ┌────────────────┐
│  fraudCheck  │  │waitForPayment  │
└───┬──────┬───┘  │ (event-based)  │
    │      │      └────────────────┘
    │OK    │FRAUD
    ▼      ▼
┌───────────────────┐  ┌──────────────┐
│sendOrderConfirm.  │  │ notifyFraud  │
└───────┬───────────┘  └──────────────┘
        │ OK
        ▼
┌────────────────────┐
│ waitForShipment    │
│ (waits for event)  │
└────────┬───────────┘
         │ event received
         ▼
┌────────────────┐
│ completeOrder  │
└────────┬───────┘
         │ OK
         ▼
      [success]
```

### Key Elements

| Element | Purpose |
|---------|---------|
| `<process>` | The workflow definition |
| `<action>` | An automated step (runs a Spring bean) |
| `<wait>` | Pauses until an external event |
| `<end>` | Terminal state |
| `<transition>` | Path between steps based on return value |

---

## 4. Process Actions (The Steps)

### What is an Action?

Each `<action>` in the process runs a Spring bean that implements `AbstractAction`:

```java
public class CheckOrderAction extends AbstractAction<OrderProcessModel> {

    @Override
    public String execute(OrderProcessModel process) throws Exception {
        OrderModel order = process.getOrder();

        if (order == null || order.getEntries().isEmpty()) {
            return Transition.NOK;
        }

        // Validate order
        if (isOrderValid(order)) {
            return Transition.OK;
        }

        return Transition.NOK;
    }
}
```

### Common Order Process Actions

| Action | What It Does |
|--------|-------------|
| `checkOrderAction` | Validates the order is complete |
| `authorizePaymentAction` | Authorizes payment with gateway |
| `fraudCheckAction` | Runs fraud detection |
| `reserveStockAction` | Reserves inventory |
| `sendOrderConfirmationAction` | Sends confirmation email |
| `splitOrderAction` | Splits order into consignments |
| `takePaymentAction` | Captures the authorized payment |
| `completeOrderAction` | Marks order as completed |

### Transition Return Values

Actions return strings that map to `<transition>` elements:

```java
public class Transition {
    public static final String OK = "OK";
    public static final String NOK = "NOK";
    public static final String WAIT = "WAIT";
    public static final String ERROR = "error";
}
```

---

## 5. Wait Nodes and Events

### What is a Wait Node?

A `<wait>` node **pauses** the process until an external event occurs:

```xml
<wait id="waitForShipment"
      then="completeOrder"
      prependProcessCode="true">
    <event>ShipmentConfirmed</event>
</wait>
```

This waits until someone triggers the `ShipmentConfirmed` event.

### How Events Are Triggered

```java
// External system (warehouse, payment gateway) triggers event
BusinessProcessService businessProcessService;

// Event name format: {processCode}_ShipmentConfirmed
businessProcessService.triggerEvent("order-process-00012345_ShipmentConfirmed");
```

### Common Events

| Event | Triggered By |
|-------|-------------|
| `PaymentConfirmed` | Payment gateway callback |
| `ShipmentConfirmed` | Warehouse management system |
| `FraudCheckApproved` | Fraud detection service |
| `ConsignmentPickup` | Store staff marking pickup |
| `ReturnApproved` | Customer service agent |

---

## 6. Business Process Model

### BusinessProcessModel

Every running process is stored as a model:

```
BusinessProcessModel
├── code: "order-process-00012345"       ← unique process ID
├── processDefinitionName: "order-process"
├── state: RUNNING / WAITING / SUCCEEDED / FAILED / ERROR
├── currentTasks: ["waitForShipment"]    ← current position
├── startDate: ...
├── endDate: ...
└── contextParameters: [...]             ← process-specific data
```

### OrderProcessModel (extends BusinessProcessModel)

```
OrderProcessModel
├── (all BusinessProcessModel fields)
└── order: → OrderModel    ← the order being processed
```

### Viewing Process State

In HAC (Hybris Administration Console):

```
HAC → Monitoring → Business Processes
├── Process: "order-process-00012345"
│   ├── State: WAITING
│   ├── Current Node: waitForShipment
│   ├── Start: 2026-08-21 10:00
│   └── Duration: 2h 30m
```

---

## 7. Consignments (Fulfillment Groups)

### What is a Consignment?

A consignment is a **group of order entries** that are fulfilled together — shipped from the same warehouse, picked up from the same store, etc.

### Why Split into Consignments?

```
Order: "LM-00012345"
├── Entry 1: Laptop (ships from warehouse A)
├── Entry 2: Phone Case (ships from warehouse A)
└── Entry 3: Shirt (ships from warehouse B)

Consignment 1 (Warehouse A):
├── Entry 1: Laptop
└── Entry 2: Phone Case

Consignment 2 (Warehouse B):
└── Entry 3: Shirt
```

### ConsignmentModel

```
ConsignmentModel
├── code: "cons-00012345-01"
├── order: → OrderModel
├── status: ConsignmentStatus
├── entries: [ConsignmentEntryModel...]
├── deliveryPointOfService: → PointOfServiceModel
├── warehouse: → WarehouseModel
├── shippingAddress: → AddressModel
├── trackingID: "DHL-9876543210"
├── carrier: "DHL"
└── shippingDate: 2026-08-22
```

### ConsignmentEntryModel

```
ConsignmentEntryModel
├── consignment: → ConsignmentModel
├── orderEntry: → OrderEntryModel
├── quantity: 1
└── shippedQuantity: 1
```

### Consignment Status Flow

```
READY ──► PICKPACK ──► SHIPPED ──► DELIVERED
                                      │
                                      ▼
                               RETURN_REQUESTED
                                      │
                                      ▼
                                  RETURNED
```

### Consignment Process

Consignments have their own business process:

```xml
<process name="consignment-process" start="waitForConsignment">

    <wait id="waitForConsignment" then="processConsignment">
        <event>ConsignmentReady</event>
    </wait>

    <action id="processConsignment" bean="processConsignmentAction">
        <transition name="PICKUP" to="waitForPickup"/>
        <transition name="SHIP" to="shipConsignment"/>
    </action>

    <action id="shipConsignment" bean="shipConsignmentAction">
        <transition name="OK" to="waitForDelivery"/>
    </action>

    <wait id="waitForDelivery" then="confirmDelivery">
        <event>DeliveryConfirmed</event>
    </wait>

    <action id="confirmDelivery" bean="confirmDeliveryAction">
        <transition name="OK" to="success"/>
    </action>

    <end id="success" state="SUCCEEDED"/>
</process>
```

---

## 8. Order Splitting Strategy

### How Orders Are Split

The `OrderSplittingService` decides how to split orders into consignments:

```java
public interface OrderSplittingService {
    List<ConsignmentModel> splitOrderForConsignment(
        OrderModel order,
        List<OrderEntryModel> entries
    );
}
```

### Split Criteria

```
Order Entry → Which consignment?
     │
     ├── By Warehouse: entries from same warehouse → same consignment
     ├── By Delivery Mode: same delivery method → same consignment
     ├── By Point of Service: same store pickup → same consignment
     └── Custom rules: brand, vendor, etc.
```

### Example

```
Order has 4 items:
├── Item A: Warehouse Dubai, Standard Delivery
├── Item B: Warehouse Dubai, Standard Delivery
├── Item C: Warehouse Riyadh, Standard Delivery
└── Item D: Store Pickup, Mall of Emirates

Result:
├── Consignment 1 (Dubai warehouse, Standard):  [A, B]
├── Consignment 2 (Riyadh warehouse, Standard): [C]
└── Consignment 3 (Store Pickup, MoE):          [D]
```

---

## 9. Return & Refund Process

### Return Flow

```
Customer requests return
       │
       ▼
  ReturnRequestModel created
       │
       ├── status: WAIT (pending approval)
       │
       ▼
  Agent approves/rejects in Backoffice
       │
       ├── APPROVAL_PENDING → APPROVED
       │
       ▼
  Customer ships item back
       │
       ├── Items received at warehouse
       │
       ▼
  Refund processed
       │
       ├── RefundEntryModel created
       ├── Payment refunded
       │
       ▼
  Return completed
```

### ReturnRequestModel

```
ReturnRequestModel
├── code: "RET-00012345"
├── order: → OrderModel
├── status: ReturnStatus
├── returnEntries: [ReturnEntryModel...]
├── refundDeliveryCost: true/false
├── rma: "RMA-00012345"           ← return merchandise authorization
└── returnReason: ReturnReason
```

### ReturnEntryModel

```
ReturnEntryModel
├── orderEntry: → OrderEntryModel
├── expectedQuantity: 1
├── receivedQuantity: 1
├── reachedDate: ...
├── action: ReturnAction (REFUND, REPLACE, HOLD)
└── refundAmount: 150.00
```

### Your Project: Refund Delivery Cost Strategy

```java
// NONRBDLMGRefundDeliveryCostStrategy.java
// Custom strategy for refunding delivery costs
// "NONRBD" likely = "Non-Returnable Delivery"
// Handles cases where delivery cost should/shouldn't be refunded
```

---

## 10. Email Notifications in Order Process

### How Emails Are Triggered

Emails are sent as **actions** in the business process:

```xml
<action id="sendOrderConfirmation"
        bean="sendOrderConfirmationAction">
    <transition name="OK" to="nextStep"/>
</action>
```

### Email Process

```
sendOrderConfirmationAction
       │
       ├── 1. Create EmailMessageModel
       │       ├── subject: "Order Confirmation - LM-00012345"
       │       ├── body: rendered from Velocity template
       │       ├── toAddresses: customer email
       │       └── fromAddress: noreply@landmark.com
       │
       ├── 2. Render email template
       │       └── email-orderConfirmationBody_en.vm   ← your template
       │
       └── 3. Queue for sending
               └── EmailService.send()
```

### Your Project's Email Template

```
landmarkshopscore/resources/landmarkshopscore/import/emails/
└── email-orderConfirmationBody_en.vm
```

This Velocity template contains the order confirmation email layout with:
- Order details
- Line items
- Prices and discounts
- Delivery information
- Payment summary

---

## 11. Full Order Process Flow

```
┌─────────────────────────────────────────────────────────┐
│                  COMPLETE ORDER FLOW                      │
│                                                           │
│  Customer clicks "Place Order"                           │
│         │                                                 │
│         ▼                                                 │
│  1. CREATE ORDER                                         │
│     ├── Clone CartModel → OrderModel                     │
│     ├── Generate order code                              │
│     ├── Set status = CREATED                             │
│     └── Start order-process                              │
│         │                                                 │
│         ▼                                                 │
│  2. AUTHORIZE PAYMENT                                    │
│     ├── Call payment gateway                             │
│     ├── Reserve funds on customer's card                 │
│     └── Set paymentStatus = AUTHORIZED                   │
│         │                                                 │
│         ▼                                                 │
│  3. FRAUD CHECK                                          │
│     ├── Score transaction risk                           │
│     ├── Auto-approve low risk                            │
│     └── Flag high risk for manual review                 │
│         │                                                 │
│         ▼                                                 │
│  4. SEND ORDER CONFIRMATION EMAIL                        │
│     └── Email with order details sent to customer        │
│         │                                                 │
│         ▼                                                 │
│  5. RESERVE STOCK                                        │
│     └── Decrement available inventory                    │
│         │                                                 │
│         ▼                                                 │
│  6. SPLIT ORDER INTO CONSIGNMENTS                        │
│     ├── Group entries by warehouse / delivery mode       │
│     └── Create ConsignmentModels                         │
│         │                                                 │
│         ▼                                                 │
│  7. FULFILLMENT (per consignment)                        │
│     ├── Pick & Pack in warehouse                         │
│     ├── Generate shipping label                          │
│     ├── Hand to carrier                                  │
│     └── Update tracking info                             │
│         │                                                 │
│         ▼                                                 │
│  8. CAPTURE PAYMENT                                      │
│     ├── Charge customer's card (actual debit)            │
│     └── Set paymentStatus = PAID                         │
│         │                                                 │
│         ▼                                                 │
│  9. DELIVER                                              │
│     ├── Carrier delivers to customer                     │
│     └── Delivery confirmed                               │
│         │                                                 │
│         ▼                                                 │
│  10. COMPLETE ORDER                                      │
│      └── Set status = COMPLETED                          │
│                                                           │
│  POST-ORDER:                                             │
│  ├── Customer can request RETURN                         │
│  ├── Customer can CANCEL (if not yet shipped)            │
│  └── Refund processed if applicable                      │
└─────────────────────────────────────────────────────────┘
```

---

## 12. Key Services

```java
// Order
OrderService                    // CRUD for orders
CommerceCheckoutService         // Place order (cart → order)

// Business Process
BusinessProcessService          // Start/trigger processes
ProcessDefinitionFactory        // Load process definitions

// Consignment
ConsignmentService              // Manage consignments
OrderSplittingService           // Split order into consignments

// Return
ReturnService                   // Create/manage returns
RefundService                   // Process refunds

// Payment
PaymentService                  // Authorize, capture, refund payments
```

---

## 13. Summary

| Concept | What It Does |
|---------|-------------|
| **OrderModel** | The placed order (cloned from cart) |
| **OrderStatus** | Tracks order lifecycle (CREATED → COMPLETED) |
| **Business Process** | XML-defined workflow engine |
| **Action** | An automated step in a process |
| **Wait Node** | Pauses process until external event |
| **Event** | Signal that resumes a waiting process |
| **Consignment** | Group of items fulfilled together |
| **Order Splitting** | Divides order into consignments |
| **Return** | Customer return request and refund |
| **Email** | Notification triggered by process actions |

### Where Things Live

```
Process definitions:
  resources/*-process.xml

Process actions:
  src/**/actions/

Email templates:
  resources/*/import/emails/

Spring config:
  resources/*-spring.xml

Your project's customizations:
  NONRBDLMGRefundDeliveryCostStrategy.java  ← refund logic
  email-orderConfirmationBody_en.vm         ← email template
```
