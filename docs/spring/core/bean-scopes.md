# Bean Scopes

A **scope** defines how many instances of a bean Spring creates and how long they live.

## How many instances does singleton really mean?

**One instance per ApplicationContext**, not per JVM or per cluster.

```
Normal Spring Boot app (one ApplicationContext)
────────────────────────────────────────────────
1 ApplicationContext → 1 OrderService instance ✅
(this is what singleton means 99% of the time)
```

```
Multiple ApplicationContexts (rare)
─────────────────────────────────────────────────────
ApplicationContext A → its own OrderService instance
ApplicationContext B → its own OrderService instance

2 contexts → 2 instances (still singleton within each context)
```

### Multiple pods (Kubernetes)

Each pod is a completely separate JVM process — Spring has no idea other pods exist.

```
Pod 1 → JVM → ApplicationContext → OrderService@abc
Pod 2 → JVM → ApplicationContext → OrderService@def
Pod 3 → JVM → ApplicationContext → OrderService@xyz
```

5 pods running the same service → **5 independent singleton instances**, one per pod.
They share nothing in memory.

```
Spring singleton scope
  └── scoped to: ONE ApplicationContext (one JVM / one pod)

NOT scoped to:
  └── cluster
  └── multiple pods
  └── multiple JVMs
```

This is why storing state in a singleton bean is dangerous in a clustered environment —
if you store a user's cart in a singleton field, Pod 1 has it but Pod 2 doesn't.
The next request may hit Pod 2 → data missing.

```
Fix for shared state across pods
  └── Redis        (shared in-memory cache)
  └── Database     (shared persistent store)
  └── Sticky sessions  (route same user to same pod — not recommended)
```

---

## What does "instance lives" mean?

"Instance lives" = how long that object stays in memory before Spring throws it away.

```
SINGLETON
─────────────────────────────────────────────────────
App starts → Spring creates bean → stays in memory
                                        │
                                        │  entire app running
                                        │
App shuts down → Spring destroys bean → removed from memory

Instance lives as long as the APP lives ✅
```

```
PROTOTYPE
─────────────────────────────────────────────────────
getBean() called → Spring creates bean → gives it to you
                                              │
                        you use it, method finishes, no more reference
                                              │
                              Java GC collects it → removed from memory

Instance lives as long as YOUR CODE holds a reference to it
(not the app — just that one method/class using it)
```

```
REQUEST SCOPE
─────────────────────────────────────────────────────
HTTP request comes in → Spring creates bean
                              │
                    request being processed
                              │
              HTTP response sent → Spring destroys bean

Instance lives as long as ONE HTTP REQUEST lives
```

```
SESSION SCOPE
─────────────────────────────────────────────────────
User logs in → session created → Spring creates bean
                                        │
                          user makes many requests
                          (bean stays alive between them)
                                        │
              user logs out / session times out → bean destroyed

Instance lives as long as ONE USER SESSION lives
```

---

```
Request comes in → Spring asked for a bean
                        │
              What scope is it?
                        │
         ┌──────────────┼──────────────┐
      singleton      prototype       request
         │              │               │
   return the       create a new    return one tied
   same one         one every time  to this HTTP req
```

```
Scope        Instances           Lifetime
──────────   ─────────────────   ─────────────────────────────
singleton    1 per container     entire app lifetime (default)
prototype    new per request     until GC collects it
request      1 per HTTP req      HTTP request/response cycle
session      1 per HTTP sess     HTTP session lifetime
application  1 per ServletCtx   same as singleton for most apps
```

---

## Singleton (default)

Spring keeps one internal map called the **singleton cache**:

```
Singleton Cache (inside ApplicationContext)
┌──────────────────────────────────────┐
│ "orderService"   → OrderService@abc  │
│ "paymentService" → UpiPayment@def    │
│ "userService"    → UserService@xyz   │
└──────────────────────────────────────┘
```

First `getBean("orderService")` → not in cache → create → store → return.
Every call after → already in cache → return same object. No new creation.

```java
@Component  // singleton by default — @Scope("singleton") is redundant
public class OrderService { }
```

Every class that injects `OrderService` gets the **exact same object**.

### Thread safety warning

Singletons are shared across ALL threads simultaneously:

```
Thread 1 ──▶ OrderService.process()  ┐
Thread 2 ──▶ OrderService.process()  ├── same object, same time
Thread 3 ──▶ OrderService.process()  ┘
```

Safe only when the bean has **no mutable instance variables**. Storing per-request
data in a singleton field → race condition:

```java
@Component
public class OrderService {
    private Order currentOrder;  // ❌ shared across threads — race condition!

    public void process(Order o) {
        this.currentOrder = o;   // Thread 1 sets it, Thread 2 overwrites it
    }
}
```

Rule: singleton beans must be **stateless** or use thread-safe structures.

---

## Prototype

```
First getBean()   → Spring creates new instance
Second getBean()  → Spring creates another new instance
Third getBean()   → Spring creates yet another
```

```java
@Component
@Scope("prototype")
public class ReportBuilder { }  // fresh state every time
```

Use when the bean **holds state that must not be shared** across callers —
e.g. a builder that accumulates data per operation, a parser with internal buffers.

Spring creates the instance but **does NOT manage its destruction** —
`@PreDestroy` is never called on prototype beans.

### `new` vs `@Scope("prototype")` — why not just use `new`?

```
new ReportBuilder()              @Scope("prototype")
───────────────────              ───────────────────
you manage the object            Spring manages creation
no dependency injection          Spring injects all deps
@PostConstruct not called        @PostConstruct called
no Spring features               full Spring features available
```

Prototype gives you a **fresh instance with full DI support**.
Plain `new` gives you nothing from Spring.

---

## The hidden trap — prototype inside singleton

```java
@Component                    // singleton
public class OrderService {

    @Autowired
    ReportBuilder builder;    // prototype — but injected ONCE at startup!
}
```

```
Startup
  │
  ├─ Spring creates OrderService (singleton)
  │      └─ needs ReportBuilder (prototype)
  │              └─ Spring creates ONE ReportBuilder
  │                     └─ injects into OrderService ──── stored forever
  │
Runtime
  │
  ├─ call 1: orderService.process() → uses ReportBuilder@abc  ← same
  ├─ call 2: orderService.process() → uses ReportBuilder@abc  ← same
  └─ call 3: orderService.process() → uses ReportBuilder@abc  ← same
                                        prototype never refreshed!
```

`@Scope("prototype")` has NO effect here — the bean was injected once at singleton creation.

### Fix 1 — inject via ApplicationContext

```java
@Component
public class OrderService {

    private final ApplicationContext ctx;

    public OrderService(ApplicationContext ctx) { this.ctx = ctx; }

    public void process() {
        ReportBuilder builder = ctx.getBean(ReportBuilder.class);  // fresh every time
        builder.build();
    }
}
```

### Fix 2 — `@Lookup` (cleaner, no ApplicationContext coupling)

```java
@Component
public abstract class OrderService {

    public void process() {
        ReportBuilder builder = newReportBuilder();  // Spring overrides this method
        builder.build();
    }

    @Lookup
    protected abstract ReportBuilder newReportBuilder();
}
```

```
Runtime with @Lookup
  │
  ├─ call 1: process() → newReportBuilder() → Spring → new ReportBuilder@abc
  ├─ call 2: process() → newReportBuilder() → Spring → new ReportBuilder@def
  └─ call 3: process() → newReportBuilder() → Spring → new ReportBuilder@xyz
```

Spring subclasses `OrderService` at runtime and overrides `newReportBuilder()`
to call `ctx.getBean(ReportBuilder.class)` internally — prototype behavior without
touching `ApplicationContext` directly.

---

## Request & Session scope (web apps)

```java
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class CartContext {
    private List<Item> items = new ArrayList<>();
    // new instance per HTTP request
}
```

```java
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UserSession {
    private String userId;
    // survives across multiple requests from the same user
}
```

### Why `proxyMode` is required

Without `proxyMode`, injecting a request-scoped bean into a singleton crashes at startup —
no HTTP request exists yet when the singleton is being created.

With `proxyMode`, Spring injects a **proxy** instead of the real bean:

```
OrderService (singleton)
  └── cartContext = Proxy (created at startup — safe, no real request needed)
                       │
        HTTP Request 1 arrives
                       │
                  Proxy delegates → CartContext instance A
        HTTP Request 2 arrives
                       │
                  Proxy delegates → CartContext instance B
```

The proxy reads `RequestContextHolder` to find the active request and routes
to the correct instance automatically.

---

## `@Lazy` — defer singleton creation

By default all singletons are created eagerly at startup. `@Lazy` defers until first use:

```
Normal startup:
  creates UserService ──▶ creates PaymentService ──▶ creates ReportService ──▶ app ready

With @Lazy on ReportService:
  creates UserService ──▶ creates PaymentService ──▶ app ready
                                                         │
                                         first getBean("reportService") called
                                                         │
                                               creates ReportService
```

```java
@Component
@Lazy
public class ReportService { }  // created only when first needed
```

Useful for expensive beans (DB connections, heavy init) that may not always be used.

---

## Full scope decision guide

```
Question                                    Scope
────────────────────────────────────────    ──────────────────────
Stateless shared logic?                     singleton (default)
Per-call mutable state?                     prototype
Expensive, may not always be needed?        singleton + @Lazy
Per HTTP request data?                      request (web only)
Per user session data?                      session (web only)
```

---

## Q&A

**Q: What is the default scope of a Spring bean?**
Singleton.

---

**Q: Does `@Scope("singleton")` need to be written explicitly?**
No — singleton is the default. Writing it adds no value, just noise.
Only write `@Scope` when changing from the default:
```java
@Component
@Scope("prototype")   // write this — not the default
public class ReportBuilder { }
```

---

**Q: Singleton means one instance for the entire app lifetime?**
One instance per **ApplicationContext**, not per JVM or per cluster.

In a normal Spring Boot app there is one ApplicationContext → one instance → yes, entire app lifetime.

In Kubernetes with multiple pods:
```
Pod 1 → JVM → ApplicationContext → OrderService@abc
Pod 2 → JVM → ApplicationContext → OrderService@def
Pod 3 → JVM → ApplicationContext → OrderService@xyz
```
Each pod has its own JVM and its own singleton instance — they share nothing in memory.
Storing state in a singleton field in a multi-pod setup → data inconsistency across pods.

---

**Q: You have `ReportBuilder` marked `@Scope("prototype")` injected into a singleton `OrderService`. How many `ReportBuilder` instances are created during the app lifetime?**

Only **one** — even though `ReportBuilder` is prototype-scoped.

```
Startup
  └─ Spring creates OrderService (singleton)
         └─ needs ReportBuilder → creates ONE instance → injects it → held forever

Runtime
  ├─ call 1  → OrderService uses ReportBuilder@abc  ← same
  ├─ call 2  → OrderService uses ReportBuilder@abc  ← same
  └─ call 99 → OrderService uses ReportBuilder@abc  ← same
```

The singleton's lifecycle dominates. `@Scope("prototype")` is effectively ignored
when the bean sits in a singleton field.

```
@Autowired ReportBuilder builder   →  injected once → 1 instance (broken)
ctx.getBean(ReportBuilder.class)   →  asks Spring every call → new each time (correct)
@Lookup method                     →  Spring asks every call → new each time (correct)
```

---

**Q: Is `@PreDestroy` called on prototype beans? Why?**

No — `@PreDestroy` is never called on prototype beans.

```
Singleton — Spring manages fully
─────────────────────────────────────────
Spring creates bean → tracks it in singleton cache
                            │
                      app shuts down
                            │
               Spring knows about it → calls @PreDestroy ✅

Prototype — Spring manages only creation
─────────────────────────────────────────────────
Spring creates bean → hands it to you → forgets about it
                                              │
                              Spring no longer tracks it
                                              │
                           app shuts down → Spring has no reference
                                              │
                                    @PreDestroy never called ❌
```

`@PostConstruct` is still called (at creation time). Only `@PreDestroy` is skipped.

```java
@Component
@Scope("prototype")
public class ReportBuilder {

    @PostConstruct
    public void init() { }      // Spring calls this ✅

    @PreDestroy
    public void cleanup() { }   // Spring NEVER calls this ❌
}
```

If cleanup matters, you are responsible for calling it manually:

```java
ReportBuilder builder = ctx.getBean(ReportBuilder.class);
try {
    builder.build();
} finally {
    builder.cleanup();  // your responsibility
}
```

---

**Q: A singleton bean has a field `private Order currentOrder`. What problem does this cause in a multi-threaded environment?**

```java
@Component  // singleton — ONE instance shared across ALL threads
public class OrderService {
    private Order currentOrder;  // shared field ❌

    public void process(Order order) {
        this.currentOrder = order;   // step 1 — save order
        // ... some processing ...
        pay(this.currentOrder);      // step 2 — use it
    }
}
```

3 users hit this at the same time:

```
Thread 1 (User A)  Thread 2 (User B)  Thread 3 (User C)
─────────────────  ─────────────────  ─────────────────
process(OrderA)
  currentOrder = OrderA
                   process(OrderB)
                     currentOrder = OrderB  ← overwrites OrderA!
                                              Thread 1 still running!
  pay(currentOrder)
       ↓
  pays OrderB ❌   ← User A pays for User B's order!
```

`currentOrder` is one memory location shared by all threads — any thread can overwrite it at any time.

```
WITHOUT shared state (safe)        WITH shared state (dangerous)
───────────────────────────        ──────────────────────────────
Thread 1 → local variable          Thread 1 → currentOrder = OrderA
Thread 2 → local variable          Thread 2 → currentOrder = OrderB ← overwrites!
Thread 3 → local variable          Thread 3 → currentOrder = OrderC ← overwrites again!
each thread has its own copy       all threads share ONE field
```

Fix — keep per-request data as local variables (live on thread's own stack):

```java
@Component
public class OrderService {

    public void process(Order order) {
        Order currentOrder = order;  // local variable — this thread only ✅
        pay(currentOrder);
    }
}
```

```
Rule: singleton beans must be STATELESS
      → no instance fields holding per-request data
      → only inject other beans (services, repos) as fields
```

---

**Q: Service and repo are also instance fields — why are they safe but `Order currentOrder` is not?**

All three are instance fields, but the difference is whether the field **changes after the bean is created**:

```
PaymentService payment   →  injected once at startup, NEVER changes
OrderRepository repo     →  injected once at startup, NEVER changes
Order currentOrder       →  changes with EVERY request  ← unsafe
```

```
SAFE instance fields                  UNSAFE instance fields
────────────────────────────          ────────────────────────────
other Spring beans                    per-request data
  (PaymentService, OrderRepo)           (Order, User, Cart, String name)
injected once, read-only forever      written on every request
same value no matter which thread     different value per thread needed
```

Thread 1 and Thread 2 both reading `payment` → fine, it never changes.
Thread 1 and Thread 2 both writing `currentOrder` → race condition.

**How to decide — ask one question:**

```
Does this field change after the bean is created?
        │
   ┌────┴────┐
   NO        YES
   │          │
  safe ✅    unsafe ❌
(injected    (per-request data
once, fixed)  → use local variable)
```

**Simple mental model:**

```
COLLABORATOR (another bean that helps you do work) → instance field ✅
DATA (belongs to one request/user)                 → local variable ✅
```

```java
public void process(Order order) {

    // DATA — keep as local variable (lives on thread's own stack)
    int retryCount = 0;
    Order currentOrder = order;

    // COLLABORATOR — already injected as instance field, just use it
    payment.pay(currentOrder.getAmount());
    repo.save(currentOrder);
}
```

---

**Q: When is `@PreDestroy` called across different scopes?**

```
Scope        @PostConstruct    @PreDestroy
──────────   ──────────────    ───────────────────────────────
singleton    ✅ called          ✅ called (at app shutdown)
prototype    ✅ called          ❌ never called
request      ✅ called          ✅ called (at end of HTTP request)
session      ✅ called          ✅ called (at session timeout/logout)
```

For singleton — Spring registers a JVM shutdown hook and calls `@PreDestroy` on all singleton beans before the app exits:

```
Ctrl+C / kill signal
        │
Spring catches shutdown hook
        │
calls @PreDestroy on all singleton beans
        │
ApplicationContext closed
        │
JVM exits
```

```java
@Component  // singleton
public class DatabasePool {

    @PostConstruct
    public void init() {
        System.out.println("connection pool opened");  // at startup
    }

    @PreDestroy
    public void cleanup() {
        System.out.println("connection pool closed");  // at shutdown
    }
}
```

Common real-world uses of `@PreDestroy`:
```
Release DB connections
Close file handles
Flush cache to disk
Stop background threads
Deregister from service registry (Eureka)
```

Prototype beans are not tracked by Spring after creation — so it can't call `@PreDestroy` even if it wanted to. Cleanup for prototype beans is your responsibility.

---

**Q: What does `proxyMode = ScopedProxyMode.TARGET_CLASS` do and when is it required?**

**The problem without proxyMode:**

```java
@Component                      // singleton — created at startup
public class OrderService {

    @Autowired
    CartContext cart;           // request-scoped — needs an HTTP request to exist
}
```

```
App starts up
    │
    Spring creates OrderService (singleton)
    │
    needs to inject CartContext
    │
    BUT — no HTTP request exists yet at startup!
    │
    Spring panics → error ❌
```

**What proxyMode does:**

Instead of injecting the real `CartContext`, Spring injects a proxy (empty shell) at startup:

```
App starts up
    │
    Spring creates OrderService (singleton)
    │
    needs CartContext → injects a PROXY instead ✅
    (safe to create at startup — no real request needed)
    │
    App ready
```

When a real HTTP request comes in:

```
HTTP Request arrives
    │
    OrderService uses this.cart  ← looks like CartContext
    │
    actually it's the PROXY
    │
    proxy checks: which HTTP request is active right now?
    │
    proxy routes to the REAL CartContext for that request
```

```
OrderService (singleton)
  └── cart = Proxy (one proxy, lives forever)
                  │
    Request 1 → Proxy → CartContext instance A
    Request 2 → Proxy → CartContext instance B
    Request 3 → Proxy → CartContext instance C
```

**TARGET_CLASS vs INTERFACES:**

```java
// TARGET_CLASS — works for any class (uses CGLIB to subclass) — use this by default
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)

// INTERFACES — works only if bean implements an interface (uses JDK proxy)
@Scope(value = "request", proxyMode = ScopedProxyMode.INTERFACES)
```

**When is proxyMode required:**

```
Short-lived bean injected into long-lived bean → proxyMode required

Short-lived     Long-lived     proxyMode needed?
────────────    ──────────     ─────────────────
request      →  singleton      YES
session      →  singleton      YES
prototype    →  singleton      NO — use @Lookup instead
```

---

**Q: What is the difference between `new ReportBuilder()` and `ctx.getBean(ReportBuilder.class)`?**

```
new ReportBuilder()                    ctx.getBean(ReportBuilder.class)
─────────────────────────────────      ────────────────────────────────
created by JVM                         created by Spring IoC container
you manage it                          Spring manages creation
no dependency injection                all @Autowired deps injected
@PostConstruct not called              @PostConstruct called
@PreDestroy not called                 Spring aware of its lifecycle
no Spring features                     full Spring features available
always creates new object              respects scope
```

`ctx.getBean()` always respects the scope:

```
ctx.getBean(ReportBuilder.class)
        │
   what scope is ReportBuilder?
        │
   ┌────┴────────┐
singleton      prototype
   │               │
returns same    returns new
instance        instance every time
```

`new` completely bypasses Spring — scope, DI, lifecycle — all ignored.

---

**Q: `@Scope("prototype")` can be written as a constant too?**
Yes — avoids magic strings:
```java
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)  // same as "prototype"
```

---

## Code location

`study-projects/spring-core/src/main/java/com/example/springcore/scopes/`

| File | Demonstrates |
|------|-------------|
| `SingletonBean.java` | `@Scope("singleton")` — same instance |
| `PrototypeBean.java` | `@Scope("prototype")` — new instance each time |
| `ScopesDemo.java` | calls `getBean()` twice for each, prints identity hash |
