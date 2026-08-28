# Builder Design Pattern — Interview Ready Notes

> Complete reference: types, examples, comparisons, tricky questions, and real-world usage.

---

## Quick Cheat Sheet *(last-minute revision)*

| Type | One Line | Use When |
|---|---|---|
| Fluent / Inner Static | Chained setters, `build()` at end | New class, many optional fields |
| Step Builder | Interfaces enforce order | Field order matters, required steps |
| Classic GoF (Director) | Director controls construction | Same steps → multiple output formats |
| Telescoping Fix | Replace messy constructors | Existing class, 4+ constructor params |
| Recursive Builder | Builder works across inheritance | Parent + child class hierarchy |

---

## 1. Fluent Inner Static Builder

A nested `Builder` class with chained setters, returned via `build()`. The most common modern pattern.

**When to use**
- Objects with many optional fields
- HTTP request / API client configuration
- Database query objects
- Test data / fixtures
- Fresh new class designed from scratch

**Example**
```java
new HttpRequest.Builder("https://api.example.com")
    .method("POST")
    .header("Authorization", "Bearer xyz")
    .timeout(10)
    .build();
```

---

## 2. Step Builder (Staged Builder)

Each step returns the next step's interface — the compiler enforces the correct order. You cannot call `build()` until all required steps are done.

**Three files required**

| File | Role |
|---|---|
| `Connection.java` | Product class — final immutable object, all fields `final`, created only by `build()` |
| `Steps.java` | Four interfaces, one per required step, each returns the next interface |
| `ConnectionBuilder.java` | Implements all four step interfaces, compiler prevents wrong order |

**When to use**
- Wizard / multi-step forms
- Database connection setup
- Order / checkout flows
- When field order has business logic

**Example**
```java
Connection conn = ConnectionBuilder.builder()
    .host("localhost")    // Step 1 — required
    .port(5432)           // Step 2 — required
    .database("mydb")     // Step 3 — required
    .username("admin")    // optional
    .password("secret")   // optional
    .build();
```

---

## 3. Classic GoF Builder (with Director)

A separate `Director` class controls construction. Useful when multiple builders share the same construction algorithm but produce different products.

**When to use**
- Report generation — PDF, HTML, CSV
- Document export pipelines
- Game character creation
- Multiple output formats from one blueprint

**Example**
```java
ReportDirector director = new ReportDirector();

ReportBuilder pdf = new PdfReportBuilder();
director.constructAnnualReport(pdf);   // same steps → PDF

ReportBuilder html = new HtmlReportBuilder();
director.constructAnnualReport(html);  // same steps → HTML
```

---

## 4. Telescoping Constructor Fix

Replaces a mess of overloaded constructors with a builder. The most practical real-world reason to use the pattern.

**When to use**
- Any existing class with 4+ constructor parameters
- Configs with many optional fields
- SDK / library public APIs
- When callers cannot tell what positional arguments mean

**Before (avoid this)**
```java
new Pizza("large", true, false, true)
// What does true, false, true even mean??
```

**After (builder)**
```java
new Pizza.Builder("large")
    .cheese()
    .mushrooms()
    .build();
```

---

## 5. Telescoping Fix vs Fluent / Inner Static

These two look almost identical in code. The difference is the **situation** you are in — not the code you write.

| Aspect | Telescoping Fix | Fluent / Inner Static |
|---|---|---|
| Starting point | Existing class with messy constructors | Fresh new class designed from scratch |
| Goal | Refactor / clean up old code | Design readable API from the beginning |
| Object complexity | Simple — boolean flags, optional params | Rich — configs, headers, nested values |
| Validation in `build()` | ✅ Can have (no restriction) | ✅ Can have (no restriction) |
| Code structure | Identical to Fluent builder | Identical to Telescoping fix |

### Simple decision rule

```
Existing class with messy constructors?  →  Use Telescoping Fix
Writing a brand new class from scratch?  →  Use Fluent / Inner Static
```

> **Key insight:** Fluent/Inner Static is the _"do it right from the beginning"_ version of Telescoping Fix.
> Both use identical code structure — the only difference is whether you are **fixing** an existing problem or **preventing** one.

---

## 6. Builder vs Other Patterns *(very common interview question)*

### Builder vs Factory Pattern

| | Builder | Factory |
|---|---|---|
| How it creates | Step by step, you control each part | Single method call, one shot |
| Object complexity | Complex objects with many fields | Simple or family of objects |
| Who controls construction | Client (you chain the steps) | Factory method (hides the logic) |
| Returns | Same type always | Can return different subtypes |
| Example | `new Pizza.Builder().cheese().build()` | `ShapeFactory.create("circle")` |

> **Interview answer:** Use **Factory** when creation is simple and you want to hide the type. Use **Builder** when the object needs many configuration steps.

### Builder vs Prototype Pattern

| | Builder | Prototype |
|---|---|---|
| How it creates | Builds fresh from scratch | Clones an existing object |
| Use case | Complex construction logic | Expensive to create, cheap to clone |
| Example | Building a DB connection config | Copying a pre-configured template object |

> **Interview answer:** Use **Prototype** when creating is expensive (e.g. loading from DB) and you just need a copy. Use **Builder** when you need to construct something fresh with custom configuration.

### Builder vs Abstract Factory

| | Builder | Abstract Factory |
|---|---|---|
| Focus | Constructing one complex object | Creating families of related objects |
| Steps | Many steps, one product | One step, multiple related products |
| Example | Building one `House` object | Creating `Chair + Sofa + Table` (same style) |

---

## 7. Lombok `@Builder` *(practical, asked in Spring Boot interviews)*

Lombok auto-generates the entire builder for you at compile time.

```java
import lombok.Builder;

@Builder
public class User {
    private String name;
    private String email;
    private int age;
    private String role;
}

// Usage — Lombok generates all of this automatically:
User user = User.builder()
    .name("Priya")
    .email("priya@example.com")
    .age(28)
    .role("ADMIN")
    .build();
```

**Useful Lombok builder annotations**

| Annotation | Purpose |
|---|---|
| `@Builder` | Generates full inner static builder |
| `@Builder.Default` | Set default value for a field |
| `@Singular` | For collection fields — adds one item at a time |
| `@SuperBuilder` | Builder that works with inheritance (like recursive builder) |

```java
@Builder
public class Order {
    private String id;

    @Builder.Default
    private String status = "PENDING";   // default value

    @Singular
    private List<String> items;          // .item("apple").item("banana")
}

Order o = Order.builder()
    .id("ORD-001")
    .item("apple")
    .item("banana")
    .build();
// status defaults to "PENDING"
```

---

## 8. Real World Examples in Java & Android

| Class | Type | Notes |
|---|---|---|
| `StringBuilder` | Fluent builder | Most basic example — `.append().append().toString()` |
| `HttpClient.newBuilder()` | Fluent builder | Java 11 built-in HTTP client |
| `Stream.builder()` | Fluent builder | Build a stream element by element |
| `ProcessBuilder` | Fluent builder | Build and launch OS processes |
| `UriComponentsBuilder` | Fluent builder | Spring — build URLs step by step |
| `AlertDialog.Builder` | Fluent builder | Android — build dialog boxes |
| `DocumentBuilder` | Classic GoF | XML parsing in Java |

```java
// HttpClient — real Java 11 builder
HttpClient client = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)
    .connectTimeout(Duration.ofSeconds(10))
    .build();

// StringBuilder — simplest real-world builder
String result = new StringBuilder()
    .append("Hello")
    .append(", ")
    .append("World")
    .toString();
```

---

## 9. Thread Safety

**Is the Builder thread safe?**
No — by default the builder itself is NOT thread safe. Do not share a builder instance across threads.

**Is the built object thread safe?**
Yes — if all fields are `final` (immutable), the object produced by `build()` is thread safe.

```java
// Builder — NOT thread safe (don't share across threads)
Pizza.Builder builder = new Pizza.Builder("large");

// Built object — thread safe if immutable
Pizza pizza = builder.cheese().build();  // all fields final = safe to share
```

> **Interview answer:** The builder itself is mutable and not thread safe. But the object it produces — if designed with all `final` fields — is immutable and therefore thread safe.

---

## 10. When NOT to Use Builder *(interviewers love this)*

Builder is overkill for simple objects. Avoid it when:

- The object has only 1–2 parameters → just use a constructor
- All fields are required → a constructor with validation is cleaner
- The object is a simple data holder (DTO) → use a record (Java 16+) or plain class
- You need fast object creation in a loop → builder adds overhead

```java
// Overkill — don't do this
Point p = new Point.Builder().x(10).y(20).build();

// Just do this
Point p = new Point(10, 20);
```

---

## 11. Can Builder Create Immutable Objects? *(tricky question)*

**Yes** — this is actually one of the main advantages of the builder pattern.

```java
public class User {
    private final String name;    // final = immutable
    private final String email;   // final = immutable
    private final int age;        // final = immutable

    private User(Builder b) {
        this.name  = b.name;
        this.email = b.email;
        this.age   = b.age;
    }

    public static class Builder {
        private String name;
        private String email;
        private int age;

        public Builder name(String n)  { this.name = n;  return this; }
        public Builder email(String e) { this.email = e; return this; }
        public Builder age(int a)      { this.age = a;   return this; }

        public User build() {
            // validate before creating
            if (name == null || name.isEmpty())
                throw new IllegalStateException("name is required");
            return new User(this);
        }
    }
}
// User object is fully immutable once built
```

> **Key:** Make the product class constructor `private` and all fields `final`. Only the builder can create the object, and once created it cannot be changed.

---

## 12. Common Mistakes to Avoid

| Mistake | Why it's wrong | Fix |
|---|---|---|
| Making product constructor `public` | Anyone can bypass the builder | Make it `private` or package-private |
| Not validating in `build()` | Invalid objects get created silently | Add null checks and throw `IllegalStateException` |
| Sharing builder across threads | Builder is mutable, not thread safe | Create a new builder per thread |
| Using builder for simple 1-2 param objects | Overkill, adds complexity | Use plain constructor |
| Forgetting to call `build()` | You get a Builder, not the product | IDE warnings help here |
| Mutable fields in the product | Object is not truly immutable | Make all fields `final` |

---

## 13. Tricky Interview Questions & Answers

**Q: What is the difference between Builder and Factory pattern?**
> Builder constructs a complex object step by step — the client controls each part. Factory creates an object in one call and hides the creation logic. Use Factory for simple creation, Builder for complex multi-step construction.

**Q: Can you use Builder with inheritance?**
> Yes — use the Recursive/Hierarchical Builder with the Curiously Recurring Template Pattern (CRTP). The parent builder has a generic `self()` method that subclass builders override to return their own type, allowing chaining across parent and child methods without casting.

**Q: Is Builder a creational pattern?**
> Yes — Builder is one of the five GoF creational patterns alongside Singleton, Factory Method, Abstract Factory, and Prototype.

**Q: What problem does Builder solve that Factory does not?**
> Builder solves the problem of constructing objects that require many optional parameters or a specific construction sequence. Factory solves the problem of deciding which class to instantiate.

**Q: How does Lombok `@Builder` work internally?**
> Lombok processes the annotation at compile time and generates the inner static `Builder` class, all setter methods with chaining, and the `build()` method — the same code you would write by hand. No runtime overhead.

**Q: Can the same Builder produce different objects?**
> Yes — by resetting fields between calls or by using the Director pattern, the same builder can be reused to produce multiple different objects of the same type.

---

*Builder Design Pattern — Interview Ready Reference • Java*

---

## 14. How Does Factory Hide the Logic?

Without factory, the **caller decides** which class to create — tightly coupled:

```java
// Caller knows everything — bad
if (type.equals("circle")) {
    shape = new Circle(radius);
} else if (type.equals("square")) {
    shape = new Square(side);
} else if (type.equals("triangle")) {
    shape = new Triangle(base, height);
}
```

With factory, the **caller just asks** — factory decides internally:

```java
// Caller knows nothing about which class is created
Shape shape = ShapeFactory.create("circle", 5);
```

```java
// All the if-else logic is hidden inside the factory
public class ShapeFactory {
    public static Shape create(String type, double size) {
        switch (type) {
            case "circle":   return new Circle(size);
            case "square":   return new Square(size);
            case "triangle": return new Triangle(size);
            default: throw new IllegalArgumentException("Unknown: " + type);
        }
    }
}
```

**What exactly is hidden?**
- Which class is instantiated (`Circle`, `Square`, etc.)
- Constructor arguments and how they map
- Any setup logic after creation (e.g. `shape.init()`)
- If you add a new shape tomorrow — caller code does not change at all

> **Analogy:** Think of ordering food at a restaurant. You say "give me pasta" — you don't know which chef made it, which kitchen, which recipe. That is exactly what factory hides.

---

## 15. Real Spring Framework Examples

### Builder Pattern in Spring

**`UriComponentsBuilder`** — build URLs step by step
```java
// Used in REST controllers and WebClient
URI uri = UriComponentsBuilder
    .fromHttpUrl("https://api.example.com")
    .path("/users/{id}")
    .queryParam("active", true)
    .queryParam("role", "ADMIN")
    .buildAndExpand(42)
    .toUri();
// → https://api.example.com/users/42?active=true&role=ADMIN
```

**`MockMvcRequestBuilders`** — in Spring tests
```java
mockMvc.perform(
    MockMvcRequestBuilders
        .post("/api/users")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer xyz")
        .content(requestBody)
).andExpect(status().isOk());
```

**`WebClient`** (Spring WebFlux) — HTTP client
```java
WebClient client = WebClient.builder()
    .baseUrl("https://api.example.com")
    .defaultHeader("Authorization", "Bearer xyz")
    .codecs(c -> c.defaultCodecs().maxInMemorySize(1 * 1024 * 1024))
    .build();
```

---

### Factory Pattern in Spring

**`BeanFactory` / `ApplicationContext`** — the most famous Spring factory
```java
// Spring decides which bean class to instantiate — you just ask by type
ApplicationContext ctx = new AnnotationConfigApplicationContext(AppConfig.class);

UserService service = ctx.getBean(UserService.class);
// Spring hides: which impl? singleton or prototype? any proxy wrapping?
```

**`SessionFactory`** (Hibernate + Spring)
```java
// Factory hides: connection pooling, dialect selection, cache setup
SessionFactory factory = new Configuration()
    .configure("hibernate.cfg.xml")
    .buildSessionFactory();

Session session = factory.openSession(); // factory gives you a session
```

**`@Bean` as a factory method**
```java
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        // This IS a factory method — caller just injects DataSource
        // They don't know if it's HikariCP, DBCP, or H2
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://localhost/mydb")
            .username("admin")
            .password("secret")
            .build();
            // ↑ notice: DataSourceBuilder itself is also a Builder!
    }
}

// In your service — you don't know which DataSource impl this is
@Autowired
private DataSource dataSource; // factory hid the details
```

---

### Both Used Together in Spring *(very common in real projects)*

```java
// Builder builds the object → Factory (@Bean) manages and provides it
@Bean
public WebClient webClient() {            // Factory method
    return WebClient.builder()            // Builder constructs it
        .baseUrl("https://api.example.com")
        .defaultHeader("Content-Type", "application/json")
        .build();
}

// Anywhere in your app — Spring factory gives you the built object
@Autowired
private WebClient webClient;
```

> **Interview tip:** In Spring, `@Bean` methods are factory methods — they hide how the object is created. Inside those factory methods, you often use builders like `WebClient.builder()` or `DataSourceBuilder.create()`. So **both patterns work together constantly** in real Spring applications.

---

*Builder Design Pattern — Interview Ready Reference • Java*

---

## 16. Is Builder Creational, Structural, or Behavioural?

**Builder is a Creational pattern.**

| Category | Patterns | Focus |
|---|---|---|
| **Creational** | Builder, Factory, Abstract Factory, Singleton, Prototype | How objects are **created** |
| Structural | Adapter, Decorator, Proxy, Composite, Facade | How objects are **composed** |
| Behavioural | Strategy, Observer, Command, Iterator, Template Method | How objects **communicate** |

> **Interview answer:** Builder is creational because its sole purpose is to control and simplify how a complex object is constructed. It separates the construction logic from the object's representation.

---

## 17. What is the Difference Between `build()` and a Constructor?

| | Constructor | `build()` |
|---|---|---|
| Validation | Hard to do cleanly with many params | Easy — validate all fields before creating |
| Readability | `new User("John", null, 0, true)` — unclear | `.name("John").active(true).build()` — clear |
| Optional fields | Need overloaded constructors | Just skip the setter — default value is used |
| Immutability | Can create immutable objects | Can create immutable objects |
| Object returned | Always same type | Always same type |
| Failure handling | Throw exception in constructor | Throw exception in `build()` before object exists |

```java
// Constructor — hard to read, hard to validate
User u = new User("John", null, 0, true, "ADMIN", null);

// build() — readable, validates cleanly
User u = new User.Builder("John")
    .role("ADMIN")
    .active(true)
    .build();  // validation happens here, before object is created
```

---

## 18. Can You Call `build()` Multiple Times?

**Yes — but be careful.** Each `build()` call creates a new object with the current state of the builder.

```java
Pizza.Builder builder = new Pizza.Builder("large").cheese();

Pizza pizza1 = builder.build();          // large + cheese
Pizza pizza2 = builder.pepperoni().build(); // large + cheese + pepperoni
Pizza pizza3 = builder.build();          // large + cheese + pepperoni (same as pizza2!)
```

> **Tricky part:** If you add toppings between calls, each subsequent `build()` includes everything added so far. The builder accumulates state — it does not reset after `build()`.

**Safe pattern — create a fresh builder each time:**
```java
Pizza pizza1 = new Pizza.Builder("large").cheese().build();
Pizza pizza2 = new Pizza.Builder("large").pepperoni().build(); // clean, independent
```

---

## 19. How to Handle Required vs Optional Fields in Builder?

**Three approaches:**

**Approach 1 — Required fields in Builder constructor**
```java
public static class Builder {
    // required — passed in constructor, cannot be skipped
    private final String name;
    private final String email;

    // optional — have defaults
    private int age = 0;
    private String role = "USER";

    public Builder(String name, String email) {  // required enforced here
        this.name = name;
        this.email = email;
    }

    public Builder age(int a)    { this.age = a;   return this; }
    public Builder role(String r){ this.role = r;  return this; }

    public User build() { return new User(this); }
}

// Cannot skip name or email — compiler enforces it
User u = new User.Builder("John", "john@example.com").role("ADMIN").build();
```

**Approach 2 — Validate in `build()`**
```java
public User build() {
    if (name == null || name.isEmpty())
        throw new IllegalStateException("name is required");
    if (email == null || email.isEmpty())
        throw new IllegalStateException("email is required");
    return new User(this);
}
```

**Approach 3 — Step Builder (compiler enforced)**
```java
// Already covered in Section 2 — the strongest guarantee
// You literally cannot call build() until required steps are done
```

> **Interview answer:** Best practice is to put truly required fields in the Builder constructor and optional fields as setter methods. Add validation in `build()` as a safety net.

---

## 20. What Happens If You Forget to Call `build()`?

You get the **Builder object**, not the product. This is a common beginner mistake.

```java
// BUG — forgot .build()
Pizza.Builder pizza = new Pizza.Builder("large").cheese().pepperoni();
// pizza is a Builder, not a Pizza!

// Correct
Pizza pizza = new Pizza.Builder("large").cheese().pepperoni().build();
```

**How to catch this early:**
- IDE warns you about unused builder objects
- Use Lombok `@Builder` — harder to misuse
- Code review catches it easily
- Unit tests fail because you get wrong type

---

## 21. Why is the Product Constructor `private`?

To force everyone to use the builder — no one can bypass it.

```java
public class User {
    private final String name;
    private final String role;

    private User(Builder b) {   // private — only Builder can call this
        this.name = b.name;
        this.role = b.role;
    }

    public static class Builder {
        public User build() {
            return new User(this);  // only place new User() is called
        }
    }
}

// This will NOT compile:
User u = new User(...);  // compile error — constructor is private

// Must use builder:
User u = new User.Builder().name("John").build();  // only valid way
```

**Benefits of private constructor:**
- Guarantees validation always runs in `build()`
- Guarantees object is always fully initialized
- Prevents partially constructed objects
- Makes the builder the single entry point

---

## 22. Why Do Builder Setter Methods Return `this`?

To enable **method chaining** — calling multiple methods on one line.

```java
// Without returning this — cannot chain
builder.name("John");
builder.age(28);
builder.role("ADMIN");
User u = builder.build();

// With returning this — can chain
User u = builder.name("John").age(28).role("ADMIN").build();
```

```java
// How it works internally
public Builder name(String n) {
    this.name = n;
    return this;   // returns same Builder instance
}

// So this:
builder.name("John").age(28)
// is same as:
Builder b1 = builder.name("John");  // returns same builder
Builder b2 = b1.age(28);            // same builder again
```

> **Interview answer:** Returning `this` from each setter method gives back the same builder instance, allowing you to chain calls. This is called a Fluent Interface — it makes the code read like a sentence.

---

## 23. Builder vs Fluent Interface — Are They the Same?

**No — but they are related.**

| | Builder Pattern | Fluent Interface |
|---|---|---|
| What it is | A design pattern | A coding style / technique |
| Purpose | Construct complex objects | Make code readable via chaining |
| `build()` at end | Yes — required | No — not always needed |
| Example | `new Pizza.Builder().cheese().build()` | `list.stream().filter().map().collect()` |

> **Fluent Interface** is the technique of returning `this` to allow chaining.
> **Builder Pattern** uses Fluent Interface as its implementation style.

```java
// Fluent Interface — no build(), just chaining actions
Arrays.asList(1,2,3,4,5)
    .stream()
    .filter(n -> n > 2)
    .map(n -> n * 10)
    .collect(Collectors.toList());
// No "product" created — just operations chained

// Builder Pattern — uses fluent interface, but ends with build()
Pizza pizza = new Pizza.Builder("large")
    .cheese()
    .pepperoni()
    .build();   // ← this is what makes it a Builder, not just fluent
```

---

## 24. Can Builder Cause Memory Leaks?

**Yes — if the builder holds references to large objects and is never released.**

```java
public class ReportBuilder {
    private List<byte[]> pages = new ArrayList<>();  // holds large data

    public ReportBuilder addPage(byte[] pageData) {
        pages.add(pageData);   // accumulates memory
        return this;
    }

    public Report build() {
        return new Report(pages);
    }
}

// Memory leak risk — builder kept alive but build() never called
ReportBuilder builder = new ReportBuilder();
builder.addPage(hugeData1);
builder.addPage(hugeData2);
// forgot to call build() and builder stays in memory with all that data
```

**How to avoid:**
- Always call `build()` and release the builder reference after
- Clear internal collections after `build()` is called
- Do not store builders as long-lived instance variables

```java
public Report build() {
    Report r = new Report(pages);
    pages.clear();   // release memory after build
    return r;
}
```

---

## 25. How to Make Builder Thread Safe

By default builders are NOT thread safe. Two options:

**Option 1 — Synchronize the builder (rarely needed)**
```java
public synchronized Builder name(String n) {
    this.name = n;
    return this;
}
```

**Option 2 — Don't share builders across threads (recommended)**
```java
// Each thread creates its own builder — no sharing needed
Runnable task = () -> {
    User user = new User.Builder()   // new builder per thread
        .name(Thread.currentThread().getName())
        .build();
};

new Thread(task).start();
new Thread(task).start();
```

> **Interview answer:** The simplest and recommended approach is to never share a builder across threads. Create a new builder in each thread. The object produced by `build()` is safe to share if it is immutable (all fields `final`).

---

## 26. Builder in Java Records (Java 16+)

Java records are immutable data classes. They do not need a builder for simple cases — but for many optional fields you still use one.

```java
// Simple record — no builder needed
public record Point(int x, int y) {}
Point p = new Point(10, 20);  // clean and simple

// Complex record with many optional fields — still use builder
public record HttpConfig(
    String baseUrl,
    int timeout,
    String authToken,
    boolean followRedirects,
    int maxRetries
) {
    public static class Builder {
        private String baseUrl;
        private int timeout = 30;
        private String authToken;
        private boolean followRedirects = true;
        private int maxRetries = 3;

        public Builder baseUrl(String u)         { this.baseUrl = u;           return this; }
        public Builder timeout(int t)            { this.timeout = t;           return this; }
        public Builder authToken(String a)       { this.authToken = a;         return this; }
        public Builder followRedirects(boolean f){ this.followRedirects = f;   return this; }
        public Builder maxRetries(int r)         { this.maxRetries = r;        return this; }

        public HttpConfig build() {
            if (baseUrl == null) throw new IllegalStateException("baseUrl required");
            return new HttpConfig(baseUrl, timeout, authToken, followRedirects, maxRetries);
        }
    }
}

HttpConfig config = new HttpConfig.Builder()
    .baseUrl("https://api.example.com")
    .timeout(10)
    .authToken("Bearer xyz")
    .build();
```

---

## 27. `BeanFactory` vs `ApplicationContext` in Spring *(both are factories)*

| | `BeanFactory` | `ApplicationContext` |
|---|---|---|
| Base interface | Yes — root factory interface | Extends `BeanFactory` |
| Bean creation | Lazy — created on first `getBean()` call | Eager — all singletons created at startup |
| AOP support | No | Yes |
| Event publishing | No | Yes |
| i18n / messages | No | Yes |
| Use in production | Rarely | Always — it is the standard |

```java
// BeanFactory — basic, lazy, rarely used directly
BeanFactory factory = new XmlBeanFactory(new ClassPathResource("beans.xml"));
UserService service = factory.getBean(UserService.class); // created here, lazily

// ApplicationContext — full featured, eager, use this always
ApplicationContext ctx = new AnnotationConfigApplicationContext(AppConfig.class);
// all singleton beans already created at this point
UserService service = ctx.getBean(UserService.class);
```

> **Interview answer:** `ApplicationContext` is the standard in all real Spring applications. `BeanFactory` is the low-level parent interface — you would only use it directly in extremely memory-constrained environments where lazy loading matters.

---

## 28. What is `@SuperBuilder` and When Do You Need It?

`@SuperBuilder` is the Lombok annotation for builders that work across **inheritance hierarchies**. Regular `@Builder` breaks with inheritance.

```java
// @Builder breaks with inheritance — compile error
@Builder
public class Animal {
    private String name;
    private int age;
}

@Builder  // ERROR — cannot use @Builder when parent also has @Builder
public class Dog extends Animal {
    private String breed;
}
```

```java
// @SuperBuilder fixes this
@SuperBuilder
public class Animal {
    private String name;
    private int age;
}

@SuperBuilder
public class Dog extends Animal {
    private String breed;
}

// Now you can chain parent + child fields freely
Dog dog = Dog.builder()
    .name("Rex")       // Animal field
    .age(3)            // Animal field
    .breed("Labrador") // Dog field
    .build();
```

> **Interview answer:** Use `@SuperBuilder` whenever you have inheritance and want builder support on both parent and child classes. Regular `@Builder` only works on a single class with no inheritance.

---

*Builder Design Pattern — Complete Interview Reference • Java*