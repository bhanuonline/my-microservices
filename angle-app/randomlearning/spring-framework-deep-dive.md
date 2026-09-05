# Spring Framework Deep Dive — Beans, AOP, Transactions, Boot Lifecycle

A comprehensive reference on Spring's internals: the IoC container, bean lifecycle, AOP proxies, transaction management, Spring Boot startup, and 40+ interview questions from junior to expert.

---

## What is Spring?

**Spring Framework** (created by Rod Johnson, 2003) is a Java application framework built on two core ideas:

1. **Inversion of Control (IoC)** — the framework manages your object graph; you don't `new` things yourself
2. **Aspect-Oriented Programming (AOP)** — cross-cutting concerns (transactions, security, logging) are woven into your code without cluttering it

Spring gives you:
- **Container** — manages object lifecycle and dependencies
- **AOP** — declarative transactions, caching, security, custom aspects
- **Data access** — JDBC template, JPA integration, transaction management
- **Web** — Spring MVC, WebFlux
- **Boot** — auto-configuration, embedded servers, "just run" applications

**Spring vs Spring Boot:**
- **Spring** — the framework (IoC, AOP, MVC, etc.)
- **Spring Boot** — opinionated auto-configuration + embedded server on top of Spring; production-ready in minutes

---

# PART 1 — BEANS AND THE IOC CONTAINER

## 1. What is a Bean?

A **Spring bean** is any Java object whose lifecycle Spring manages. Spring:
- Creates the bean
- Wires its dependencies
- Applies configuration (proxies, interceptors)
- Manages its lifecycle (initialization, destruction)

## 2. The IoC container — `ApplicationContext`

The container that holds beans is called an **ApplicationContext**. Common implementations:

| Class | Use case |
|---|---|
| `ClassPathXmlApplicationContext` | XML-based config (legacy) |
| `AnnotationConfigApplicationContext` | Java-based config with `@Configuration` |
| `AnnotationConfigWebApplicationContext` | Web apps |
| `GenericWebApplicationContext` | Modern web apps |
| Boot's `SpringApplication` | Handles it for you |

## 3. Ways to define beans

### 3.1. XML (legacy, still supported)

```xml
<bean id="orderService" class="com.acme.OrderService">
    <constructor-arg ref="orderRepository"/>
</bean>
```

### 3.2. Java configuration (recommended)

```java
@Configuration
public class AppConfig {

    @Bean
    public OrderService orderService(OrderRepository repo) {
        return new OrderService(repo);
    }

    @Bean
    public OrderRepository orderRepository(DataSource ds) {
        return new JdbcOrderRepository(ds);
    }
}
```

### 3.3. Component scanning + stereotype annotations

Spring scans the classpath for annotated classes and registers them as beans.

```java
@Component
public class OrderService { ... }

@Service      // semantically = @Component, for service layer
public class OrderService { ... }

@Repository   // = @Component + JPA/JDBC exception translation
public class JpaOrderRepository { ... }

@Controller   // = @Component, for web MVC
public class OrderController { ... }

@RestController   // = @Controller + @ResponseBody
public class OrderRestController { ... }
```

Enable scanning:
```java
@Configuration
@ComponentScan("com.acme")
public class AppConfig { }
```

Spring Boot's `@SpringBootApplication` does this automatically for the package containing the main class.

## 4. Dependency Injection

Three ways to inject dependencies:

### 4.1. Constructor injection (RECOMMENDED)

```java
@Service
public class OrderService {
    private final OrderRepository repo;

    // No @Autowired needed since Spring 4.3 if there's only one constructor
    public OrderService(OrderRepository repo) {
        this.repo = repo;
    }
}
```

**Pros:**
- Fields can be `final` (immutable, thread-safe)
- Cannot construct with `null` dependencies
- Easy to test (just pass mocks)
- Circular dependencies detected at startup (not runtime)

### 4.2. Setter injection

```java
@Service
public class OrderService {
    private OrderRepository repo;

    @Autowired
    public void setRepo(OrderRepository repo) { this.repo = repo; }
}
```

**Use for:** optional dependencies, reconfigurable state.

### 4.3. Field injection (AVOID)

```java
@Service
public class OrderService {
    @Autowired
    private OrderRepository repo;   // NOT final!
}
```

**Problems:**
- Fields can't be `final`
- Requires reflection for testing (or a DI framework)
- Hidden dependencies (compilable without repo)
- Prone to circular-dependency issues at runtime

**Rule:** always use **constructor injection** for required dependencies.

## 5. Autowiring by type vs by name

By default, Spring autowires by **type**. If multiple beans of the same type exist, resolution needs a hint:

```java
@Bean
public DataSource primaryDataSource() { ... }

@Bean
public DataSource replicaDataSource() { ... }

// Ambiguity! Resolve with:
@Autowired
@Qualifier("primaryDataSource")
private DataSource ds;

// Or mark one as primary:
@Bean
@Primary
public DataSource primaryDataSource() { ... }
```

## 6. Bean scopes

| Scope | Meaning |
|---|---|
| **singleton** (default) | One instance per container |
| **prototype** | New instance each time it's requested |
| **request** | One per HTTP request (web only) |
| **session** | One per HTTP session (web only) |
| **application** | One per ServletContext (web only) |
| **websocket** | One per WebSocket session |

Usage:
```java
@Bean
@Scope("prototype")
public HeavyObject heavyObject() { return new HeavyObject(); }
```

### 6.1. The singleton-injecting-prototype gotcha

```java
@Service
public class OrderProcessor {
    @Autowired private HeavyObject heavy;   // prototype
    // BUG: heavy is injected ONCE at singleton construction — not new per call
}
```

**Fix options:**
1. `ApplicationContext.getBean(HeavyObject.class)` per call
2. `@Lookup` method injection
3. `ObjectFactory<HeavyObject>` or `Provider<HeavyObject>`

```java
@Autowired
private ObjectFactory<HeavyObject> factory;

public void process() {
    HeavyObject fresh = factory.getObject();   // new each time
}
```

---

# PART 2 — BEAN LIFECYCLE (THE COMPLETE PICTURE)

Understanding this is the single biggest differentiator between mid and senior Spring developers.

## The 10 lifecycle phases

```
1. Bean definition loaded
        ↓
2. Instantiation (constructor call)
        ↓
3. Populate properties (dependency injection)
        ↓
4. Aware-interface callbacks
        ↓
5. BeanPostProcessor.postProcessBeforeInitialization
        ↓
6. @PostConstruct methods
        ↓
7. InitializingBean.afterPropertiesSet()
        ↓
8. Custom init-method (init-method="..." or @Bean(initMethod=...))
        ↓
9. BeanPostProcessor.postProcessAfterInitialization
        ↓
10. Bean ready for use
        ↓
(later, on container shutdown)
        ↓
11. @PreDestroy methods
        ↓
12. DisposableBean.destroy()
        ↓
13. Custom destroy-method
```

## Code demonstrating each phase

```java
public class LifecycleDemo implements
    InitializingBean, DisposableBean,
    BeanNameAware, BeanFactoryAware, ApplicationContextAware {

    // Phase 2: Instantiation
    public LifecycleDemo() {
        System.out.println("2. Constructor");
    }

    // Phase 3: Property injection
    @Autowired
    public void setDependency(OtherBean d) {
        System.out.println("3. Property injection");
    }

    // Phase 4: Aware callbacks
    @Override
    public void setBeanName(String name) {
        System.out.println("4a. BeanNameAware: " + name);
    }

    @Override
    public void setBeanFactory(BeanFactory bf) {
        System.out.println("4b. BeanFactoryAware");
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        System.out.println("4c. ApplicationContextAware");
    }

    // Phase 6: @PostConstruct
    @PostConstruct
    public void postConstruct() {
        System.out.println("6. @PostConstruct");
    }

    // Phase 7: InitializingBean
    @Override
    public void afterPropertiesSet() {
        System.out.println("7. afterPropertiesSet");
    }

    // Phase 8: custom init
    public void init() {
        System.out.println("8. Custom init method");
    }

    // Phase 11: @PreDestroy
    @PreDestroy
    public void preDestroy() {
        System.out.println("11. @PreDestroy");
    }

    // Phase 12: DisposableBean
    @Override
    public void destroy() {
        System.out.println("12. destroy");
    }

    // Phase 13: custom destroy
    public void cleanup() {
        System.out.println("13. Custom destroy method");
    }
}
```

## BeanPostProcessor — the extension point

`BeanPostProcessor` runs code **before** and **after** each bean's initialization. This is how AOP, `@Autowired`, `@Value`, and most Spring magic happens.

```java
@Component
public class TimingPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String name) {
        System.out.println("Before init: " + name);
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        // AOP proxies are typically created here!
        return bean;
    }
}
```

**Key insight:** `postProcessAfterInitialization` is where Spring may **replace your bean with a proxy** (for AOP, transactions, etc.). This is why AOP works transparently.

## Difference between `BeanFactoryPostProcessor` and `BeanPostProcessor`

| | BeanFactoryPostProcessor | BeanPostProcessor |
|---|---|---|
| Operates on | Bean definitions (metadata) | Bean instances (objects) |
| When | Before any bean is instantiated | For each bean, after instantiation |
| Example use | `PropertySourcesPlaceholderConfigurer`, modifying scope | AOP proxies, `@Autowired` injection |

---

# PART 3 — AOP (ASPECT-ORIENTED PROGRAMMING)

## The problem AOP solves

Some concerns cut across many classes: transactions, security, logging, metrics, caching. Without AOP:

```java
public void placeOrder(Order o) {
    long start = System.nanoTime();
    log.info("Placing order {}", o.id());
    try {
        securityCheck();
        beginTransaction();
        try {
            // ...actual business logic — 2 lines...
            commit();
        } catch (Exception e) {
            rollback();
            throw e;
        }
    } finally {
        log.info("Took {}ms", (System.nanoTime() - start) / 1_000_000);
    }
}
```

Every method has this scaffolding. AOP moves it out.

## AOP concepts

| Concept | Meaning |
|---|---|
| **Aspect** | A class encapsulating a cross-cutting concern (`@Aspect`) |
| **Join Point** | A point in program execution (a method call, exception thrown). In Spring AOP: only method executions |
| **Pointcut** | An expression selecting which join points to advise |
| **Advice** | The code to run at a pointcut |
| **Weaving** | The process of applying aspects to code |
| **Proxy** | Runtime object Spring inserts to intercept calls |
| **Target** | The original object being advised |

## Types of advice

```java
@Aspect
@Component
public class LoggingAspect {

    @Before("execution(* com.acme..*Service.*(..))")
    public void before(JoinPoint jp) {
        System.out.println("BEFORE: " + jp.getSignature());
    }

    @After("execution(* com.acme..*Service.*(..))")
    public void after(JoinPoint jp) {
        System.out.println("AFTER (always): " + jp.getSignature());
    }

    @AfterReturning(pointcut = "execution(* com.acme..*Service.*(..))",
                     returning = "result")
    public void afterReturning(JoinPoint jp, Object result) {
        System.out.println("SUCCESS: " + result);
    }

    @AfterThrowing(pointcut = "execution(* com.acme..*Service.*(..))",
                    throwing = "ex")
    public void afterThrowing(JoinPoint jp, Exception ex) {
        System.out.println("ERROR: " + ex.getMessage());
    }

    @Around("execution(* com.acme..*Service.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            return result;
        } finally {
            System.out.println((System.nanoTime() - start) + " ns");
        }
    }
}
```

## Pointcut expression language

```java
// All public methods in service package
execution(public * com.acme.service.*.*(..))

// All methods returning String
execution(String com.acme..*(..))

// Methods with @Transactional annotation
@annotation(org.springframework.transaction.annotation.Transactional)

// Methods on classes annotated with @Service
within(@org.springframework.stereotype.Service *)

// Methods with any argument of type Order
args(com.acme.Order, ..)

// Combined with && || !
execution(* com.acme..*(..)) && @annotation(Auditable)
```

## Proxies — JDK Dynamic vs CGLIB

Spring AOP uses **runtime proxies**. Two proxy types:

### JDK Dynamic Proxy
- Works only if the target implements an **interface**
- Creates a proxy that implements the same interface
- Cannot proxy `final` classes or `final` methods

### CGLIB Proxy
- Creates a **runtime subclass** of the target
- Works on classes without interfaces
- Cannot proxy `final` classes or `final` methods (can't subclass/override)
- Cannot proxy `private` methods

Spring Boot defaults to CGLIB since 2.x. Force JDK dynamic:
```
spring.aop.proxy-target-class=false
```

## The self-invocation gotcha

**This is the #1 AOP interview question.**

```java
@Service
public class OrderService {

    @Transactional
    public void placeOrder(Order o) {
        // ...
        this.audit(o);   // ← DIRECT method call — bypasses proxy!
    }

    @Transactional(propagation = REQUIRES_NEW)
    public void audit(Order o) {
        // expected to run in a new transaction — but DOESN'T
    }
}
```

**Why:** the proxy wraps external calls to `placeOrder`. Inside the method, `this` is the **target object**, not the proxy — so `this.audit()` bypasses AOP entirely. The `REQUIRES_NEW` propagation on `audit` is ignored.

**Fixes:**
1. Move `audit` to a separate bean and inject it
2. Get the proxy: `((OrderService) AopContext.currentProxy()).audit(o)` (with `@EnableAspectJAutoProxy(exposeProxy = true)`)
3. Self-injection (self-reference bean):
```java
@Autowired @Lazy private OrderService self;
public void placeOrder(Order o) { self.audit(o); }
```

## Common Spring AOP use cases

- `@Transactional` — transaction management
- `@Cacheable` / `@CacheEvict` — caching
- `@Async` — asynchronous execution (returns immediately)
- `@Retryable` — automatic retry on failure
- `@PreAuthorize` / `@PostAuthorize` — Spring Security
- Custom `@Auditable`, `@Timed`, `@LogExecution` aspects

---

# PART 4 — TRANSACTIONS

## The core annotation

```java
@Service
public class OrderService {

    @Transactional
    public void placeOrder(Order o) {
        orderRepo.save(o);
        inventoryRepo.reserve(o.productId(), o.qty());
        // If either throws → rollback; else → commit
    }
}
```

`@Transactional` is implemented via AOP — a proxy wraps the method, opens a transaction on entry, commits or rolls back on exit.

## `@Transactional` attributes

### Propagation — what to do if a transaction already exists

| Propagation | Behavior |
|---|---|
| **REQUIRED** (default) | Join existing tx, or create one if none |
| **REQUIRES_NEW** | Suspend existing tx, start a new one |
| **NESTED** | Nested transaction (savepoint) — rollback only inner |
| **SUPPORTS** | Use tx if exists, else run non-tx |
| **NOT_SUPPORTED** | Suspend existing tx, run non-tx |
| **MANDATORY** | Must have existing tx; throw if not |
| **NEVER** | Must NOT have tx; throw if one exists |

### Isolation — how transactions see each other

| Level | Reads | Prevents |
|---|---|---|
| **READ_UNCOMMITTED** | Dirty reads possible | (nothing) |
| **READ_COMMITTED** | Only committed data | Dirty reads |
| **REPEATABLE_READ** | Same data on re-read within tx | Dirty, non-repeatable reads |
| **SERIALIZABLE** | Full isolation | All read anomalies |
| **DEFAULT** | Uses DB default (usually READ_COMMITTED for Postgres, REPEATABLE_READ for MySQL) | |

### rollbackFor — which exceptions trigger rollback

**Default:** rollback on **unchecked** exceptions (`RuntimeException`, `Error`) only. **Checked exceptions do NOT rollback by default.**

```java
@Transactional(rollbackFor = Exception.class)   // rollback on any exception
public void placeOrder() throws IOException { ... }

@Transactional(noRollbackFor = OrderNotFoundException.class)
public void placeOrder() { ... }
```

### Other attributes

```java
@Transactional(
    propagation = Propagation.REQUIRED,
    isolation = Isolation.READ_COMMITTED,
    timeout = 30,                        // seconds
    readOnly = true,                     // optimization hint
    rollbackFor = { IOException.class }
)
```

## Common transaction gotchas

### Gotcha 1 — self-invocation bypasses `@Transactional`

Same as the AOP gotcha above. `this.method()` inside a class does not go through the proxy — `@Transactional` on that method is ignored.

### Gotcha 2 — checked exceptions don't rollback

```java
@Transactional
public void save() throws SQLException {
    repo.save(...);
    if (badState) throw new SQLException();   // rollback? NO.
}
```

**Fix:** `@Transactional(rollbackFor = Exception.class)`, or throw a `RuntimeException`.

### Gotcha 3 — `@Transactional` on private methods does nothing

Proxies can't intercept private methods. Must be public (or protected, if you enable `TransactionalProxyConfiguration.mode = ASPECTJ`).

### Gotcha 4 — final method → CGLIB can't proxy

```java
@Service
public class OrderService {
    @Transactional
    public final void placeOrder() { ... }   // CGLIB can't override this!
}
```

At startup Spring throws or silently skips. **Fix:** remove `final`.

### Gotcha 5 — `catch` swallows exception → no rollback

```java
@Transactional
public void placeOrder() {
    try {
        // ...
        throw new RuntimeException();
    } catch (Exception e) {
        log.error("failed", e);
        // exception swallowed → tx commits!
    }
}
```

Even if you want to log, either rethrow or explicitly `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`.

### Gotcha 6 — `REQUIRES_NEW` starts a new physical transaction

If you nest `REQUIRES_NEW` inside `REQUIRED`, the outer is **suspended** while the inner runs. If the inner commits successfully but the outer rolls back, the inner's changes are **preserved**.

### Gotcha 7 — connection pool starvation with `REQUIRES_NEW`

Each `REQUIRES_NEW` holds an additional connection. Deeply nested `REQUIRES_NEW` under load can exhaust the connection pool → deadlock.

### Gotcha 8 — Long transactions kill performance

A `@Transactional` method that also does REST calls or reads files keeps the DB connection open for the whole method. Rule: keep transactions short and CPU-only if possible.

## How Spring picks the transaction manager

```java
@Configuration
@EnableTransactionManagement
public class DbConfig {

    @Bean
    public PlatformTransactionManager transactionManager(DataSource ds) {
        return new DataSourceTransactionManager(ds);   // JDBC
    }

    // For JPA:
    // @Bean public JpaTransactionManager transactionManager(EntityManagerFactory emf) {...}

    // For distributed / XA:
    // @Bean public JtaTransactionManager transactionManager() {...}
}
```

You can have **multiple** transaction managers, choose per `@Transactional`:
```java
@Transactional("secondaryTxManager")
```

---

# PART 5 — SPRING BOOT LIFECYCLE

## What Spring Boot adds

Spring Boot is Spring Framework + three big features:

1. **Auto-configuration** — configures beans based on classpath contents
2. **Starters** — curated dependency bundles (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`)
3. **Embedded servers** — Tomcat/Jetty/Undertow bundled in the jar (no separate app server needed)
4. **Actuator** — production endpoints (`/actuator/health`, `/actuator/metrics`)

## `@SpringBootApplication` decomposed

```java
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

`@SpringBootApplication` is a meta-annotation combining:

```java
@SpringBootConfiguration        // = @Configuration + Boot marker
@EnableAutoConfiguration         // triggers auto-config
@ComponentScan                   // scans this package and below
public @interface SpringBootApplication { }
```

## Startup phases

```
1. SpringApplication.run() called
        ↓
2. Determine application type (WEB, REACTIVE, NONE) from classpath
        ↓
3. Load bootstrap context (config, secrets)
        ↓
4. Publish ApplicationStartingEvent
        ↓
5. Prepare environment (load application.properties/yml, profiles)
        ↓
6. Publish ApplicationEnvironmentPreparedEvent
        ↓
7. Create ApplicationContext (e.g., AnnotationConfigServletWebServerApplicationContext)
        ↓
8. Publish ApplicationContextInitializedEvent
        ↓
9. Load bean definitions (auto-configurations + your beans)
        ↓
10. Publish ApplicationPreparedEvent
        ↓
11. Refresh context (instantiate & wire all beans; run BeanPostProcessors)
        ↓
12. Start embedded server (if web app) — servlet container starts, port opens
        ↓
13. Publish ApplicationStartedEvent
        ↓
14. Run CommandLineRunners and ApplicationRunners
        ↓
15. Publish ApplicationReadyEvent — YOUR APP IS READY
        ↓
(later, on shutdown)
        ↓
16. Publish ApplicationContextClosedEvent
        ↓
17. Destroy beans (in reverse order of creation)
```

## Auto-configuration — how it works

The core mechanism:

1. `@EnableAutoConfiguration` loads `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Spring Boot 3.x) or `META-INF/spring.factories` (2.x)
2. Each listed configuration is a `@Configuration` class annotated with `@ConditionalOn...`
3. Conditions are evaluated; matching configurations register beans

Example:
```java
@Configuration
@ConditionalOnClass(DataSource.class)
@ConditionalOnMissingBean(DataSource.class)
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceAutoConfiguration {

    @Bean
    public DataSource dataSource(DataSourceProperties p) {
        return DataSourceBuilder.create()
                                 .url(p.getUrl())
                                 .username(p.getUsername())
                                 .password(p.getPassword())
                                 .build();
    }
}
```

Translation: "If `DataSource` class is on classpath **AND** no `DataSource` bean already defined, create one from `application.properties`."

## Common conditional annotations

| Annotation | Fires if |
|---|---|
| `@ConditionalOnClass` | The specified class is on the classpath |
| `@ConditionalOnMissingClass` | The specified class is NOT on the classpath |
| `@ConditionalOnBean` | A bean of that type already exists |
| `@ConditionalOnMissingBean` | No bean of that type exists — **most common in auto-config** |
| `@ConditionalOnProperty` | A property is set (e.g., `mail.enabled=true`) |
| `@ConditionalOnWebApplication` | Running in web context |
| `@ConditionalOnExpression` | SpEL expression evaluates to true |
| `@ConditionalOnJava` | JVM version matches |

## Override auto-configuration

Define your own bean of the same type — auto-config's `@ConditionalOnMissingBean` backs off.

```java
@Configuration
public class MyConfig {

    @Bean
    public DataSource dataSource() {
        return new HikariDataSource();   // your custom DataSource
    }
    // Auto-config sees this bean exists → skips its own DataSource
}
```

## Property injection

```java
# application.properties
app.max-retries=3
app.rate-limit-per-minute=100
```

```java
@Value("${app.max-retries}")
private int maxRetries;
```

Better — `@ConfigurationProperties`:

```java
@ConfigurationProperties("app")
public record AppProperties(int maxRetries, int rateLimitPerMinute) {}

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig { }
```

Now `AppProperties` is a fully-typed, validated bean.

## Profiles

Different beans/configs per environment.

```java
@Profile("dev")
@Bean
public DataSource devDataSource() { return new H2DataSource(); }

@Profile("prod")
@Bean
public DataSource prodDataSource() { return new HikariDataSource(); }
```

Activation:
```
spring.profiles.active=prod
```

Or command line: `--spring.profiles.active=prod`.

Or environment: `SPRING_PROFILES_ACTIVE=prod`.

## CommandLineRunner / ApplicationRunner

Run code after bean initialization but before app is "ready":

```java
@Component
public class DataSeeder implements CommandLineRunner {
    @Override
    public void run(String... args) {
        // seed initial data
    }
}
```

`ApplicationRunner` is similar but receives `ApplicationArguments` (typed CLI args).

## Actuator endpoints

Add `spring-boot-starter-actuator` and expose:

```
management.endpoints.web.exposure.include=health,info,metrics,env,prometheus
```

Common endpoints:
- `/actuator/health` — liveness, readiness (Kubernetes hooks)
- `/actuator/metrics` — JVM, HTTP, custom metrics
- `/actuator/env` — environment variables
- `/actuator/beans` — all registered beans
- `/actuator/mappings` — HTTP request mappings
- `/actuator/loggers` — change log levels at runtime
- `/actuator/prometheus` — Prometheus scrape endpoint

## Graceful shutdown

```
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

On `SIGTERM`, Boot:
1. Stops accepting new requests
2. Waits for in-flight requests to complete (up to timeout)
3. Runs `@PreDestroy` on beans in reverse creation order
4. Exits

Essential for zero-downtime deploys.

---

# PART 6 — INTERVIEW QUESTIONS

## Basic

**Q1. What is Inversion of Control (IoC)?**
A design principle where the framework controls object creation and dependency wiring, not your code. You don't `new` your dependencies — Spring does it and injects them. Enables loose coupling, easier testing, and centralized configuration.

**Q2. What is a Spring Bean?**
Any Java object whose lifecycle is managed by the Spring container (`ApplicationContext`). Beans are created, wired, configured, and destroyed by Spring.

**Q3. Difference between `@Component`, `@Service`, `@Repository`, `@Controller`?**
All are equivalent to `@Component` (Spring registers them as beans). They differ semantically for readability and by adding role-specific behavior:
- `@Service` — service layer (no extra behavior; documentation)
- `@Repository` — DAO layer; adds automatic exception translation for JPA/JDBC exceptions
- `@Controller` — web MVC controllers; enables handler mapping
- `@RestController` — `@Controller` + `@ResponseBody` for REST APIs

**Q4. Difference between `@Autowired` and `@Inject`?**
Functionally equivalent. `@Autowired` is Spring-specific; `@Inject` is JSR-330 (standard Java). Prefer `@Autowired` (or better, no annotation with constructor injection).

**Q5. What are the bean scopes?**
`singleton` (default, one per container), `prototype` (new every request), `request`, `session`, `application`, `websocket` (web scopes).

**Q6. What is Dependency Injection (DI)?**
A pattern where dependencies are provided from outside a class (via constructor, setter, or field) rather than created inside. DI is one implementation of the Dependency Inversion Principle.

**Q7. Constructor vs setter vs field injection — which is best?**
**Constructor injection** — dependencies are `final`, immutable, testable without reflection, and circular dependencies fail at startup (not runtime). Setter for optional dependencies. Field injection is discouraged — hard to test without Spring.

## Intermediate

**Q8. Explain the Spring bean lifecycle.**
Instantiation → property injection → aware callbacks → BeanPostProcessor.before → `@PostConstruct` → `InitializingBean.afterPropertiesSet` → custom init → BeanPostProcessor.after → ready. On shutdown: `@PreDestroy` → `DisposableBean.destroy` → custom destroy.

**Q9. What is a `BeanPostProcessor`?**
An extension point that runs code before and after each bean's initialization. Used to add behavior (like AOP proxies), validate beans, or inject values. It's how `@Autowired`, `@Value`, and AOP work under the hood.

**Q10. What's the difference between `BeanPostProcessor` and `BeanFactoryPostProcessor`?**
`BeanFactoryPostProcessor` operates on **bean definitions (metadata)** before any beans are instantiated. `BeanPostProcessor` operates on **instantiated beans** — one round of pre/post per bean.

**Q11. What is AOP?**
Aspect-Oriented Programming: a way to modularize cross-cutting concerns (transactions, security, logging) by applying them to methods without changing the methods themselves. Spring AOP uses runtime proxies to intercept method calls.

**Q12. Explain the AOP terms: Aspect, Advice, Pointcut, Join Point.**
- **Aspect** — class encapsulating the concern (annotated `@Aspect`)
- **Advice** — code that runs (`@Before`, `@After`, `@Around`)
- **Pointcut** — expression selecting which methods to advise (`execution(...)`, `@annotation(...)`)
- **Join Point** — a specific place execution can be intercepted (in Spring AOP, only method executions)

**Q13. Difference between JDK dynamic proxy and CGLIB?**
JDK dynamic proxy: works only if target implements an interface; creates a proxy implementing the same interface. CGLIB: creates a runtime subclass; works on classes without interfaces but can't proxy `final` classes/methods. Spring Boot 2.x+ defaults to CGLIB.

**Q14. Why does `@Transactional` not work on self-invocation?**
Because `@Transactional` is implemented via a proxy. `this.method()` bypasses the proxy — it calls the target directly. The transaction annotation on the internal method has no effect.

**Q15. What's the default rollback rule for `@Transactional`?**
Rollback only on **unchecked exceptions** (`RuntimeException`, `Error`). Checked exceptions do NOT trigger rollback unless you specify `rollbackFor`.

## Senior

**Q16. Explain transaction propagation modes.**
- **REQUIRED** (default) — join existing tx or create one
- **REQUIRES_NEW** — suspend outer, start new tx
- **NESTED** — savepoint inside existing tx; can roll back inner alone
- **SUPPORTS** — use tx if exists, else non-tx
- **MANDATORY** — must have existing tx (throw if not)
- **NOT_SUPPORTED** — suspend tx, run non-tx
- **NEVER** — throw if tx exists

**Q17. What's the difference between `REQUIRED` and `REQUIRES_NEW`?**
`REQUIRED` joins the existing outer transaction — inner rollback rolls back the outer too. `REQUIRES_NEW` suspends the outer and starts a fresh one — inner commits/rolls back independently. Beware connection pool starvation with nested `REQUIRES_NEW`.

**Q18. How does Spring Boot auto-configuration work?**
`@EnableAutoConfiguration` loads a list of `@Configuration` classes from `META-INF/spring/...AutoConfiguration.imports` (Boot 3.x) or `META-INF/spring.factories` (2.x). Each is annotated with `@ConditionalOn...` conditions. Matching configs are applied — usually with `@ConditionalOnMissingBean` so your custom beans win.

**Q19. How do you override an auto-configured bean?**
Declare your own bean of the same type. Auto-config's `@ConditionalOnMissingBean` sees your bean exists and backs off.

**Q20. What's the difference between `@Bean` and `@Component`?**
`@Bean` — declared inside a `@Configuration` class; you write the factory method explicitly. Used for third-party classes or complex construction. `@Component` — Spring finds it via component scanning and instantiates via the no-arg constructor (or one it picks).

**Q21. When does `@ConfigurationProperties` beat `@Value`?**
For groups of related properties, type safety, validation with `@Validated`, and IDE-friendly config metadata. Use `@Value` for one-off individual properties.

**Q22. What's the difference between `CommandLineRunner` and `ApplicationRunner`?**
Both run after context refresh. `CommandLineRunner` receives raw `String[] args`; `ApplicationRunner` receives typed `ApplicationArguments` (option flags, non-option args).

**Q23. Explain the difference between Spring MVC and Spring WebFlux.**
Spring MVC — servlet-based, one thread per request (blocking I/O). Spring WebFlux — reactive, non-blocking, uses `Mono`/`Flux`. WebFlux scales to more concurrent requests but demands reactive code all the way down (no `Thread.sleep`, no blocking JDBC).

**Q24. How does Spring choose between JDK proxy and CGLIB?**
By default (Spring Boot): CGLIB always. Force JDK by setting `spring.aop.proxy-target-class=false`. When the target class implements interfaces, JDK proxy is available. Both use `@EnableAspectJAutoProxy` for AOP.

**Q25. Explain the "circular dependency" problem in Spring.**
Bean A depends on B; B depends on A. With constructor injection, Spring detects this at startup and throws `BeanCurrentlyInCreationException`. With field/setter injection, Spring can resolve it via three-level cache (early references), but the design is smelly — refactor.

## Expert

**Q26. What are the three levels of Spring's singleton bean cache?**
Used to resolve circular dependencies with setter/field injection:
- `singletonObjects` — fully initialized beans
- `earlySingletonObjects` — beans in creation but wrapped for early reference
- `singletonFactories` — object factories that create early references

Constructor injection can't use this — the bean can't exist without its constructor completing.

**Q27. What is `@Import`?**
Includes other `@Configuration` classes (or plain `@Component`s or `ImportSelector`s) into the current context. Used heavily by Boot's auto-configuration — enables composing configuration.

**Q28. Explain `@Conditional` and give an example.**
Spring 4+ mechanism to conditionally register beans based on runtime conditions. Used by Boot's `@ConditionalOnClass`, `@ConditionalOnMissingBean`, etc. Custom conditions implement `Condition.matches()`.

```java
public class OnLinuxCondition implements Condition {
    public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata m) {
        return System.getProperty("os.name").toLowerCase().contains("linux");
    }
}
@Bean
@Conditional(OnLinuxCondition.class)
public LinuxSpecificBean bean() { return new LinuxSpecificBean(); }
```

**Q29. Explain `@Async` — how does it work, and what are the gotchas?**
`@Async` methods run in a background thread pool. Under the hood: same proxy mechanism as `@Transactional`. Gotchas:
- Self-invocation bypasses `@Async`
- Must enable with `@EnableAsync`
- Configure the executor pool explicitly (default is single-threaded up to Boot 2.x)
- Return `void` or `CompletableFuture<T>` (not raw `T`)
- Exceptions in `void` methods are lost — use `CompletableFuture` and handle in caller

**Q30. What is the ApplicationEventPublisher pattern?**
Spring's built-in Observer pattern. Publish `ApplicationEvent` instances; `@EventListener` methods on any bean receive them. Synchronous by default; use `@Async` on the listener for async.

```java
@Component
public class OrderService {
    @Autowired ApplicationEventPublisher publisher;
    public void place() { publisher.publishEvent(new OrderPlaced(...)); }
}

@Component
public class EmailNotifier {
    @EventListener
    public void on(OrderPlaced event) { sendConfirmationEmail(...); }
}
```

**Q31. What is the difference between `@Configuration` and `@Component`?**
Both register beans. `@Configuration` classes use CGLIB proxying so calls to `@Bean` methods return the singleton bean (not a new instance every call). `@Component` doesn't proxy — every method call creates a new object.

Use `@Configuration` for classes with multiple `@Bean` methods that reference each other:
```java
@Configuration
public class Config {
    @Bean public A a() { return new A(); }
    @Bean public B b() { return new B(a()); }   // calls to a() return the SAME A singleton
}
```

**Q32. Explain `@Transactional` on a private method — does it work?**
No. Spring AOP proxies can only intercept public methods (or protected, if you configure it). Private methods bypass the proxy entirely. If you need transactions on private methods, use AspectJ compile-time weaving instead of proxies.

**Q33. What's the difference between `PlatformTransactionManager` and `ReactiveTransactionManager`?**
`PlatformTransactionManager` — blocking, imperative (Spring MVC, JDBC/JPA). `ReactiveTransactionManager` — non-blocking, for WebFlux + R2DBC. You can't mix them; a WebFlux app with JPA is fundamentally blocking.

**Q34. Explain lazy initialization in Spring.**
By default, singleton beans are eagerly initialized at container startup. `@Lazy` defers creation until first access. `@Lazy` at class level makes the bean lazy. `@Lazy` at injection point creates a proxy — the actual bean is instantiated on first method call. Boot property `spring.main.lazy-initialization=true` makes all beans lazy — good for startup time, bad for catching config errors early.

**Q35. How would you register beans at runtime programmatically?**
Use `GenericApplicationContext.registerBean(...)` or a `BeanDefinitionRegistryPostProcessor`. Or implement `ImportBeanDefinitionRegistrar` and reference via `@Import`.

---

# DEBUG SCENARIOS — spot the bug

## D1. Why doesn't `@Transactional` roll back?

```java
@Service
public class OrderService {
    @Transactional
    public void placeOrder() throws IOException {
        repo.save(order);
        throw new IOException("filesystem fail");
    }
}
```

**Bug:** `IOException` is checked. Default rollback rule = rollback only on unchecked exceptions.

**Fix:** `@Transactional(rollbackFor = Exception.class)` — or throw a `RuntimeException` wrapper.

---

## D2. Why doesn't `@Async` execute asynchronously?

```java
@Service
public class OrderService {
    public void placeOrder() {
        // ...
        sendEmail();   // supposed to run async
    }

    @Async
    public void sendEmail() { /* SMTP call */ }
}
```

**Bug:** self-invocation. `sendEmail()` inside `placeOrder()` bypasses the proxy.

**Fix:** move `sendEmail()` to a separate bean and inject it, or use self-injection with `@Lazy`.

---

## D3. Why is `HeavyObject` always the same instance?

```java
@Bean
@Scope("prototype")
public HeavyObject heavyObject() { return new HeavyObject(); }

@Service
public class Consumer {
    @Autowired private HeavyObject heavy;   // expected NEW every time
}
```

**Bug:** `Consumer` is singleton. Its `heavy` is injected **once** at construction — not new per use.

**Fix:** `@Autowired ObjectFactory<HeavyObject> factory;` and call `factory.getObject()` per use.

---

## D4. Why does `@PostConstruct` run before dependencies are ready?

```java
@Component
public class Foo {
    @Autowired private Bar bar;
    public Foo() {
        System.out.println(bar.something());   // NullPointerException
    }
}
```

**Bug:** `bar` is injected AFTER the constructor runs. Constructor sees uninitialized fields.

**Fix:** use constructor injection so `bar` is available at construction time; or move logic to `@PostConstruct`:
```java
@PostConstruct
public void init() { System.out.println(bar.something()); }
```

---

## D5. Why does the `@Cacheable` method not cache?

```java
@Service
public class ProductService {
    @Cacheable("products")
    public Product findById(String id) { /* DB query */ }

    public Product findAll() {
        return findById("root");   // internal call — cache bypassed
    }
}
```

**Bug:** self-invocation. Same AOP proxy gotcha.

**Fix:** move `findById` to a separate service, or self-inject.

---

## D6. Why does the app fail to start with a circular dependency error?

```java
@Service
public class A {
    private final B b;
    public A(B b) { this.b = b; }
}

@Service
public class B {
    private final A a;
    public B(A a) { this.a = a; }
}
```

**Bug:** constructor-injected circular dependency. Spring cannot resolve — throws `BeanCurrentlyInCreationException`.

**Fix:** refactor the design (usually indicates a missing 3rd class that both A and B use). If genuinely unavoidable, use setter/field injection (Spring's 3-level cache handles it), but this is a code smell.

---

## D7. Why does the DataSource not use my custom config?

```java
@Configuration
public class MyConfig {
    public DataSource dataSource() {   // MISSING @Bean!
        return new HikariDataSource(...);
    }
}
```

**Bug:** without `@Bean`, Spring doesn't register the method as a bean factory. Auto-configuration's default DataSource wins.

**Fix:** add `@Bean` annotation.

---

## D8. Why does `@Value` return null in a constructor?

```java
@Service
public class Foo {
    @Value("${app.retries}")
    private int retries;
    public Foo() {
        System.out.println(retries);   // prints 0, not 3
    }
}
```

**Bug:** `@Value` is field-injected AFTER the constructor runs.

**Fix:** inject via constructor:
```java
public Foo(@Value("${app.retries}") int retries) { this.retries = retries; }
```

---

## D9. Two `DataSource` beans, ambiguous injection error at startup

```java
@Bean public DataSource primary() { ... }
@Bean public DataSource replica() { ... }

@Service
public class MyService {
    @Autowired private DataSource ds;   // AMBIGUOUS!
}
```

**Bug:** two beans of the same type, no disambiguation.

**Fix:** mark one `@Primary`, or use `@Qualifier`:
```java
@Autowired @Qualifier("primary") private DataSource ds;
```

---

## D10. `@Transactional` on a `final` method throws at startup

```java
@Service
public class OrderService {
    @Transactional
    public final void placeOrder() { }
}
```

**Bug:** CGLIB tries to subclass `OrderService` to add the transaction proxy, but can't override a `final` method.

**Fix:** remove `final`. (This is Q10 in the `final` interview file — final methods can't be intercepted by CGLIB.)

---

## D11. `@EventListener` for domain events runs before DB commit

```java
@Component
class OrderService {
    @Transactional
    public void place(Order o) {
        repo.save(o);
        publisher.publishEvent(new OrderPlaced(o));
        // event handler runs SYNCHRONOUSLY — sees uncommitted DB state
    }
}

@EventListener
public void on(OrderPlaced e) {
    // reads from DB — may not find the order yet!
}
```

**Bug:** synchronous `@EventListener` runs before transaction commit.

**Fix:** `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` — listener fires only after commit.

---

## D12. Bean initialization runs on every request

```java
@Bean
@Scope("prototype")
public UserContext userContext() {
    return new UserContext(SecurityContextHolder.getContext());
}

@RestController
class MyController {
    @Autowired private UserContext ctx;   // injected ONCE at startup
}
```

**Bug:** `UserContext` should be per-request but is injected once into a singleton controller.

**Fix:** use `@RequestScope`, or inject an `ObjectFactory<UserContext>`, or use scoped proxy:
```java
@Bean @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public UserContext userContext() { ... }
```

---

# QUICK-FIRE RAPID ROUND

| Question | Answer |
|---|---|
| Default bean scope? | singleton |
| Best DI mechanism? | Constructor injection |
| What triggers rollback by default? | Unchecked exceptions only |
| Default propagation? | REQUIRED |
| Spring Boot's proxy default? | CGLIB |
| Can `@Transactional` be on private methods? | No |
| Can `@Async` self-invoke? | No — bypasses proxy |
| `@Bean` vs `@Component`? | `@Bean` is explicit; `@Component` is scanned |
| `@Configuration` uses which proxy? | CGLIB (for `@Bean` method dedup) |
| What's `@SpringBootApplication` = ? | `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan` |
| How to override auto-config? | Define your own bean of the same type |
| Bean lifecycle callbacks in order? | `@PostConstruct` → `afterPropertiesSet` → custom `init-method` |
| ApplicationContext vs BeanFactory? | ApplicationContext = BeanFactory + more features (events, i18n, resource loading) |
| Which event fires when app is fully ready? | `ApplicationReadyEvent` |
| Spring Boot property source order? | Command line > env vars > `application-{profile}.properties` > `application.properties` |
| `@Value` vs `@ConfigurationProperties`? | Single value vs typed group |
| Static classes and Spring? | Spring can't inject into static fields |
| `@Autowired required=false`? | Injects null if no bean found |
| Boot's `spring.factories` replaced by? | `AutoConfiguration.imports` in Boot 3.x |
| How to graceful-shutdown Boot? | `server.shutdown=graceful` |

---

# ONE-SENTENCE SUMMARIES

- **IoC container** — the `ApplicationContext` that creates, wires, and manages beans
- **Bean** — any Java object whose lifecycle Spring manages
- **Dependency Injection** — dependencies provided from outside; constructor injection preferred
- **Bean scopes** — singleton (default), prototype, request, session, application
- **Bean lifecycle** — instantiate → inject → aware → `@PostConstruct` → init → ready → `@PreDestroy` → destroy
- **BeanPostProcessor** — extension point that adds behavior (like AOP proxies) to every bean
- **AOP** — cross-cutting concerns via runtime proxies; think transactions, security, logging
- **Aspect / Advice / Pointcut / Join Point** — the class / code / expression / place
- **JDK vs CGLIB proxy** — interface-based vs subclass-based; CGLIB is Boot's default
- **Self-invocation gotcha** — internal `this.method()` calls bypass the proxy
- **`@Transactional`** — declarative transactions via AOP; rollback on unchecked only by default
- **Propagation** — REQUIRED (join or create), REQUIRES_NEW (always new), NESTED (savepoint), etc.
- **Auto-configuration** — Boot registers beans based on classpath + `@ConditionalOn...` rules
- **`@SpringBootApplication`** — `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`
- **`ApplicationReadyEvent`** — fires when Boot app is fully started and ready to serve
- **Graceful shutdown** — `SIGTERM` → drain requests → destroy beans → exit

---

## The interview-safe summary you can drop verbatim

> Spring Framework is built on two ideas: Inversion of Control (the container manages your object graph — you don't `new` dependencies) and Aspect-Oriented Programming (cross-cutting concerns like transactions and security are applied via runtime proxies without cluttering business code). Beans are Java objects Spring manages through a well-defined lifecycle — instantiation, dependency injection, aware callbacks, `@PostConstruct`, initialization, and finally destruction on shutdown. AOP works by wrapping beans in proxies (JDK dynamic for interfaces, CGLIB for classes) — which is why self-invocation of `@Transactional` or `@Async` methods bypasses the proxy and silently fails. `@Transactional` supports propagation (REQUIRED, REQUIRES_NEW, NESTED, etc.) and isolation levels, but only rolls back on unchecked exceptions by default. Spring Boot layers on auto-configuration — `@ConditionalOnMissingBean`-guarded `@Configuration` classes that register sensible defaults, always yielding to your own beans if you define them. The startup lifecycle publishes events (ApplicationStarting, ContextRefreshed, ApplicationReady) that let you hook in at each phase, and graceful shutdown ensures in-flight requests complete before beans are destroyed.
