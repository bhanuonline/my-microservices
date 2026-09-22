# 12. Product Catalog

## What is the Product Catalog in SAP Hybris?

The Product Catalog is the system that organizes, versions, and delivers all product data in Hybris. It handles **what products exist**, **how they're organized** (categories), **what attributes they have** (classification), and **how changes go live** (versioning & sync).

---

# PART A: Discussion Topics (Codebase Deep Dive)

These are the 8 topics we discussed in detail with real Landmark codebase examples.

---

## Discussion 1: Product Data Model

### Product is a ComposedType

In Hybris, Product IS a ComposedType — it is defined as `<itemtype>` in `items.xml`. Every `<itemtype>` in Hybris is a ComposedType in the type system.

### How to Create a Custom Product Type

To **modify** an existing type like Product (add attributes):
- `autocreate="false"` — because the type already exists, you're modifying it
- `generate="false"` — no need to regenerate the Java Model class

To **create** a NEW type extending Product:
- `autocreate="true"` — creating a brand new type
- `generate="true"` — generate a new Java Model class

### Landmark Codebase: Product Modified Directly

**File:** `landmarkshopscore/resources/landmarkshopscore-items.xml` (lines 1591-1723)

```xml
<!-- MODIFYING existing Product — NOT creating a new type -->
<itemtype code="Product" autocreate="false" generate="false">
    <attributes>
        <attribute qualifier="originalProductId" type="java.lang.String">
            <persistence type="property"/>
        </attribute>
        <attribute qualifier="tier" type="java.lang.String">
            <persistence type="property"/>
        </attribute>
        <attribute qualifier="styleNumber" type="java.lang.String">
            <persistence type="property"/>
        </attribute>
        <attribute qualifier="brand" type="java.lang.String">
            <persistence type="property"/>
        </attribute>
        <!-- ... 32 attributes total added directly on Product -->
    </attributes>
</itemtype>
```

**32 custom attributes added directly on Product:**
`originalProductId`, `tier`, `styleNumber`, `concept`, `brand`, `warrantyInfo`, `productType`, `bundleCalculation`, `conceptGroup`, `conceptDepartment`, `conceptClass`, `conceptSubclass`, `brandSerialNumber`, `itemPackWeight`, `pageTitle`, `ranking`, `comingSoon`, `season`, `collectionId`, `sellable`, `conceptDelivery`, `extraProductDetails`, `hit`, `VPN`, `CV`, `modelName`, `originalConcept`, `giftCardFlag`, `sizeGuide`, `originalCode`, `extendedProductType`, `isDigital`

### LMGProduct — The Empty Shell

**File:** `landmarkshopscore-items.xml` (lines 1725-1729)

```xml
<!-- NEW type extending Product — but EMPTY, zero custom attributes -->
<itemtype code="LMGProduct" extends="Product" autocreate="true" generate="true"
          jaloclass="com.landmarkshops.core.jalo.LMGProduct">
</itemtype>
```

LMGProduct exists but has **zero custom attributes**. All 32 business attributes were added directly on Product.

### When to Add on Product vs Custom Type?

| Add on Product directly | Add on Custom Type |
|---|---|
| Attribute is universal — every product in the system needs it | Attribute is specific to a subset of products |
| Example: `brand`, `tier`, `season` — every Landmark product has these | Example: `screenSize` only for electronics |
| Simpler — no casting needed, `product.getBrand()` works everywhere | Requires casting: `((ElectronicsProduct) product).getScreenSize()` |

**Why Landmark chose direct on Product:** Virtually every product IS a Landmark product. Having 32 attributes directly on Product avoids casting everywhere.

### How to Override an Attribute

Use `redeclare="true"` to override an inherited attribute's properties:

```xml
<attribute qualifier="existingAttribute" redeclare="true" type="NewType">
    <persistence type="property"/>
</attribute>
```

This changes the type, modifiers, or default value of an attribute inherited from a parent type.

---

## Discussion 2: Product & Variant Hierarchy

### The Type Hierarchy in the Codebase

```
Product (OOTB Hybris)
├── LMGProduct (empty shell — autocreate="true", generate="true")
├── VariantProduct (OOTB Hybris)
│   ├── LMGColorVariantProduct (extends VariantProduct)
│   └── LMGSizeVariantProduct (extends VariantProduct)
```

### LMGColorVariantProduct

**File:** `landmarkshopscore-items.xml` (lines 1792-1849)

```xml
<itemtype code="LMGColorVariantProduct" extends="VariantProduct" autocreate="true" generate="true">
    <attributes>
        <attribute qualifier="color" type="localized:java.lang.String"/>
        <attribute qualifier="baseColor" type="java.lang.String"/>
        <attribute qualifier="pantone" type="java.lang.String"/>
        <attribute qualifier="shadeImageUrl" type="java.lang.String"/>
        <attribute qualifier="indexed" type="java.lang.Boolean"/>
        <attribute qualifier="showMoreColors" type="java.lang.Boolean"/>
        <attribute qualifier="colorVPN" type="java.lang.String"/>
        <attribute qualifier="colorCV" type="java.lang.String"/>
        <attribute qualifier="size" type="localized:java.lang.String"/>
    </attributes>
</itemtype>
```

**9 attributes:** color, baseColor, pantone, shadeImageUrl, indexed, showMoreColors, colorVPN, colorCV, size

### LMGSizeVariantProduct

**File:** `landmarkshopscore-items.xml` (lines 1975-1999)

```xml
<itemtype code="LMGSizeVariantProduct" extends="VariantProduct" autocreate="true" generate="true">
    <attributes>
        <attribute qualifier="size" type="localized:java.lang.String"/>
        <attribute qualifier="brandSize" type="java.lang.String"/>
        <attribute qualifier="age" type="localized:java.lang.String"/>
    </attributes>
</itemtype>
```

**3 attributes:** size, brandSize, age

### Key Insight: Both Are Siblings, Not Parent-Child

```
VariantProduct
├── LMGColorVariantProduct   ← sibling
└── LMGSizeVariantProduct    ← sibling (NOT child of Color)
```

Both extend VariantProduct directly — they are **independent siblings** in the type system. The parent-child relationship between color and size variants is controlled by **DATA** (the `variantType` attribute), NOT by the type hierarchy.

### How Variant Hierarchy is Data-Driven

```
Product (variantType = LMGColorVariantProduct)
├── LMGColorVariantProduct: "Black" (variantType = LMGSizeVariantProduct)
│   ├── LMGSizeVariantProduct: "S"
│   ├── LMGSizeVariantProduct: "M"
│   └── LMGSizeVariantProduct: "L"
└── LMGColorVariantProduct: "White" (variantType = LMGSizeVariantProduct)
    ├── LMGSizeVariantProduct: "S"
    └── LMGSizeVariantProduct: "M"
```

The `variantType` on the base Product says "my children are Color variants." The `variantType` on each Color variant says "my children are Size variants."

### Can Size Exist Without Color?

YES. The type system allows it because both are independent siblings. You could set `variantType = LMGSizeVariantProduct` directly on a Product, skipping color entirely. But in Landmark's data, the typical pattern is Product → Color → Size (two-level).

### VariantProduct Custom Attribute

**File:** `landmarkshopscore-items.xml` (lines 1782-1790)

```xml
<itemtype code="VariantProduct" autocreate="false" generate="false">
    <attributes>
        <attribute qualifier="variantSortOrder" type="java.lang.Integer">
            <persistence type="property"/>
        </attribute>
    </attributes>
</itemtype>
```

A `variantSortOrder` attribute was added to control display ordering of variants.

### Which Attributes at Which Level?

| Level | Type | What goes here |
|---|---|---|
| Base Product | Product/LMGProduct | brand, name, description, styleNumber, concept — things common to ALL variants |
| Color Variant | LMGColorVariantProduct | color, baseColor, pantone, shadeImageUrl — things that change per color |
| Size Variant | LMGSizeVariantProduct | size, brandSize, age — things that change per size |

**Rule:** An attribute goes at the LOWEST level where its value changes. If it's the same across all colors and sizes, it goes on the base product.

---

## Discussion 3: Categories and Product Relations

### Category is NOT a Single Root

There is no single mandatory root category. Multiple top-level categories can exist independently:

```
Men           ← top-level
Women         ← top-level
Kids          ← top-level
Home          ← top-level
Beauty        ← top-level
```

### Category-Product Relation: Many-to-Many

The built-in `CategoryProductRelation` uses the `supercategories` attribute. It is **many-to-many** — a product can be in multiple categories, and a category can have multiple products.

```
Product: "Unisex Running Shoes"
├── supercategories:
│   ├── men-shoes-sportsshoes
│   ├── women-shoes-sportsshoes
│   └── boys-sportsshoes
```

### Category Custom Attributes in Landmark

**File:** `landmarkshopscore-items.xml` (lines 2071-2180)

```xml
<itemtype code="Category" autocreate="false" generate="false">
    <attributes>
        <attribute qualifier="pageTitle" type="localized:java.lang.String"/>
        <attribute qualifier="warrantyInfo" type="localized:java.lang.String"/>
        <attribute qualifier="displayOrder" type="java.lang.Integer"/>
        <attribute qualifier="imageFormat" type="java.lang.String"/>
        <attribute qualifier="threshold" type="java.lang.Double"/>
        <attribute qualifier="metaDescription" type="localized:java.lang.String"/>
        <attribute qualifier="indexed" type="java.lang.Boolean"/>
        <attribute qualifier="isBrand" type="java.lang.Boolean"/>
        <attribute qualifier="isCMSCategory" type="java.lang.Boolean"/>
        <attribute qualifier="metaTitle" type="localized:java.lang.String"/>
        <attribute qualifier="categoryUrlWithFilters" type="java.lang.Boolean"/>
        <!-- 19 custom attributes total -->
    </attributes>
</itemtype>
```

**19 custom attributes** added to Category — all `autocreate="false"` because Category already exists.

---

## Discussion 4: Catalog Versioning and Sync

### Landmark Catalog Setup

**File:** `landmarkshopsinitialdata/.../ae/2-catalog.impex`

```impex
# ONE product catalog shared across all brands and countries
INSERT_UPDATE Catalog;id[unique=true]
;landmarkProductCatalog

INSERT_UPDATE CatalogVersion;catalog(id)[unique=true];version[unique=true];active;languages(isoCode);readPrincipals(uid)
;landmarkProductCatalog;Staged;false;en,ar;employeegroup
;landmarkProductCatalog;Online;true;en,ar;employeegroup
```

### Architecture: 1 Product Catalog + 49 Content Catalogs

```
landmarkProductCatalog              ← ONE shared product catalog
├── Staged
└── Online

maxaeContentCatalog                 ← Max UAE content catalog
splashaeContentCatalog              ← Splash UAE content catalog
homecentreaeContentCatalog          ← Home Centre UAE content catalog
lifestyleaeContentCatalog           ← Lifestyle UAE content catalog
centrepointaeContentCatalog         ← Centrepoint UAE content catalog
maxsaContentCatalog                 ← Max KSA content catalog
splashsaContentCatalog              ← Splash KSA content catalog
... (49 content catalogs = brand × country combinations)
```

**Key design:** Products live in ONE catalog. CMS content (banners, pages, components) is brand+country specific.

### Sync Process: Staged → Online

Editing happens in **Staged**, publishing to **Online** via `SyncItemJob`:
- `createNewItems: true` — new products in Staged get created in Online
- `removeMissingItems: true` — deleted products in Staged get removed from Online
- Only changed items sync (delta sync)

---

## Discussion 5: Availability & Assignment Groups (Multicountry)

### The Problem: One Catalog, Many Stores

With ONE `landmarkProductCatalog` shared across all countries, how do you control which products are visible in which store?

### The Solution: ProductAvailabilityAssignment

**File:** `multicountry/resources/multicountry-items.xml` (lines 180-307)

```xml
<itemtype code="ProductAvailabilityAssignment" autocreate="true" generate="true">
    <deployment table="prodavailassignment" typecode="25002"/>
    <attributes>
        <attribute qualifier="catalogVersion" type="CatalogVersion"/>
        <attribute qualifier="status" type="ProductAvailabilityStatus"/>
        <attribute qualifier="onlineDate" type="java.util.Date"/>
        <attribute qualifier="offlineDate" type="java.util.Date"/>
        <attribute qualifier="isReturnable" type="java.lang.Boolean"/>
        <attribute qualifier="isClickCollect" type="java.lang.Boolean"/>
        <attribute qualifier="samedayDelivery" type="java.lang.Boolean"/>
        <attribute qualifier="isFragile" type="java.lang.Boolean"/>
        <attribute qualifier="isVendorSourced" type="java.lang.Boolean"/>
        <attribute qualifier="discontinued" type="java.lang.Boolean"/>
        <attribute qualifier="retailLaunchDate" type="java.util.Date"/>
        <!-- ... more country-specific attributes -->
    </attributes>
</itemtype>
```

### ProductAvailabilityGroup

**File:** `multicountry-items.xml` (lines 159-178)

```xml
<itemtype code="ProductAvailabilityGroup" autocreate="true" generate="true">
    <deployment table="prodavailgroup" typecode="25001"/>
    <attributes>
        <attribute qualifier="id" type="java.lang.String">
            <modifiers unique="true"/>
        </attribute>
        <attribute qualifier="description" type="java.lang.String"/>
    </attributes>
</itemtype>
```

### The Three Relations

**File:** `multicountry-items.xml` (lines 21-66)

```xml
<!-- Product → Assignment (1:many) — one product can have multiple country assignments -->
<relation code="Product2ProductAvailabilityAssignmentRel">
    <sourceElement type="Product" qualifier="product" cardinality="one"/>
    <targetElement type="ProductAvailabilityAssignment" qualifier="availabilityAssignments" cardinality="many"/>
</relation>

<!-- Assignment → Group (many:1) — many assignments belong to one group -->
<relation code="ProductAvailabilityAssignment2ProductAvailabilityGroupRel">
    <sourceElement type="ProductAvailabilityAssignment" qualifier="availabilityAssignment" cardinality="many"/>
    <targetElement type="ProductAvailabilityGroup" qualifier="availabilityGroup" cardinality="one"/>
</relation>

<!-- Group → BaseStore (many:many) — groups linked to stores -->
<relation code="ProductAvailabilityGroup2BaseStoreRel">
    <sourceElement type="ProductAvailabilityGroup" qualifier="availabilityGroups" cardinality="many"/>
    <targetElement type="BaseStore" qualifier="baseStores" cardinality="many"/>
</relation>
```

### How It All Connects

```
Product: "Nike Air Max 90"
├── ProductAvailabilityAssignment (UAE)
│   ├── status: APPROVED
│   ├── onlineDate: 2024-01-01
│   ├── offlineDate: null
│   ├── isReturnable: true
│   ├── isClickCollect: true
│   └── availabilityGroup → "UAE-Group"
│                               └── baseStores: [maxae, splashae, centrepointae]
│
├── ProductAvailabilityAssignment (KSA)
│   ├── status: APPROVED
│   ├── onlineDate: 2024-02-01
│   ├── isReturnable: false
│   └── availabilityGroup → "KSA-Group"
│                               └── baseStores: [maxsa, splashsa]
│
└── (NO assignment for Kuwait) → product NOT visible in Kuwait stores
```

### Search Restrictions — Auto-Filtering

**File:** `multicountry/resources/impex/productSearchRestriction/multicountry.impex`

```impex
# Frontend: Only show products with availability for the current store
INSERT_UPDATE SearchRestriction;code[unique=true];name;active;query;restrictedType(code);principal(uid)
;Frontend_ProductBaseStore;Frontend Product BaseStore;true;
 "{item:pk} IN ({{ SELECT {pa:product} FROM {ProductAvailabilityAssignment AS pa
   JOIN ProductAvailabilityGroup AS pag ON {pa:availabilityGroup} = {pag:pk}
   JOIN ProductAvailabilityGroup2BaseStoreRel AS rel ON {pag:pk} = {rel:source}
   JOIN BaseStore AS bs ON {rel:target} = {bs:pk}
   WHERE {bs:uid} = ?session.currentStore }})"
 ;Product;customergroup

# Only show APPROVED assignments
;Frontend_ProductAvailabilityAssignmentApproved;...;true;
 "{item:status} = {{SELECT {pk} FROM {ProductAvailabilityStatus} WHERE {code} = 'APPROVED'}}"
 ;ProductAvailabilityAssignment;customergroup

# Only show assignments within date range
;Frontend_ProductAvailabilityAssignmentOnline;...;true;
 "{item:onlineDate} <= ?session.currentTime AND ({item:offlineDate} IS NULL OR {item:offlineDate} >= ?session.currentTime)"
 ;ProductAvailabilityAssignment;customergroup
```

These search restrictions run **automatically** — any FlexibleSearch query for products is filtered by the session's current store, only returning approved and date-valid products.

---

## Discussion 6: Multicountry Catalog Sync

### The Custom Sync Job

**File:** `multicountry/src/.../MultiCountryCatalogVersionSyncJob.java`

```xml
<!-- In multicountry-items.xml (lines 309-314) -->
<itemtype code="MultiCountryCatalogVersionSyncJob" extends="CatalogVersionSyncJob"
          autocreate="true" generate="true">
</itemtype>
```

### Product Locking During Sync

The sync job overrides the `copy()` method to check product locks:

```java
// Key override in MultiCountryCatalogVersionSyncJob.copy()
if (!(original instanceof Product)) {
    return super.copy(parent, original, copyToUpdate, ts); // non-products always sync
}

// Check if product is locked by any employee
Collection<Employee> lockedBy = MulticountryManager.getInstance().getLockedBy((Product) original);

if (lockedBy.isEmpty()) {
    return super.copy(parent, original, copyToUpdate, ts); // unlocked products sync normally
}

LOG.warn("Product " + product.getCode() + " locked by: " + lockedBy);
return null; // LOCKED products are SKIPPED
```

### Employee-Product Lock Relation

**File:** `multicountry-items.xml` (lines 84-97)

```xml
<relation code="Employee2LockedProductsRel">
    <sourceElement type="Employee" qualifier="lockedBy" cardinality="many"/>
    <targetElement type="Product" qualifier="lockedProducts" cardinality="many"/>
</relation>
```

### Why Locking?

When a country merchandiser is editing a product's availability assignment, they "lock" the product. The sync job respects this lock — it won't overwrite their in-progress changes. Once they save and unlock, the next sync will pick it up.

```
Sync Job Runs
    │
    ├── Product A (unlocked) → syncs normally ✓
    ├── Product B (locked by merchandiser) → SKIPPED ✗
    ├── Category X (not a Product) → syncs normally ✓
    └── Product C (unlocked) → syncs normally ✓
```

---

## Discussion 7: Classification System (Codebase Details)

### Landmark's Classification Catalog

**File:** `landmarkshopsinitialdata/.../ae/2-catalog.impex`

```impex
INSERT_UPDATE ClassificationSystem;id[unique=true]
;landmarkClassificationCatalog

INSERT_UPDATE ClassificationSystemVersion;catalog(id)[unique=true];version[unique=true];active
;landmarkClassificationCatalog;1.0;true
```

### The 11 Classification Classes

**File:** `sampleData/productCatalogs/kw/common/2-Classification.impex`

```impex
INSERT_UPDATE ClassificationClass;code[unique=true];$classificationSysVer[virtual=true,unique=true];allowedPrincipals(uid)[default='customergroup']
;babyandchild_common_classification        ← shared baby+child attrs
;babyandchild_nonapparel_classification    ← baby gear/toys specific
;babyandchild_apparel_classification       ← baby clothing specific
;fashion_common_classification             ← shared fashion attrs
;shoes_classification                      ← shoes specific
;fashion_classification                    ← fashion clothing specific
;fashion_accessories_classification        ← accessories specific
;beauty_classification                     ← beauty/fragrance specific
;home_common_classification                ← shared home attrs
;home_household_classification             ← household items specific
;home_furniture_classification             ← furniture specific
```

### The "Common + Specific" Pattern

Each business domain has a **common** class (shared attributes) plus **specific** classes:

```
Fashion:
├── fashion_common_classification     ← fit, style, wash, material, modelStats (ALL fashion)
├── fashion_classification            ← backDetail, shape, waistDetail (clothing only)
└── fashion_accessories_classification ← tieClosure, frameColour, lensFinish (accessories only)

Home:
├── home_common_classification        ← primaryMaterial, dimensions, weight (ALL home)
├── home_furniture_classification     ← seatingCapacity, mattressSize, woodType (furniture only)
└── home_household_classification     ← threadCount, bulbType, capacityL (household only)

Baby & Child:
├── babyandchild_common_classification     ← type, material, ageGroup, character (ALL baby)
├── babyandchild_apparel_classification    ← sleeveLength, neckline, pattern (clothing only)
└── babyandchild_nonapparel_classification ← safety, wheels, foldability (gear/toys only)
```

### ~200+ Classification Attributes

**File:** `sampleData/productCatalogs/kw/common/2-Classification.impex`

Sample attributes per class:

| Class | Sample Attributes |
|---|---|
| `shoes_classification` | heelHeight, heelHeightCm, shaftHeightCm, soleMaterial, upperMaterial, comfortDetail |
| `fashion_common_classification` | fit, style, wash, material, careInformation, modelStats, modelWears, sleeveLength, neckline |
| `fashion_accessories_classification` | tieClosure, buckleClosure, frameColour, lensColour, lensFinish, lensMaterial, ringSize |
| `beauty_classification` | fragranceFamily, fragranceName, skinType, sizeMl, sizeG, gender |
| `home_furniture_classification` | seatingCapacity, mattressSize, sofaBedSleepingAreaWidthCM, woodType, trundle, adjustableShelves |
| `home_household_classification` | threadCount, bulbType, capacityL, fragile, fillingWeightGSM, burnTime |
| `babyandchild_nonapparel_classification` | safetyHarnessPoints, recliningPositions, foldability, wheelLock, sterilePeriodHrs, batteryType |

### ClassAttributeAssignment — Linking Attributes to Classes

```impex
INSERT_UPDATE ClassAttributeAssignment;$class[unique=true];$attribute[unique=true];attributeType(code[default=string]);searchable[default=true];listable[default=true];comparable[default=true];multiValued[default=false]
;shoes_classification;heelHeight;string
;shoes_classification;soleMaterial;string
;shoes_classification;upperMaterial;string
;fashion_common_classification;fit;string
;fashion_common_classification;style;string
;fashion_common_classification;wash;string
;beauty_classification;fragranceFamily;string
;beauty_classification;skinType;string
```

All attributes typed as `string`, all `searchable=true`, `listable=true`, `comparable=true`.

### CategoryCategoryRelation — Mapping Product Categories to Classification Classes

**File:** `sampleData/productCatalogs/kw/common/4-ClassificationMapping.impex`

```impex
INSERT_UPDATE CategoryCategoryRelation;source(code,...)[unique=true];target(code,...)[unique=true]
;babyandchild_apparel_classification;boys-clothing-bottoms
;babyandchild_apparel_classification;girls-shoes-sandals
;babyandchild_nonapparel_classification;babygear-carseats
;babyandchild_nonapparel_classification;toys-educationaltoys-books
;shoes_classification;men-shoes-formalshoes
;shoes_classification;women-shoes-heels
;fashion_classification;men-regular-tops-tshirts
;fashion_common_classification;men-regular-tops-tshirts    ← SAME category, MULTIPLE classes!
;beauty_classification;women-fragrance-eaudeperfume
;home_furniture_classification;bedroom-beds
;home_household_classification;kitchen-cookware-bakeware
```

**Key insight:** A single product category like `men-regular-tops-tshirts` is linked to BOTH `fashion_classification` AND `fashion_common_classification`. This means a t-shirt inherits attributes from BOTH classes — `backDetail`, `shape`, `waistDetail` from fashion_classification AND `fit`, `style`, `wash`, `material` from fashion_common_classification.

### Setting Feature Values on Products

**File:** `sampleData/productCatalogs/kw/max/9-attribute.impex`

```impex
$clAttrModifiers=system='landmarkClassificationCatalog',version='1.0',translator=de.hybris.platform.catalog.jalo.classification.impex.ClassificationAttributeTranslator

$feature1=@neckline[$clAttrModifiers];
$feature2=@fit[$clAttrModifiers];
$feature3=@material[$clAttrModifiers];
$feature4=@wash[$clAttrModifiers];
$feature7=@type[$clAttrModifiers];
$feature8=@careInformation[$clAttrModifiers];

UPDATE LMGProduct;code[unique=true];$catalogVersion;$feature1;$feature2;$feature3;$feature4;...;$feature7;$feature8
;SP16CW16CP-MXSP16;;Toddlers;;Cotton polyester Spandex blend;;;;Pants;Machine wash
;FX0302M-MXSP16;;Toddlers;;Cotton;;;Short sleeves;Sets;Machine wash
;SU16SD2298-MXSU16;;New borns;;Cotton;;;Sleeveless;Dresses;Machine wash
```

The `@attributeCode` syntax with `ClassificationAttributeTranslator` is the special impex syntax for setting classification feature values. Behind the scenes, Hybris creates `ProductFeatureModel` records.

### How Products Connect to Classification (Indirect)

```
Products do NOT directly connect to classification classes.
Products belong to Categories.
Categories are linked to ClassificationClasses via CategoryCategoryRelation.
So any product in "men-shoes-boots" automatically inherits
the attributes defined in "shoes_classification".
```

```
ClassificationClass: shoes_classification
    ├── heelHeight
    ├── soleMaterial
    └── upperMaterial
         │
    CategoryCategoryRelation
         │
    Category: men-shoes-boots (in landmarkProductCatalog)
         │
    Product: "BOOT-001" (in this category)
         └── ProductFeature: heelHeight = "3cm"
         └── ProductFeature: soleMaterial = "Rubber"
```

### Custom Extension: ClassAttributeGroup

**File:** `landmarkshopscore-items.xml` (lines 10057-10088)

```xml
<!-- Adding a 'group' attribute to ClassificationAttribute -->
<itemtype code="ClassificationAttribute" generate="false" autocreate="false">
    <attributes>
        <attribute qualifier="group" type="ClassAttributeGroup">
            <persistence type="property"/>
        </attribute>
    </attributes>
</itemtype>

<!-- NEW type for grouping classification attributes on PDP -->
<itemtype code="ClassAttributeGroup" generate="true" autocreate="true">
    <deployment table="ClassAttributeGroup" typecode="16119"/>
    <attributes>
        <attribute qualifier="code" type="java.lang.String">
            <modifiers unique="true" optional="false" initial="true" write="false"/>
        </attribute>
        <attribute qualifier="name" type="localized:java.lang.String"/>
        <attribute qualifier="style" type="ClassAttributeGroupStyle">
            <defaultvalue>em().getEnumerationValue("ClassAttributeGroupStyle","TABLE")</defaultvalue>
        </attribute>
        <attribute qualifier="groupPosition" type="java.lang.Integer"/>
    </attributes>
</itemtype>
```

**Purpose:** On the PDP (product detail page), classification attributes are displayed in **groups** instead of a flat list. Each group can be styled differently and ordered via `groupPosition`.

```
Group: "Material & Construction" (position: 1, style: TABLE)
├── upperMaterial: Leather
├── soleMaterial: Rubber
└── liningMaterial: Textile

Group: "Fit & Comfort" (position: 2, style: TABLE)
├── heelHeightCm: 3
├── comfortDetail: Padded insole
└── closure: Lace-up
```

### Classification vs Direct Attributes — When to Use Which?

| Direct Attribute (items.xml) | Classification Attribute |
|---|---|
| Fixed at build time — requires system update | Dynamic — add/remove via impex at runtime |
| Same attributes on ALL products | Different attributes per category |
| Requires code change + build + deploy | Business users manage via Backoffice |
| Good for universal fields (code, name, brand) | Good for specs that vary by product type |

---

## Discussion 8: Pricing — How Products Get Their Price

### No `price` Attribute on ProductModel

There is **NO** `price` field on ProductModel. This is a deliberate design. Price is stored as a **separate record** called `PriceRow`.

### Why? Because One Product Has MANY Prices

```
Product: "Nike Air Max 90" (size 42, black)
│
├── PriceRow: 299 AED  (currency=AED, group=pricegroup-ae)  ← UAE price
├── PriceRow: 299 SAR  (currency=SAR, group=pricegroup-sa)  ← KSA price
├── PriceRow: 59.46 GBP (currency=GBP, group=pricegroup-uk) ← UK price
├── PriceRow: 250 AED  (currency=AED, minqtd=5)             ← bulk discount
└── PriceRow: 199 AED  (currency=AED, startTime=Jan, endTime=Feb) ← sale price
```

A flat `price` attribute could only hold ONE value. But the real world needs prices per currency, per country, per customer group, per quantity, per time period.

### PriceRowModel — The Actual Price Record

```
PriceRowModel (SEPARATE item — not on Product)
├── product → ProductModel         ← which product
├── price: 29.99                   ← the actual price value
├── currency → CurrencyModel (AED) ← which currency
├── unit → UnitModel (pieces)      ← per what unit
├── ug → UserPriceGroup            ← for which customer/store group
├── catalogVersion → CatalogVersion ← staged/online aware
├── net: true/false                ← net or gross price?
├── startTime / endTime            ← optional time-limited pricing
└── minqtd: 1                      ← minimum quantity for this price
```

### Europe1 Pricing System

Hybris uses a system called **Europe1** (named after the internal project). The `Europe1PriceFactory` is the engine that resolves the correct price at runtime.

### How Prices Are Imported — Two Approaches in the Codebase

**Approach 1: Inline with `europe1prices` translator**

**File:** `sampleData/productCatalogs/ae/max/6-size-variant.impex`

```impex
$prices=europe1prices[translator=de.hybris.platform.europe1.jalo.impex.Europe1PricesTranslator]

INSERT_UPDATE LMGSizeVariantProduct;code[unique=true];$prices;...
;158022526;1 pieces = 29.99 AED N;...
```

The `Europe1PricesTranslator` parses the shorthand and creates PriceRow records.

**Approach 2: Direct PriceRow insertion**

**File:** `multicountry/resources/impex/apparelMultiCountryProductCatalog/pricerows/product-prices_gb.impex`

```impex
INSERT_UPDATE PriceRow;product(code,$catalogVersion)[unique=true];currency(isocode)[unique=true];price;ug(code)[unique=true];$catalogVersion;unit(code)[default=pieces]
;300441142;GBP;8,46;pricegroup-uk;
;29531;GBP;21,21;pricegroup-uk;
;300047513;GBP;67,96;pricegroup-uk;
```

Each row explicitly creates a PriceRow: product + currency + price + **UserPriceGroup**.

### UserPriceGroup — Key for Multicountry Pricing

The `ug` field is the `UserPriceGroup`. Each BaseStore is configured with a UserPriceGroup. When a customer visits a store, the session picks up that group, and Europe1 only returns matching PriceRows.

```
pricegroup-ae  → UAE prices in AED
pricegroup-sa  → KSA prices in SAR
pricegroup-uk  → UK prices in GBP
```

### Price Resolution at Runtime

```
Customer views product on storefront
       │
       ▼
  PriceService.getProductPrice(product)
       │
       ▼
  Europe1 PriceFactory:
       ├── 1. Session currency? (AED)
       ├── 2. User's price group? (pricegroup-ae)
       ├── 3. Quantity? (1)
       ├── 4. Current date? (for time-limited prices)
       │
       ▼
  Filter matching PriceRows → select BEST match → return 299 AED
```

### How Code Reads Price — Landmark's Custom Service

**File:** `landmarkshopscore/src/.../price/service/impl/DefaultLMGCommercePriceService.java`

```java
public class DefaultLMGCommercePriceService extends DefaultCommercePriceService
        implements LMGCommercePriceService {

    // Find the lowest-priced variant for display
    protected void getPriceInformationOfProduct(ProductModel product, Map<ProductModel, Double> productPriceMap) {
        // product.getEurope1Prices() — the built-in collection of all PriceRows
        for (final PriceRowModel priceRow : product.getEurope1Prices()) {
            CurrencyModel defaultCurrency = baseStoreService.getCurrentBaseStore().getDefaultCurrency();
            if (priceRow.getCurrency().equals(defaultCurrency) && CommonUtil.isValidCurrency(priceRow)) {
                productPriceMap.put(product, priceRow.getPrice());
                break;
            }
        }
        // Recursively check variants too
        Collection<VariantProductModel> variants = product.getVariants();
        if (CollectionUtils.isNotEmpty(variants)) {
            for (VariantProductModel variant : variants) {
                getPriceInformationOfProduct(variant, productPriceMap);
            }
        }
    }
}
```

**Key:** `product.getEurope1Prices()` returns ALL PriceRows for the product. The code filters by current store's currency.

### Region-Specific Price Strategy

**File:** `landmarkshopscore/src/.../price/strategies/factory/LMGPriceStrategyFactory.java`

```java
public class LMGPriceStrategyFactory {
    public static LMGPriceStrategy getPriceStrategy(String priceStrategyKey) {
        // Looks up a Map<String, LMGPriceStrategy> bean called "priceStrategies"
        // Key = baseStore UID (lowercase)
        return ((Map<String, LMGPriceStrategy>) priceStrategies).get(priceStrategyKey.toLowerCase());
    }
}
```

Two implementations:
- `GCCLMGPriceStrategy` — for Gulf stores (AE, SA, KW, BH, OM, QA)
- `INLMGPriceStrategy` — for India stores

Different regions have different tax rules (VAT vs GST), promotion structures, and price display logic.

### Price Access Summary

| What you might think | How it actually works |
|---|---|
| `product.getPrice()` | Does NOT exist |
| `product.getEurope1Prices()` | Returns ALL PriceRows for the product |
| `priceService.getProductPrice(product)` | Returns the BEST matching PriceRow for session context |
| `commercePriceService.getWebPriceForProduct(product)` | Returns PriceInformation with the display price |

### Full Price Architecture

```
                    ┌────────────────────┐
                    │    ProductModel     │
                    │   (NO price field)  │
                    └────────┬───────────┘
                             │
                    europe1Prices (collection)
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ PriceRow │  │ PriceRow │  │ PriceRow │
        │ 299 AED  │  │ 299 SAR  │  │ 59 GBP   │
        │ group-ae │  │ group-sa │  │ group-uk │
        └──────────┘  └──────────┘  └──────────┘

    At runtime, Europe1 picks the RIGHT PriceRow based on:
    → session currency
    → user's price group (from BaseStore)
    → quantity
    → date range
```

---

# PART B: Reference Documentation

The sections below are the original reference documentation for Product Catalog concepts.

---

## 1. Catalog Architecture

### The Big Picture

```
CatalogModel
├── CatalogVersionModel: "Staged"     ← editing/draft version
│   ├── Products
│   ├── Categories
│   ├── Media (images)
│   └── CMS content
│
└── CatalogVersionModel: "Online"     ← live/published version
    ├── Products
    ├── Categories
    ├── Media
    └── CMS content
```

### Why Two Versions?

This is the **Staged/Online pattern** — the core concept of Hybris catalogs:

| Version | Purpose | Who Uses It |
|---------|---------|-------------|
| **Staged** | Draft workspace. Changes are made here first. | Business users, product managers |
| **Online** | Live data shown to customers. Read-only in practice. | Storefront, customers |

Think of it like a **publishing workflow**:
- Edit in Staged (draft)
- Review changes
- **Synchronize** (publish) Staged → Online
- Customers see updated data

---

## 2. Catalog Model

### CatalogModel

```
CatalogModel
├── id: "landmarkProductCatalog"
├── name: "Landmark Product Catalog"
├── catalogVersions:
│   ├── CatalogVersionModel ("Staged")
│   └── CatalogVersionModel ("Online")
└── defaultCatalog: true
```

### CatalogVersionModel

```
CatalogVersionModel
├── catalog: → CatalogModel
├── version: "Staged" or "Online"
├── active: true/false
├── languages: [en, ar]               ← supported languages
├── rootCategories: [...]             ← top-level categories
├── generationDate: timestamp         ← when last synced
└── generatorInfo: "Full sync"        ← sync details
```

### Multiple Catalogs

A typical Hybris project has several catalogs:

```
landmarkProductCatalog          ← products (the main one)
├── Staged
└── Online

landmarkContentCatalog          ← CMS pages, banners, components
├── Staged
└── Online

classificationSystemCatalog     ← product attributes/specs
├── 1.0
└── 2.0
```

---

## 3. Products

### ProductModel

The central model for any sellable item:

```
ProductModel
├── code: "SKU-12345"                    ← unique identifier
├── name: "Nike Air Max 90"             ← localized name
├── description: "..."                  ← localized description
├── ean: "1234567890123"                ← barcode
├── catalogVersion: → Staged/Online
├── supercategories: [Shoes, Nike]      ← categories it belongs to
├── approvalStatus: APPROVED            ← check, approved, unapproved
├── unit: → UnitModel (pieces, kg...)
├── thumbnail: → MediaModel
├── picture: → MediaModel
├── galleryImages: [MediaModel...]
├── variantType: → VariantTypeModel     ← if this is a base product
└── variants: [VariantProductModel...]  ← its variant children
```

### Product Variants

Variants represent different options of the same product (size, color, etc.):

```
Base Product: "Nike Air Max 90"
├── Variant: "Nike Air Max 90 - Black - Size 42"
├── Variant: "Nike Air Max 90 - Black - Size 43"
├── Variant: "Nike Air Max 90 - White - Size 42"
└── Variant: "Nike Air Max 90 - White - Size 43"
```

**Model Hierarchy:**

```
ProductModel (base)
└── VariantProductModel (extends ProductModel)
    ├── baseProduct: → ProductModel (parent)
    └── Additional variant attributes (size, color, etc.)
```

### Multi-Level Variants

Hybris supports nested variants:

```
GenericProduct: "Nike Air Max 90"           ← Level 0 (style)
├── StyleVariant: "Nike Air Max 90 - Black" ← Level 1 (color)
│   ├── SizeVariant: "Black - Size 42"      ← Level 2 (size)
│   └── SizeVariant: "Black - Size 43"
└── StyleVariant: "Nike Air Max 90 - White"
    ├── SizeVariant: "White - Size 42"
    └── SizeVariant: "White - Size 43"
```

---

## 4. Categories

### What Are Categories?

Categories organize products into a browsable hierarchy — like folders for products.

### CategoryModel

```
CategoryModel
├── code: "shoes"
├── name: "Shoes"                         ← localized
├── description: "All footwear"
├── catalogVersion: → CatalogVersionModel
├── supercategories: [CategoryModel...]   ← parent categories
├── categories: [CategoryModel...]        ← child subcategories
├── products: [ProductModel...]           ← products in this category
└── allowedPrincipals: [...]              ← visibility restrictions
```

### Category Hierarchy Example

```
Root
├── Men
│   ├── Clothing
│   │   ├── T-Shirts
│   │   ├── Jeans
│   │   └── Jackets
│   ├── Shoes
│   │   ├── Sneakers
│   │   └── Formal
│   └── Accessories
│       ├── Watches
│       └── Bags
│
├── Women
│   ├── Clothing
│   ├── Shoes
│   └── Accessories
│
└── Kids
    ├── Boys
    └── Girls
```

### Products in Multiple Categories

A product can belong to **multiple categories** simultaneously:

```
Product: "Unisex Running Shoes"
├── supercategories:
│   ├── Men > Shoes > Sneakers
│   ├── Women > Shoes > Sneakers
│   └── Sports > Running
```

### Category Types

| Type | Purpose |
|------|---------|
| `CategoryModel` | Standard browsing category |
| `ClassificationClassModel` | For classification attributes (extends Category) |
| `BrandCategoryModel` | Brand-specific category |

---

## 5. Catalog Versioning (Staged → Online)

### The Sync Process

Synchronization copies data from Staged to Online:

```
     STAGED                          ONLINE
┌─────────────────┐   Sync Job   ┌─────────────────┐
│ Product A (v2)  │ ──────────► │ Product A (v2)  │
│ Product B (new) │ ──────────► │ Product B (new) │
│ Product C (v1)  │  no change  │ Product C (v1)  │
│ Category X (v3) │ ──────────► │ Category X (v3) │
└─────────────────┘              └─────────────────┘
```

### SyncItemJob

This is the CronJob that performs synchronization:

```
SyncItemJob
├── sourceVersion: "Staged"          ← copy FROM here
├── targetVersion: "Online"          ← copy TO here
├── syncPrincipals: [admin]          ← who can run it
├── createNewItems: true             ← create new items in target?
├── removeMissingItems: true         ← delete items not in source?
├── syncOrder: 0                     ← execution order
└── syncLanguages: [en, ar]          ← which languages to sync
```

### What Gets Synced?

The sync process handles:

```
Products          ← product data, prices, stock
Categories        ← hierarchy, assignments
Media             ← images, documents
CMS Components    ← banners, paragraphs
CMS Pages         ← content pages
Keywords          ← search keywords
```

### Sync Modes

| Mode | Description |
|------|-------------|
| **Full Sync** | Syncs everything — slow but thorough |
| **Partial Sync** | Only sync items modified since last sync |
| **Selective Sync** | Sync specific items you select |

### Sync Status

Each item has a sync status:

```
COUNTERPART_MISSING    ← exists in Staged but not yet in Online
IN_SYNC                ← same version in both
NOT_SYNC               ← changed in Staged, not yet synced to Online
```

### Running a Sync (Backoffice / HAC)

```
Backoffice → System → Catalog Synchronization
  → Select "landmarkProductCatalog: Staged → Online"
  → Click "Synchronize"
  → Monitor progress
```

Or via ImpEx:

```impex
$START_USERRIGHTS
Type;UID;MemberOfGroups;Password
UserGroup;productmanagergroup;;;
$END_USERRIGHTS

# Trigger sync
INSERT_UPDATE CatalogVersionSyncJob; code[unique=true]; sourceVersion; targetVersion
; landmarkProductSync ; landmarkProductCatalog:Staged ; landmarkProductCatalog:Online
```

---

## 6. Classification System

### What is Classification?

Classification is how you add **structured technical attributes** to products — attributes that vary by product type.

Example: A TV and a shirt are both products, but a TV needs "screen size" and "resolution" while a shirt needs "fabric" and "collar type."

### Classification Architecture

```
ClassificationSystem (a special Catalog)
└── ClassificationSystemVersion
    └── ClassificationClass (a special Category)
        └── ClassificationAttribute
            └── ClassificationAttributeValue
```

### Step by Step

**Step 1: Classification System** (the container)

```
ClassificationSystem: "LandmarkClassification"
└── Version: "1.0"
```

**Step 2: Classification Classes** (like product type templates)

```
ClassificationClass: "Electronics"
├── ClassificationAttribute: "Screen Size"
├── ClassificationAttribute: "Resolution"
└── ClassificationAttribute: "Battery Life"

ClassificationClass: "Apparel"
├── ClassificationAttribute: "Fabric"
├── ClassificationAttribute: "Size"
└── ClassificationAttribute: "Color"
```

**Step 3: Classification Attributes** (the actual specs)

```
ClassificationAttribute: "Screen Size"
├── code: "screenSize"
├── name: "Screen Size"
├── systemVersion: → ClassificationSystemVersion
├── attributeType: NUMBER
├── unit: → ClassificationAttributeUnit (inches)
└── defaultValue: null
```

**Step 4: Assign to Products**

```
Product: "Samsung 55-inch TV"
├── classificationClasses: [Electronics]
└── featureValues:
    ├── Screen Size = 55 inches
    ├── Resolution = "4K UHD"
    └── Battery Life = N/A
```

### Classification Attribute Types

| Type | Example | Values |
|------|---------|--------|
| `string` | Brand Name | Free text |
| `number` | Screen Size | Numeric with unit |
| `boolean` | Waterproof | true/false |
| `enum` | Color | Predefined list |
| `date` | Release Date | Date value |
| `reference` | Compatible Products | References to other items |

### Feature Values

`ProductFeatureModel` stores the actual values:

```
ProductFeatureModel
├── product: → ProductModel
├── classificationAttributeAssignment: → assignment
├── value: "55"
├── unit: → ClassificationAttributeUnit ("inches")
├── valuePosition: 0
└── qualifier: "landmarkClassification/1.0/electronics.screensize"
```

### Classification Qualifier Pattern

The unique path to any classification value:

```
{systemId}/{version}/{classCode}.{attributeCode}

Example: landmarkClassification/1.0/electronics.screensize
```

---

## 7. Catalog-Aware Items

### What Does "Catalog-Aware" Mean?

In Hybris, certain item types are tied to a catalog version. This means the **same code** can exist in both Staged and Online:

```
Product code: "SKU-001"
├── In Staged catalog → one ItemModel instance
└── In Online catalog → another ItemModel instance (possibly different data)
```

### Catalog-Aware Types

| Type | Catalog-Aware? |
|------|---------------|
| `ProductModel` | Yes |
| `CategoryModel` | Yes |
| `MediaModel` | Yes |
| `CMSComponentModel` | Yes |
| `KeywordModel` | Yes |
| `UserModel` | No (global) |
| `OrderModel` | No (global) |
| `AddressModel` | No (global) |

### Session Catalog Version

When you access catalog-aware data, Hybris needs to know **which version** to read:

```java
// Set the session catalog version
catalogVersionService.setSessionCatalogVersion("landmarkProductCatalog", "Online");

// Now any product query will return Online versions
ProductModel product = productService.getProductForCode("SKU-001");
// → returns the Online version of SKU-001
```

### Storefront Default

The storefront automatically sets the session to **Online** catalog:
- Customers see Online data
- Backoffice users can switch between Staged and Online

---

## 8. ImpEx for Catalog Setup

### Create a Catalog

```impex
INSERT_UPDATE Catalog; id[unique=true]; name[lang=en]
; landmarkProductCatalog ; Landmark Product Catalog

INSERT_UPDATE CatalogVersion; catalog(id)[unique=true]; version[unique=true]; active; languages(isocode)
; landmarkProductCatalog ; Staged ; false ; en,ar
; landmarkProductCatalog ; Online ; true  ; en,ar
```

### Create Categories

```impex
$catalogVersion = catalogVersion(catalog(id[default='landmarkProductCatalog']),version[default='Staged'])[unique=true]

INSERT_UPDATE Category; code[unique=true]; name[lang=en]; $catalogVersion; supercategories(code,$catalogVersion)
; root       ; Root          ;  ;
; men        ; Men           ;  ; root
; women      ; Women         ;  ; root
; men-shoes  ; Men's Shoes   ;  ; men
; men-shirts ; Men's Shirts  ;  ; men
```

### Create Products

```impex
$catalogVersion = catalogVersion(catalog(id[default='landmarkProductCatalog']),version[default='Staged'])[unique=true]

INSERT_UPDATE Product; code[unique=true]; name[lang=en]; $catalogVersion; supercategories(code,$catalogVersion); approvalStatus(code); unit(code)
; SKU-001 ; Nike Air Max 90 ;  ; men-shoes ; approved ; pieces
; SKU-002 ; Adidas Superstar ;  ; men-shoes ; approved ; pieces
```

### Setup Classification

```impex
# Classification System
INSERT_UPDATE ClassificationSystem; id[unique=true]; name[lang=en]
; LandmarkClassification ; Landmark Classification

INSERT_UPDATE ClassificationSystemVersion; catalog(id)[unique=true]; version[unique=true]; active
; LandmarkClassification ; 1.0 ; true

# Classification Class
$clVersion = catalogVersion(catalog(id[default='LandmarkClassification']),version[default='1.0'])[unique=true]

INSERT_UPDATE ClassificationClass; code[unique=true]; name[lang=en]; $clVersion
; electronics ; Electronics ;

# Classification Attribute
INSERT_UPDATE ClassificationAttribute; code[unique=true]; name[lang=en]; systemVersion(catalog(id),version)
; screenSize ; Screen Size ; LandmarkClassification:1.0
```

---

## 9. Catalog Access and Restrictions

### Catalog Version Access

You can restrict who sees which catalog version:

```
CatalogVersion: "Staged"
├── readPrincipals: [productmanagergroup, cmsmanagergroup]
└── writePrincipals: [productmanagergroup]

CatalogVersion: "Online"
├── readPrincipals: [customergroup]    ← all customers
└── writePrincipals: []                ← nobody writes directly
```

### Category Visibility

Categories can also be restricted:

```java
CategoryModel category;
category.setAllowedPrincipals(Arrays.asList(customerGroup));
// Only customers in this group see this category
```

---

## 10. Summary: Key Concepts

| Concept | Model/Service | Purpose |
|---------|--------------|---------|
| Catalog | `CatalogModel` | Container for product data |
| Version | `CatalogVersionModel` | Staged vs Online |
| Product | `ProductModel` | Sellable item |
| Variant | `VariantProductModel` | Product options (size, color) |
| Category | `CategoryModel` | Product organization hierarchy |
| Sync | `SyncItemJob` | Staged → Online publishing |
| Classification | `ClassificationSystem` | Structured product attributes |
| Feature | `ProductFeatureModel` | Actual attribute values on products |

### Key Services

```java
CatalogService            // Manage catalogs
CatalogVersionService     // Manage versions, set session version
ProductService            // CRUD for products
CategoryService           // CRUD for categories
ClassificationService     // Manage classification attributes
```

---

## Visual Summary

```
┌──────────────────────────────────────────────────────┐
│                   PRODUCT CATALOG                     │
│                                                       │
│  ┌─────────────┐          ┌─────────────┐            │
│  │   STAGED    │  ─sync─► │   ONLINE    │            │
│  │  (editing)  │          │  (live)     │            │
│  └─────────────┘          └─────────────┘            │
│        │                        │                     │
│        ▼                        ▼                     │
│  ┌──────────┐            ┌──────────┐                │
│  │Categories│            │Categories│                │
│  │  └─Men   │            │  └─Men   │                │
│  │    └─Shoe│            │    └─Shoe│                │
│  └──────────┘            └──────────┘                │
│        │                        │                     │
│        ▼                        ▼                     │
│  ┌──────────┐            ┌──────────┐                │
│  │ Products │            │ Products │                │
│  │  Nike 90 │            │  Nike 90 │                │
│  │  Adidas  │            │  Adidas  │                │
│  └──────────┘            └──────────┘                │
│        │                                              │
│        ▼                                              │
│  ┌───────────────────┐                               │
│  │  CLASSIFICATION   │                               │
│  │  screenSize: 55"  │                               │
│  │  resolution: 4K   │                               │
│  └───────────────────┘                               │
└──────────────────────────────────────────────────────┘
```
