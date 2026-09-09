# Interceptors — Deep Dive

Interceptors are **lifecycle hooks** that fire automatically when a model is created, saved, loaded, or removed. They're Hybris's answer to "I need to run some logic every time a Product is saved" — without wrapping every save call.

Think of them as **database triggers, but in Java**, running inside your application before Hybris hits the DB.

---

## 1. The 5 Interceptor Types

| Type | When it fires | Typical use |
|---|---|---|
| `InitDefaultsInterceptor` | `modelService.create()` — brand new model | Set defaults (status = ACTIVE, createdDate = now) |
| `PrepareInterceptor` | Before save — after your code, before validation | Compute derived fields, normalize data |
| `ValidateInterceptor` | Before save — after Prepare | Reject invalid data by throwing exception |
| `RemoveInterceptor` | Before delete | Cleanup, cascade, or prevent deletion |
| `LoadInterceptor` | After load from DB | Post-process loaded data (rare, performance-sensitive) |

### Execution order on `save()`

```
modelService.create()  ──► InitDefaultsInterceptor
   │
   │ (your code sets fields)
   │
modelService.save()    ──► PrepareInterceptor
                       ──► ValidateInterceptor
                       ──► (SQL INSERT/UPDATE)

modelService.remove()  ──► RemoveInterceptor
                       ──► (SQL DELETE)

<any read>             ──► LoadInterceptor  (fires on every load — beware!)
```

---

## 2. InitDefaultsInterceptor — set defaults on creation

Fires when you call `modelService.create(...)`. The model has no PK yet.

```java
public class OrderInitDefaultsInterceptor implements InitDefaultsInterceptor<OrderModel> {

    @Override
    public void onInitDefaults(OrderModel order, InterceptorContext ctx) {
        order.setStatus(OrderStatus.CREATED);
        order.setDate(new Date());
        order.setCode("ORD-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
```

**When to use:** any field where "if user didn't set it, use this default."

**Alternative:** you can also set defaults directly in `items.xml` via `<defaultvalue>` — use that for constants, use InitDefaults for computed defaults.

---

## 3. PrepareInterceptor — mutate before save

Runs before validation. Use to normalize data or compute derived fields.

```java
public class ProductPrepareInterceptor implements PrepareInterceptor<ProductModel> {

    @Override
    public void onPrepare(ProductModel product, InterceptorContext ctx)
            throws InterceptorException {
        // Normalize code to uppercase
        if (product.getCode() != null) {
            product.setCode(product.getCode().toUpperCase());
        }

        // Auto-generate search keywords from name
        if (product.getName() != null) {
            product.setSearchKeywords(product.getName().toLowerCase());
        }
    }
}
```

**Key rule:** you *can* modify the model here. In `ValidateInterceptor` you *shouldn't* — validation should be read-only.

---

## 4. ValidateInterceptor — reject bad data

Runs after Prepare. Throws `InterceptorException` to abort the save.

```java
public class PriceRowValidateInterceptor implements ValidateInterceptor<PriceRowModel> {

    @Override
    public void onValidate(PriceRowModel price, InterceptorContext ctx)
            throws InterceptorException {
        if (price.getPrice() == null || price.getPrice() < 0) {
            throw new InterceptorException("Price must be >= 0");
        }
        if (price.getCurrency() == null) {
            throw new InterceptorException("Currency is required");
        }
    }
}
```

**Behavior:** the exception bubbles up from `modelService.save()`. The transaction rolls back automatically.

---

## 5. Prepare vs Validate — Critical Distinction

These two interceptors are the most commonly confused. Understanding when to use each is critical.

### 5.1 Sequence (what runs when)

```
modelService.save(product)
        │
        ▼
┌───────────────────┐
│ 1. Prepare        │  ← mutate/normalize/compute
└───────────────────┘
        │
        ▼
┌───────────────────┐
│ 2. Validate       │  ← check, throw if invalid
└───────────────────┘
        │
        ▼
┌───────────────────┐
│ 3. SQL INSERT/    │
│    UPDATE         │
└───────────────────┘
```

**Rule:** Prepare **always** runs before Validate. This ordering is guaranteed by Hybris — you cannot change it.

**Why this order matters:** Prepare cleans up the data *first*, then Validate checks the cleaned version. If it were reversed, you'd validate raw input and then mutate it — meaning invalid data could slip through after validation.

### 5.2 Core Difference

| Aspect | PrepareInterceptor | ValidateInterceptor |
|---|---|---|
| **Purpose** | Change the model | Check the model |
| **Can mutate?** | ✅ Yes (that's the point) | ❌ No (should be read-only) |
| **Can throw?** | Rarely — throwing here is unusual | ✅ Yes — throwing is how it rejects |
| **Runs when** | Step 1 of save | Step 2 of save |
| **Analogy** | "Auto-formatter" | "Linter" |

### 5.3 When to use Prepare

Use when you need to **transform or compute** data before it hits the DB.

**Examples:**
- Normalize: trim whitespace, lowercase email, uppercase product code
- Auto-populate: `modifiedDate = now`, `modifiedBy = currentUser`
- Compute derived: `searchKeywords = name + description`
- Denormalize: flatten a list into a searchable string

```java
public class ProductPrepareInterceptor implements PrepareInterceptor<ProductModel> {
    @Override
    public void onPrepare(ProductModel p, InterceptorContext ctx) {
        p.setCode(p.getCode().toUpperCase().trim());     // normalize
        p.setModifiedDate(new Date());                    // auto-populate
    }
}
```

### 5.4 When to use Validate

Use when you need to **reject invalid data** by throwing.

**Examples:**
- Required field missing
- Format wrong (email doesn't have `@`)
- Business rule violation (price < 0, discount > 100%)
- Referential check (order references a deleted customer)

```java
public class ProductValidateInterceptor implements ValidateInterceptor<ProductModel> {
    @Override
    public void onValidate(ProductModel p, InterceptorContext ctx)
            throws InterceptorException {
        if (p.getCode() == null || p.getCode().isBlank()) {
            throw new InterceptorException("Product code is required");
        }
        if (!p.getCode().matches("[A-Z]+-\\d+")) {
            throw new InterceptorException("Code must match FORMAT-123");
        }
    }
}
```

### 5.5 Why the split matters — a concrete scenario

**Task:** product codes must be uppercase, in format `LETTERS-DIGITS`, and cannot be blank.

**Wrong approach — all in Validate:**
```java
public void onValidate(ProductModel p, InterceptorContext ctx) {
    p.setCode(p.getCode().toUpperCase());  // mutation in validate = anti-pattern
    if (!p.getCode().matches("[A-Z]+-\\d+")) throw ...
}
```
Problem: mutations in Validate can be lost, unpredictable, and violate the contract.

**Wrong approach — all in Prepare:**
```java
public void onPrepare(ProductModel p, InterceptorContext ctx) {
    if (p.getCode() == null) throw new InterceptorException(...);  // possible but not idiomatic
    p.setCode(p.getCode().toUpperCase());
}
```
Problem: mixes transformation with rejection — hard to reason about.

**Right approach — split by responsibility:**
```java
// PREPARE: transform
onPrepare:  p.setCode(p.getCode() == null ? null : p.getCode().toUpperCase().trim());

// VALIDATE: check
onValidate: if (p.getCode() == null || p.getCode().isBlank())
                throw new InterceptorException("Code required");
            if (!p.getCode().matches("[A-Z]+-\\d+"))
                throw new InterceptorException("Bad format");
```

Now:
- Prepare handles the "make it correct if possible" job
- Validate handles the "reject if not correct" job
- Because Prepare runs first, Validate sees the *cleaned* value → user typing `"  laptop-001  "` becomes `"LAPTOP-001"` and passes validation

### 5.6 Quick decision guide

Ask yourself: **"Am I changing the model or checking the model?"**

- Changing → **Prepare**
- Checking → **Validate**
- Both → **two separate interceptors** (one Prepare + one Validate), not one big one

### 5.7 Summary of Prepare vs Validate

| Question | Answer |
|---|---|
| Which runs first? | **Prepare**, then Validate |
| Which can mutate? | Only Prepare |
| Which can reject? | Both technically, but rejection is Validate's job |
| Do I need both? | Only if you both transform *and* enforce rules |
| Can Prepare see the pre-DB state? | Yes — it's the last chance to change data |
| Can Validate see Prepare's changes? | Yes — because Prepare ran first |

**Mental model:** Prepare is the *scribe* rewriting the request into a clean form. Validate is the *bouncer* checking the clean form and rejecting bad ones. Scribe first, bouncer second.

---

## 6. RemoveInterceptor — control deletion

Fires before delete. Use for cleanup or to block removal.

```java
public class CustomerRemoveInterceptor implements RemoveInterceptor<CustomerModel> {

    @Override
    public void onRemove(CustomerModel customer, InterceptorContext ctx)
            throws InterceptorException {
        // Block delete if customer has orders
        if (!customer.getOrders().isEmpty()) {
            throw new InterceptorException(
                "Cannot delete customer with existing orders: " + customer.getUid());
        }

        // Cascade: remove addresses first
        for (AddressModel addr : customer.getAddresses()) {
            ctx.getModelService().remove(addr);
        }
    }
}
```

**Warning:** cascading removes from a RemoveInterceptor can trigger their own RemoveInterceptors — watch for loops.

---

## 7. LoadInterceptor — post-process on read

Fires **every time** a model is loaded from the DB. Powerful but **dangerous for performance**.

```java
public class ProductLoadInterceptor implements LoadInterceptor<ProductModel> {

    @Override
    public void onLoad(ProductModel product, InterceptorContext ctx) {
        // e.g. decrypt a sensitive field after load
        String encrypted = product.getInternalNotes();
        if (encrypted != null) {
            product.setInternalNotes(decrypt(encrypted));
        }
    }
}
```

**Avoid unless necessary.** If you query 10,000 products, this fires 10,000 times. Prefer `DynamicAttributeHandler` (computed on access) for derived reads.

---

## 8. InterceptorContext — the power tool

`InterceptorContext` tells you *what's changing*. Use it to skip work when nothing relevant changed.

### Key methods

| Method | Returns |
|---|---|
| `isNew(model)` | true if this is an INSERT (not UPDATE) |
| `isModified(model, "attr")` | true if that specific attribute changed |
| `isRemoved(model)` | true during remove |
| `getDirtyAttributes(model)` | Map of changed attributes → old values |
| `getModelService()` | Get ModelService without @Resource injection |

### Example: only recompute on name change

```java
public class ProductPrepareInterceptor implements PrepareInterceptor<ProductModel> {

    @Override
    public void onPrepare(ProductModel product, InterceptorContext ctx)
            throws InterceptorException {

        // Only update search keywords when name actually changed
        if (ctx.isNew(product) || ctx.isModified(product, ProductModel.NAME)) {
            product.setSearchKeywords(product.getName().toLowerCase());
        }
    }
}
```

**Without this check**, every save on the product (even unrelated field changes) would recompute keywords — wasted work.

---

## 9. Registering interceptors (Spring XML)

Every interceptor needs an `InterceptorMapping` bean.

```xml
<bean id="productPrepareInterceptor"
      class="com.example.ProductPrepareInterceptor"/>

<bean id="productPrepareInterceptorMapping"
      class="de.hybris.platform.servicelayer.interceptor.impl.InterceptorMapping">
    <property name="interceptor" ref="productPrepareInterceptor"/>
    <property name="typeCode" value="Product"/>
    <property name="order" value="100"/>  <!-- optional -->
</bean>
```

### Multiple interceptors on the same type

If you have 3 ValidateInterceptors on `Product`, all 3 run. Order is controlled by:
1. The `order` property (lower runs first) — Hybris 5.5+
2. Otherwise: bean definition order (fragile)

**Best practice:** always set `order` explicitly when you have >1 interceptor per (type, phase).

---

## 10. Skipping interceptors — when and how

Sometimes you need to save without triggering interceptors (data migration, admin fixes).

### Option A: `SessionContext`
```java
SessionContext ctx = jaloSession.createLocalSessionContext();
ctx.setAttribute("disableInterceptorTypes", List.of("validate"));
try {
    modelService.save(model);
} finally {
    jaloSession.removeLocalSessionContext();
}
```

### Option B: `InterceptorExecutionPolicy` constants
```java
Map<String, Object> disableParams = new HashMap<>();
disableParams.put(
    InterceptorExecutionPolicy.DISABLED_INTERCEPTOR_TYPES,
    List.of(InterceptorExecutionPolicy.InterceptorType.VALIDATE)
);
// pass via SessionService or ServiceLayerUtils
```

**Use sparingly.** If you find yourself disabling interceptors often, the interceptor logic probably belongs somewhere else.

---

## 11. Interceptor vs DynamicAttributeHandler

Both compute values, but they're for different jobs.

| Aspect | Interceptor | DynamicAttributeHandler |
|---|---|---|
| Fires on | save/load/remove | attribute access (getter) |
| Stored in DB? | Yes (mutates the field) | No (computed each time) |
| Cost | Once per save | Every getter call |
| Use when | Value must persist | Value derives from others |

**Example:** `product.totalStock` (sum across warehouses)
- **DynamicAttributeHandler:** compute on read — always fresh, no persistence
- **Interceptor:** compute on save — stored, but stale if warehouse stock changes elsewhere

Rule of thumb: **derive on read (Dynamic), persist on write (Interceptor).**

---

## 12. Common pitfalls

### Pitfall 1: Infinite loops
A `PrepareInterceptor` on `Product` calls `modelService.save(category)`, which has an interceptor that saves a product... loop.

**Fix:** use `ctx.isModified()` to break the cycle, or refactor to avoid cross-saves inside interceptors.

### Pitfall 2: LoadInterceptor performance
```java
List<ProductModel> all = flexibleSearchService.search(...).getResult();
// If ProductLoadInterceptor is expensive, this is N × cost
```

**Fix:** move logic to a service method, or use `DynamicAttributeHandler`.

### Pitfall 3: Modifying in ValidateInterceptor
```java
public void onValidate(ProductModel p, InterceptorContext ctx) {
    p.setCode(p.getCode().trim());  // WRONG — do this in Prepare
}
```

Modifications in Validate may not be persisted correctly. Keep Validate read-only.

### Pitfall 4: RemoveInterceptor on cascade
Removing a `Customer` triggers cascade to `Address`. If `AddressRemoveInterceptor` also saves something, ordering gets weird.

**Fix:** keep RemoveInterceptors minimal — validation and cleanup only, no writes.

### Pitfall 5: Assuming interceptor order across modules
Two extensions add ValidateInterceptors on `Order`. Without explicit `order`, load order determines execution — fragile across environments.

**Fix:** always set `order` when >1 interceptor exists per (type, phase).

---

## 13. Real-world patterns

### Auto-populate `createdBy` / `modifiedBy`
```java
public class AuditPrepareInterceptor implements PrepareInterceptor<ItemModel> {
    @Resource private UserService userService;

    @Override
    public void onPrepare(ItemModel item, InterceptorContext ctx) {
        UserModel current = userService.getCurrentUser();
        if (ctx.isNew(item)) {
            // set createdBy if the model has such a field
        }
        // set modifiedBy on every save
    }
}
```

### Denormalize for search
```java
public class OrderPrepareInterceptor implements PrepareInterceptor<OrderModel> {
    @Override
    public void onPrepare(OrderModel order, InterceptorContext ctx) {
        // Flatten entries into a searchable string
        String skuList = order.getEntries().stream()
            .map(e -> e.getProduct().getCode())
            .collect(Collectors.joining(","));
        order.setSearchableSkus(skuList);
    }
}
```

### Generate business codes
```java
public class OrderInitDefaultsInterceptor implements InitDefaultsInterceptor<OrderModel> {
    @Resource private KeyGenerator orderCodeGenerator;

    @Override
    public void onInitDefaults(OrderModel order, InterceptorContext ctx) {
        order.setCode(orderCodeGenerator.generate().toString());
    }
}
```

---

## 14. Testing interceptors

Interceptors are Spring beans — test them like any other bean, but you also want integration tests via `ServicelayerTest`.

### Unit test (mock context)
```java
@Test
public void rejectsNegativePrice() {
    PriceRowValidateInterceptor interceptor = new PriceRowValidateInterceptor();
    PriceRowModel price = mock(PriceRowModel.class);
    when(price.getPrice()).thenReturn(-10.0);

    assertThrows(InterceptorException.class,
        () -> interceptor.onValidate(price, mock(InterceptorContext.class)));
}
```

### Integration test
```java
public class PriceRowInterceptorIntegrationTest extends ServicelayerTest {
    @Resource private ModelService modelService;

    @Test(expected = ModelSavingException.class)
    public void rejectsNegativePriceOnSave() {
        PriceRowModel price = modelService.create(PriceRowModel.class);
        price.setPrice(-5.0);
        modelService.save(price);  // Interceptor throws → wrapped in ModelSavingException
    }
}
```

Integration tests catch the *real* wrapping exception (`ModelSavingException`), which contains the `InterceptorException`.

---

## 15. Quick decision guide

| Need | Use |
|---|---|
| Set default on new object | `InitDefaultsInterceptor` |
| Normalize / compute on save | `PrepareInterceptor` |
| Reject invalid save | `ValidateInterceptor` |
| Prevent or clean up on delete | `RemoveInterceptor` |
| Post-process on read (rare) | `LoadInterceptor` |
| Compute virtual field | `DynamicAttributeHandler` (not an interceptor) |
| Cross-cutting logic before save | `PrepareInterceptor` |
| Run only when attribute X changed | `PrepareInterceptor` + `ctx.isModified(m, "X")` |

---

## 16. Concurrency FAQ — What happens when two threads modify the same model?

### Q1: If two threads modify the same model at the same time, what happens?

**A:** By default, **last-write-wins silently**. Each thread has its own session and its own copy of the model. Whichever `save()` runs last overwrites the earlier one — no exception, no warning.

```
Thread A: load Product("P1")  → in-memory copy A (stock=10)
Thread B: load Product("P1")  → in-memory copy B (stock=10)

Thread A: setStock(5), save() → DB: stock=5
Thread B: setStock(8), save() → DB: stock=8   ← A's change is LOST
```

This is the classic **lost update** problem.

---

### Q2: Does Hybris throw an exception on concurrent modification?

**A:** Sometimes — but not reliably. Hybris tracks `modifiedtime` on every item. If enabled and checked, you get `ConcurrentModificationException` (wrapped in `ModelSavingException`):

```
Thread A: load, modifiedtime = T1
Thread B: load, modifiedtime = T1
Thread A: save → DB modifiedtime = T2
Thread B: save → Hybris sees T1 != T2 → throws ConcurrentModificationException
```

**Out of the box, most saves DO NOT check this.** You have to opt in via optimistic locking or `FOR UPDATE`.

---

### Q3: Why does my second thread see stale data even after the first thread saved?

**A:** Because **each thread has its own session cache**. Even in the same JVM:

```java
// Thread A
ProductModel p = productService.getForCode("P1");  // stock = 10
p.setStock(5);
modelService.save(p);

// Thread B (right after)
ProductModel p = productService.getForCode("P1");  // ALSO stock = 10 — cached!
```

Thread B may be reading from its own local cache, not the DB.

**Fix:** call `modelService.refresh(p)` before critical writes to force a re-read.

---

### Q4: How do I prevent lost updates? (Pessimistic locking)

**A:** Use FlexibleSearch's `FOR UPDATE` inside a transaction. The DB row is locked until the transaction commits — other threads block.

```java
Transaction.current().execute(() -> {
    String q = "SELECT {pk} FROM {Product} WHERE {code}=?code FOR UPDATE";
    FlexibleSearchQuery fsq = new FlexibleSearchQuery(q);
    fsq.addQueryParameter("code", "P1");
    fsq.setResultClassList(List.of(ProductModel.class));

    ProductModel p = flexibleSearchService.search(fsq).getResult().get(0);
    p.setStock(p.getStock() - 1);
    modelService.save(p);
    return null;
});
```

**Use when:** you must read-then-write atomically (stock decrement, counter update, balance transfer).

---

### Q5: What's the alternative? (Optimistic locking)

**A:** Check `modifiedtime` yourself and retry on mismatch.

```java
ProductModel p = productService.getForCode("P1");
Date loadedAt = p.getModifiedtime();

// ... do work ...

modelService.refresh(p);
if (!p.getModifiedtime().equals(loadedAt)) {
    throw new ConcurrentModificationException("Product changed");
}
modelService.save(p);
```

**Use when:** conflicts are rare and retry is cheap.

---

### Q6: Pessimistic vs Optimistic — which do I pick?

**A:**

| Criterion | Pessimistic (`FOR UPDATE`) | Optimistic (modifiedtime + retry) |
|---|---|---|
| Conflict rate | High | Low |
| Lock cost | Higher (blocks other threads) | Lower (no lock) |
| Retry logic needed? | No | Yes |
| Deadlock risk? | Yes | No |
| Best for | Stock, balances, counters | Profile edits, config changes |

Rule of thumb: **high-contention writes → pessimistic. Low-contention writes → optimistic.**

---

### Q7: Can I just use `synchronized` in Java?

**A:** **No — not reliably.** `synchronized` only locks within one JVM. Hybris runs in clusters:

```
Node A JVM: synchronized(lock) { ... }   ← locks only Node A
Node B JVM: synchronized(lock) { ... }   ← different lock object, both run simultaneously
```

Two nodes can enter the "synchronized" block at the same time. Only **DB-level locks (`FOR UPDATE`)** work across a cluster.

---

### Q8: What about cluster nodes and cache invalidation?

**A:** Hybris broadcasts cache invalidation events via JMS between cluster nodes. But there's **a lag** — events arrive milliseconds after the DB write. Within that window, another node still has stale cached data.

```
Node A: save Product → DB updated → invalidation event sent
Node B: (event not yet received) → reads cached stale data → saves stale value
```

**Fix:** `FOR UPDATE` sidesteps this entirely — row-level DB locks are cluster-wide.

---

### Q9: Are interceptors thread-safe?

**A:** The interceptor **beans** are singletons, so their fields must be thread-safe (usually stateless). But interceptors **do not** prevent concurrent modification of the same model — they'll happily run on both threads.

```
Thread A save Product → PrepareInterceptor runs
Thread B save Product → PrepareInterceptor runs (in parallel, on stale copy)
```

**Rule:** design interceptors to be **idempotent** — running twice should produce the same result. Never rely on interceptors to serialize concurrent access.

---

### Q10: Concrete example — how do I safely decrement stock?

**Broken version (race condition):**
```java
public void decrementStock(String sku, int qty) {
    ProductModel p = productService.getForCode(sku);
    p.setStock(p.getStock() - qty);   // two threads: both read 10, both save 9
    modelService.save(p);
}
```

**Safe version (`FOR UPDATE`):**
```java
public void decrementStock(String sku, int qty) {
    Transaction.current().execute(() -> {
        String q = "SELECT {pk} FROM {Product} WHERE {code}=?code FOR UPDATE";
        FlexibleSearchQuery fsq = new FlexibleSearchQuery(q);
        fsq.addQueryParameter("code", sku);
        fsq.setResultClassList(List.of(ProductModel.class));

        ProductModel p = flexibleSearchService.search(fsq).getResult().get(0);
        p.setStock(p.getStock() - qty);
        modelService.save(p);
        return null;
    });
}
```

Now the second thread **blocks** at `FOR UPDATE` until the first commits, then reads the fresh value.

---

### Q11: What if I want to retry on conflict instead of blocking?

**A:** Wrap in a retry loop around optimistic locking.

```java
int attempts = 0;
while (attempts++ < 3) {
    try {
        Transaction.current().execute(() -> {
            ProductModel p = productService.getForCode("P1");
            p.setStock(p.getStock() - 1);
            modelService.save(p);
            return null;
        });
        return;   // success
    } catch (ConcurrentModificationException e) {
        // retry
    }
}
throw new RuntimeException("Failed after 3 attempts");
```

**Best for:** rare conflicts where blocking would hurt throughput.

---

### Q12: Quick reference — which tool for which scenario?

| Scenario | Solution |
|---|---|
| Two threads modifying different products | Nothing needed — no shared state |
| Two threads reading same product, only one writes | Nothing needed |
| Two threads doing read-modify-write on same row | **`FOR UPDATE`** |
| Rare conflict, want throughput | **Optimistic locking + retry** |
| Cross-cluster serialization | **`FOR UPDATE`** (only DB locks work cluster-wide) |
| Single-JVM only, low volume | `synchronized` (fragile — breaks in cluster) |

---

### Q13: Mental model for Hybris concurrency

Think of Hybris models as **local snapshots**, not live references. When you load a model, you get a **photograph** of the DB row at that moment. Two threads with two photographs can't see each other's edits — only the DB is the source of truth, and only `FOR UPDATE` guarantees exclusive access to that truth.

**Golden rules:**
1. Default Hybris behavior is **last-write-wins** — races are silent
2. Every thread has its **own session cache** — stale data is the norm
3. `refresh()` before critical reads to force a DB round-trip
4. `FOR UPDATE` for any read-modify-write on the same row
5. Never rely on `synchronized` in a clustered deployment
6. Design interceptors to be **idempotent**
7. Retry loops handle transient conflicts gracefully

---

## 17. Transactions

Transactions group multiple DB operations into a single **all-or-nothing** unit. If any step fails, everything rolls back. This is critical for interceptors, concurrency, and multi-step writes.

### 17.1 The core idea

Without a transaction:
```java
modelService.save(order);          // Step 1: saved
modelService.save(payment);        // Step 2: THROWS
modelService.save(deliveryInfo);   // Step 3: never runs
```

**Result:** you have an order with no payment and no delivery. Inconsistent data.

With a transaction:
```java
Transaction.current().execute(() -> {
    modelService.save(order);
    modelService.save(payment);        // THROWS
    modelService.save(deliveryInfo);
    return null;
});
// All three rolled back — DB is unchanged
```

**Result:** as if nothing happened. Consistent.

### 17.2 The basic pattern

Hybris uses `Transaction.current().execute(TransactionBody)`:

```java
Transaction.current().execute(new TransactionBody() {
    @Override
    public Object execute() throws Exception {
        // your work here
        return null;
    }
});
```

With lambdas (Java 8+):
```java
Transaction.current().execute(() -> {
    modelService.save(model);
    return null;
});
```

You can return a value:
```java
OrderModel savedOrder = (OrderModel) Transaction.current().execute(() -> {
    OrderModel o = modelService.create(OrderModel.class);
    o.setCode("ORD-001");
    modelService.save(o);
    return o;
});
```

### 17.3 Rollback rules

| What happens inside `execute()` | Outcome |
|---|---|
| Returns normally | Transaction **commits** |
| Throws any exception | Transaction **rolls back**, exception propagates |
| Catches exception internally | Transaction **commits** (no rollback trigger!) |

**Critical pitfall:** if you catch an exception inside the transaction body and don't rethrow, the transaction commits anyway.

```java
Transaction.current().execute(() -> {
    modelService.save(order);
    try {
        modelService.save(payment);
    } catch (Exception e) {
        log.error("Payment failed", e);   // swallowed
    }
    return null;
});
// Order is committed, payment isn't — INCONSISTENT
```

**Fix:** rethrow, or don't catch:
```java
Transaction.current().execute(() -> {
    modelService.save(order);
    modelService.save(payment);   // let it throw
    return null;
});
```

### 17.4 Example: order placement (multi-step atomic write)

```java
public OrderModel placeOrder(CartModel cart, PaymentInfoModel payment) {
    return (OrderModel) Transaction.current().execute(() -> {
        // 1. Create order from cart
        OrderModel order = modelService.create(OrderModel.class);
        order.setCode(orderCodeGenerator.generate().toString());
        order.setUser(cart.getUser());
        order.setTotalPrice(cart.getTotalPrice());
        modelService.save(order);

        // 2. Copy cart entries to order entries
        for (CartEntryModel ce : cart.getEntries()) {
            OrderEntryModel oe = modelService.create(OrderEntryModel.class);
            oe.setOrder(order);
            oe.setProduct(ce.getProduct());
            oe.setQuantity(ce.getQuantity());
            modelService.save(oe);
        }

        // 3. Attach payment
        payment.setOrder(order);
        modelService.save(payment);

        // 4. Delete cart
        modelService.remove(cart);

        return order;
    });
}
```

If step 3 throws (e.g. payment validation fails via a ValidateInterceptor), steps 1, 2, and 4 all roll back. You don't get an order without payment, or a deleted cart with no order.

### 17.5 Example: stock decrement with FOR UPDATE

Transactions pair naturally with pessimistic locking:

```java
public void decrementStock(String sku, int qty) {
    Transaction.current().execute(() -> {
        // FOR UPDATE holds the row lock until commit
        String q = "SELECT {pk} FROM {Product} WHERE {code}=?code FOR UPDATE";
        FlexibleSearchQuery fsq = new FlexibleSearchQuery(q);
        fsq.addQueryParameter("code", sku);
        fsq.setResultClassList(List.of(ProductModel.class));

        ProductModel p = flexibleSearchService.search(fsq).getResult().get(0);
        if (p.getStock() < qty) {
            throw new InsufficientStockException(sku);   // rollback + release lock
        }
        p.setStock(p.getStock() - qty);
        modelService.save(p);
        return null;
    });
    // Lock released on commit
}
```

**Key point:** the lock is held for the entire transaction. Keep transactions short.

### 17.6 Example: batch import with per-item transactions

For bulk operations, wrap **each item** in its own transaction so one bad row doesn't kill the whole batch:

```java
public void importProducts(List<ProductDto> dtos) {
    for (ProductDto dto : dtos) {
        try {
            Transaction.current().execute(() -> {
                ProductModel p = modelService.create(ProductModel.class);
                p.setCode(dto.code);
                p.setName(dto.name);
                modelService.save(p);
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to import " + dto.code, e);
            // continue with next item
        }
    }
}
```

Each failed product rolls back individually; the successful ones stay.

### 17.7 Nested transactions

Calling `execute()` inside another `execute()` **joins the outer transaction** — it doesn't start a new one.

```java
Transaction.current().execute(() -> {          // outer tx begins
    modelService.save(a);

    Transaction.current().execute(() -> {      // JOINS outer tx (no new tx)
        modelService.save(b);
        return null;
    });

    throw new RuntimeException();               // rolls back BOTH a and b
});
```

**Implication:** if you want a "sub-operation" to commit independently, you can't just nest — you need to complete the outer transaction first or use `Transaction.current().begin()` / `commit()` manually.

### 17.8 Transactions + Interceptors

Interceptors run **inside** the transaction that triggered them:

```
Transaction begins
    ├── modelService.save(order)
    │     ├── PrepareInterceptor runs   ← inside tx
    │     ├── ValidateInterceptor runs  ← inside tx; if it throws, rollback
    │     └── SQL INSERT
    └── Transaction commits (or rolls back if any interceptor threw)
```

**Consequence:** if a `ValidateInterceptor` throws, everything in the transaction — including saves that happened before this one — rolls back.

**Also:** interceptors that save other models participate in the same transaction. This is usually what you want, but be aware:

```java
public class OrderPrepareInterceptor implements PrepareInterceptor<OrderModel> {
    @Override
    public void onPrepare(OrderModel o, InterceptorContext ctx) {
        AuditLogModel log = ctx.getModelService().create(AuditLogModel.class);
        log.setMessage("Order " + o.getCode() + " saved");
        ctx.getModelService().save(log);   // part of same tx as the order save
    }
}
```

If the order save later rolls back, the audit log also disappears.

### 17.9 Manual transaction control

For fine-grained control (rare):

```java
Transaction tx = Transaction.current();
tx.begin();
try {
    modelService.save(a);
    modelService.save(b);
    tx.commit();
} catch (Exception e) {
    tx.rollback();
    throw e;
}
```

**Prefer `execute()` for 99% of cases.** Manual control is error-prone (forgetting to rollback in a rare code path is a common bug).

### 17.10 Common pitfalls

**Pitfall 1: Long-running transactions**
```java
Transaction.current().execute(() -> {
    for (ProductModel p : allProducts) {   // 100,000 products
        callExternalPricingApi(p);         // 500ms each
        modelService.save(p);
    }
    return null;
});
```
Locks held for hours. Other threads pile up. **Fix:** batch per item or in chunks.

**Pitfall 2: Catching and swallowing exceptions**
Covered in §17.3 — silently commits inconsistent state.

**Pitfall 3: Nested transaction confusion**
```java
Transaction.current().execute(() -> {
    try {
        Transaction.current().execute(() -> {
            modelService.save(model);
            throw new RuntimeException();
        });
    } catch (Exception e) {
        // continue as if nothing happened
    }
    return null;
});
```
The inner throw marks the **entire outer transaction** for rollback. Catching it doesn't undo that. When the outer commits, Hybris throws because rollback was already required.

**Pitfall 4: External calls inside transactions**
```java
Transaction.current().execute(() -> {
    modelService.save(order);
    externalPaymentApi.charge(...);   // network call — slow, unreliable
    return null;
});
```
Locks held during the network call. If the API hangs, DB locks hang too. **Fix:** call external services outside the transaction, then transact only the DB writes.

**Pitfall 5: Not returning null**
```java
Transaction.current().execute(() -> {
    modelService.save(model);
    // forgot to return
});
```
Won't compile — `TransactionBody.execute()` returns `Object`. Always `return null` or return a value.

### 17.11 Quick reference

| Situation | What to do |
|---|---|
| Multi-step write that must be atomic | `Transaction.current().execute(...)` |
| Read-modify-write on same row | Transaction + `FOR UPDATE` |
| Batch import, tolerate partial failure | Per-item transaction inside a loop |
| Batch import, all-or-nothing | Single transaction wrapping the whole loop |
| Rollback on failure | Just throw — don't catch |
| Return a value | Cast the `execute()` return type |
| Sub-operation should commit independently | Complete outer tx first; don't nest |
| External API call | Do it **outside** the transaction |

### 17.12 Mental model

A transaction is a **staging area**. Every save, remove, and interceptor side-effect goes into staging. On normal return → staging is applied to the DB. On any thrown exception → staging is discarded.

**Golden rules:**
1. **Throw to rollback** — never catch-and-swallow inside a transaction
2. **Keep transactions short** — locks are held for the whole duration
3. **No slow I/O inside** — no HTTP calls, no file writes, no `Thread.sleep`
4. **Nested `execute()` joins the outer** — it does not create a sub-transaction
5. **Interceptor exceptions roll back** the whole transaction, not just the one save
6. **Prefer `execute()` over manual `begin/commit/rollback`**

---

## 18. Multiple Interceptors on the Same Type

You can register **many interceptors on the same type** — same phase or different phases. This is a core pattern for building modular Hybris projects.

### 18.1 Concrete example — `Order` with 3 interceptors

Imagine an e-commerce project with three separate concerns bolted onto `Order`:

1. **Normalize data** (Prepare) — from your core order extension
2. **Enforce business rules** (Validate) — order total must match sum of entries
3. **Enforce compliance rules** (Validate) — orders shipping to certain countries need extra fields

Each is a **separate class**. Each is a **separate InterceptorMapping bean**.

**First interceptor — normalize (Prepare):**
```java
public class OrderNormalizePrepareInterceptor implements PrepareInterceptor<OrderModel> {
    @Override
    public void onPrepare(OrderModel order, InterceptorContext ctx) {
        if (order.getCode() != null) {
            order.setCode(order.getCode().toUpperCase().trim());
        }
        order.setModifiedTime(new Date());
    }
}
```

**Second interceptor — total validation (Validate):**
```java
public class OrderTotalValidateInterceptor implements ValidateInterceptor<OrderModel> {
    @Override
    public void onValidate(OrderModel order, InterceptorContext ctx)
            throws InterceptorException {
        double sum = order.getEntries().stream()
            .mapToDouble(OrderEntryModel::getTotalPrice)
            .sum();
        if (Math.abs(order.getTotalPrice() - sum) > 0.01) {
            throw new InterceptorException(
                "Order total " + order.getTotalPrice() + " != sum of entries " + sum);
        }
    }
}
```

**Third interceptor — compliance (Validate):**
```java
public class OrderComplianceValidateInterceptor implements ValidateInterceptor<OrderModel> {
    private static final Set<String> STRICT_COUNTRIES = Set.of("DE", "FR", "IT");

    @Override
    public void onValidate(OrderModel order, InterceptorContext ctx)
            throws InterceptorException {
        String country = order.getDeliveryAddress().getCountry().getIsocode();
        if (STRICT_COUNTRIES.contains(country) && order.getVatId() == null) {
            throw new InterceptorException("VAT ID required for " + country);
        }
    }
}
```

**Registering all three (Spring XML):**
```xml
<!-- Prepare -->
<bean id="orderNormalizeInterceptor"
      class="com.example.OrderNormalizePrepareInterceptor"/>
<bean id="orderNormalizeMapping"
      class="de.hybris.platform.servicelayer.interceptor.impl.InterceptorMapping">
    <property name="interceptor" ref="orderNormalizeInterceptor"/>
    <property name="typeCode" value="Order"/>
    <property name="order" value="100"/>
</bean>

<!-- Validate #1 -->
<bean id="orderTotalValidator"
      class="com.example.OrderTotalValidateInterceptor"/>
<bean id="orderTotalValidatorMapping"
      class="de.hybris.platform.servicelayer.interceptor.impl.InterceptorMapping">
    <property name="interceptor" ref="orderTotalValidator"/>
    <property name="typeCode" value="Order"/>
    <property name="order" value="200"/>
</bean>

<!-- Validate #2 -->
<bean id="orderComplianceValidator"
      class="com.example.OrderComplianceValidateInterceptor"/>
<bean id="orderComplianceValidatorMapping"
      class="de.hybris.platform.servicelayer.interceptor.impl.InterceptorMapping">
    <property name="interceptor" ref="orderComplianceValidator"/>
    <property name="typeCode" value="Order"/>
    <property name="order" value="300"/>
</bean>
```

### 18.2 Execution order

When you call `modelService.save(order)`:

```
PrepareInterceptor phase:
   [order 100] OrderNormalizePrepareInterceptor       runs first
   (only one prepare in this example)

ValidateInterceptor phase:
   [order 200] OrderTotalValidateInterceptor          runs first
   [order 300] OrderComplianceValidateInterceptor     runs second

→ SQL INSERT/UPDATE
```

**Lower `order` runs first.** If the total-validator throws, the compliance-validator never runs — the save aborts immediately.

### 18.3 When you NEED multiple interceptors

**Reason 1 — Separation of concerns**

Bad: one god-interceptor with 200 lines covering totals, addresses, VAT, discounts, currency.
Good: one interceptor per rule — each independently testable, replaceable, disable-able.

**Reason 2 — Different extensions add different rules**

Real Hybris projects have 10–50 extensions. Each may care about `Order`:
- **Core**: order code format, non-null customer
- **Promotions**: discount eligibility
- **Payment**: payment info attached
- **Compliance**: VAT ID for EU
- **Loyalty**: points calculation on save

Each extension registers **its own interceptor**. No extension touches the others' code. This is how Hybris stays modular.

**Reason 3 — Prepare + Validate combo**

You almost always want both on the same type when you need normalization + rules:
```
PrepareInterceptor  → normalize input (trim, uppercase, defaults)
ValidateInterceptor → check the normalized values
```

**Reason 4 — Optional / feature-flagged rules**

Register an interceptor conditionally per environment:
```xml
<beans profile="strict-compliance">
    <bean id="orderExtraComplianceMapping"
          class="...InterceptorMapping">
        <property name="interceptor" ref="strictComplianceInterceptor"/>
        <property name="typeCode" value="Order"/>
        <property name="order" value="500"/>
    </bean>
</beans>
```

### 18.4 When you DON'T need multiple

- Trivial validation (one rule) → one interceptor is fine
- Related checks that always change together → keep them in one
- Same-phase logic that shares expensive state → one interceptor avoids duplicate work

**Rule of thumb:** if two rules could **independently** be true, false, added, or removed, they belong in separate interceptors.

### 18.5 When `order` matters

Set `order` explicitly when:

- **A validator depends on data another interceptor sets** — e.g. `AuditPrepareInterceptor` sets `modifiedBy = currentUser`; a later prepare interceptor uses it.
- **You want cheap checks before expensive ones** — put "is code null?" (fast) before "does code exist in external system?" (slow HTTP). The fast one throws first, saving the network call.
- **Two extensions add interceptors** and you need deterministic ordering across environments.

If none apply, `order` is optional — but setting it explicitly is still a good habit.

### 18.6 Debugging multiple interceptors

If a save fails, the stack trace tells you which interceptor threw:

```
de.hybris.platform.servicelayer.interceptor.impl.InterceptorMapping
    → com.example.OrderComplianceValidateInterceptor.onValidate
    → InterceptorException: VAT ID required for DE
```

**Tip:** always throw with a clear message identifying which rule failed. `throw new InterceptorException("VAT ID required")` beats `throw new InterceptorException("invalid")`.

### 18.7 Mental model

Think of interceptors as **middleware for save/load/remove**. Just like HTTP middleware:
- Each does one job
- They chain in order
- Any one can abort the chain by throwing
- Multiple can live on the same route (type)

Adding more interceptors doesn't require modifying existing ones — that's the whole point.

### 18.8 Summary of multi-interceptor pattern

- **Yes**, you can have multiple interceptors on the same type — same phase or different phases
- **You need this when:** concerns are separate, different extensions add rules, or you want Prepare + Validate combo
- **Order matters** when interceptors depend on each other, or when cheap-before-expensive helps performance
- **Prefer many small interceptors** over one god-interceptor — testable, modular, extension-friendly

---

## Summary

- 5 interceptor types, each fires at a specific lifecycle point
- Order: **InitDefaults → Prepare → Validate → (DB) → Load** on reads, **Remove → (DB)** on deletes
- **Prepare vs Validate:** Prepare mutates (scribe), Validate checks (bouncer). Prepare always runs first.
- `InterceptorContext` lets you run logic conditionally (isNew, isModified)
- Register via `InterceptorMapping` bean; set `order` when multiple interceptors on same type
- **Read-only interceptors** (Validate, Load) should not mutate; **write interceptors** (Prepare, InitDefaults) can
- Prefer `DynamicAttributeHandler` for derived reads to avoid LoadInterceptor overhead
- Test with `ServicelayerTest` for real lifecycle wiring
