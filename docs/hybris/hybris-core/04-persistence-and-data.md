# Persistence & Data in Hybris

Hybris persistence is different from typical Spring/JPA apps. You don't write SQL or entity classes yourself — the **type system generates them** and you interact through **ModelService** and **FlexibleSearchService**.

---

## 1. The Big Picture

```
   items.xml  ──(ant all)──►  Auto-generated Model classes (ProductModel.java)
                                            │
                                            ▼
   Your code ──► ModelService / FlexibleSearchService ──► DB (MySQL/HANA/Oracle)
                              │
                              └──► Interceptors fire at lifecycle events
```

**Key idea:** You never write `INSERT INTO products...`. You create a `ProductModel`, set fields, call `modelService.save(product)`. Hybris figures out the SQL.

---

## 2. ModelService — the CRUD gateway

`ModelService` is your main entry point for **Create, Read, Update, Delete**.

### Example: Create a product

```java
@Resource
private ModelService modelService;

public void createProduct() {
    ProductModel product = modelService.create(ProductModel.class);
    product.setCode("LAPTOP-001");
    product.setName("Dell XPS 15");
    modelService.save(product);   // INSERT happens here
}
```

### Common methods

| Method | What it does |
|---|---|
| `create(Class)` | New unsaved model instance |
| `save(model)` | Insert or update (Hybris decides) |
| `saveAll()` | Batch save |
| `remove(model)` | DELETE |
| `refresh(model)` | Reload from DB, discard in-memory changes |
| `detach(model)` | Remove from session cache |
| `get(PK)` | Load by primary key |

### Update example

```java
ProductModel p = productService.getProductForCode("LAPTOP-001");
p.setName("Dell XPS 15 (2026)");
modelService.save(p);  // UPDATE
```

**Intuition:** Think of `ModelService` as JPA's `EntityManager`, but for auto-generated types.

---

## 3. FlexibleSearchService — the query engine

FlexibleSearch is Hybris's SQL-like DSL. It looks like SQL but uses `{TypeName}` and `{attribute}` in braces.

### Example: find product by code

```java
@Resource
private FlexibleSearchService flexibleSearchService;

public ProductModel findByCode(String code) {
    String query = "SELECT {p:pk} FROM {Product AS p} WHERE {p:code} = ?code";

    FlexibleSearchQuery fsq = new FlexibleSearchQuery(query);
    fsq.addQueryParameter("code", code);
    fsq.setResultClassList(Collections.singletonList(ProductModel.class));

    SearchResult<ProductModel> result = flexibleSearchService.search(fsq);
    return result.getResult().isEmpty() ? null : result.getResult().get(0);
}
```

### Why the curly braces?

- `{Product}` → resolves to the actual DB table (e.g. `products`)
- `{p:code}` → resolves to the actual column (e.g. `p_code`)
- Hybris translates FlexibleSearch → real SQL at runtime

### Joins example

```sql
SELECT {p:pk}
FROM {Product AS p JOIN Category AS c ON {p:supercategory}={c:pk}}
WHERE {c:code} = ?categoryCode
```

**Intuition:** FlexibleSearch = SQL that survives when Hybris renames underlying tables/columns during upgrades.

---

## 4. The DAO Pattern (recommended structure)

Don't scatter FlexibleSearch queries across your codebase. Wrap them in a **DAO**.

```java
public interface ProductDao {
    ProductModel findByCode(String code);
    List<ProductModel> findByCategory(String categoryCode);
}

public class DefaultProductDao implements ProductDao {
    @Resource private FlexibleSearchService flexibleSearchService;

    @Override
    public ProductModel findByCode(String code) {
        // query as shown above
    }
}
```

Then a **Service** uses the DAO:

```java
public class DefaultProductService implements ProductService {
    @Resource private ProductDao productDao;
    @Resource private ModelService modelService;

    public ProductModel getForCode(String code) {
        return productDao.findByCode(code);
    }
}
```

**Layered pattern:** Controller → Facade → Service → DAO → FlexibleSearch/ModelService → DB

---

## 5. Interceptors — lifecycle hooks

Interceptors run automatically when a model is saved/loaded/removed. Great for validation and derived data.

### Types

| Interceptor | When it fires |
|---|---|
| `PrepareInterceptor` | Before save — mutate the model (e.g. set defaults) |
| `ValidateInterceptor` | Before save — reject invalid data |
| `RemoveInterceptor` | Before delete — cleanup or prevent |
| `LoadInterceptor` | After load from DB |
| `InitDefaultsInterceptor` | On `modelService.create()` |

### Example: validate product code format

```java
public class ProductCodeValidator implements ValidateInterceptor<ProductModel> {

    @Override
    public void onValidate(ProductModel product, InterceptorContext ctx)
            throws InterceptorException {
        if (product.getCode() == null || !product.getCode().matches("[A-Z]+-\\d+")) {
            throw new InterceptorException("Code must match FORMAT-123");
        }
    }
}
```

Register in spring XML:

```xml
<bean id="productCodeValidator" class="com.example.ProductCodeValidator"/>

<bean id="productCodeValidatorMapping"
      class="de.hybris.platform.servicelayer.interceptor.impl.InterceptorMapping">
    <property name="interceptor" ref="productCodeValidator"/>
    <property name="typeCode" value="Product"/>
</bean>
```

**Intuition:** Interceptors = triggers, but in Java, running before Hybris hits the DB.

---

## 6. Transactions

Wrap multi-step writes in a transaction so partial failures roll back.

```java
Transaction.current().execute(new TransactionBody() {
    @Override
    public Object execute() {
        OrderModel order = modelService.create(OrderModel.class);
        // ... set fields
        modelService.save(order);

        PaymentInfoModel payment = modelService.create(PaymentInfoModel.class);
        modelService.save(payment);
        return null;
    }
});
```

If either save throws, both roll back.

---

## 7. ImpEx — bulk data loading

ImpEx is Hybris's own CSV-like scripting language for importing/exporting data. **You will use this constantly.**

### Example: create products

```impex
INSERT_UPDATE Product; code[unique=true] ; name         ; catalogVersion(catalog(id),version)
                     ; LAPTOP-001        ; Dell XPS 15  ; electronicsProductCatalog:Staged
                     ; PHONE-001         ; iPhone 17    ; electronicsProductCatalog:Staged
```

Parts explained:

- `INSERT_UPDATE` → insert if new, update if exists (also: `INSERT`, `UPDATE`, `REMOVE`)
- `Product` → type name from items.xml
- `code[unique=true]` → the lookup key
- `catalogVersion(catalog(id),version)` → resolve a foreign key by these two attributes

### Run via HAC

Go to `/hac` → Console → ImpEx Import → paste → Import.

**Intuition:** ImpEx = SQL scripts for Hybris. Every non-trivial project has hundreds of `.impex` files under `resources/`.

---

## 8. Sessions & Detach

Hybris keeps a **session-level cache** of loaded models. Two dangers:

1. **Stale data** — another thread updates the DB; your model is old. Fix: `modelService.refresh(model)`.
2. **Memory bloat** — long-running jobs holding thousands of models. Fix: `modelService.detach(model)` after processing each.

```java
for (ProductModel p : allProducts) {
    process(p);
    modelService.detach(p);  // free memory
}
```

---

## 9. Putting it together — a realistic flow

**Task:** import products via ImpEx, then price them via Java.

**Step 1** — `products.impex`:

```impex
INSERT_UPDATE Product; code[unique=true]; name
                     ; SKU-100          ; Widget
```

**Step 2** — Java service adds a price:

```java
public void addPrice(String productCode, BigDecimal amount) {
    ProductModel product = productDao.findByCode(productCode);

    PriceRowModel price = modelService.create(PriceRowModel.class);
    price.setProduct(product);
    price.setPrice(amount.doubleValue());
    price.setCurrency(commonI18NService.getCurrency("USD"));

    modelService.save(price);   // interceptors fire, then INSERT
}
```

**Step 3** — a `ValidateInterceptor` on `PriceRow` rejects negative prices before save.

**Step 4** — later, query it:

```sql
SELECT {pr:pk} FROM {PriceRow AS pr} WHERE {pr:product} = ?product
```

---

## 10. Common pitfalls to remember

- **Never use `new ProductModel()`** — always `modelService.create()`. Direct `new` creates a detached, unmanaged model.
- **FlexibleSearch is case-sensitive** for type names — `{Product}` works, `{product}` may not.
- **`saveAll()` is faster than looping `save()`** for bulk work.
- **Interceptors can cause cycles** — a save-triggered interceptor that saves another model can loop.
- **`ant updatesystem` after items.xml change** — else your new attribute doesn't exist in the DB.

---

## Suggested next steps

- **ImpEx syntax deep-dive** (macros, translators, headers) — you'll use this daily
- **FlexibleSearch advanced** (subqueries, localized attributes, `GROUP BY`)
- **Writing a full CRUD example** (items.xml → model → DAO → service → interceptor)
