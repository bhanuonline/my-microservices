# 11. Cart & Checkout

## What is Cart & Checkout in SAP Hybris?

Cart & Checkout is the backbone of the e-commerce buying experience. It manages everything from the moment a customer adds a product to their cart until the order is placed. In Hybris, this involves a layered architecture of **models, strategies, services, and facades**.

---

## 1. The Cart Model

### CartModel (extends AbstractOrderModel)

In Hybris, both `CartModel` and `OrderModel` extend `AbstractOrderModel`. This means carts and orders share the same structure — entries, prices, discounts, delivery info, payment info.

```
AbstractOrderModel
├── CartModel          ← active shopping session
└── OrderModel         ← placed/finalized order
```

### Key Fields on CartModel

| Field | Type | Purpose |
|-------|------|---------|
| `entries` | `List<AbstractOrderEntryModel>` | Line items in the cart |
| `totalPrice` | `Double` | Final total after all calculations |
| `totalDiscounts` | `Double` | Sum of all discounts applied |
| `deliveryCost` | `Double` | Shipping cost |
| `deliveryMode` | `DeliveryModeModel` | Selected delivery option |
| `paymentInfo` | `PaymentInfoModel` | Selected payment method |
| `user` | `UserModel` | Who owns this cart |
| `currency` | `CurrencyModel` | Cart currency |
| `site` | `BaseSiteModel` | Which storefront |
| `calculated` | `Boolean` | Whether cart has been calculated |

### CartEntryModel

Each product added to cart creates a `CartEntryModel`:

```
CartEntryModel
├── product        → ProductModel
├── quantity       → Long
├── basePrice      → Double (unit price)
├── totalPrice     → Double (quantity × basePrice - discounts)
├── entryNumber    → Integer (position in cart)
├── deliveryMode   → DeliveryModeModel (entry-level delivery)
├── deliveryPointOfService → PointOfServiceModel (for click-and-collect)
```

---

## 2. Cart Lifecycle

```
Customer browses site
       │
       ▼
  Add to Cart  ──────────────────────────────────────┐
       │                                              │
       ▼                                              │
  Cart Created (or existing cart retrieved)            │
       │                                              │
       ▼                                              │
  Cart Calculation (prices, taxes, delivery, promos)  │
       │                                              │
       ▼                                              │
  Checkout Flow Starts                                │
       │                                              │
       ├── Select Delivery Address                    │
       ├── Select Delivery Mode                       │
       ├── Select Payment Method                      │
       ├── Review Order                               │
       │                                              │
       ▼                                              │
  Place Order ─── CartModel cloned to OrderModel      │
       │                                              │
       ▼                                              │
  Cart Cleared ◄──────────────────────────────────────┘
```

---

## 3. Commerce Cart Service

`CommerceCartService` is the main service that handles cart operations.

### Key Operations

```java
// Add to cart
CommerceCartModification addToCart(CommerceCartParameter parameter);

// Update quantity
CommerceCartModification updateQuantityForCartEntry(CommerceCartParameter parameter);

// Remove entry
CommerceCartModification removeEntryFromCart(CommerceCartParameter parameter);

// Recalculate cart
void recalculateCart(CommerceCartParameter parameter);

// Validate cart before checkout
List<CommerceCartModification> validateCart(CommerceCartParameter parameter);
```

### CommerceCartParameter

This is a parameter object (DTO) that carries all inputs:

```java
CommerceCartParameter param = new CommerceCartParameter();
param.setCart(cartModel);
param.setProduct(productModel);
param.setQuantity(2);
param.setPointOfService(posModel);  // for click-and-collect
param.setCreateNewEntry(false);      // merge with existing entry?
```

### CommerceCartModification (Return Object)

```java
CommerceCartModification result = commerceCartService.addToCart(param);

result.getStatusCode();     // "success", "lowStock", "noStock"
result.getQuantityAdded();  // actual qty added
result.getQuantity();       // requested qty
result.getEntry();          // the cart entry created/modified
```

---

## 4. Cart Calculation Strategy

### What Gets Calculated?

When the cart is "calculated," Hybris computes:

1. **Entry prices** — base price × quantity
2. **Entry discounts** — from promotions
3. **Delivery cost** — based on delivery mode
4. **Payment cost** — if payment mode has a cost
5. **Taxes** — based on tax rules
6. **Order-level discounts** — cart promotions
7. **Total price** — sum of everything

### CommerceCartCalculationStrategy

This is the strategy that orchestrates the entire calculation:

```java
public interface CommerceCartCalculationStrategy {
    boolean calculateCart(CommerceCartParameter parameter);
    boolean recalculateCart(CommerceCartParameter parameter);
}
```

**Default Implementation Flow:**

```
calculateCart()
    │
    ├── 1. CalculationService.calculate(cart)
    │       ├── Calculate each entry's base price (via PriceFactory / Europe1)
    │       ├── Apply entry-level promotions
    │       ├── Calculate delivery cost
    │       ├── Calculate payment cost
    │       └── Calculate taxes
    │
    ├── 2. PromotionEngineService.updatePromotions()
    │       ├── Evaluate all promotion rules
    │       ├── Apply qualifying promotions
    │       └── Recalculate affected entries
    │
    ├── 3. Recalculate if promotions changed anything
    │
    └── 4. Mark cart as calculated = true
```

### Your Project's Custom Strategy

In your codebase, there's a custom strategy:

```
DefaultLMGCommerceCartCalculationStrategy
```

This likely adds Landmark-specific logic such as:
- Custom delivery cost calculations
- VAT handling for different regions (UAE, KSA, etc.)
- Gift card or voucher adjustments
- Multi-brand cart calculations

### How to Customize Cart Calculation

```xml
<!-- In spring config -->
<bean id="commerceCartCalculationStrategy"
      class="com.landmarkshops.core.strategies.impl.DefaultLMGCommerceCartCalculationStrategy"
      parent="defaultCommerceCartCalculationStrategy">
    <property name="calculationService" ref="calculationService"/>
    <property name="promotionEngineService" ref="promotionEngineService"/>
</bean>
```

---

## 5. Delivery Modes

### What is a Delivery Mode?

A delivery mode represents a shipping option — Standard Delivery, Express Delivery, Click & Collect, etc.

### DeliveryModeModel Hierarchy

```
DeliveryModeModel (abstract base)
├── ZoneDeliveryModeModel          ← price varies by delivery zone
│   └── LMGZoneDeliveryMode        ← your custom extension
└── FixedDeliveryModeModel         ← flat rate delivery
```

### ZoneDeliveryModeModel

This is the most common model. Delivery cost depends on the **zone** the customer is in:

```
Zone: "UAE-Dubai"
├── ZoneDeliveryModeValue
│   ├── minimum: 0.0 AED
│   ├── value: 15.0 AED     ← delivery cost
│   └── currency: AED
│
Zone: "KSA-Riyadh"
├── ZoneDeliveryModeValue
│   ├── minimum: 0.0 SAR
│   ├── value: 25.0 SAR
│   └── currency: SAR
```

### How Delivery Cost is Calculated

```
Customer places order
       │
       ▼
  Get customer's delivery address
       │
       ▼
  Determine which Zone the address falls in
       │
       ▼
  Look up ZoneDeliveryModeValue for that zone + delivery mode
       │
       ▼
  Return the delivery cost
```

### Key Service: DeliveryModeService

```java
// Get all supported delivery modes for the cart
Collection<DeliveryModeModel> getSupportedDeliveryModes(CartModel cart);

// Check if a delivery mode is valid for the cart
boolean isDeliveryModeSupported(DeliveryModeModel mode, CartModel cart);
```

### Your Custom: LMGZoneDeliveryMode

Your project extends ZoneDeliveryMode to add:
- Brand-specific delivery rules
- Free delivery thresholds
- Estimated delivery time calculations

```java
// From your codebase
LMGCartDeliveryEstimateService
// Provides estimated delivery dates based on:
// - Warehouse location
// - Customer zone
// - Selected delivery mode
```

---

## 6. Payment Modes

### PaymentModeModel

Represents how a customer pays:

```
PaymentModeModel
├── code: "creditcard"
├── name: "Credit Card"
├── paymentInfoType: CreditCardPaymentInfoModel
└── active: true
```

### Common Payment Modes in E-commerce

| Code | Description |
|------|-------------|
| `creditcard` | Visa, Mastercard, etc. |
| `cashondelivery` | Cash on Delivery (COD) |
| `applepay` | Apple Pay |
| `tabby` | Buy Now Pay Later |
| `giftcard` | Gift card payment |

### PaymentInfoModel

When a customer selects payment, a `PaymentInfoModel` is created:

```
PaymentInfoModel (base)
├── CreditCardPaymentInfoModel
│   ├── cardNumber (masked)
│   ├── cardType (visa, master)
│   ├── expiryMonth / expiryYear
│   └── subscriptionId (tokenized reference)
│
└── Custom models for COD, gift cards, etc.
```

### Payment Flow During Checkout

```
Customer selects payment method
       │
       ▼
  PaymentInfoModel created and set on CartModel
       │
       ▼
  Cart recalculated (payment cost may apply)
       │
       ▼
  Order placed
       │
       ▼
  Payment authorization via PaymentService
       │
       ├── Success → Order confirmed
       └── Failure → Order in error state
```

---

## 7. Checkout Flow

### The Checkout Steps

Hybris checkout follows these steps in order:

```
Step 1: DELIVERY_ADDRESS
  └── Customer selects/enters shipping address

Step 2: DELIVERY_MODE
  └── Customer selects shipping method (standard, express, etc.)

Step 3: PAYMENT_METHOD
  └── Customer enters payment details

Step 4: REVIEW
  └── Customer reviews entire order

Step 5: PLACE_ORDER
  └── Order is created from the cart
```

### AcceleratorCheckoutFacade

This facade manages the checkout flow:

```java
// Set delivery address
boolean setDeliveryAddress(AddressData addressData);

// Set delivery mode
boolean setDeliveryMode(String deliveryModeCode);

// Set payment info
boolean setPaymentDetails(String paymentInfoId);

// Place order
OrderData placeOrder() throws InvalidCartException;

// Check if each step is complete
boolean hasCheckoutCart();
boolean hasDeliveryAddress();
boolean hasDeliveryMode();
boolean hasPaymentInfo();
```

### Place Order — Cart to Order Conversion

When `placeOrder()` is called:

```
CartModel
    │
    ▼
  Validation
    ├── Is cart calculated?
    ├── Are all entries in stock?
    ├── Is delivery address set?
    ├── Is delivery mode set?
    └── Is payment info set?
    │
    ▼
  CommerceCheckoutService.placeOrder()
    │
    ├── 1. Create OrderModel (clone of CartModel)
    ├── 2. Set order status = CREATED
    ├── 3. Generate order code
    ├── 4. Save order
    ├── 5. Start order business process
    ├── 6. Clear the cart
    │
    ▼
  OrderModel returned
```

---

## 8. Cart Validation

Before placing an order, the cart must be validated:

### Types of Validation

| Validation | What It Checks |
|-----------|----------------|
| **Stock** | Is each product still in stock for the requested quantity? |
| **Price** | Have prices changed since the product was added? |
| **Delivery** | Is the delivery mode still valid? |
| **Payment** | Is the payment method valid? |
| **Promotions** | Are applied promotions still valid? |
| **Cart size** | Is the cart not empty? |

### CommerceCartModification Status Codes

```java
"success"               // All good
"lowStock"              // Quantity reduced due to low stock
"noStock"               // Product out of stock, entry removed
"unavailable"           // Product no longer available
"couponNotApplied"      // Voucher code invalid
"potentialPromotions"   // Promotions changed
```

---

## 9. Multi-Cart Support

Hybris supports multiple carts per user:

### Session Cart vs Saved Carts

```
User
├── Session Cart (active, one at a time)
│   └── The cart currently being shopped
│
└── Saved Carts (multiple)
    ├── "My Wishlist Cart"
    ├── "Office Supplies Reorder"
    └── "Birthday Gift List"
```

### SavedCartService

```java
// Save current cart
CommerceSaveCartResult saveCart(CommerceSaveCartParameter parameter);

// Restore a saved cart as the active cart
CommerceSaveCartResult restoreSavedCart(CommerceSaveCartParameter parameter);

// Flag a saved cart to expire
CommerceSaveCartResult flagForDeletion(CommerceSaveCartParameter parameter);
```

---

## 10. Real-World Example: Full Add-to-Cart Flow

```
1. Customer clicks "Add to Cart" on storefront
       │
       ▼
2. Controller receives request
   CartPageController.addToCart(productCode, qty)
       │
       ▼
3. Facade layer
   CartFacade.addToCart(productCode, qty)
       │
       ▼
4. Service layer
   CommerceCartService.addToCart(parameter)
       │
       ├── 4a. Check stock availability
       │       StockService.getStockLevel(product, warehouse)
       │
       ├── 4b. Check if product already in cart
       │       If yes → update quantity
       │       If no  → create new CartEntryModel
       │
       ├── 4c. Calculate cart
       │       CommerceCartCalculationStrategy.calculateCart()
       │           ├── Price each entry
       │           ├── Apply promotions
       │           ├── Calculate delivery
       │           └── Calculate taxes
       │
       └── 4d. Return CommerceCartModification
               ├── statusCode: "success"
               ├── quantityAdded: 2
               └── entry: CartEntryModel
       │
       ▼
5. Response sent to frontend
   CartModificationData (JSON/HTML)
```

---

## 11. Key Spring Beans to Know

```xml
<!-- Cart service -->
<alias name="defaultCommerceCartService" alias="commerceCartService"/>

<!-- Cart calculation -->
<alias name="lmgCommerceCartCalculationStrategy" alias="commerceCartCalculationStrategy"/>

<!-- Checkout facade -->
<alias name="defaultAcceleratorCheckoutFacade" alias="acceleratorCheckoutFacade"/>

<!-- Delivery mode service -->
<alias name="defaultDeliveryModeService" alias="deliveryModeService"/>

<!-- Cart validation hooks -->
<alias name="defaultCommerceCartValidationStrategy" alias="commerceCartValidationStrategy"/>
```

---

## 12. Summary: Key Concepts

| Concept | Hybris Class | Purpose |
|---------|-------------|---------|
| Cart | `CartModel` | Holds items being purchased |
| Cart Entry | `CartEntryModel` | One line item in the cart |
| Cart Service | `CommerceCartService` | Business logic for cart operations |
| Calculation | `CommerceCartCalculationStrategy` | Computes prices, taxes, delivery |
| Delivery Mode | `ZoneDeliveryModeModel` | Shipping option with zone-based pricing |
| Payment Mode | `PaymentModeModel` | Payment method configuration |
| Checkout | `AcceleratorCheckoutFacade` | Multi-step checkout flow |
| Place Order | `CommerceCheckoutService` | Converts cart to order |
| Validation | `CommerceCartValidationStrategy` | Pre-order validation checks |

---

## Quick Reference: Where Things Live in Your Project

```
landmarkshopscore/
├── strategies/impl/DefaultLMGCommerceCartCalculationStrategy.java
├── refund/strategy/impl/NONRBDLMGRefundDeliveryCostStrategy.java
└── ...

landmarkshopsdeliverysystem/
├── service/impl/LMGCartDeliveryEstimateService.java
└── ...

landmarkshopsfacades/
├── order/impl/DefaultLMGVoucherFacade.java
└── ...

landmarkshopsstorefront/
├── web/webroot/WEB-INF/tags/responsive/checkout/
│   └── checkoutBillingPayment.tag
└── ...
```
