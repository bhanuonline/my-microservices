# Dependency Injection

## What is a dependency?

When class A needs class B to do its job, B is a **dependency** of A.

```java
class OrderService {
    PaymentService payment;   // dependency
    EmailService email;       // dependency
}
```

---

## The problem WITHOUT DI

```java
class OrderService {
    PaymentService payment = new UpiPayment();  // hardcoded
    EmailService email     = new SmtpEmail();   // hardcoded
}
```

- `OrderService` controls which implementation it uses — **tight coupling**
- Can't swap `UpiPayment` → `CardPayment` without changing `OrderService`
- Can't unit test `OrderService` in isolation — always creates real objects

---

## DI flips the responsibility (IoC)

```
WITHOUT DI                    WITH DI (Spring)
──────────────────────        ──────────────────────
OrderService                  IoC Container
  │                             │
  ├─ new UpiPayment()           ├─ creates UpiPayment
  └─ new SmtpEmail()            ├─ creates SmtpEmail
                                └─ injects both into OrderService
                                        ↓
                                   OrderService
                                   (just receives deps)
```

`OrderService` no longer says "I'll create my tools" — it says "give me my tools."
This is **Inversion of Control (IoC)**.

---

## 3 Types of DI

### 1. Constructor Injection (preferred)

```java
@Component
public class OrderService {
    private final PaymentService payment;   // final → immutable, never null

    public OrderService(PaymentService payment) {
        this.payment = payment;
    }
}
```

Spring reads the constructor, finds a `PaymentService` bean, and calls `new OrderService(upiPayment)`.

**Why it's best:**
- Dep is `final` — always present, can never be null after construction
- Testable without Spring: `new OrderService(mockPayment)`

---

### 2. Setter Injection

```java
@Component
public class OrderService {
    private PaymentService payment;

    @Autowired
    public void setPayment(PaymentService payment) {
        this.payment = payment;
    }
}
```

Use only when the dependency is **truly optional** (object still works without it).
Risk: dep may not be set → NPE at runtime.

---

### 3. Field Injection (avoid)

```java
@Component
public class OrderService {
    @Autowired
    private PaymentService payment;   // injected via reflection
}
```

- Can't make dep `final`
- Can't test without starting a Spring context
- Hides what the class needs — not visible in the constructor

---

## What happens if Spring finds 2 beans of the same type and no @Qualifier?

`NoUniqueBeanDefinitionException` at startup — app fails to start.

```
Parameter 0 of constructor required a single bean, but 2 were found:
  - dieselEngine
  - petrolEngine
```

Three ways to fix it:

```
Fix              How                                When to use
─────────────    ────────────────────────────────   ──────────────────────────
@Qualifier       point to the exact bean by name    you know which one you want
@Primary         mark one bean as the default       one impl is used 90% of the time
List<Engine>     inject all of them                 you need all implementations
```

```java
// @Qualifier
public Car(@Qualifier("petrolEngine") Engine engine) { ... }

// @Primary
@Primary
@Component
public class PetrolEngine implements Engine { ... }

// inject all
public Car(List<Engine> engines) { ... }
```

### Bean naming rule — Introspector.decapitalize()

Spring derives the default bean name from the class name using Java's `Introspector.decapitalize()`:

```
Normal rule:    first letter lowercased
  EmailNotification → emailNotification
  PushNotification  → pushNotification

Special rule:   if BOTH first AND second letters are uppercase → name unchanged
  SMSNotification   → SMSNotification  (NOT sMSNotification)
  URLParser         → URLParser        (NOT uRLParser)
```

This matters when using `@Qualifier` — the name must match exactly:

```java
@Qualifier("emailNotification")   // ✅ EmailNotification
@Qualifier("sMSNotification")     // ❌ SMSNotification — both S and M are uppercase
@Qualifier("SMSNotification")     // ✅ correct
```

---

### What does @Primary do?

When Spring finds 2 beans of the same type, `@Primary` tells Spring **which one to pick by default** — without needing `@Qualifier` at the injection point.

```
2 beans found: dieselEngine, petrolEngine (@Primary)
                    ↓
         no @Qualifier at injection point?
                    ↓
         pick @Primary → petrolEngine
```

**`@Qualifier` beats `@Primary`:**

```java
// petrolEngine is @Primary
// but this explicitly asks for dieselEngine → @Qualifier wins
public Car(@Qualifier("dieselEngine") Engine engine) { ... }
```

```
@Primary   = default choice when nothing else is specified
@Qualifier = always wins over @Primary
```

### Where can @Primary be used?

`@Primary` is valid only on a **class** or **`@Bean` method** — not on parameters.

```
Target         Example
────────────   ──────────────────────────────────────────
class          @Primary
               @Component
               public class PetrolEngine implements Engine { }

@Bean method   @Bean
               @Primary
               public Engine engine() {
                   return new PetrolEngine();
               }

Parameter      ❌ not valid — use @Qualifier there instead
```

**Rule of thumb:**
- `@Primary` — goes on the **bean definition** (where the bean is created)
- `@Qualifier` — goes on the **injection point** (where the bean is consumed)

---

## @Qualifier — place it at the point of injection

When two beans implement the same interface, Spring doesn't know which to inject. `@Qualifier` tells it — but it must go **where Spring is doing the injection**, not somewhere else.

```
Injection style      Where @Qualifier goes
───────────────      ─────────────────────
Field injection   →  on the field
Constructor       →  on the constructor parameter
Setter            →  on the setter parameter
```

**Field injection** — `@Qualifier` on the field ✅
```java
@Qualifier("petrolEngine")
private Engine engine;
```

**Constructor injection** — `@Qualifier` on the parameter ✅
```java
private final Engine engine;

public Car(@Qualifier("petrolEngine") Engine engine) {
    this.engine = engine;
}
```

**Common mistake** — constructor injection but `@Qualifier` on the field ❌
```java
@Qualifier("petrolEngine")   // Spring never reads this here
private final Engine engine;

public Car(Engine engine) {  // Spring looks here — no @Qualifier → crash
    this.engine = engine;
}
```

Spring resolves constructor injection by reading annotations on the **parameter**, not the field. Putting `@Qualifier` on the field is silently ignored → `NoUniqueBeanDefinitionException` at startup.

---

## How Spring resolves a dependency

```
@Autowired → type: PaymentService
        │
  How many beans of this type?
        │
   ┌────┴────┐
   1        >1
   │         │
inject     Is one @Primary?
           │
       ┌───┴───┐
      yes      no
       │        │
   inject     @Qualifier present?
   @Primary    │
           ┌───┴───┐
          yes      no
           │        │
       inject    NoUniqueBeanDefinitionException (startup crash)
```

---

## Why is constructor injection preferred over field injection?

```
Field injection          Constructor injection
────────────────         ──────────────────────────────
Spring creates object    Spring CANNOT create object
first, then sets         without the dependency
the field                — they arrive together
      ↓                          ↓
field can be null        dep is final → never null
after construction       after construction
```

Three reasons:

**1. Dep is `final` — can never be null or reassigned after creation**
```java
private final PaymentService payment;  // guaranteed to be set, can never change
```

**2. Object cannot exist without its dependencies — enforced at compile time**
```java
// Spring cannot call this constructor without a PaymentService bean
public OrderService(PaymentService payment) { ... }
```

**3. Testable with plain Java — no Spring context needed**
```java
// constructor injection — works without Spring
OrderService service = new OrderService(mockPayment);

// field injection — Spring sets it via reflection
// forced to start full Spring context just to run a unit test
```

---

## Constructor vs Setter vs Field

| | Constructor | Setter | Field |
|---|---|---|---|
| Dep can be `final` | yes | no | no |
| Dep always present | yes | no | no |
| Testable without Spring | yes | yes | no |
| Recommended | default choice | optional deps only | avoid |

---

## Field vs Setter injection — what's the difference?

They look the same (both set a field after construction) but differ in **how Spring sets the value** and **what you can do with it**.

```
Field injection            Setter injection
──────────────────         ──────────────────────────────
Spring uses reflection     Spring calls your method
to set the field           which sets the field
directly — your code
is never involved          you wrote the method → you control it
```

**Field injection:**
```java
@Autowired
private PaymentService payment;   // Spring reaches in and sets this directly
                                  // you wrote nothing — Spring did everything
```

**Setter injection:**
```java
private PaymentService payment;

@Autowired
public void setPayment(PaymentService payment) {  // Spring calls THIS method
    this.payment = payment;                        // which sets the field
}
```

The real differences show up here:

| | Field | Setter |
|---|---|---|
| You can add logic | No | Yes |
| You can add validation | No | Yes |
| Dep can be optional | No | Yes (`@Autowired(required=false)`) |
| Testable without Spring | No | Yes (just call the setter) |
| Dep can be null | Yes | Yes |

**Setter gives you a hook — field doesn't:**
```java
@Autowired
public void setPayment(PaymentService payment) {
    if (payment == null) throw new IllegalArgumentException("...");
    this.payment = payment;
    this.payment.init();   // can do extra work here
}
```

With field injection, Spring bypasses your code entirely via reflection — you can't add any of that.

**In practice:** both are worse than constructor injection. Field injection is the worst of the three — setter at least gives you a method to call in tests without Spring.

### So setter has more flexibility — does that make it better?

Setter gives you a **method body** — you can put anything in it:

```java
@Autowired
public void setPayment(PaymentService payment) {
    // validate
    // log
    // call payment.init()
    // set conditionally
}
```

Field injection has no method — Spring sets it silently, you get no say.

But flexibility doesn't make setter better than constructor:

```
Constructor          Setter
────────────         ────────────────────────────
dep is final         dep can be null (set late)
always present       may never be set → NPE risk
fail at startup      fail at runtime
```

Setter's flexibility is useful for **optional dependencies**. If the dep is required, constructor is safer — setter's extra power is solving a problem constructor doesn't have in the first place.

---

## AOP proxy — why same-class calls bypass @Retryable, @Transactional, @Async

Spring AOP annotations (`@Retryable`, `@Transactional`, `@Async`, `@Cacheable`) work via a **proxy** — a wrapper Spring puts around your bean. Calling the method from within the same class bypasses the proxy entirely.

```
External call                    Internal call (same class)
─────────────────────────        ─────────────────────────
Other class calls                this.methodB()
  paymentService.methodA()               ↓
         ↓                       calls method DIRECTLY
  hits Spring proxy              bypasses proxy entirely
         ↓                                ↓
  @Retryable fires               @Retryable never fires
  retries on failure             no retry happens
```

```
┌─────────────────────────────────────────────────┐
│  Spring Proxy (has @Retryable logic)            │
│                                                 │
│   ┌─────────────────────────────────────────┐  │
│   │  PaymentService (real object)           │  │
│   │                                         │  │
│   │   methodA() {                           │  │
│   │       this.methodB()  ← goes here       │  │
│   │   }             directly, skips proxy   │  │
│   │                                         │  │
│   │   @Retryable                            │  │
│   │   methodB() { ... }                     │  │
│   │                                         │  │
│   └─────────────────────────────────────────┘  │
│                                                 │
└─────────────────────────────────────────────────┘

External call → hits proxy → @Retryable fires ✓
Internal call → skips proxy → @Retryable ignored ✗
```

Fix — move the `@Retryable` method to a separate bean so the call goes through the proxy:

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
        helper.methodB();   // goes through proxy → retry works
    }
}

@Service
public class PaymentHelper {
    @Retryable
    public void methodB() { ... }
}
```

Same rule applies to all AOP-based annotations — `@Transactional`, `@Async`, `@Cacheable` all fail when called from within the same class.

---

## Circular dependency

```
BeanA constructor needs BeanB
BeanB constructor needs BeanA
         ↓
BeanCurrentlyInCreationException at startup
```

Fix: extract a third class, or restructure responsibilities.
Setter injection can mask the error but doesn't fix the design problem.

---

## Factory Method Injection

### 1. `@Bean` method (most common)

A `@Bean` method in `@Configuration` is a factory method — Spring calls it, takes the returned object, and manages it as a bean.

```java
@Configuration
public class AppConfig {

    @Bean
    public PaymentService paymentService() {
        return new UpiPayment();  // you control creation
    }
}
```

```
Spring calls AppConfig.paymentService()
       ↓
   gets UpiPayment instance
       ↓
   registers it as bean "paymentService"
       ↓
   injects wherever PaymentService is @Autowired
```

Use `@Bean` over `@Component` when:
- You don't own the class (third-party library)
- Creation needs complex logic or config values
- You want to control the exact instance returned

---

### Private constructor — use @Bean with static factory method

If a class has a private constructor and can only be created via a static factory method, you cannot use `@Component` — Spring calls the constructor directly and can't access a private one.

```
@Component   Spring calls the constructor directly
             → constructor is private → Spring can't access it → fails

@Bean        Spring calls YOUR method
             → your method calls DatabaseConnection.create()
             → you control how it's created → works ✓
```

Use `@Bean` wrapping the static factory method:

```java
@Configuration
public class AppConfig {

    @Bean
    public DatabaseConnection databaseConnection() {
        return DatabaseConnection.create("jdbc:postgresql://localhost/db");
    }
}
```

```
Spring calls AppConfig.databaseConnection()
         ↓
calls DatabaseConnection.create(url)   ← static factory
         ↓
gets the instance → registers as a Spring bean → injectable anywhere
```

### 2. Static factory method on the class

```java
public class DatabaseConnection {
    private final String url;

    private DatabaseConnection(String url) { this.url = url; }

    public static DatabaseConnection create(String url) {  // static factory
        return new DatabaseConnection(url);
    }
}

// wire it via @Bean
@Bean
public DatabaseConnection dbConnection() {
    return DatabaseConnection.create("jdbc:postgresql://localhost/db");
}
```

---

### 3. `FactoryBean<T>` — complex creation logic

Spring's dedicated interface when a `@Bean` method isn't enough:

```java
@Component
public class ConnectionPoolFactory implements FactoryBean<ConnectionPool> {

    @Override
    public ConnectionPool getObject() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost/db");
        config.setMaximumPoolSize(10);
        return new HikariDataSource(config);  // multi-step creation
    }

    @Override
    public Class<?> getObjectType() {
        return ConnectionPool.class;
    }
}
```

```
Spring sees ConnectionPoolFactory implements FactoryBean<ConnectionPool>
       ↓
   calls getObject() to get the actual bean
       ↓
   registers ConnectionPool as the bean — not the factory itself
```

Inject it normally — you receive what `getObject()` returned:

```java
@Autowired
ConnectionPool pool;  // Spring gives you ConnectionPool, not the factory
```

### Two @Bean methods where one calls the other — one instance or two?

**One instance** — Spring intercepts the method call via a CGLIB proxy.

```java
@Configuration
public class AppConfig {

    @Bean
    public Engine engine() {
        return new Engine();       // instance created here
    }

    @Bean
    public Car car() {
        return new Car(engine());  // looks like a normal method call
    }
}
```

Looks like `engine()` is called twice → 2 instances. But Spring intercepts:

```
car() calls engine()
         ↓
Spring proxy intercepts the call
         ↓
is there already a bean named "engine"?
         ↓
YES → return the existing instance
         ↓
Car gets the SAME Engine instance — not a new one
```

`@Configuration` classes are wrapped in a **CGLIB proxy** at startup. Every `@Bean` method call goes through the proxy — which checks the bean container first before creating a new instance.

```
@Configuration + CGLIB proxy   → method calls intercepted → singleton guaranteed
@Component                      → no proxy → calling engine() creates a new instance
```

Key rule:
```
@Configuration   → @Bean methods always return the same instance
@Component       → avoid calling @Bean methods from other @Bean methods
                   Spring won't intercept → new instance created each time
```

### Bean vs Instance — are they the same?

```
Bean     = a registered object in Spring's container (has a name)
Instance = the actual Java object in memory
```

For singleton scope: 1 Bean = 1 Instance. But they mean different things:

```java
@Bean
public Engine engine() { ... }    // Bean 1 — named "engine"

@Bean
public Car car() { ... }          // Bean 2 — named "car"
```

2 beans ARE created — `engine` and `car`. The question is which Engine instance goes into Car:

```
Spring creates:
  Bean 1 → engine  (1 Engine instance)
  Bean 2 → car     (1 Car instance)
                        ↓
              Car needs an Engine inside it
                        ↓
              does Spring create a NEW Engine? → NO
              it reuses the SAME Engine bean already created
```

```
Beans created:      2  (engine + car)       ← two separate beans
Engine instances:   1  (shared, not new)    ← Engine object not duplicated
```

```
engine bean  ──────────────────────┐
                                   ▼
car bean     ──── contains ──►  same Engine object
```

Two beans, but the Engine object inside Car is not a new one — it is the same object already registered as the `engine` bean.

### All three compared

```
Method             When to use
────────────────   ─────────────────────────────────────────
@Component         you own the class, simple creation
@Bean method       third-party class OR needs config/args
Static factory     class hides its constructor, has own factory
FactoryBean<T>     complex multi-step creation with lifecycle
```

---

## Inject all implementations — List vs Map

### List injection

```java
@Component
public class PaymentRouter {
    private final List<PaymentService> allPayments;

    public PaymentRouter(List<PaymentService> allPayments) {
        this.allPayments = allPayments;
        // Spring injects [UpiPayment, CardPayment] automatically
    }
}
```

Use when you need to **run all implementations** (e.g. run all validators, notify all listeners).

### Map injection

```java
@Component
public class PaymentRouter {
    private final Map<String, PaymentService> paymentMap;

    public PaymentRouter(Map<String, PaymentService> paymentMap) {
        this.paymentMap = paymentMap;
        // {"upiPayment" → UpiPayment, "cardPayment" → CardPayment}
    }

    public void pay(String method, double amount) {
        paymentMap.get(method + "Payment").pay(amount);  // dynamic dispatch
    }
}
```

Use when you need to **pick one impl by name at runtime** (strategy pattern with dynamic key).

### Runtime selection — why @Qualifier won't work

If the user picks an implementation at runtime, `@Qualifier` is the wrong tool:

```
Write time  =  you, the developer, typing the code
               you already know which impl you want
               hardcoded in the source file — never changes unless you edit it

Runtime     =  app is running, user is interacting
               you don't know in advance which impl they'll pick
```

```java
// Write time — YOU decided "petrolEngine" while writing this code
public Car(@Qualifier("petrolEngine") Engine engine) { ... }

// Runtime — user sends "upiPayment" in a request, you don't know this until app runs
public void pay(String method) {
    paymentMap.get(method).pay();   // method comes from user input
}
```

```
@Qualifier        → you decided at write time  → fixed forever
Map<String, T>    → user decides at runtime    → changes per request
```

```
@Qualifier         decided at startup — fixed, hardcoded at compile time
Map<String, T>     decided at runtime — user picks by name
```

Use `Map<String, PaymentService>` — Spring builds it automatically from all beans of that type:

```java
@Component
public class PaymentRouter {

    private final Map<String, PaymentService> paymentMap;

    public PaymentRouter(Map<String, PaymentService> paymentMap) {
        this.paymentMap = paymentMap;
        // {"upiPayment" → UpiPayment, "cardPayment" → CardPayment, "netBanking" → NetBanking}
    }

    public void pay(String method, double amount) {
        PaymentService service = paymentMap.get(method);  // user picks at runtime
        if (service == null) throw new IllegalArgumentException("Unknown method: " + method);
        service.pay(amount);
    }
}
```

```
Startup:   Spring builds the map automatically from all PaymentService beans
Runtime:   user sends "upiPayment" → map.get("upiPayment") → UpiPayment.pay()
```

Map key = bean name, which defaults to class name in camelCase:

```
UpiPayment  → "upiPayment"
CardPayment → "cardPayment"
NetBanking  → "netBanking"
```

### @Qualifier vs List — which is the correct design?

**`List<Notification>` is the correct design when you want ALL implementations.**

```
@Qualifier approach          List<Notification> approach
───────────────────────      ───────────────────────────
picks ONE specific bean      gets ALL beans automatically
add Slack? → edit 3 files    add Slack? → add 1 file only
3 fields + 3 methods         1 field + 1 method
bean naming traps (SMS bug)  no @Qualifier → no naming issues
```

With `@Qualifier` you must type the exact bean name — human error is possible:
```java
@Qualifier("sMSNotification")   // ❌ wrong — Spring named it SMSNotification
@Qualifier("SMSNotification")   // ✅ correct — but easy to get wrong
```

With `List<Notification>` you never write `@Qualifier` — Spring finds all beans automatically:
```java
private final List<Notification> notifications;   // no names, no traps
```

```
@Qualifier   you are responsible for knowing the exact bean name → error possible
List<T>      Spring finds all beans of that type → no names involved → no errors
```

Use `@Qualifier` when you want ONE specific impl at write time.
Use `List<T>` when you want ALL impls — send to all channels, run all validators, notify all listeners.

### Single vs List vs Map — when to choose

```
Need                                    Use
──────────────────────────────────      ──────────────────────────────
One specific impl, always the same      Single bean (@Primary/@Qualifier)
Run ALL impls (validators, listeners)   List<T>
Pick ONE impl by name at runtime        Map<String, T>
```

```
List<PaymentService>          Map<String, PaymentService>
─────────────────────         ──────────────────────────────
[UpiPayment,                  {"upiPayment"  → UpiPayment,
 CardPayment]                  "cardPayment" → CardPayment}
  ↓                              ↓
iterate all                   pick one by name
```

---

## Performance — Single vs Collection injection

**Short answer: no performance difference.** Spring singleton beans are created at startup — all of them, always — regardless of how you inject them.

```
Startup
  ├─ creates UpiPayment    (singleton — 1 instance in memory)
  ├─ creates CardPayment   (singleton — 1 instance in memory)
  └─ creates NetBanking    (singleton — 1 instance in memory)
          │
          ▼
Single injection:  orderService.payment = upiPayment         ← same object
List injection:    [upiPayment, cardPayment, netBanking]      ← same objects
Map injection:     {"upi"→upiPayment, "card"→cardPayment}    ← same objects
```

Collection injection is just a **different view** of beans already in memory — no extra instantiation, no extra cost.

### When it DOES become a concern

If you have many implementations but only ever use one:

```
10 PaymentService impls — all created at startup
You only ever call UpiPayment

→ 9 beans created and never used → wasted startup time + memory
```

Fix with `@Lazy` — defer creation until first use:

```java
@Component
@Lazy
public class RarelyUsedPayment implements PaymentService { ... }
```

Or `@ConditionalOnProperty` — only register if a config flag is on:

```java
@Component
@ConditionalOnProperty(name = "payment.crypto.enabled", havingValue = "true")
public class CryptoPayment implements PaymentService { ... }
```

---

## Duplicate bean names — what happens?

If two `@Configuration` classes both define a bean with the same name, behaviour depends on Spring Boot version:

**Spring Boot 2.x and earlier — last one wins (silent overwrite):**
```
Config1 defines "dataSource" → registered
Config2 defines "dataSource" → overwrites Config1
no error — silent overwrite
```

**Spring Boot 3.x — throws error by default:**
```
BeanDefinitionOverrideException:
Invalid bean definition with name 'dataSource'
```

Spring Boot 3 made overriding strict because silent overwrites caused hard-to-debug bugs — you thought you were using one `dataSource` but actually got a different one.

You can still allow it explicitly:
```properties
# application.properties
spring.main.allow-bean-definition-overriding=true
```

```
Spring Boot 2   → last one wins (silent)  ← dangerous
Spring Boot 3   → exception at startup    ← safe, forces you to fix it
```

**Fix — give them different names:**
```java
@Configuration
public class Config1 {
    @Bean("primaryDataSource")     // explicit name
    public DataSource dataSource() { ... }
}

@Configuration
public class Config2 {
    @Bean("secondaryDataSource")   // different name
    public DataSource dataSource() { ... }
}
```

---

## @Configuration vs @Component for @Bean methods

Looks identical but behaviour is completely different:

```java
// @Configuration
@Configuration
public class AppConfig {
    @Bean
    public Engine engine() { return new Engine(); }

    @Bean
    public Car car() { return new Car(engine()); }  // calls engine()
}

// @Component
@Component
public class AppConfig {
    @Bean
    public Engine engine() { return new Engine(); }

    @Bean
    public Car car() { return new Car(engine()); }  // looks the same
}
```

```
@Configuration                   @Component
──────────────────────────       ──────────────────────────
CGLIB proxy wraps the class      NO proxy — plain class

engine() call intercepted        engine() call NOT intercepted
Spring checks container first    plain Java method call
returns existing bean            creates NEW Engine object

Car gets SAME Engine bean        Car gets a DIFFERENT Engine
1 Engine instance total          2 Engine instances total
```

This is called **Full mode** vs **Lite mode**:

```
@Configuration = Full mode   → @Bean methods intercepted → singleton guaranteed
@Component     = Lite mode   → @Bean methods NOT intercepted → new instance each call
```

When one `@Bean` method calls another:
```java
@Bean
public Car car() {
    return new Car(engine());  // ← this call behaves differently
}
```
- `@Configuration` → `engine()` returns existing bean — safe ✓
- `@Component` → `engine()` creates new Engine — breaks singleton ✗

**Rule:**
```
Always use @Configuration when @Bean methods call each other
@Component is fine when @Bean methods are completely independent
```

---

## Managing third-party classes with @Bean

You can't add `@Component` to a class you don't own. Use `@Bean` inside a `@Configuration` class instead:

```java
@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
```

Spring manages it as a singleton bean, injectable anywhere:

```java
@Component
public class OrderService {

    private final ObjectMapper mapper;

    public OrderService(ObjectMapper mapper) {  // injected normally
        this.mapper = mapper;
    }
}
```

`@Bean` also lets you configure the object before handing it to Spring — you can't do this with `@Component`:

```java
@Bean
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.registerModule(new JavaTimeModule());
    return mapper;
}
```

---

## @Component vs @Service vs @Repository vs @Controller

All four are stereotype annotations — they all do the same thing as `@Component` (register the class as a Spring bean). The difference is **semantic** — they tell the reader what role the class plays.

```
Annotation      Used for                    Layer
────────────    ────────────────────────    ─────────────
@Component      generic bean, no role       any
@Service        business logic              service layer
@Repository     database operations         data layer
@Controller     handle HTTP requests        web layer
```

```
HTTP Request
     ↓
@Controller      ← receives request, returns response
     ↓
@Service         ← business logic, rules
     ↓
@Repository      ← talks to database
```

One extra thing `@Repository` does that others don't — **exception translation**.

Spring wraps your `@Repository` class in a **proxy** at startup. That proxy intercepts every method call, catches any DB exceptions, and translates them:

```
Your code calls repo.save()
         ↓
Spring proxy intercepts the call
         ↓
calls the real repo.save()
         ↓
DB throws SQLException
         ↓
proxy catches it
         ↓
translates → DataAccessException
         ↓
your service layer receives DataAccessException
```

Without `@Repository`:
```
repo.save() → SQLException       ← raw JDBC exception, DB-specific
```

With `@Repository`:
```
repo.save() → DataAccessException  ← Spring's exception, DB-agnostic
```

Why this matters — every DB throws its own exception type:

```
MySQL throws:       com.mysql.jdbc.exceptions.jdbc4.CommunicationsException
PostgreSQL throws:  org.postgresql.util.PSQLException
MongoDB throws:     com.mongodb.MongoException

With @Repository all become → DataAccessException
                                      ↓
your service catches one exception type
regardless of which DB you use
```

So if you switch from MySQL to PostgreSQL, your exception handling code doesn't change — Spring normalises it for you.

---

## @Component vs @Bean

```
@Component                        @Bean
──────────────────────────        ──────────────────────────────
you own the class                 you don't own the class
Spring finds it automatically     you write a method that returns it
via @ComponentScan                inside a @Configuration class
annotation on the class           annotation on the method
```

```java
// @Component — annotation on the class itself
@Component
public class PetrolEngine implements Engine { ... }

// @Bean — annotation on a method inside @Configuration
@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {   // Jackson's class — you don't own it
        return new ObjectMapper();
    }
}
```

**Control over creation** — the key extra difference:

```
@Component   Spring controls how the object is created (calls default constructor)
@Bean        YOU control how the object is created — can pass args, set config, etc.
```

```java
@Bean
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);  // custom config
    return mapper;
}
```

You can't do that with `@Component` — Spring just calls the default constructor.

---

## @Lazy — defer bean creation to first use

By default Spring creates all singleton beans at startup. Use `@Lazy` to delay creation until the bean is first requested:

```java
@Lazy
@Component
public class ReportGenerator {
    // created only when first requested, not at startup
}
```

```
Without @Lazy                With @Lazy
─────────────────────        ─────────────────────────────
app starts                   app starts
  ↓                            ↓
Spring creates ALL beans      Spring skips ReportGenerator
including ReportGenerator       ↓
even if never used           first time someone injects it
                               ↓
                             Spring creates it then
```

Two places you can put `@Lazy`:

```java
// Option 1 — on the bean itself (lazy for everyone)
@Lazy
@Component
public class ReportGenerator { ... }

// Option 2 — on the injection point (lazy only for this consumer)
public class ReportService {
    public ReportService(@Lazy ReportGenerator generator) { ... }
}
```

When to use `@Lazy`:
```
heavy bean        — expensive to create (DB connections, file loading)
rarely used       — not needed on every request
optional feature  — only used in specific scenarios
```

---

## When NOT to use DI

Not everything needs to be a Spring bean:

```java
// BAD — no state, no collaborators, no reason to be a bean
@Component
public class MathUtils {
    public int add(int a, int b) { return a + b; }
}

// GOOD — pure function, just make it static
public class MathUtils {
    public static int add(int a, int b) { return a + b; }
}
```

Use DI for objects that have **state**, **collaborators**, or need to be **swapped/mocked**.
Skip DI for: pure utility functions, value objects (`Money`, `Address`), simple data holders.

---

## @ComponentScan — how Spring finds beans

`@SpringBootApplication` auto-scans the package it lives in **and all sub-packages**.

The scan root is the **package of the class where `@SpringBootApplication` lives**:

```
@SpringBootApplication lives here:
com.example.springcore.SpringCoreApp
         ↓
scan root = com.example.springcore
```

Anything that starts with `com.example.springcore` is inside:

```
com.example.springcore.di.PetrolEngine        ✓  starts with com.example.springcore
com.example.springcore.payments.UpiPayment    ✓  starts with com.example.springcore

com.other.payments.ThirdPartyPay              ✗  does NOT start with com.example.springcore
com.example.order.OrderService                ✗  does NOT start with com.example.springcore
```

Think of it like a folder — Spring only looks inside the scan root folder:

```
com.example.springcore          ← Spring only looks INSIDE this folder
  └── di/
  └── payments/
  └── config/

com.other/                      ← Spring never opens this folder
com.example.order/              ← Spring never opens this folder
```

The full package prefix must match — not just part of it. `com.example.order` fails even though it shares `com.example` — Spring does not do partial matches.

What happens if a bean is outside the scan root:

```
Spring never finds it
       ↓
bean is never created
       ↓
NoSuchBeanDefinitionException at startup
when something tries to inject it
```

Two ways to fix it:

**Option 1 — move the class inside the scan root** (preferred):
```
com.example.springcore.payments.ThirdPartyPay   ← now inside scan root ✓
```

**Option 2 — explicitly tell Spring where to scan:**
```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.example.springcore", "com.other.payments"})
public class SpringCoreApp { ... }
```

---

## @DependsOn — controlling bean creation order

Spring creates beans in any order it wants at startup. `@DependsOn` tells Spring — "create this other bean first, before me."

```
Without @DependsOn:
  Spring creates beans in any order
  DatabaseConfig might start before SchemaInitializer
  → database accessed before schema is created → error

With @DependsOn:
  Spring sees @DependsOn("schemaInitializer") on DatabaseConfig
  → creates schemaInitializer first
  → then creates DatabaseConfig
  → schema is ready before database is accessed
```

```java
// must run first — creates tables
@Component
public class SchemaInitializer {
    public SchemaInitializer() {
        System.out.println("Creating tables...");
    }
}

// must run after SchemaInitializer
@Component
@DependsOn("schemaInitializer")   // wait for this bean first
public class DatabaseConfig {
    public DatabaseConfig() {
        System.out.println("Connecting to database...");
    }
}
```

```
Without @DependsOn          With @DependsOn
─────────────────────       ─────────────────────────
any order                   schemaInitializer first
DatabaseConfig might           ↓
start first → crash         DatabaseConfig second → safe
```

When to use it:
```
bean B needs bean A to have run some startup logic first
but B does not directly @Autowire A
— Spring doesn't know about the dependency
— @DependsOn makes the implicit dependency explicit
```

If B directly injects A via `@Autowired`, Spring already knows to create A first — `@DependsOn` is not needed. Only use it when the dependency is **implicit** (not via injection).

**Explicit dependency — Spring knows automatically:**
```java
@Component
public class DatabaseConfig {

    private final SchemaInitializer schema;

    public DatabaseConfig(SchemaInitializer schema) {  // directly injecting
        this.schema = schema;
    }
}
```

```
Spring sees constructor needs SchemaInitializer
         ↓
Spring knows: create SchemaInitializer first, then DatabaseConfig
         ↓
no @DependsOn needed — Spring figured it out from the injection
```

**Implicit dependency — Spring has no idea:**
```java
@Component
public class SchemaInitializer {
    public SchemaInitializer() {
        System.out.println("Creating tables...");  // creates DB tables as side effect
    }
}

@Component
public class DatabaseConfig {
    public DatabaseConfig() {
        System.out.println("Connecting...");  // needs tables to exist
        // does NOT inject SchemaInitializer
        // Spring has no idea DatabaseConfig needs SchemaInitializer first
    }
}
```

```
Spring sees DatabaseConfig constructor — no parameters
         ↓
Spring thinks: no dependencies, can create in any order
         ↓
DatabaseConfig created before SchemaInitializer → tables don't exist → crash
```

The dependency exists — but only as a **side effect** (tables created during construction), not as an injection. Spring cannot see it.

```
Explicit dependency   = A is injected into B via constructor/@Autowired
                        Spring sees it → creates A first automatically

Implicit dependency   = A must run before B for a side effect
                        but B does not inject A
                        Spring cannot see it → @DependsOn needed
```

```
Explicit = you told Spring directly "I need A"
Implicit = you secretly need A to have run, but never told Spring
           @DependsOn = telling Spring the secret
```

---

## Optional dependencies — two ways to handle

**Option 1 — Setter injection with `@Autowired(required = false)`:**
```java
@Component
public class ReportService {

    private EmailService email;

    @Autowired(required = false)
    public void setEmail(EmailService email) {
        this.email = email;
    }

    public void generateReport() {
        if (email != null) {
            email.send("Report ready");   // only if present
        }
    }
}
```

**Option 2 — Constructor injection with `Optional<T>`** (preferred in modern Spring):
```java
@Component
public class ReportService {

    private final Optional<EmailService> email;

    public ReportService(Optional<EmailService> email) {
        this.email = email;
    }

    public void generateReport() {
        email.ifPresent(e -> e.send("Report ready"));
    }
}
```

```
Option 1 — setter + required=false    dep is null if missing, you check manually
Option 2 — Optional<T>               makes optionality explicit in the signature
                                      reader knows immediately it may be absent
```

Option 2 is preferred — `Optional<T>` makes the intent clear in the constructor signature itself. With setter, someone reading the code might not realise the dep is optional.

## Optional dependency (`required = false`)

```java
@Autowired(required = false)
private SmsService sms;   // null if no SmsService bean exists — won't crash
```

Use sparingly. Prefer redesigning so the dep either always exists or isn't needed at all.

---

## Testability — the real payoff of constructor injection

```java
// Unit test — zero Spring context, runs in milliseconds
class OrderServiceTest {
    @Test
    void should_charge_correct_amount() {
        PaymentService mockPayment = Mockito.mock(PaymentService.class);
        OrderService service = new OrderService(mockPayment);  // plain Java

        service.placeOrder("item-1", 500);

        Mockito.verify(mockPayment).pay(500);
    }
}
```

With **field injection** you'd need `@SpringBootTest` to boot the whole context just to run this — 10–30x slower.

---

## Code location

`study-projects/spring-core/src/main/java/com/example/springcore/di/`

| File | Demonstrates |
|------|-------------|
| `Engine.java` | dependency interface |
| `PetrolEngine.java` | concrete implementation |
| `ConstructorInjectionCar.java` | constructor injection |
| `SetterInjectionCar.java` | setter injection |
| `DiDemo.java` | runs both and prints output |
