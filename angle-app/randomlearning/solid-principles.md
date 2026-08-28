# SOLID Principles — Complete Reference & Interview Guide

The 5 principles of object-oriented design that separate maintainable code from technical debt. Coined by Robert C. Martin ("Uncle Bob") in the early 2000s. Every senior interview asks about these.

---

## What is SOLID?

**SOLID** is an acronym for 5 design principles that make software:
- **Easier to change** without breaking existing behavior
- **Easier to test** in isolation
- **Easier to understand** — each class has a clear purpose
- **Easier to extend** — new features don't require rewriting old code

| Letter | Principle | One-liner |
|---|---|---|
| **S** | Single Responsibility | A class should have one reason to change |
| **O** | Open/Closed | Open for extension, closed for modification |
| **L** | Liskov Substitution | Subtypes must be substitutable for their base types |
| **I** | Interface Segregation | Many small interfaces > one big interface |
| **D** | Dependency Inversion | Depend on abstractions, not concretions |

## Why SOLID matters

**Without SOLID:** codebases become "big balls of mud" — every change breaks something unrelated; every class knows too much; tests are impossible to write.

**With SOLID:** classes have clear boundaries; changes stay localized; new features slot in via extension rather than modification; testing becomes trivial.

**Warning:** SOLID is a **guideline**, not a religion. Over-applying SOLID leads to over-abstraction (see "Enterprise FizzBuzz"). The right amount depends on how much change you expect.

---

# S — Single Responsibility Principle (SRP)

## Formal definition

> A class should have **one, and only one, reason to change.**

Robert Martin later refined this to: *"A class should be responsible to one, and only one, actor."* An "actor" is a group of stakeholders (e.g., HR department, finance team, sysadmins) who might request changes.

## Intuition

If two different people, for two different reasons, would ask you to modify the same class — the class has too many responsibilities.

## Violation — a "God class"

```java
public class Employee {
    private String name;
    private BigDecimal salary;
    private String department;

    // Data — changes when data model changes
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }

    // Business logic — changes when finance rules change
    public BigDecimal calculatePay() {
        return salary.multiply(new BigDecimal("0.85"));   // apply tax
    }

    // Persistence — changes when DB schema changes
    public void saveToDatabase() {
        String sql = "INSERT INTO employees (name, salary) VALUES (?, ?)";
        // JDBC boilerplate...
    }

    // Reporting — changes when management wants new report format
    public String generateReport() {
        return "Employee: " + name + ", Salary: " + salary;
    }

    // Email — changes when SMTP config changes
    public void sendEmail(String message) {
        // SMTP logic...
    }
}
```

**Problems:**
- 5 different reasons to change (data, tax rules, DB, reports, email infrastructure)
- Testing `calculatePay()` requires mocking database, SMTP, reporting
- Change to email API breaks tests for salary calculation

## Fix — split by responsibility

```java
public record Employee(String name, BigDecimal salary, String department) {}

public class PayrollCalculator {
    public BigDecimal calculatePay(Employee e) {
        return e.salary().multiply(new BigDecimal("0.85"));
    }
}

public class EmployeeRepository {
    public void save(Employee e) { /* JDBC */ }
    public Employee findById(String id) { /* JDBC */ }
}

public class EmployeeReportGenerator {
    public String generate(Employee e) {
        return "Employee: " + e.name() + ", Salary: " + e.salary();
    }
}

public class EmailService {
    public void send(String to, String message) { /* SMTP */ }
}
```

Now each class:
- Has one reason to change
- Is independently testable
- Has minimal dependencies

## Real-world Java examples

- **JDK:** `String` is arguably the SRP counter-example (does too much — parsing, formatting, storage). Modern Java splits some concerns into `StringBuilder`, `String.format`, etc.
- **Spring:** `@RestController`, `@Service`, `@Repository`, `@Component` — clear separation of layers by responsibility
- **JPA:** entities vs repositories vs services — three distinct roles

## Common misinterpretations

**Wrong:** "SRP means each class should have only one method."
**Right:** SRP is about **reasons to change**, not method count. A class with 20 methods can satisfy SRP if all methods serve the same responsibility.

**Wrong:** "SRP means we need a class for every operation."
**Right:** Utility methods, simple operations, and cohesive classes are fine. Splitting to the extreme creates its own problem (over-abstraction).

## When to relax SRP

- Simple scripts or prototypes
- Data classes (records/DTOs) with basic behavior
- Small utility classes with tightly cohesive methods
- When splitting adds more indirection than clarity

---

# O — Open/Closed Principle (OCP)

## Formal definition

> Software entities should be **open for extension, but closed for modification.**

You should be able to add new behavior without changing existing, tested code.

## Intuition

Every time you touch old code, you risk breaking something that used to work. OCP says: design so that new requirements are added via new code, not modifications to old code.

## Violation — the ever-growing switch

```java
public class DiscountCalculator {
    public BigDecimal calculate(String customerType, BigDecimal amount) {
        if (customerType.equals("REGULAR")) {
            return amount.multiply(new BigDecimal("0.95"));
        } else if (customerType.equals("PREMIUM")) {
            return amount.multiply(new BigDecimal("0.85"));
        } else if (customerType.equals("VIP")) {
            return amount.multiply(new BigDecimal("0.75"));
        }
        // adding STUDENT? edit this class.
        // adding EMPLOYEE? edit this class.
        // adding SENIOR_CITIZEN? edit this class again.
        return amount;
    }
}
```

**Problems:**
- Every new customer type requires modifying `DiscountCalculator`
- Risk of breaking existing types when adding new ones
- Class grows unboundedly

## Fix — polymorphism / Strategy pattern

```java
public interface DiscountStrategy {
    BigDecimal apply(BigDecimal amount);
}

public class RegularDiscount implements DiscountStrategy {
    public BigDecimal apply(BigDecimal a) { return a.multiply(new BigDecimal("0.95")); }
}

public class PremiumDiscount implements DiscountStrategy {
    public BigDecimal apply(BigDecimal a) { return a.multiply(new BigDecimal("0.85")); }
}

public class VipDiscount implements DiscountStrategy {
    public BigDecimal apply(BigDecimal a) { return a.multiply(new BigDecimal("0.75")); }
}

public class DiscountCalculator {
    public BigDecimal calculate(DiscountStrategy strategy, BigDecimal amount) {
        return strategy.apply(amount);
    }
}
```

Adding a `StudentDiscount` = new class, zero modification to existing code.

## Real-world Java examples

- **Comparator** — sort algorithm is closed; you extend by providing new Comparators
- **Servlet filters** — filter chain is closed; you add filters
- **Spring `@Bean`** — the framework is closed; you add beans
- **Java's `List.sort(Comparator)`** — sort algorithm doesn't change; you supply the comparison

## Modern Java version (functional)

```java
public class DiscountCalculator {
    public BigDecimal calculate(Function<BigDecimal, BigDecimal> discount, BigDecimal amount) {
        return discount.apply(amount);
    }
}

// caller
calc.calculate(a -> a.multiply(new BigDecimal("0.85")), price);
```

Lambdas make OCP even more natural.

## Common misinterpretations

**Wrong:** "OCP means never modify existing code."
**Right:** Bug fixes, refactors, and improvements to existing code are fine. OCP is about *extensibility* — designing so **new features** don't require modifying **stable** code.

**Wrong:** "OCP requires interfaces for everything."
**Right:** Use OCP where you expect variation. Not everything needs to be extensible — YAGNI applies.

## When OCP is genuinely needed

- Payment methods (adding new gateways)
- Notification channels (email, SMS, Slack, push)
- Export formats (CSV, JSON, XML, PDF)
- Authentication providers
- Any "plug-in" architecture

---

# L — Liskov Substitution Principle (LSP)

## Formal definition

> If `S` is a subtype of `T`, then objects of type `T` may be replaced with objects of type `S` **without altering the correctness** of the program.

Named after Barbara Liskov (1987). Any place you use a base class, a subclass must work correctly.

## Intuition

**Subclasses must honor the contract of their parent.** If someone writes code against `List`, replacing an `ArrayList` with a `LinkedList` should never break behavior. If a subclass throws where the parent didn't, or accepts fewer inputs, or produces different outputs — it violates LSP.

## Classic violation — the Rectangle/Square problem

```java
public class Rectangle {
    protected int width, height;
    public void setWidth(int w)  { this.width = w; }
    public void setHeight(int h) { this.height = h; }
    public int area()            { return width * height; }
}

public class Square extends Rectangle {
    @Override
    public void setWidth(int w) {
        this.width = w;
        this.height = w;   // maintain square invariant
    }
    @Override
    public void setHeight(int h) {
        this.width = h;
        this.height = h;
    }
}
```

**Test that breaks:**
```java
void test(Rectangle r) {
    r.setWidth(5);
    r.setHeight(4);
    assert r.area() == 20;   // passes for Rectangle, FAILS for Square (returns 16)
}
```

The test is legitimate — it uses only the `Rectangle` interface. But `Square` violates it.

**Root cause:** mathematically, a square IS a rectangle. But **in code**, `Square extends Rectangle` violates LSP because `Square` can't honor `Rectangle`'s contract (independent width/height).

**Fix:** don't use inheritance here. Make both implement `Shape`:
```java
public interface Shape { int area(); }
public record Rectangle(int width, int height) implements Shape {
    public int area() { return width * height; }
}
public record Square(int side) implements Shape {
    public int area() { return side * side; }
}
```

## LSP violations to recognize

### Throwing where the parent didn't

```java
public class Bird {
    public void fly() { /* fly */ }
}

public class Penguin extends Bird {
    @Override
    public void fly() {
        throw new UnsupportedOperationException("Penguins can't fly");
    }
}
```

Caller code:
```java
public void migrate(Bird b) { b.fly(); }
migrate(new Penguin());   // KABOOM at runtime
```

**Fix:** don't force `Penguin` to be a `Bird` if `Bird` implies flying. Use a smaller interface:
```java
public interface Bird {}
public interface FlyingBird extends Bird { void fly(); }
public class Sparrow implements FlyingBird { public void fly() { ... } }
public class Penguin implements Bird {}   // no fly()
```

### Strengthening preconditions

Parent:
```java
public void deposit(BigDecimal amount) {
    if (amount.signum() < 0) throw new IllegalArgumentException();
    balance = balance.add(amount);
}
```

Subclass:
```java
@Override
public void deposit(BigDecimal amount) {
    if (amount.compareTo(new BigDecimal("100")) < 0) throw new IllegalArgumentException();
    super.deposit(amount);
}
```

The subclass rejects inputs the parent accepted → violates LSP. Client code that worked with parent breaks.

### Weakening postconditions

Parent guarantees returning non-null; subclass sometimes returns null. Callers relying on the parent's contract now NPE.

### Changing thrown exception types

Parent throws `IOException`; subclass throws `RuntimeException`. Callers catching `IOException` are now surprised.

## Design by Contract (rules of LSP)

- **Preconditions may be weakened** in subclasses (accept more inputs — OK)
- **Postconditions may be strengthened** in subclasses (guarantee more — OK)
- **Invariants must be preserved**
- **No new checked exceptions** in overriding methods

## Real-world Java examples

- **JDK LSP violation:** `List.add(E)` throws `UnsupportedOperationException` for immutable lists returned by `List.of(...)`. Technically an LSP violation — code that treats a `List` as mutable breaks
- **Spring `Optional.get()`** throws `NoSuchElementException` — but the contract is clear, so callers handle it
- **`java.sql.PreparedStatement`** is a subtype of `Statement` and honors LSP correctly

## Common misinterpretations

**Wrong:** "LSP means IS-A relationship."
**Right:** LSP is about **behavioral substitutability**, not just type hierarchy. Two things can be "IS-A" in the real world but not in code (Square/Rectangle).

**Wrong:** "LSP means never override methods."
**Right:** Overriding is fine — as long as the subclass honors the parent's contract.

---

# I — Interface Segregation Principle (ISP)

## Formal definition

> **Clients should not be forced to depend on methods they do not use.**

Prefer many small, focused interfaces over one large, general-purpose interface.

## Intuition

If a class implements an interface but has to leave several methods empty or throw `UnsupportedOperationException`, that interface is too big.

## Violation — the "fat interface"

```java
public interface Machine {
    void print();
    void scan();
    void fax();
    void copy();
    void email();
}

public class OldPrinter implements Machine {
    public void print() { /* actually prints */ }

    // OldPrinter doesn't do any of these
    public void scan()  { throw new UnsupportedOperationException(); }
    public void fax()   { throw new UnsupportedOperationException(); }
    public void copy()  { throw new UnsupportedOperationException(); }
    public void email() { throw new UnsupportedOperationException(); }
}
```

**Problems:**
- `OldPrinter` is forced to declare methods it can't implement
- Callers can't tell from the type whether a `Machine` can actually scan
- Adding `void bluetoothConnect()` to `Machine` breaks all implementations

## Fix — split into small interfaces

```java
public interface Printer   { void print(); }
public interface Scanner   { void scan(); }
public interface Fax       { void fax(); }
public interface Copier    { void copy(); }
public interface Emailer   { void email(); }

public class OldPrinter implements Printer {
    public void print() { /* prints */ }
}

public class ModernMultifunction implements Printer, Scanner, Copier {
    public void print() { }
    public void scan()  { }
    public void copy()  { }
}
```

Clients depend only on the capabilities they use:
```java
public void printReport(Printer p, String report) { p.print(report); }
```

## Real-world Java examples

- **`java.util.List` vs `java.util.Collection`** — Collection is the smaller supertype for code that only needs iteration/size
- **`Comparable<T>` vs `Comparator<T>`** — separate small interfaces for "natural ordering" vs "custom ordering"
- **Spring's `@Repository`, `@Service`, `@Controller`** — different interfaces for different roles
- **JPA:** `EntityManager` vs `EntityManagerFactory` — clients use what they need
- **Servlet API:** `HttpServletRequest` vs `ServletRequest` — HTTP-specific vs generic

## Java 8+ default methods and ISP

Default methods let you add functionality to an interface without breaking implementers — but this doesn't excuse fat interfaces. If default methods make the interface do "too much," it's still an ISP violation.

## Common misinterpretations

**Wrong:** "Every interface should have one method."
**Right:** Cohesive interfaces are fine. `List` has 20+ methods, all related to list operations — that's cohesion, not violation. Split when methods serve different clients.

**Wrong:** "ISP applies only to interfaces."
**Right:** The principle applies to any abstraction — abstract classes, base classes, even module APIs.

---

# D — Dependency Inversion Principle (DIP)

## Formal definition

> 1. **High-level modules should not depend on low-level modules. Both should depend on abstractions.**
> 2. **Abstractions should not depend on details. Details should depend on abstractions.**

## Intuition

Business logic (high-level) shouldn't be coupled to specific frameworks or infrastructure (low-level). Both should communicate through interfaces (abstractions).

## Violation — high-level depends on low-level

```java
public class OrderService {
    private MySQLOrderRepository repo = new MySQLOrderRepository();   // concrete!
    private SmtpEmailSender emailer = new SmtpEmailSender();          // concrete!

    public void placeOrder(Order o) {
        repo.save(o);
        emailer.send(o.customerEmail(), "Order confirmed");
    }
}
```

**Problems:**
- `OrderService` (high-level business logic) directly depends on `MySQLOrderRepository` (low-level infrastructure)
- Testing requires a real MySQL database + real SMTP server
- Switching to PostgreSQL requires modifying `OrderService`
- Adding a mock for tests is impossible without changing production code

## Fix — depend on abstractions

```java
// Abstractions in the domain layer
public interface OrderRepository {
    void save(Order o);
}
public interface EmailSender {
    void send(String to, String message);
}

// High-level module depends on abstractions
public class OrderService {
    private final OrderRepository repo;
    private final EmailSender emailer;

    public OrderService(OrderRepository repo, EmailSender emailer) {
        this.repo = repo;
        this.emailer = emailer;
    }

    public void placeOrder(Order o) {
        repo.save(o);
        emailer.send(o.customerEmail(), "Order confirmed");
    }
}

// Low-level implementations
public class MySQLOrderRepository implements OrderRepository { }
public class PostgresOrderRepository implements OrderRepository { }
public class SmtpEmailSender implements EmailSender { }
public class MockEmailSender implements EmailSender { }   // for tests
```

Now:
- Business logic is independent of MySQL, SMTP, etc.
- Testing is trivial — inject mocks
- Switching implementations is a config change, not code change

## The "inversion" concept

**Traditional flow:**
```
High-level  →  depends on  →  Low-level (concrete)
```

**Inverted flow:**
```
High-level  ←  implements the interface  ─  Low-level
     │
     depends on
     ↓
Abstraction (interface, defined by high-level)
```

The **direction of dependency is inverted** — now the low-level depends on the interface defined by the high-level. This is why it's called Dependency **Inversion**.

## Dependency Injection ≠ Dependency Inversion

Common confusion in interviews:

| | Dependency Inversion (DIP) | Dependency Injection (DI) |
|---|---|---|
| What | A design principle | A design pattern / technique |
| Purpose | Decouple modules via abstractions | Provide dependencies from outside |
| Example | `OrderService` takes `OrderRepository` interface | Spring `@Autowired` a repository |

**DI implements DIP.** DIP is the principle; DI is one way to achieve it. You can have DIP without a DI framework (manual constructor injection).

## Real-world Java examples

- **Spring:** `@Autowired` interfaces, not concrete classes
- **JDBC:** `Connection`, `Statement`, `ResultSet` are interfaces; drivers provide impls
- **SLF4J:** logging facade; Log4j/Logback are the impls
- **`java.util.List`** — code depends on the interface; implementation is `ArrayList` or `LinkedList`

## Common misinterpretations

**Wrong:** "DIP means always inject dependencies via Spring."
**Right:** DIP is a design principle — depend on abstractions. Spring is one way to wire abstractions to implementations, but manual injection is equally valid.

**Wrong:** "DIP means you must use interfaces for everything."
**Right:** Use abstractions where you expect variation. A `Point(x, y)` doesn't need a `PointInterface`.

**Wrong:** "DIP inverts control flow."
**Right:** DIP inverts **dependency direction**, not control flow. That's a related but different concept (IoC = Inversion of Control).

---

# HOW SOLID PRINCIPLES WORK TOGETHER

The principles reinforce each other:

- **SRP** identifies boundaries → naturally leads to smaller classes
- **OCP** requires abstractions → drives you toward polymorphism
- **LSP** ensures those abstractions work correctly → subtypes are honest
- **ISP** keeps abstractions focused → clients get exactly what they need
- **DIP** aligns dependencies with abstractions → whole system stays loosely coupled

**Together they enable:**
- Test isolation (inject mocks)
- Feature toggling (swap implementations)
- Parallel team development (agree on interfaces, implement independently)
- Framework independence (business logic doesn't know it's in Spring)

---

# FULL WORKED EXAMPLE — Refactoring for SOLID

## Before (violates all 5)

```java
public class ReportGenerator {
    public void generateReport(String userId) {
        // 1. Fetch data from MySQL
        Connection conn = DriverManager.getConnection("jdbc:mysql://...");
        String query = "SELECT * FROM users WHERE id = ?";
        // ... JDBC boilerplate

        // 2. Format as CSV
        StringBuilder csv = new StringBuilder();
        csv.append("Name,Email,Salary\n");
        csv.append(user.getName() + "," + user.getEmail() + "," + user.getSalary() + "\n");

        // 3. Save to disk
        Files.writeString(Path.of("/tmp/report.csv"), csv.toString());

        // 4. Email it
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.example.com");
        Session session = Session.getInstance(props);
        // ... SMTP boilerplate

        // 5. Log to file
        Files.writeString(Path.of("/var/log/reports.log"),
            "Report generated at " + Instant.now(),
            StandardOpenOption.APPEND);
    }
}
```

**Violations:**
- **SRP:** 5 responsibilities in one method (fetch, format, save, email, log)
- **OCP:** Adding PDF format = modify this class
- **LSP:** Not directly, but any subclass would inherit the mess
- **ISP:** Class has too many concerns to interface cleanly
- **DIP:** Depends on concrete MySQL, SMTP, filesystem

## After (SOLID-compliant)

```java
// Domain
public record User(String id, String name, String email, BigDecimal salary) {}

// Abstractions (defined by high-level modules)
public interface UserRepository {
    Optional<User> findById(String id);
}

public interface ReportFormatter {
    String format(User user);
}

public interface ReportSink {
    void save(String report);
}

public interface AuditLogger {
    void logReportGenerated(String userId);
}

// High-level module — pure business logic
public class ReportService {
    private final UserRepository repo;
    private final ReportFormatter formatter;
    private final ReportSink sink;
    private final AuditLogger audit;

    public ReportService(UserRepository repo, ReportFormatter formatter,
                         ReportSink sink, AuditLogger audit) {
        this.repo = repo;
        this.formatter = formatter;
        this.sink = sink;
        this.audit = audit;
    }

    public void generateReport(String userId) {
        User user = repo.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        String report = formatter.format(user);
        sink.save(report);
        audit.logReportGenerated(userId);
    }
}

// Low-level implementations (details)
public class MySQLUserRepository implements UserRepository { }
public class PostgresUserRepository implements UserRepository { }

public class CsvFormatter implements ReportFormatter { }
public class PdfFormatter implements ReportFormatter { }
public class JsonFormatter implements ReportFormatter { }

public class FilesystemReportSink implements ReportSink { }
public class S3ReportSink implements ReportSink { }
public class EmailReportSink implements ReportSink { }

public class Slf4jAuditLogger implements AuditLogger { }
```

**What we gained:**
- **SRP:** Each class has one job
- **OCP:** Add PDF format = new class, no modification
- **LSP:** Every `ReportSink` behaves interchangeably
- **ISP:** Small, focused interfaces
- **DIP:** `ReportService` depends on abstractions, not MySQL/SMTP

**Testing:**
```java
@Test
void generatesReportForExistingUser() {
    User u = new User("1", "Alice", "a@x.com", new BigDecimal("1000"));
    UserRepository repo = id -> Optional.of(u);   // lambda mock
    ReportFormatter fmt = user -> "REPORT:" + user.name();
    List<String> saved = new ArrayList<>();
    ReportSink sink = saved::add;
    AuditLogger audit = uid -> {};

    new ReportService(repo, fmt, sink, audit).generateReport("1");

    assertEquals(List.of("REPORT:Alice"), saved);
}
```

No database, no SMTP, no filesystem — pure unit test.

---

# INTERVIEW QUESTIONS

## Basic

**Q1. What does SOLID stand for?**
Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion. 5 OO design principles by Robert C. Martin.

**Q2. What is SRP?**
A class should have one, and only one, reason to change. It should be responsible to one actor (group of stakeholders).

**Q3. What is OCP?**
Software entities should be open for extension but closed for modification. Add new behavior via new code, not by changing existing code.

**Q4. What is LSP?**
Subtypes must be substitutable for their base types without altering program correctness. Subclasses must honor their parent's contract.

**Q5. What is ISP?**
Clients should not be forced to depend on methods they don't use. Prefer many small, focused interfaces over one big one.

**Q6. What is DIP?**
High-level modules should not depend on low-level modules. Both should depend on abstractions. Abstractions should not depend on details.

## Intermediate

**Q7. What is a "reason to change" in SRP?**
A distinct actor or stakeholder who might request modifications. Different reasons come from different concerns (business rules, persistence, UI, reporting).

**Q8. Why is Square/Rectangle a classic LSP violation?**
Mathematically, a square IS a rectangle. In code, `Square extends Rectangle` breaks because `Rectangle`'s contract (independent width/height) can't hold in a square. Setting the width also changes the height, violating client expectations.

**Q9. How does the Strategy pattern relate to OCP?**
Strategy IS the classic OCP implementation. The Strategy interface is closed to modification; you add new strategies (extension) without changing the core class.

**Q10. Is DIP the same as Dependency Injection?**
No. DIP is a **principle** (depend on abstractions). DI is a **technique** (inject dependencies from outside) that helps you implement DIP. You can practice DIP without a DI framework.

**Q11. Give a JDK example of ISP.**
`Comparable<T>` (natural ordering, one method: `compareTo`) vs `Comparator<T>` (external ordering). Two small, focused interfaces for different clients.

**Q12. Give a Spring example of DIP.**
`@Autowired` an interface (`UserRepository`) rather than a concrete class (`MySQLUserRepository`). Business code depends on the abstraction; Spring wires the concrete implementation at runtime.

## Senior

**Q13. When would you deliberately violate SRP?**
- Simple utility scripts with no growth expected
- Data classes with cohesive behavior
- Records with a few computed methods (record + methods on it may look like SRP violation but are cohesive)
- When splitting adds more indirection than value

**Q14. Explain how "Design by Contract" enforces LSP.**
Preconditions may be **weakened** in subclasses (accept more). Postconditions may be **strengthened** (guarantee more). Invariants must be preserved. If a subclass violates these, it violates LSP.

**Q15. Java's immutable `List.of(...)` throws on `add()` — is this an LSP violation?**
Technically yes — a method advertised as adding to a list throws instead. Java documents this as expected behavior, but it's a **legitimate LSP concern**. Some codebases mitigate by using `List<E>` for read-only APIs and `Collection<E>` or a distinct type when mutation is required.

**Q16. How does OCP interact with YAGNI ("You Aren't Gonna Need It")?**
Tension. OCP encourages designing for extension; YAGNI says don't add complexity for hypothetical needs. Balance: apply OCP where you have concrete evidence of variation (existing multiple implementations, known future needs). Don't build extensibility for imagined requirements.

**Q17. What's the difference between DIP and IoC (Inversion of Control)?**
- **DIP** — dependency direction (high-level depends on abstractions)
- **IoC** — control flow (framework calls your code, not the other way around; e.g., Spring controllers, Servlet lifecycle, event handlers)

They're related — using DIP often enables IoC — but they're separate concepts.

**Q18. Can you have all 5 principles violated in one class?**
Yes — see the "God class" ReportGenerator earlier. It fetches data (SRP), can't extend without modification (OCP), any subclass will inherit tangled behavior (LSP), no cohesive interface exists to segregate (ISP), and depends on MySQL/SMTP concretes (DIP). Refactoring solves all five simultaneously.

**Q19. How do lambdas change SOLID?**
Lambdas make OCP and DIP easier: pass behavior as a `Function`/`Consumer` instead of subclassing. Strategy pattern becomes a lambda. Interfaces with a single method (Comparators, Runnables) become one-liners. Modern Java naturally leans SOLID with lambdas.

**Q20. Explain SOLID's relationship to microservices.**
SOLID at class level → clean modular code. The same principles apply at microservice boundaries: each service has one responsibility (SRP), communicates via versioned contracts (OCP for API evolution), depends on abstractions (message contracts, not internal DB schemas — DIP). Microservices are SOLID at scale.

---

# DEBUG SCENARIOS — spot the violation

## D1. Which principle does this violate?

```java
public class UserService {
    public void createUser(String name) {
        User u = new User(name);
        new EmailSender().send(u.getEmail(), "Welcome");
        new SmsSender().send(u.getPhone(), "Welcome");
        new DatabaseWriter().save(u);
    }
}
```

**Violates DIP** — `UserService` instantiates concrete `EmailSender`, `SmsSender`, `DatabaseWriter` directly. Testing is impossible without real infrastructure.

**Fix:** inject the dependencies via constructor.

---

## D2. Which principle does this violate?

```java
public class DocumentProcessor {
    public void process(Document doc) {
        parseDocument(doc);
        validateDocument(doc);
        saveDocument(doc);
        sendEmailNotification(doc);
        logToAuditSystem(doc);
        updateSearchIndex(doc);
    }
    private void parseDocument(Document doc) { ... }
    private void validateDocument(Document doc) { ... }
    private void saveDocument(Document doc) { ... }
    private void sendEmailNotification(Document doc) { ... }
    private void logToAuditSystem(Document doc) { ... }
    private void updateSearchIndex(Document doc) { ... }
}
```

**Violates SRP** — 6 different concerns (parsing, validation, persistence, notification, audit, search) in one class. Each has a different reason to change.

**Fix:** extract each into its own class; orchestrate them from a thin service.

---

## D3. Which principle does this violate?

```java
public interface Vehicle {
    void startEngine();
    void chargeBattery();
    void refuel();
    void pedal();
}

public class Car implements Vehicle {
    public void startEngine()  { /* start */ }
    public void refuel()       { /* pump gas */ }
    public void chargeBattery() { throw new UnsupportedOperationException(); }
    public void pedal()         { throw new UnsupportedOperationException(); }
}

public class Tesla implements Vehicle {
    public void startEngine()  { /* start */ }
    public void chargeBattery() { /* charge */ }
    public void refuel()        { throw new UnsupportedOperationException(); }
    public void pedal()         { throw new UnsupportedOperationException(); }
}
```

**Violates ISP** — `Vehicle` forces every implementation to have methods it doesn't need.

**Fix:** split into `interface Startable`, `interface Chargeable`, `interface Refuelable`, `interface Pedalable`. Each vehicle implements what applies.

---

## D4. Which principle does this violate?

```java
public class Account {
    protected BigDecimal balance;
    public void withdraw(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) throw new InsufficientFundsException();
        balance = balance.subtract(amount);
    }
}

public class SavingsAccount extends Account {
    @Override
    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("100")) < 0) {
            throw new IllegalArgumentException("Minimum withdrawal is $100");
        }
        super.withdraw(amount);
    }
}
```

**Violates LSP** — `SavingsAccount` strengthens the precondition (requires minimum $100). Code that works with `Account` (any amount) breaks when given a `SavingsAccount`.

**Fix:** don't make `SavingsAccount` a subclass. Compose or use separate types.

---

## D5. Which principle does this violate?

```java
public class PaymentProcessor {
    public void process(Payment p) {
        if (p.getMethod().equals("CREDIT_CARD")) {
            // credit card logic
        } else if (p.getMethod().equals("PAYPAL")) {
            // paypal logic
        } else if (p.getMethod().equals("BITCOIN")) {
            // bitcoin logic
        } else if (p.getMethod().equals("APPLE_PAY")) {
            // apple pay logic
        }
        // adding a new method = modify this class
    }
}
```

**Violates OCP** — adding a new payment method requires modifying `PaymentProcessor`.

**Fix:** Strategy pattern with `PaymentMethod` interface; register new methods without touching `PaymentProcessor`.

---

## D6. Which two principles does this violate?

```java
public class OrderReport {
    private MySQLDatabase db = new MySQLDatabase();

    public String generateAndSend(String orderId) {
        Order order = db.query("SELECT * FROM orders WHERE id = " + orderId);
        String csv = order.toCsv();
        String pdf = order.toPdf();
        String html = order.toHtml();
        new EmailService().send("boss@company.com", csv);
        db.log("Report sent for " + orderId);
        return html;
    }
}
```

**Violates SRP** (multiple concerns in one method: query, format, email, log, return) **and DIP** (depends on concrete `MySQLDatabase`, `EmailService`).

Also arguably OCP (adding XML format modifies this class) and ISP (no cohesive interface).

---

## D7. Which principle does this violate?

```java
public interface UserRepository {
    User findById(String id);
    List<User> findAll();
    void save(User user);
    void delete(String id);
    void backup();                   // backup the entire user DB
    void restoreFromBackup();        // restore from backup
    void migrateSchema();            // schema migration
    void reindex();                  // rebuild search index
    void purgeOldRecords();          // GDPR compliance
}
```

**Violates ISP** — `UserRepository` mixes CRUD (needed by most callers) with admin operations (backup, migrate, purge) that only DevOps/DBAs need.

**Fix:** split into `UserRepository` (CRUD) and `UserRepositoryAdmin` (admin ops). Regular services depend on the small interface; admin tools depend on the admin one.

---

## D8. Which principle does this Spring code violate?

```java
@Service
public class OrderService {
    @Autowired
    private MySQLOrderRepository orderRepository;   // concrete class!

    @Autowired
    private SendGridEmailService emailService;      // concrete class!

    public void placeOrder(Order o) { ... }
}
```

**Violates DIP** — `OrderService` depends on concrete implementations. Even with `@Autowired`, this makes swapping implementations require code changes.

**Fix:** depend on interfaces:
```java
@Autowired private OrderRepository orderRepository;
@Autowired private EmailService emailService;
```

Spring wires the concrete implementations via `@Bean` config, keeping the service framework-agnostic.

---

# QUICK-FIRE RAPID ROUND

| Question | Answer |
|---|---|
| Who coined SOLID? | Robert C. Martin (Uncle Bob) |
| Which principle does Strategy pattern implement? | OCP |
| Which principle does DI framework enable? | DIP |
| A subclass throws where parent didn't — which violation? | LSP |
| A `saveToDb()` method inside a domain class violates? | SRP |
| Which principle drives "code to an interface"? | DIP |
| Big interface with many rarely-used methods violates? | ISP |
| Every new feature requires modifying old class? | OCP violation |
| Two teams work on same class? | SRP violation |
| Composition-over-inheritance ties to which principle? | LSP (subclasses often break contracts) |
| Which principle is about "actor"? | SRP (refined definition) |
| Java default methods help which principle? | OCP (extend interfaces without breaking impls) |
| Records help which principle? | SRP (data-only classes, focused responsibility) |
| Facade pattern relates to which principle? | ISP (simplified interface to complex system) |
| Bridge pattern relates to which principle? | DIP (abstraction separate from implementation) |
| IoC container implements which principle? | DIP (wires abstractions to implementations) |

---

# ONE-SENTENCE SUMMARIES

- **SRP** — one reason to change per class; split responsibilities
- **OCP** — extend via new code, don't modify existing code; use polymorphism
- **LSP** — subtypes must honor their parent's contract; be substitutable
- **ISP** — small focused interfaces beat big general ones; don't force clients to depend on what they don't use
- **DIP** — depend on abstractions, not concretions; high-level modules define the interfaces

---

# COMMON ANTI-PATTERNS SOLID PREVENTS

| Anti-pattern | Violates | Fix |
|---|---|---|
| God Class / Blob | SRP | Split by responsibility |
| Feature Envy | SRP | Move behavior to where the data is |
| Long switch statements | OCP | Replace with polymorphism |
| Type checking with `instanceof` | LSP | Use proper polymorphism |
| Fat interface | ISP | Split into role interfaces |
| Concrete class dependencies | DIP | Introduce abstractions, inject |
| Shotgun surgery (one change → many files) | OCP + SRP | Consolidate concerns |
| Inappropriate intimacy (classes know too much of each other) | DIP | Depend on abstractions |
| Refused bequest (subclass ignores parent methods) | LSP + ISP | Composition or split hierarchy |

---

# BEYOND SOLID — related principles

Once you understand SOLID, these are the next tier:

- **DRY** (Don't Repeat Yourself) — extract common logic
- **KISS** (Keep It Simple, Stupid) — avoid over-engineering
- **YAGNI** (You Aren't Gonna Need It) — don't build for imagined future needs
- **Law of Demeter** ("only talk to your friends") — don't chain `a.getB().getC().getD()`
- **Composition over Inheritance** — usually preferred; more flexible
- **Tell, Don't Ask** — put behavior with data; avoid "getter chains"
- **Fail Fast** — throw early when preconditions fail; don't propagate broken state
- **Single Level of Abstraction (SLA)** — methods should operate at one level; don't mix high and low-level in the same method

---

## The interview-safe summary you can drop verbatim

> SOLID is five OO design principles by Robert C. Martin that make code easier to change, test, and extend. **S**ingle Responsibility says a class should have one reason to change. **O**pen/Closed says design so new behavior comes from new code, not modifications to existing code — Strategy pattern is the archetype. **L**iskov Substitution says subclasses must honor their parent's contract; the classic violation is Square extending Rectangle. **I**nterface Segregation says prefer many small interfaces to one big one so clients aren't forced to depend on methods they don't use. **D**ependency Inversion says high-level modules should depend on abstractions, not on concrete low-level details — which is what dependency injection frameworks like Spring implement. The principles reinforce each other: SRP identifies boundaries, OCP requires abstractions, LSP makes them honest, ISP keeps them focused, and DIP aligns dependencies with abstractions. Applied thoughtfully — not religiously — SOLID produces the kind of code that senior engineers describe as "clean."
