# 15. Pricing

## What is the Pricing System in SAP Hybris?

The pricing system determines **how much a product costs**. In Hybris, pricing is handled by the **Europe1 Pricing Engine** — a powerful, flexible system that supports multiple prices per product based on currency, user group, quantity, date, and more.

The name "Europe1" is historical — it's the default pricing strategy in Hybris regardless of region.

---

## 1. Pricing Architecture

### The Big Picture

```
Customer views product
       │
       ▼
  PriceFactory (Europe1)
       │
       ├── Find all matching PriceRows
       ├── Filter by currency, user, date, quantity
       ├── Select the best price
       │
       ▼
  Return final price to customer
```

### Key Components

```
┌──────────────────────────────────────────┐
│            PRICING ENGINE                │
│                                          │
│  ┌──────────────┐   ┌────────────────┐  │
│  │  PriceRow     │   │  TaxRow        │  │
│  │  (base price) │   │  (tax rate)    │  │
│  └──────────────┘   └────────────────┘  │
│                                          │
│  ┌──────────────┐   ┌────────────────┐  │
│  │  DiscountRow  │   │  PriceFactory  │  │
│  │  (discounts)  │   │  (engine)      │  │
│  └──────────────┘   └────────────────┘  │
└──────────────────────────────────────────┘
```

---

## 2. Price Rows

### What is a PriceRow?

A PriceRow defines a **price for a product** under specific conditions. One product can have **many PriceRows** for different scenarios.

### PriceRowModel

```
PriceRowModel
├── product: → ProductModel              ← which product
├── productId: "SKU-001"                 ← or by product code
├── price: 150.00                        ← the price value
├── currency: → CurrencyModel (AED)      ← which currency
├── unit: → UnitModel (pieces)           ← price per what unit
├── unitFactor: 1                        ← multiplier (e.g., per 100g)
├── net: true/false                      ← is this a net price?
├── minqtd: 1                            ← minimum quantity for this price
├── startTime: 2026-01-01               ← valid from
├── endTime: 2026-12-31                  ← valid until
├── user: → UserModel                    ← specific user price
├── userGroup: → UserGroupModel (ug)     ← price for user group
├── catalogVersion: → CatalogVersionModel
├── channel: → PriceRowChannel           ← channel-specific price
└── productMatchQualifier: Long          ← matching priority
```

### Multiple Price Rows Example

A single product can have many prices:

```
Product: "Nike Air Max 90"

PriceRow 1: Standard price
├── price: 450.00 AED
├── currency: AED
├── minqtd: 1
└── userGroup: null (applies to everyone)

PriceRow 2: Bulk price
├── price: 400.00 AED
├── currency: AED
├── minqtd: 5               ← buy 5+, get this price
└── userGroup: null

PriceRow 3: VIP customer price
├── price: 380.00 AED
├── currency: AED
├── minqtd: 1
└── userGroup: vipCustomerGroup

PriceRow 4: KSA price (different currency)
├── price: 170.00 SAR
├── currency: SAR
├── minqtd: 1
└── userGroup: null

PriceRow 5: Wholesale price
├── price: 300.00 AED
├── currency: AED
├── minqtd: 1
└── userGroup: wholesalerGroup
```

---

## 3. The Europe1 Price Factory

### What is the Price Factory?

The `Europe1PriceFactory` is the engine that **resolves prices**. When asked "What's the price of product X?", it:

1. Collects all PriceRows for the product
2. Filters them based on context (currency, user, quantity, date)
3. Returns the best matching price

### Price Resolution Algorithm

```
Input: ProductModel, UserModel, CurrencyModel, Date, Quantity
       │
       ▼
Step 1: Collect all PriceRows for this product
       │
       ▼
Step 2: Filter by Currency
       ├── Keep only rows matching the session currency
       │
       ▼
Step 3: Filter by Date
       ├── Keep only rows where startTime ≤ now ≤ endTime
       │
       ▼
Step 4: Filter by User/UserGroup
       ├── Keep rows where user matches OR userGroup matches
       ├── Also keep rows with no user restriction (global prices)
       │
       ▼
Step 5: Filter by Quantity
       ├── Keep rows where minqtd ≤ requested quantity
       │
       ▼
Step 6: Select Best Match
       ├── User-specific price > UserGroup price > Global price
       ├── Higher minqtd > Lower minqtd (more specific wins)
       │
       ▼
Output: Single best price
```

### Match Quality (Priority System)

The Europe1 engine uses a **match quality** score to determine the best price:

```
Match Quality (higher = more specific = wins)

Level 5: Exact user + exact product          ← most specific
Level 4: User group + exact product
Level 3: Exact user + product group
Level 2: User group + product group
Level 1: Global (no user) + exact product
Level 0: Global + product group              ← least specific
```

### Example Resolution

```
Customer: Ahmed (member of "vipCustomerGroup")
Product: Nike Air Max 90
Currency: AED
Quantity: 3

Available PriceRows:
├── Row 1: 450 AED, any user, minqtd=1     → Match Quality 1
├── Row 2: 400 AED, any user, minqtd=5     → minqtd > 3, EXCLUDED
├── Row 3: 380 AED, vipGroup, minqtd=1     → Match Quality 4
├── Row 4: 170 SAR, any user, minqtd=1     → wrong currency, EXCLUDED
├── Row 5: 300 AED, wholesalerGroup         → wrong group, EXCLUDED

Winner: Row 3 — 380 AED (VIP group price, highest match quality)
```

---

## 4. Price Factory Deep Dive

### Europe1PriceFactory Class Hierarchy

```
PriceFactory (interface)
└── Europe1PriceFactory (default implementation)
    └── Handles:
        ├── getBasePrice()      → product base price
        ├── getDiscounts()      → applicable discounts
        ├── getTaxes()          → applicable tax rates
        └── getPaymentCost()    → payment method cost
```

### Key Methods

```java
public interface PriceFactory {

    // Get the base price for a product
    PriceValue getBasePrice(AbstractOrderEntryModel entry);

    // Get all applicable discounts
    List<DiscountValue> getDiscounts(AbstractOrderEntryModel entry);

    // Get all applicable taxes
    List<TaxValue> getTaxes(AbstractOrderEntryModel entry);

    // Get the delivery cost
    PriceValue getDeliveryCost(AbstractOrderModel order);

    // Get the payment cost
    PriceValue getPaymentCost(AbstractOrderModel order);
}
```

### PriceValue

The returned price object:

```java
PriceValue
├── value: 380.00              ← the price
├── currencyIso: "AED"         ← currency
├── net: true                  ← is this net (excl. tax)?
```

---

## 5. Tax Rows

### What is a TaxRow?

A TaxRow defines the **tax rate** applicable to a product:

```
TaxRowModel
├── product: → ProductModel           ← which product
├── productGroup: → ProductTaxGroup   ← or which product group
├── tax: → TaxModel                   ← tax type (VAT, GST, etc.)
├── value: 5.0                        ← percentage
├── currency: → CurrencyModel         ← currency-specific tax
├── user: → UserModel                 ← user-specific tax
└── userGroup: → UserTaxGroup         ← group-specific tax
```

### Tax Calculation

```
Base Price: 380.00 AED (net)
Tax Rate: 5% VAT
Tax Amount: 19.00 AED
Gross Price: 399.00 AED
```

### Net vs Gross Pricing

| Mode | Description | How Tax Works |
|------|-------------|---------------|
| **Net** | Prices exclude tax | Tax added on top: 380 + 5% = 399 |
| **Gross** | Prices include tax | Tax extracted: 399 includes 19 tax |

This is configured at the **BaseStore** level:

```
BaseStore: "landmark-ae"
└── net: false    ← prices include tax (gross)

BaseStore: "landmark-b2b"
└── net: true     ← prices exclude tax (net, tax added at checkout)
```

### Product Tax Groups

Instead of assigning tax rows to each product individually, you group products:

```
ProductTaxGroup: "fullTax"
├── Products: electronics, clothing, accessories
└── Tax Rate: 5% VAT

ProductTaxGroup: "reducedTax"
├── Products: food, medicine
└── Tax Rate: 0% VAT

ProductTaxGroup: "zeroTax"
├── Products: exports
└── Tax Rate: 0%
```

---

## 6. Discount Rows

### What is a DiscountRow?

A DiscountRow defines **price reductions** outside the promotion system:

```
DiscountRowModel
├── product: → ProductModel
├── productGroup: → ProductDiscountGroup
├── discount: → DiscountModel
├── value: 10.0                     ← percentage or absolute
├── currency: → CurrencyModel
├── user: → UserModel
├── userGroup: → UserDiscountGroup
└── catalogVersion: → CatalogVersionModel
```

### DiscountModel

```
DiscountModel
├── code: "employee_discount"
├── name: "Employee Discount"
├── global: false
├── value: 20.0
├── priority: 1
└── currency: AED
```

### Discounts vs Promotions

| Feature | DiscountRow | Promotion |
|---------|-------------|-----------|
| Complexity | Simple percentage/amount | Complex rules & conditions |
| Conditions | User, product, date | Cart total, combos, coupons |
| Management | ImpEx / Backoffice | Rule Engine / Backoffice |
| Use case | Employee discounts, partner pricing | Marketing campaigns |

---

## 7. Price Groups

### Why Price Groups?

Instead of creating PriceRows for every single product, you can assign products to **groups** and set prices at the group level.

### Types of Groups

#### User Price Group (UPG)

```
UserPriceGroup: "wholesaleGroup"
└── Assigned to: B2B customers

PriceRow:
├── userGroup: wholesaleGroup
├── product: → ProductModel
└── price: 300.00    ← wholesale price
```

#### Product Price Group (PPG)

```
ProductPriceGroup: "premiumProducts"
└── Assigned to: all premium products

PriceRow:
├── productGroup: premiumProducts
└── price: base price for all premium products
```

#### User Tax Group (UTG)

```
UserTaxGroup: "domesticCustomers"
└── Tax rows for domestic customers

UserTaxGroup: "internationalCustomers"
└── Different tax rows for international
```

#### Product Tax Group (PTG)

```
ProductTaxGroup: "standardVAT"
└── 5% VAT

ProductTaxGroup: "zeroRated"
└── 0% VAT
```

### Assigning Groups

```java
// Assign user to a price group
UserModel user;
user.setEurope1PriceFactory_UPG(wholesaleGroup);

// Assign product to a price group
ProductModel product;
product.setEurope1PriceFactory_PPG(premiumProducts);

// Assign product to tax group
product.setEurope1PriceFactory_PTG(standardVATGroup);
```

---

## 8. ImpEx for Pricing

### Create Price Rows

```impex
$catalogVersion = catalogVersion(catalog(id[default='landmarkProductCatalog']),version[default='Online'])[unique=true]

INSERT_UPDATE PriceRow; product(code,$catalogVersion)[unique=true]; price; currency(isocode)[unique=true]; unit(code); minqtd; net; $catalogVersion
; SKU-001 ; 450.00 ; AED ; pieces ; 1 ; false ;
; SKU-001 ; 170.00 ; SAR ; pieces ; 1 ; false ;
; SKU-001 ; 400.00 ; AED ; pieces ; 5 ; false ;   # bulk price
; SKU-002 ; 350.00 ; AED ; pieces ; 1 ; false ;
```

### Create User Group Prices

```impex
INSERT_UPDATE UserPriceGroup; code[unique=true]; name[lang=en]
; vipPriceGroup ; VIP Customer Prices

INSERT_UPDATE PriceRow; product(code,$catalogVersion)[unique=true]; ug(code)[unique=true]; price; currency(isocode)[unique=true]; unit(code); net
; SKU-001 ; vipPriceGroup ; 380.00 ; AED ; pieces ; false
```

### Create Tax Rows

```impex
INSERT_UPDATE Tax; code[unique=true]; name[lang=en]; value
; uae-vat ; UAE VAT ; 5

INSERT_UPDATE ProductTaxGroup; code[unique=true]
; standardTaxGroup

INSERT_UPDATE TaxRow; tax(code)[unique=true]; pg(code)[unique=true]; value; currency(isocode)
; uae-vat ; standardTaxGroup ; 5 ; AED
```

### Assign Tax Group to Products

```impex
UPDATE Product; code[unique=true]; $catalogVersion; Europe1PriceFactory_PTG(code)
; SKU-001 ;; standardTaxGroup
; SKU-002 ;; standardTaxGroup
```

---

## 9. Currency and Price Conversion

### Multi-Currency Support

Hybris supports multiple currencies per site:

```
BaseSite: "landmark-ae"
├── defaultCurrency: AED
└── currencies: [AED, USD]

BaseSite: "landmark-ksa"
├── defaultCurrency: SAR
└── currencies: [SAR, USD]
```

### How Currency Affects Price Resolution

```
Customer session currency: AED
       │
       ▼
  PriceFactory finds PriceRows
       │
       ├── PriceRow 1: 450 AED  ← matches currency ✓
       ├── PriceRow 2: 170 SAR  ← wrong currency ✗
       └── PriceRow 3: 50 USD   ← wrong currency ✗
       │
       ▼
  Result: 450 AED
```

### Currency Conversion

If no PriceRow exists for the session currency, Hybris can convert:

```java
CurrencyModel sourceCurrency = ...; // USD
CurrencyModel targetCurrency = ...; // AED

// Conversion rate defined on CurrencyModel
// AED.conversion = 3.67 (relative to base currency)

double convertedPrice = commonI18NService.convertCurrency(
    sourceCurrency, targetCurrency, 50.00
);
// Result: ~183.50 AED
```

---

## 10. Pricing in the Calculation Flow

### Where Pricing Fits in Cart Calculation

```
Cart Calculation
       │
       ▼
  For each CartEntry:
       │
       ├── 1. PriceFactory.getBasePrice(entry)
       │       └── Europe1 resolves best PriceRow → 380.00 AED
       │
       ├── 2. entry.basePrice = 380.00
       │
       ├── 3. entry.totalPrice = basePrice × quantity
       │       └── 380.00 × 2 = 760.00
       │
       ├── 4. PriceFactory.getDiscounts(entry)
       │       └── Any DiscountRows? → Apply them
       │
       ├── 5. PriceFactory.getTaxes(entry)
       │       └── 5% VAT → 38.00 tax per entry
       │
       └── 6. Promotions evaluated (separate from pricing)
               └── May create additional discounts
       │
       ▼
  Order-level:
       ├── subtotal = sum of entry totalPrices
       ├── deliveryCost = PriceFactory.getDeliveryCost()
       ├── paymentCost = PriceFactory.getPaymentCost()
       ├── totalTax = sum of all taxes
       └── totalPrice = subtotal + delivery + payment + tax - discounts
```

---

## 11. Custom Price Logic

### Overriding the Price Factory

You can customize pricing by extending or replacing the price factory:

```java
public class CustomPriceFactory extends Europe1PriceFactory {

    @Override
    public PriceValue getBasePrice(AbstractOrderEntryModel entry) {
        // Custom logic before standard pricing
        PriceValue standardPrice = super.getBasePrice(entry);

        // Apply custom modifications
        if (isSpecialCustomer(entry.getOrder().getUser())) {
            return applySpecialDiscount(standardPrice);
        }

        return standardPrice;
    }
}
```

### PDT (Price/Discount/Tax) Rows

Europe1 uses a unified system called **PDT** (Price-Discount-Tax):

```
PDT System
├── P = PriceRow     → determines base price
├── D = DiscountRow  → determines discounts
└── T = TaxRow       → determines taxes

All three use the same matching logic:
Product + User + Date + Quantity → Best Match
```

### Product Price Group (PPG) vs Direct Price

```
Direct Price:
  PriceRow → product = SKU-001 → price = 450 AED
  (One row per product, more specific)

Group Price:
  PriceRow → productGroup = "electronics" → price = generic
  (One row for many products, less specific)

Direct always wins over group (higher match quality)
```

---

## 12. Time-Based Pricing

### Date-Restricted Prices

You can have prices that are only valid during certain periods:

```impex
INSERT_UPDATE PriceRow; product(code,$catalogVersion)[unique=true]; price; currency(isocode); startTime[dateformat=dd.MM.yyyy]; endTime[dateformat=dd.MM.yyyy]
; SKU-001 ; 450.00 ; AED ; ;                              # regular price (always valid)
; SKU-001 ; 350.00 ; AED ; 01.06.2026 ; 30.06.2026       # summer sale price
; SKU-001 ; 300.00 ; AED ; 25.11.2026 ; 25.11.2026       # Black Friday price
```

### Resolution with Dates

```
Today: June 15, 2026

PriceRow 1: 450 AED (no date restriction)  ← matches
PriceRow 2: 350 AED (June 1-30)            ← matches (more specific)
PriceRow 3: 300 AED (Nov 25 only)          ← EXCLUDED (outside date range)

Winner: 350 AED (date-restricted prices take priority)
```

---

## 13. Quantity-Based (Tiered) Pricing

### Volume Discounts

```impex
INSERT_UPDATE PriceRow; product(code,$catalogVersion)[unique=true]; price; currency(isocode); minqtd
; SKU-001 ; 450.00 ; AED ; 1      # 1-4 items
; SKU-001 ; 400.00 ; AED ; 5      # 5-9 items
; SKU-001 ; 350.00 ; AED ; 10     # 10-49 items
; SKU-001 ; 300.00 ; AED ; 50     # 50+ items
```

### How It Works

```
Customer orders quantity: 7

PriceRow 1: minqtd=1,  price=450  ← matches (7 ≥ 1)
PriceRow 2: minqtd=5,  price=400  ← matches (7 ≥ 5) ← BEST (highest minqtd that fits)
PriceRow 3: minqtd=10, price=350  ← excluded (7 < 10)
PriceRow 4: minqtd=50, price=300  ← excluded (7 < 50)

Winner: 400 AED per unit
Total: 400 × 7 = 2,800 AED
```

---

## 14. Key Services

```java
// Price resolution
PriceService                     // Get prices for products
Europe1PriceFactory              // The pricing engine

// Tax
TaxService                       // Tax calculations
CommonI18NService                // Currency conversion

// Discount
DiscountService                  // Manage discounts

// Calculation
CalculationService               // Full cart/order calculation
  └── calls PriceFactory for each entry
```

---

## 15. Summary

| Concept | What It Does |
|---------|-------------|
| **PriceRow** | Defines a price for a product under conditions |
| **Europe1 PriceFactory** | Resolves the best price from all PriceRows |
| **Match Quality** | Priority system: user-specific > group > global |
| **TaxRow** | Defines tax rate for product/group |
| **DiscountRow** | Defines price reduction outside promotions |
| **User Price Group** | Price tier for customer segments |
| **Product Price Group** | Price grouping for products |
| **Net/Gross** | Whether prices include or exclude tax |
| **minqtd** | Volume/tiered pricing threshold |
| **Date range** | Time-limited pricing (sales, seasons) |

### Price Resolution Summary

```
┌──────────────────────────────────────────────────────┐
│              EUROPE1 PRICE RESOLUTION                 │
│                                                       │
│  Input: Product + User + Currency + Qty + Date        │
│         │                                             │
│         ▼                                             │
│  ┌─────────────────────────────────────┐             │
│  │     All PriceRows for product       │             │
│  │  ┌─────┐┌─────┐┌─────┐┌─────┐     │             │
│  │  │Row 1││Row 2││Row 3││Row 4│     │             │
│  │  │450  ││400  ││380  ││170  │     │             │
│  │  │AED  ││AED  ││AED  ││SAR  │     │             │
│  │  │any  ││qty≥5││VIP  ││any  │     │             │
│  │  └─────┘└─────┘└─────┘└─────┘     │             │
│  └─────────────────┬───────────────────┘             │
│                    │                                  │
│         Filter by currency (AED)                      │
│         Filter by date (today)                        │
│         Filter by user/group                          │
│         Filter by quantity                            │
│                    │                                  │
│                    ▼                                  │
│           ┌─────────────┐                            │
│           │  Best Match  │                            │
│           │   380 AED    │                            │
│           │  (VIP price) │                            │
│           └─────────────┘                            │
└──────────────────────────────────────────────────────┘
```
