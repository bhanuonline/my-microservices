# Spring Proxy

## What is a proxy?

```
Real object:   your actual class — business logic only
Proxy:         a wrapper Spring puts around your class
               intercepts every method call
               adds extra behaviour (retry, transaction, logging...)
               then calls the real method
```

```
Without proxy:
  caller ──────────────────────► real method runs

With proxy:
  caller ──► proxy intercepts ──► extra behaviour ──► real method runs
```

Think of it like a receptionist:
```
You want to meet the CEO (real bean)
Receptionist (proxy) stops you first
  → checks if you have appointment (@Transactional check)
  → logs your visit (@Async logging)
  → retries if CEO is busy (@Retryable)
Then lets you in
```

---

## How Spring creates the proxy

```
You write:         @Service PaymentService
Spring sees:       @Transactional or @Retryable on it
Spring creates:    PaymentServiceProxy extends PaymentService
                   wraps your real PaymentService inside
                   registers THE PROXY as the bean, not your class
```

```
Spring container
  └── "paymentService" bean ──► PaymentServiceProxy  (what you get when injected)
                                      │
                                      └── real PaymentService inside
```

---

## How does Spring decide to create a proxy?

Spring scans every bean at startup and checks for AOP annotations. Having multiple `@Autowired` dependencies does NOT trigger a proxy.

```java
// NO proxy — only @Service and @Autowired
@Service
public class OrderService {

    @Autowired
    private PaymentService payment;

    @Autowired
    private EmailService email;

    @Autowired
    private InventoryService inventory;

    public void placeOrder() {
        payment.pay();
        email.send();
        inventory.reduce();
    }
}
```

Spring's decision process at startup:

```
Spring scans OrderService
         ↓
sees @Service → register as bean ✓
         ↓
sees @Autowired → inject dependencies ✓
         ↓
sees any AOP annotation?
(@Transactional, @Retryable, @Async, @Cacheable...)
         ↓
NO → plain bean registered, no proxy
YES → proxy created
```

```java
// NO proxy
@Service
public class OrderService {
    @Autowired
    private PaymentService payment;
}

// PROXY created — Spring found @Transactional
@Service
public class OrderService {
    @Autowired
    private PaymentService payment;

    @Transactional        // ← this triggers proxy creation
    public void placeOrder() { ... }
}
```

Simple rule:
```
@Autowired     = wiring instruction        → no proxy
@Service       = registration              → no proxy
@Transactional
@Retryable     = behaviour around method   → proxy created
@Async
@Cacheable
```

Proxy is about **behaviour around your method** — not about how many dependencies you have.

---

## Does @Service create a proxy?

No — `@Service` alone just registers the class as a bean. No proxy.

```
@Service      just registers the class as a bean — no proxy
@Component    just registers the class as a bean — no proxy
@Repository   registers + exception translation proxy (special case)
```

Proxy is created only when Spring sees an **AOP-based annotation**:

```java
// NO proxy — just a bean
@Service
public class PaymentService { ... }


// PROXY created — Spring sees @Transactional
@Service
public class PaymentService {

    @Transactional
    public void pay() { ... }
}
```

```
@Service alone           bean registered, no proxy
@Service + @Transactional  bean registered + proxy created to manage transaction
```

```
@Service       = "hey Spring, manage this class"
@Transactional = "hey Spring, also wrap it in a proxy
                  so you can open/commit/rollback transactions"
```

Stereotype annotations (`@Service`, `@Component`) just tell Spring to register the bean. The proxy is a separate decision — made only when Spring sees an AOP annotation.

---

## All AOP annotations that trigger proxy creation

**Built-in Spring:**
```
@Transactional       transaction management
@Async               run in separate thread
@Cacheable           cache method result
@CacheEvict          remove from cache
@CachePut            update cache
@Retryable           retry on failure
@Recover             fallback after retries exhausted
```

**Spring Security:**
```
@PreAuthorize        check permission before method
@PostAuthorize       check permission after method
@PreFilter           filter input collection
@PostFilter          filter output collection
@Secured             role-based access
```

**Spring AOP (custom aspects):**
```
@Around              wrap method — can skip, modify execution
@Before              run before method
@After               run after method
@AfterReturning      run after successful return
@AfterThrowing       run after exception
```

**Summary:**
```
Category              Annotations
──────────────────    ──────────────────────────────────────
Transaction           @Transactional
Async                 @Async
Cache                 @Cacheable, @CacheEvict, @CachePut
Retry                 @Retryable, @Recover
Security              @PreAuthorize, @PostAuthorize,
                      @PreFilter, @PostFilter, @Secured
Custom AOP            @Around, @Before, @After,
                      @AfterReturning, @AfterThrowing
```

All of these trigger proxy creation when Spring finds them on a bean.

---

## Where Spring uses proxy

```
Annotation        Why proxy needed
──────────────    ──────────────────────────────
@Transactional    open/commit/rollback transaction around method
@Retryable        catch exception → retry → call method again
@Async            run method in a different thread
@Cacheable        check cache before calling method
@Secured          check permissions before calling method
@AOP/@Around      any custom aspect
```

Rule of thumb:
```
Any annotation that adds behaviour AROUND your method = proxy used
```

---

## How to know if a proxy is being used

**1. Print the bean class:**
```java
@Autowired
PaymentService paymentService;

System.out.println(paymentService.getClass());
// real object:  class com.example.PaymentService
// proxy:        class com.example.PaymentService$$SpringCGLIB$$0
```

`$$SpringCGLIB$$` in the class name = proxy is wrapping it.

**2. If the annotation is AOP-based = proxy is used:**
```
@Transactional  → proxy ✓
@Retryable      → proxy ✓
@Async          → proxy ✓
@Cacheable      → proxy ✓
@Component      → no proxy (just a bean)
@Autowired      → no proxy (just injection)
```

---

## How to know if your call goes through the proxy

```
Call from OUTSIDE the class → goes through proxy ✓
  otherBean.pay()  → proxy intercepts → annotation fires

Call from INSIDE the same class → bypasses proxy ✗
  this.pay()       → direct call → annotation ignored
```

```
┌──────────────────────────────────────────┐
│  Proxy                                   │
│   ┌──────────────────────────────────┐   │
│   │  Real PaymentService             │   │
│   │                                  │   │
│   │  methodA() → this.methodB()      │   │
│   │              ↑ stays inside box  │   │
│   │              ↑ never hits proxy  │   │
│   │                                  │   │
│   │  @Retryable methodB()            │   │
│   └──────────────────────────────────┘   │
└──────────────────────────────────────────┘
         ↑
external call hits proxy ✓
```

Simple rule:
```
Did the call cross a bean boundary?  YES → proxy → annotation fires
Did the call stay in the same class? NO  → no proxy → annotation ignored
```

---

## CGLIB vs JDK Dynamic Proxy — when does Spring use each?

```
JDK Dynamic Proxy          CGLIB Proxy
──────────────────────     ──────────────────────────
class implements           class does NOT implement
an interface               any interface
proxy implements           proxy extends (subclasses)
the same interface         your class directly
```

```java
// JDK proxy used — PaymentService implements an interface
public interface Payment { void pay(); }

@Service
public class PaymentService implements Payment { ... }
// Spring creates proxy that implements Payment interface


// CGLIB used — no interface
@Service
public class PaymentService { ... }   // no interface
// Spring creates proxy that extends PaymentService
```

```
JDK Proxy                        CGLIB Proxy
─────────────────────            ─────────────────────
      Payment                        PaymentService
     (interface)                       (your class)
         ▲                                  ▲
         │ implements                       │ extends
  JDKPaymentProxy                  CGLIBPaymentProxy
  (Spring creates)                 (Spring creates)
```

Important limitation of CGLIB — your class cannot be `final`:

```
CGLIB extends your class → class cannot be final
                         → methods cannot be final
                         → final blocks subclassing → proxy creation fails
```

```java
@Service
public final class PaymentService { ... }  // ❌ CGLIB can't extend a final class
```

---

## Fix — move method to a separate bean

```java
// ❌ same class — proxy bypassed
@Service
public class PaymentService {
    public void methodA() {
        this.methodB();   // no retry
    }

    @Retryable
    public void methodB() { ... }
}

// ✅ separate bean — proxy is hit
@Service
public class PaymentService {
    private final PaymentHelper helper;

    public void methodA() {
        helper.methodB();   // goes through proxy → annotation fires
    }
}

@Service
public class PaymentHelper {
    @Retryable
    public void methodB() { ... }
}
```

Same rule applies to all AOP-based annotations — `@Transactional`, `@Async`, `@Cacheable`, `@Retryable` all fail when called from within the same class.
