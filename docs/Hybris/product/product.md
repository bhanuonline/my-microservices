Cat product relation
SELECT {p.pk}
FROM {CategoryProductRelation AS rel
JOIN Product AS p ON {rel.target}={p.pk}}
WHERE {rel.source}=?category



$catalogVersion=catalogversion(catalog(id[default=$productCatalog]),version[default='Online'])[unique=true,default=$productCatalog:Online]
$baseProduct=baseProduct(code, catalogVersion(catalog(id[default='$productCatalog']),version[default='Online']))

$categories=source(code, $catalogVersion)[unique=true]
$products=target(code, $catalogVersion)[unique=true]

INSERT_UPDATE CategoryProductRelation;$categories;$products
;HC_CP_UAE_cross_list;166811754

**Assign Category to Product (Java)**
product.setSupercategories(List.of(category));
modelService.save(product);

**price is NOT stored directly on the Product.?**
Pricing is handled through a separate pricing engine using Price Rows.

1️⃣ Key Rule (Very Important 🔥)

Product does NOT have a price attribute
Price is resolved dynamically based on:
Product
User / User Group
Currency
Unit
Date
Quantity

2️⃣ Core Price Model
💰 PriceRow
PriceRow
├── product
├── user / userGroup
├── currency
├── unit
├── minQuantity
├── price
├── net / gross
└── date range

📌 Stored in DB as:
pricerows

4️⃣ Price Resolution Flow (Runtime)
service
PriceService
CommercePriceService
3️⃣ Price Factory Finds Best Price
DefaultPriceService

Checks in order:
Product + User
Product + UserGroup
Product only
Currency
Unit
Date validity
Quantity

Price Resolution Priority (VERY IMPORTANT 🔥)

| Priority | Rule                 |
| -------- | -------------------- |
| 1        | Product + User       |
| 2        | Product + User Group |
| 3        | Product only         |
| 4        | Currency             |
| 5        | Date                 |
| 6        | Min Quantity         |

7️⃣ Price in PDP / PLP
On page load:
ProductFacade → PriceFacade
Calls pricing service
Converts to PriceData

8️⃣ Discounts & Promotions (Separate 🔥)
⚠ Price ≠ Final Payable Amount
Final price =
Base Price
- Promotion Discount
+ Taxes

Handled by:
Promotion Engine
Rule Engine


9️⃣ Net vs Gross Pricing

| Type  | Meaning      |
| ----- | ------------ |
| Net   | Tax excluded |
| Gross | Tax included |

🔟 Common Interview Questions
❓ Can one product have multiple prices?

✔ Yes (currency, user group, quantity)
❓ Where is price stored?
✔ PriceRow table

❓ Is price cached?
✔ Yes (region cache)

❓ Can price change per user?
✔ Yes


production-ready way to load an external price on PDP load.?
PDP Load
→ ProductController
    → ProductFacade
        → PriceFacade
            → Custom Price Strategy
                → External Pricing API

1️⃣ First, Big Picture (with Populator/Converter)
PDP Request
↓
ProductController
↓
ProductFacade
↓
ProductService (ProductModel)
↓
Pricing Service (External / Hybris)
↓
Converter (ProductModel → ProductData)
↓
Populators (price, images, stock, etc.)
↓
ProductData (used by JSP / UI)
👉 Populators & Converters sit between Model and Data (DTO)

2️⃣ What Converter & Populator Do (Quick Recap)
Converter
    Converts Model → Data
    Orchestrates populators
    converter.convert(sourceModel, targetData);

Populator
    Fills specific part of Data
    One responsibility only
    populate(ProductModel source, ProductData target);


7️⃣ When to Use Which Option?

| Scenario         | Best Option               |
| ---------------- | ------------------------- |
| PDP + PLP + Cart | PriceService override     |
| PDP only         | ProductFacade override    |
| Heavy traffic    | Dynamic attribute + cache |





