# 13. Promotions & Vouchers

## What Are Promotions in SAP Hybris?

Promotions are rules that offer **discounts, free gifts, or special pricing** to customers based on conditions like cart total, specific products, customer groups, or time periods. Hybris has two promotion systems:

1. **Legacy Promotions** — the older, simpler system (still used in many projects)
2. **Promotion Engine (Rule Engine)** — the modern, powerful system based on Drools

---

## 1. Legacy Promotions System

### How It Works

Legacy promotions use Java classes directly:

```
PromotionGroup
└── AbstractPromotion
    ├── ProductPromotion         ← discounts on products
    │   ├── ProductPercentageDiscountPromotion
    │   ├── ProductFixedPricePromotion
    │   ├── ProductBuyXGetYFreePromotion
    │   └── ProductMultiBuyPromotion
    │
    └── OrderPromotion           ← discounts on entire order
        ├── OrderThresholdDiscountPromotion
        ├── OrderThresholdFreeDeliveryPromotion
        └── OrderThresholdPercentageDiscountPromotion
```

### PromotionGroup

Promotions are organized into groups, and groups are assigned to websites:

```
PromotionGroup: "landmarkPromoGroup"
├── Promotion: "10% off shoes"
├── Promotion: "Free delivery over 200 AED"
└── Promotion: "Buy 2 get 1 free T-shirts"

BaseSite: "landmark-ae"
└── defaultPromotionGroup: → "landmarkPromoGroup"
```

### Legacy Promotion Example: 10% Off Shoes

```impex
INSERT_UPDATE ProductPercentageDiscountPromotion; code[unique=true]; title[lang=en]; percentageDiscount; promotionGroup(identifier)
; shoes_10pct ; 10% Off All Shoes ; 10 ; landmarkPromoGroup

# Assign products to promotion
INSERT_UPDATE PromotionProductRestriction; promotion(code); products(code, catalogVersion(version, catalog(id)))
; shoes_10pct ; SKU-001:Online:landmarkProductCatalog, SKU-002:Online:landmarkProductCatalog
```

### How Legacy Promotions Are Evaluated

```
Cart Calculation starts
       │
       ▼
  PromotionsService.updatePromotions()
       │
       ├── 1. Get all PromotionGroups for this site
       │
       ├── 2. For each promotion in the group:
       │       ├── Check date validity (start/end date)
       │       ├── Check restrictions (products, categories, users)
       │       ├── Evaluate conditions
       │       └── If matches → create PromotionResult
       │
       ├── 3. Sort results by priority
       │
       ├── 4. Apply promotions (create discount entries)
       │
       └── 5. Store PromotionResults on the order
```

### Promotion Results

```
AbstractOrderModel
└── allPromotionResults: List<PromotionResultModel>
    ├── PromotionResultModel
    │   ├── promotion: → AbstractPromotion
    │   ├── certainty: 1.0 (applied) or 0.5 (potential)
    │   ├── actions: [PromotionOrderEntryAdjustAction]
    │   └── consumedEntries: [PromotionOrderEntryConsumed]
    └── ...
```

**Certainty levels:**
- `1.0` = **Fired** — promotion is fully applied
- `0.0 to 0.99` = **Potential** — "Add X more to qualify" messages

---

## 2. Promotion Engine (Rule Engine)

### Why the Rule Engine?

The legacy system has limitations:
- Hard to create complex conditions
- Requires Java code for new promotion types
- Difficult for business users to manage

The **Promotion Engine** uses **Drools** (a rule engine) to make promotions configurable without code.

### Architecture

```
┌─────────────────────────────────────────────┐
│              PROMOTION ENGINE                │
│                                              │
│  ┌────────────┐    ┌──────────────────┐     │
│  │ SOURCE RULE│───►│ DROOLS RULE (.drl)│     │
│  │ (Backoffice│    │ (compiled from   │     │
│  │  or ImpEx) │    │  source rule)    │     │
│  └────────────┘    └────────┬─────────┘     │
│                             │                │
│                    ┌────────▼─────────┐      │
│                    │   RULE ENGINE    │      │
│                    │   (Drools KIE)   │      │
│                    └────────┬─────────┘      │
│                             │                │
│              ┌──────────────┼──────────────┐ │
│              ▼              ▼              ▼ │
│         CONDITIONS      ACTIONS      RESULTS│
│       "cart > 200"   "10% discount"  applied│
│       "has shoes"    "free gift"     or not │
└─────────────────────────────────────────────┘
```

### Key Concepts

#### Source Rules

A **Source Rule** defines a promotion in a business-friendly way:

```
SourceRule
├── code: "order_10pct_over_200"
├── name: "10% off orders over 200 AED"
├── priority: 500
├── status: PUBLISHED
├── startDate: 2026-01-01
├── endDate: 2026-12-31
├── conditions: (JSON-based condition tree)
└── actions: (JSON-based action definitions)
```

#### Conditions (Rule Conditions)

Conditions define **when** a promotion applies:

```json
{
  "definitionId": "y_cart_total",
  "parameters": {
    "value": { "value": 200 },
    "operator": { "value": "GREATER_THAN_OR_EQUAL" }
  }
}
```

**Common Condition Types:**

| Condition | Description |
|-----------|-------------|
| `y_cart_total` | Cart total is above/below a value |
| `y_qualifying_products` | Specific products are in the cart |
| `y_qualifying_categories` | Products from specific categories |
| `y_qualifying_group_types` | Customer belongs to a user group |
| `y_qualifying_coupons` | A coupon code has been applied |
| `y_order_threshold` | Order exceeds a value threshold |
| `y_target_customers` | Specific customer segments |

#### Actions (Rule Actions)

Actions define **what happens** when conditions are met:

```json
{
  "definitionId": "y_order_percentage_discount",
  "parameters": {
    "value": { "value": 10 }
  }
}
```

**Common Action Types:**

| Action | Description |
|--------|-------------|
| `y_order_percentage_discount` | X% off entire order |
| `y_order_fixed_discount` | Fixed amount off order |
| `y_order_entry_percentage_discount` | X% off specific products |
| `y_order_entry_fixed_discount` | Fixed amount off products |
| `y_free_gift` | Add a free product |
| `y_change_delivery_mode` | Free/discounted delivery |
| `y_partner_order_entry_percentage_discount` | Discount on paired products |

### Rule Groups and Exclusivity

Rules are organized into groups that control how they interact:

```
PromotionRuleGroup: "orderPromotions"
├── exclusive: true     ← only best promotion applies
├── Rule: "10% off orders over 200"
├── Rule: "15% off orders over 500"
└── Rule: "20% off orders over 1000"
```

**Exclusivity:**
- `exclusive: true` → Only the **best matching** rule in the group fires
- `exclusive: false` → **All matching** rules fire (can stack discounts)

### Rule Priority

When multiple rules match, **priority** determines the order:

```
Priority 500: "20% off orders over 1000"    ← evaluated first
Priority 400: "15% off orders over 500"
Priority 300: "10% off orders over 200"     ← evaluated last

Higher number = higher priority
```

### Drools Rule Compilation

Source Rules are compiled to Drools `.drl` files:

```
Source Rule (Backoffice UI)
       │
       ▼
  Rule Compiler
       │
       ▼
  Drools DRL (rule definition language)
       │
       ▼
  KIE Module (compiled rule package)
       │
       ▼
  Deployed to Rule Engine
```

### Rule Publishing Lifecycle

```
UNPUBLISHED ──► PUBLISHED ──► [rule is active and evaluated]
     ▲               │
     │               ▼
     └───── EXPIRED (past end date)
```

---

## 3. Vouchers (Coupon Codes)

### What Are Vouchers?

Vouchers are **discount codes** customers enter at checkout. In Hybris, there are two systems:

1. **Legacy Vouchers** — `VoucherModel` based
2. **Coupons (Rule Engine)** — integrated with the Promotion Engine

### Legacy Voucher Types

```
VoucherModel (base)
├── PromotionVoucher              ← single-use or multi-use public code
│   └── LMGPromotionVoucher       ← your custom extension
│
└── SerialVoucher                 ← unique codes generated in bulk
```

### PromotionVoucher

A code that many customers can use:

```
PromotionVoucher
├── code: "SUMMER2026"
├── name: "Summer Sale 2026"
├── value: 50.0                    ← discount amount
├── valueString: "50 AED"
├── freeShipping: false
├── currency: AED
├── voucherCode: "SUMMER2026"     ← what customer enters
├── redemptionQuantityLimit: 1000  ← max total uses
├── redemptionQuantityLimitPerUser: 1  ← max per customer
└── restrictions:
    ├── ProductRestriction
    ├── CategoryRestriction
    ├── UserRestriction
    ├── DateRestriction
    └── OrderRestriction (min order value)
```

### SerialVoucher

Unique codes for each customer (like gift cards):

```
SerialVoucher
├── code: "giftcard_batch_001"
├── generatedCodes:
│   ├── "GC-A1B2-C3D4"    ← each customer gets a unique code
│   ├── "GC-E5F6-G7H8"
│   ├── "GC-I9J0-K1L2"
│   └── ... (generated in batch)
└── value: 100.0
```

### Your Project: LMGPromotionVoucher

Your project extends the voucher system with custom logic:

```java
// LMGPromotionVoucher.java
// Custom voucher with Landmark-specific features:
// - Brand-specific voucher validation
// - Multi-currency support
// - Integration with loyalty programs

// LMGVoucherHelper.java
// Helper class for:
// - Voucher validation rules
// - Applying vouchers to cart
// - Checking restrictions

// DefaultLMGVoucherFacade.java
// Facade for:
// - Apply voucher to cart
// - Remove voucher from cart
// - Check voucher validity
// - Get applied vouchers
```

### Voucher Flow

```
Customer enters code "SUMMER2026"
       │
       ▼
  VoucherFacade.applyVoucher("SUMMER2026")
       │
       ├── 1. Find voucher by code
       │       VoucherService.getVoucher("SUMMER2026")
       │
       ├── 2. Validate
       │       ├── Is voucher active? (date check)
       │       ├── Has usage limit been reached?
       │       ├── Has this user already used it?
       │       ├── Does cart meet minimum order value?
       │       ├── Are restricted products in cart?
       │       └── Is the currency valid?
       │
       ├── 3. Apply to cart
       │       ├── Create discount entry on cart
       │       └── Mark voucher as applied
       │
       ├── 4. Recalculate cart
       │       └── Cart total updated with discount
       │
       └── 5. Return result
               ├── Success → "Voucher applied: 50 AED off"
               └── Failure → "Voucher not valid for this order"
```

### Voucher Redemption

When the order is placed, the voucher is "redeemed":

```
Order placed
    │
    ├── VoucherModel.redemptions += 1
    ├── VoucherInvalidationModel created
    │   ├── voucher: → VoucherModel
    │   ├── order: → OrderModel
    │   ├── user: → UserModel
    │   └── date: now
    │
    └── Voucher cannot be reused by this user
        (if redemptionQuantityLimitPerUser reached)
```

---

## 4. Coupons (Modern — Rule Engine Based)

### How Coupons Work with Rules

In the modern system, coupons are **triggers** for promotion rules:

```
CouponModel
├── couponId: "SAVE20"
├── name: "Save 20%"
├── active: true
├── startDate: ...
├── endDate: ...
└── Used as a CONDITION in a Source Rule
```

### Types of Coupons

```
AbstractCouponModel
├── SingleCodeCouponModel       ← one code, many uses
│   ├── couponId: "WELCOME10"
│   └── maxRedemptionsPerCustomer: 1
│
└── MultiCodeCouponModel        ← many unique codes
    ├── couponId: "VIP-BATCH"
    ├── generatedCodes: [...]
    └── codeGenerationConfiguration: ...
```

### Coupon + Rule Example

```
Source Rule: "20% off with coupon SAVE20"
├── Condition:
│   └── y_qualifying_coupons: couponId = "SAVE20"
│
└── Action:
    └── y_order_percentage_discount: value = 20
```

Flow:

```
Customer enters "SAVE20"
       │
       ▼
  CouponService.redeemCoupon("SAVE20", cart)
       │
       ▼
  Cart recalculation triggers Rule Engine
       │
       ▼
  Rule "20% off with coupon SAVE20" evaluates
       │
       ├── Condition: Is coupon "SAVE20" applied? → YES
       │
       └── Action: Apply 20% discount to order
```

---

## 5. Promotion Evaluation Deep Dive

### When Are Promotions Evaluated?

Promotions are re-evaluated every time the cart is calculated:

```
Events that trigger evaluation:
├── Add item to cart
├── Remove item from cart
├── Change quantity
├── Apply voucher/coupon
├── Change delivery mode
├── Change delivery address
└── Explicit recalculate
```

### Evaluation Order

```
1. Collect all applicable rules (by site, customer group, date)
       │
       ▼
2. Group rules by PromotionRuleGroup
       │
       ▼
3. For each group:
       ├── Evaluate all rules in priority order
       ├── If group is exclusive → keep only the best match
       └── If group is non-exclusive → keep all matches
       │
       ▼
4. Apply all matching rules
       ├── Create discount entries
       ├── Adjust entry prices
       └── Add free gifts if applicable
       │
       ▼
5. Store PromotionResults on the order
       │
       ▼
6. Calculate potential promotions
       └── "Spend 50 more AED to get free delivery!"
```

### Promotion Conflicts

What happens when promotions conflict?

```
Scenario: Customer has items worth 300 AED
├── Rule A: "10% off orders over 200" → saves 30 AED
├── Rule B: "50 AED off orders over 250" → saves 50 AED

If exclusive group → Rule B wins (better for customer)
If non-exclusive → Both apply → saves 80 AED (30 + 50)
```

---

## 6. Promotion Restrictions

### Product Restrictions

Limit a promotion to specific products:

```impex
INSERT_UPDATE PromotionProductRestriction; promotion(code); products(code)
; summer_sale ; SKU-001, SKU-002, SKU-003
```

### Category Restrictions

Limit to products in specific categories:

```impex
INSERT_UPDATE PromotionCategoryRestriction; promotion(code); categories(code)
; shoe_promo ; men-shoes, women-shoes
```

### User Restrictions

Limit to specific customer groups:

```impex
INSERT_UPDATE PromotionUserRestriction; promotion(code); users(uid)
; vip_discount ; vip-customer-group
```

### Date Restrictions

Promotions have start and end dates:

```impex
INSERT_UPDATE SourceRule; code[unique=true]; startDate[dateformat=dd.MM.yyyy]; endDate[dateformat=dd.MM.yyyy]
; flash_sale ; 01.06.2026 ; 03.06.2026
```

---

## 7. ImpEx Examples

### Create a Promotion Rule

```impex
# Create a rule: 10% off orders over 200 AED
INSERT_UPDATE SourceRule; code[unique=true]; name[lang=en]; priority; status(code); website(identifier)
; order_10pct ; 10% Off Orders Over 200 ; 500 ; PUBLISHED ; landmark-ae

# Define conditions (JSON)
UPDATE SourceRule; code[unique=true]; conditions
; order_10pct ; "[{""definitionId"":""y_cart_total"",""parameters"":{""value"":{""value"":200},""operator"":{""value"":""GREATER_THAN_OR_EQUAL""}}}]"

# Define actions (JSON)
UPDATE SourceRule; code[unique=true]; actions
; order_10pct ; "[{""definitionId"":""y_order_percentage_discount"",""parameters"":{""value"":{""value"":10}}}]"
```

### Create a Coupon

```impex
INSERT_UPDATE SingleCodeCoupon; couponId[unique=true]; name[lang=en]; active; maxRedemptionsPerCustomer; maxTotalRedemptions
; WELCOME10 ; Welcome 10% Off ; true ; 1 ; 10000
```

### Create a Legacy Voucher

```impex
INSERT_UPDATE PromotionVoucher; code[unique=true]; name[lang=en]; value; currency(isocode); freeShipping; voucherCode; redemptionQuantityLimit; redemptionQuantityLimitPerUser
; summer50 ; Summer 50 AED Off ; 50 ; AED ; false ; SUMMER50 ; 1000 ; 1
```

---

## 8. Key Services and Facades

```java
// Legacy Promotions
PromotionsService                 // Evaluate and apply promotions
VoucherService                    // Manage vouchers
VoucherModelService               // CRUD for voucher models

// Rule Engine
RuleEngineService                 // Core rule evaluation
PromotionEngineService            // Promotion-specific rule engine
CouponService                     // Manage coupons
SourceRuleService                 // CRUD for source rules
RuleCompilerService               // Compile source rules to Drools

// Your Project
DefaultLMGVoucherFacade           // Custom voucher facade
LMGPromotionVoucher               // Custom voucher model
LMGVoucherHelper                  // Voucher utilities
```

---

## 9. Summary

| Concept | Description |
|---------|-------------|
| **Legacy Promotion** | Simple Java-based promotions (product %, fixed price, buy-X-get-Y) |
| **Promotion Engine** | Drools-based rules with conditions and actions |
| **Source Rule** | Business-friendly rule definition |
| **Condition** | When a rule should fire |
| **Action** | What discount/gift to apply |
| **Rule Group** | Groups rules; exclusive = only best wins |
| **Priority** | Higher number = evaluated first |
| **Voucher** | Discount code (legacy system) |
| **Coupon** | Discount code (rule engine system) |
| **PromotionVoucher** | One code, many users |
| **SerialVoucher** | Unique codes per user |
| **Restriction** | Limits: product, category, user, date |

### Flow Summary

```
┌─────────────────────────────────────────────────┐
│               PROMOTIONS FLOW                    │
│                                                  │
│  Customer action (add to cart, apply code)       │
│         │                                        │
│         ▼                                        │
│  Cart Calculation                                │
│         │                                        │
│         ▼                                        │
│  ┌──────────────┐     ┌──────────────────┐      │
│  │   LEGACY     │ OR  │  RULE ENGINE     │      │
│  │ Promotions   │     │  (Drools)        │      │
│  │ + Vouchers   │     │  + Coupons       │      │
│  └──────┬───────┘     └────────┬─────────┘      │
│         │                      │                 │
│         ▼                      ▼                 │
│  PromotionResults attached to cart               │
│         │                                        │
│         ▼                                        │
│  Discounts applied to entries/order total         │
│         │                                        │
│         ▼                                        │
│  Customer sees updated prices                    │
└─────────────────────────────────────────────────┘
```
