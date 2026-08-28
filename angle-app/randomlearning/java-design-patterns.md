# Java Design Patterns — Complete Reference & Interview Guide

A ready-to-quiz reference covering the 23 Gang of Four (GoF) patterns plus a few modern ones. Each pattern has: intent, structure, code, real-world examples, when to use, and interview questions.

---

## What is a design pattern?

A **design pattern** is a reusable, named solution to a recurring design problem. It's not code you copy — it's a template for how to structure classes and objects to solve a specific kind of problem.

**Origin:** the 1994 book *"Design Patterns: Elements of Reusable Object-Oriented Software"* by the "Gang of Four" (Gamma, Helm, Johnson, Vlissides) — introduced 23 patterns still in use today.

**Why they matter:**
- **Shared vocabulary** — say "use a Factory" and every developer understands
- **Battle-tested designs** — avoid inventing broken variants
- **Framework literacy** — Spring, JDK, Hibernate are built on these patterns

**When they hurt:**
- Over-applying patterns to simple problems (see "Enterprise FizzBuzz")
- Choosing patterns before understanding the problem
- Fighting the language (many GoF patterns predate Java 8 lambdas and are simpler with functional-style code)

---

## The 23 GoF patterns at a glance

Patterns are grouped by **purpose**:

| Category | Purpose | Patterns |
|---|---|---|
| **Creational** | How objects are created | Singleton, Factory Method, Abstract Factory, Builder, Prototype |
| **Structural** | How objects are composed | Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy |
| **Behavioral** | How objects interact | Chain of Responsibility, Command, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method, Visitor |

Memorize this table — interviewers ask "name a structural pattern" or "how would you classify Observer."

---

# CREATIONAL PATTERNS

## 1. Singleton

**Intent:** Ensure a class has exactly **one instance** and provide a global point of access to it.

### Structure

```java
public class Config {
    private static final Config INSTANCE = new Config();
    private Config() {}                        // block external construction
    public static Config getInstance() { return INSTANCE; }
}
```

### Six ways to implement Singleton (know all of them)

**1. Eager initialization** (simplest, thread-safe, always allocates):
```java
private static final Config INSTANCE = new Config();
```

**2. Lazy with `synchronized` method** (safe but slow — locks every call):
```java
public static synchronized Config getInstance() {
    if (INSTANCE == null) INSTANCE = new Config();
    return INSTANCE;
}
```

**3. Double-checked locking (DCL) — requires `volatile`:**
```java
private static volatile Config instance;
public static Config getInstance() {
    if (instance == null) {
        synchronized (Config.class) {
            if (instance == null) instance = new Config();
        }
    }
    return instance;
}
```

**4. Bill Pugh / Holder pattern (BEST for lazy Singleton):**
```java
private Config() {}
private static class Holder {
    static final Config INSTANCE = new Config();
}
public static Config getInstance() { return Holder.INSTANCE; }
```
Lazy, thread-safe, no synchronization overhead — uses JVM's class-initialization safety.

**5. Enum Singleton (Effective Java Item 3 — the "official" recommendation):**
```java
public enum Config {
    INSTANCE;
    public void doWork() { ... }
}
// use: Config.INSTANCE.doWork();
```
Handles serialization automatically. Immune to reflection attacks that break other patterns.

**6. Static class (Java doesn't allow it directly — use `final` + private constructor):**
```java
public final class MathUtils {
    private MathUtils() { throw new AssertionError(); }
    public static int square(int n) { return n * n; }
}
```
Technically a utility class, not a Singleton — no instance at all.

### When to use

- Configuration objects, logger factories, connection pools, caches
- Anywhere "there should only ever be one" is a genuine requirement

### When NOT to use (Singleton is one of the most **abused** patterns)

- When you just want a global variable — bad reason
- When it makes testing hard — Singletons are notoriously test-hostile
- When you have DI — use `@Component` / `@Bean` scope=singleton instead

### Real-world examples

- `Runtime.getRuntime()` — JVM runtime
- `Logger` factories (SLF4J's `LoggerFactory.getLogger()`)
- Spring beans are singletons by default (but scope-managed, not classic Singleton)
- `java.awt.Desktop.getDesktop()`

### Interview questions

**Q: Why is enum the preferred Singleton?**
Handles serialization, cloning, and reflection attacks for free. Thread-safe by JVM class-init guarantees. Simplest correct implementation.

**Q: What breaks DCL without `volatile`?**
The JIT can reorder `instance = new Config()` so the reference is assigned before the constructor finishes. Another thread sees `instance != null` but sees uninitialized fields.

**Q: Can Singleton be broken by reflection?**
Yes — `Constructor.setAccessible(true)` bypasses the private constructor. Enum Singleton is immune (JVM rejects reflective enum instantiation).

**Q: How does serialization break Singleton?**
Deserialization creates a new instance via reflection. Fix: implement `readResolve()` to return the existing instance. Or use enum — free.

---

## 2. Factory Method

**Intent:** Define an interface for creating an object, but let subclasses (or a static method) decide which concrete class to instantiate.

### Structure

```java
public interface Signal {
    void execute();
}

public class BuySignal implements Signal {
    public void execute() { System.out.println("BUY"); }
}

public class SellSignal implements Signal {
    public void execute() { System.out.println("SELL"); }
}

public class SignalFactory {
    public static Signal create(String type) {
        return switch (type) {
            case "BUY"  -> new BuySignal();
            case "SELL" -> new SellSignal();
            default     -> throw new IllegalArgumentException(type);
        };
    }
}
```

### Static factory method — a common Java variant

Effective Java Item 1: prefer static factories to constructors.

```java
public static Optional<T> of(T value)       // returns non-empty
public static Optional<T> empty()           // returns cached empty
public static List<T> of(T... elements)     // returns immutable list
public static Instant.ofEpochMilli(long ms) // named constructor
```

**Advantages:**
- Named (constructor overloads are confusing)
- Can return cached / subclass instances
- Can return `null` or throw with clear reason

### When to use

- Object creation logic is complex (branching, caching, subtype selection)
- You want to hide the concrete class (return an interface)
- You need "named constructors"

### Real-world examples

- `List.of(...)`, `Set.of(...)`, `Map.of(...)`
- `Optional.of/empty/ofNullable`
- `Instant.now/ofEpochMilli`
- `Calendar.getInstance()`
- `Integer.valueOf(int)` — returns cached instances for small ints
- `LoggerFactory.getLogger(...)`

---

## 3. Abstract Factory

**Intent:** Provide an interface for creating **families of related** objects without specifying their concrete classes.

### Structure

```java
// Products
interface Button    { void render(); }
interface Checkbox  { void render(); }

// Concrete product families
class WinButton   implements Button   { public void render() { /* Windows */ } }
class MacButton   implements Button   { public void render() { /* macOS */ } }
class WinCheckbox implements Checkbox { public void render() { /* Windows */ } }
class MacCheckbox implements Checkbox { public void render() { /* macOS */ } }

// Abstract factory
interface GUIFactory {
    Button createButton();
    Checkbox createCheckbox();
}

class WinFactory implements GUIFactory {
    public Button   createButton()   { return new WinButton(); }
    public Checkbox createCheckbox() { return new WinCheckbox(); }
}

class MacFactory implements GUIFactory {
    public Button   createButton()   { return new MacButton(); }
    public Checkbox createCheckbox() { return new MacCheckbox(); }
}
```

### When to use

- You have multiple "families" of products (Windows/Mac UI, MySQL/Postgres drivers)
- All products in a family must be used together
- You want to switch entire families at once

### Real-world examples

- `javax.xml.parsers.DocumentBuilderFactory` + `SAXParserFactory`
- JDBC's `DataSource` implementations (each vendor provides a family: Connection, Statement, ResultSet)
- Spring's `BeanFactory`

### Factory Method vs Abstract Factory

| | Factory Method | Abstract Factory |
|---|---|---|
| Creates | One product | Family of products |
| Complexity | Simpler | More classes |
| Extension | Add one method | Add one factory + N products |

---

## 4. Builder

**Intent:** Separate the construction of a complex object from its representation. Useful when a class has many optional parameters.

### The problem it solves — the "telescoping constructor" trap

```java
new Pizza(size);
new Pizza(size, cheese);
new Pizza(size, cheese, mushrooms);
new Pizza(size, cheese, mushrooms, olives, onions, peppers, ...);   // unreadable
```

### Structure

```java
public class Pizza {
    private final Size size;
    private final boolean cheese;
    private final boolean mushrooms;
    private final boolean olives;

    private Pizza(Builder b) {
        this.size      = b.size;
        this.cheese    = b.cheese;
        this.mushrooms = b.mushrooms;
        this.olives    = b.olives;
    }

    public static class Builder {
        private final Size size;              // required
        private boolean cheese, mushrooms, olives;

        public Builder(Size size)               { this.size = size; }
        public Builder cheese(boolean v)        { this.cheese = v; return this; }
        public Builder mushrooms(boolean v)     { this.mushrooms = v; return this; }
        public Builder olives(boolean v)        { this.olives = v; return this; }
        public Pizza build()                    { return new Pizza(this); }
    }
}
```

### Usage

```java
Pizza p = new Pizza.Builder(Size.LARGE)
    .cheese(true)
    .mushrooms(true)
    .olives(false)
    .build();
```

### When to use

- Class has 4+ optional parameters
- Object needs step-by-step construction
- You want immutability with readable creation

### Real-world examples

- `StringBuilder`, `StringBuffer`
- `Stream.Builder`
- `HttpRequest.newBuilder()` (Java 11 HTTP client)
- Lombok's `@Builder` annotation
- Guava's `ImmutableList.Builder`

### Modern alternative — records with static factories

For simple cases, records + static factory methods can replace Builder:
```java
public record Pizza(Size size, boolean cheese, boolean mushrooms) {
    public static Pizza margherita(Size s) { return new Pizza(s, true, false); }
}
```

Use Builder when you have 5+ fields with genuinely optional / conditional logic.

---

## 5. Prototype

**Intent:** Create new objects by **cloning** an existing instance instead of instantiating from scratch.

### Structure

```java
public class Trade implements Cloneable {
    private String symbol;
    private BigDecimal price;

    @Override
    public Trade clone() {
        try { return (Trade) super.clone(); }
        catch (CloneNotSupportedException e) { throw new AssertionError(); }
    }
}
```

### When to use

- Creating an object is expensive (e.g., loads data, runs I/O)
- You need many similar objects with slight variations

### When NOT to use in modern Java

- **`Cloneable` is broken by design** (Effective Java Item 13). Prefer a copy constructor:
  ```java
  public Trade(Trade other) { this.symbol = other.symbol; this.price = other.price; }
  ```
- For deep copies, serialize + deserialize, or use dedicated libraries.

### Real-world examples

- `Object.clone()` — the original
- `ArrayList.clone()`, `HashMap.clone()`
- Spring's prototype-scoped beans

---

# STRUCTURAL PATTERNS

## 6. Adapter

**Intent:** Convert the interface of a class into another interface clients expect. Adapter lets classes work together that couldn't otherwise because of incompatible interfaces.

### Structure

```java
// Existing (third-party) interface — can't modify
class LegacyPaymentProcessor {
    public void makePayment(String accountNo, double amount) { ... }
}

// Your app's interface
interface PaymentGateway {
    void charge(Customer c, Money amount);
}

// Adapter
class LegacyPaymentAdapter implements PaymentGateway {
    private final LegacyPaymentProcessor legacy;
    public LegacyPaymentAdapter(LegacyPaymentProcessor legacy) { this.legacy = legacy; }

    @Override
    public void charge(Customer c, Money amount) {
        legacy.makePayment(c.getAccountNumber(), amount.toDouble());
    }
}
```

### When to use

- Integrating legacy code with a new interface
- Wrapping third-party libraries to conform to your abstractions
- Migrating APIs incrementally

### Real-world examples

- `Arrays.asList(...)` — adapts array to List interface
- `InputStreamReader` — adapts `InputStream` (byte) to `Reader` (char)
- Spring's `HandlerAdapter` — adapts various controllers to a common invocation interface

---

## 7. Bridge

**Intent:** Decouple an abstraction from its implementation so both can vary independently.

### The problem it solves

Without Bridge: `RedCircle`, `BlueCircle`, `RedSquare`, `BlueSquare` — a class explosion.

With Bridge:

```java
interface Color {
    void applyColor();
}
class Red  implements Color { public void applyColor() { /* red */ } }
class Blue implements Color { public void applyColor() { /* blue */ } }

abstract class Shape {
    protected Color color;
    public Shape(Color color) { this.color = color; }
    public abstract void draw();
}

class Circle extends Shape {
    public Circle(Color color) { super(color); }
    public void draw() { color.applyColor(); /* draw circle */ }
}

class Square extends Shape {
    public Square(Color color) { super(color); }
    public void draw() { color.applyColor(); /* draw square */ }
}
```

Adding a new shape (Triangle) or a new color (Green) is O(1), not O(N).

### Real-world examples

- JDBC — `Connection` (abstraction) + driver (implementation)
- `java.awt.peer.*` — AWT components + platform peers
- SLF4J — logging facade + Logback/Log4j implementation

---

## 8. Composite

**Intent:** Compose objects into tree structures to represent part-whole hierarchies. Lets clients treat individual objects and compositions uniformly.

### Structure

```java
interface FileSystemNode {
    long size();
}

class File implements FileSystemNode {
    private final long bytes;
    public File(long bytes) { this.bytes = bytes; }
    public long size() { return bytes; }
}

class Directory implements FileSystemNode {
    private final List<FileSystemNode> children = new ArrayList<>();
    public void add(FileSystemNode n) { children.add(n); }
    public long size() {
        return children.stream().mapToLong(FileSystemNode::size).sum();
    }
}
```

Clients call `size()` uniformly — doesn't matter if it's a file or a directory.

### Real-world examples

- Swing's `Container` / `Component` hierarchy
- HTML DOM (elements can contain elements)
- Filesystem representations
- AST (Abstract Syntax Tree) in compilers

---

## 9. Decorator

**Intent:** Attach additional responsibilities to an object dynamically. Provides a flexible alternative to subclassing for extending functionality.

### Structure

```java
interface Coffee {
    String description();
    BigDecimal cost();
}

class SimpleCoffee implements Coffee {
    public String description() { return "Coffee"; }
    public BigDecimal cost()    { return new BigDecimal("2.00"); }
}

// Decorator base
abstract class CoffeeDecorator implements Coffee {
    protected final Coffee inner;
    public CoffeeDecorator(Coffee inner) { this.inner = inner; }
}

class Milk extends CoffeeDecorator {
    public Milk(Coffee c) { super(c); }
    public String description() { return inner.description() + " + milk"; }
    public BigDecimal cost()    { return inner.cost().add(new BigDecimal("0.50")); }
}

class Sugar extends CoffeeDecorator {
    public Sugar(Coffee c) { super(c); }
    public String description() { return inner.description() + " + sugar"; }
    public BigDecimal cost()    { return inner.cost().add(new BigDecimal("0.25")); }
}
```

### Usage

```java
Coffee c = new Sugar(new Milk(new SimpleCoffee()));
c.description();   // "Coffee + milk + sugar"
c.cost();          // 2.75
```

### Real-world examples

- **`java.io`** is entirely decorators: `new BufferedReader(new InputStreamReader(new FileInputStream(...)))`
- `Collections.unmodifiableList(list)` — adds "throw on modification"
- `Collections.synchronizedList(list)` — adds thread-safety
- Servlet filters (each filter wraps the next)

### Decorator vs Inheritance

Inheritance is compile-time; you're stuck. Decorators compose at runtime — you can add "make it thread-safe + logging + caching" in any order to any base.

---

## 10. Facade

**Intent:** Provide a **simplified interface** to a complex subsystem.

### Structure

```java
// Complex subsystem
class VideoCodec { ... }
class AudioMixer { ... }
class Transcoder { ... }
class FileWriter { ... }

// Facade
public class VideoConverter {
    public File convert(File input, String targetFormat) {
        // orchestrate: VideoCodec → AudioMixer → Transcoder → FileWriter
        // client doesn't need to know any of this
    }
}
```

Client:
```java
new VideoConverter().convert(input, "mp4");   // one call
```

### When to use

- Simplifying a complex library for common use cases
- Hiding legacy or third-party APIs
- Creating a stable API on top of unstable internals

### Real-world examples

- `javax.faces.context.FacesContext` — hides JSF internals
- Spring's `JdbcTemplate` — facade over JDBC
- `Files.readAllBytes(path)` — facade over stream/channel/buffer handling
- SLF4J API — facade over multiple logging backends

---

## 11. Flyweight

**Intent:** Share large numbers of fine-grained objects efficiently by separating **intrinsic** (shared) state from **extrinsic** (per-instance) state.

### The problem it solves

Say you're rendering 1 million characters in a document. Each character has font, size, color. Instead of creating 1M objects:

```java
public final class Character {
    private final char symbol;      // intrinsic — shared
    private final Font font;        // intrinsic
    private final int size;         // intrinsic
    // color, position are extrinsic — passed in when needed
}

public class CharacterFactory {
    private static final Map<String, Character> POOL = new HashMap<>();
    public static Character get(char c, Font f, int size) {
        return POOL.computeIfAbsent(c + f.getName() + size, k -> new Character(c, f, size));
    }
}
```

Now 1M rendered characters share ~100 objects.

### Real-world examples

- `Integer.valueOf(int)` — caches -128 to 127
- `String` pool — interned strings shared
- `Character.valueOf(char)` — caches ASCII

---

## 12. Proxy

**Intent:** Provide a surrogate or placeholder for another object to control access to it.

### Types of proxies

- **Virtual proxy** — lazy initialization (Hibernate lazy-loaded entities)
- **Protection proxy** — access control (security wrappers)
- **Remote proxy** — represents an object in another JVM (RMI stubs)
- **Smart reference** — adds behavior on access (logging, caching, metrics)

### Structure

```java
interface Image {
    void display();
}

class RealImage implements Image {
    private final String filename;
    public RealImage(String f) { this.filename = f; loadFromDisk(); }
    private void loadFromDisk() { System.out.println("Loading " + filename); }
    public void display()      { System.out.println("Displaying " + filename); }
}

class ImageProxy implements Image {
    private final String filename;
    private RealImage real;
    public ImageProxy(String f) { this.filename = f; }
    public void display() {
        if (real == null) real = new RealImage(filename);   // lazy load
        real.display();
    }
}
```

### Real-world examples

- **Spring AOP** — every `@Transactional`, `@Cacheable`, `@Async` method is proxied
- **Hibernate lazy loading** — entity references are proxies until accessed
- `java.lang.reflect.Proxy` — dynamic proxy for interfaces
- CGLIB — subclass-based proxies used by Spring for classes without interfaces
- RMI stubs

### Proxy vs Decorator

| | Proxy | Decorator |
|---|---|---|
| Intent | Control access | Add behavior |
| Client aware? | Usually no (transparent) | Yes (opts in) |
| Lifecycle | Same as target | Wraps at runtime |

---

# BEHAVIORAL PATTERNS

## 13. Chain of Responsibility

**Intent:** Pass a request along a chain of handlers. Each handler decides either to process it or to pass it to the next.

### Structure

```java
abstract class Handler {
    protected Handler next;
    public Handler linkWith(Handler next) { this.next = next; return next; }
    public abstract void handle(Request r);
}

class AuthHandler extends Handler {
    public void handle(Request r) {
        if (!r.isAuthenticated()) throw new SecurityException();
        if (next != null) next.handle(r);
    }
}

class LoggingHandler extends Handler {
    public void handle(Request r) {
        System.out.println("Request: " + r);
        if (next != null) next.handle(r);
    }
}

class BusinessHandler extends Handler {
    public void handle(Request r) { /* do the thing */ }
}
```

### Usage

```java
Handler chain = new AuthHandler();
chain.linkWith(new LoggingHandler())
     .linkWith(new BusinessHandler());
chain.handle(request);
```

### Real-world examples

- **Servlet filter chain** — each filter passes to the next
- Spring Security filter chain
- Node.js Express middleware (same idea, different language)
- Log4j appender chain
- `try/catch` chains (Java handles this natively at the language level)

---

## 14. Command

**Intent:** Encapsulate a request as an object, letting you parameterize clients with different requests, queue or log requests, and support undoable operations.

### Structure

```java
interface Command {
    void execute();
    void undo();
}

class BuyStock implements Command {
    private final Broker broker;
    private final String symbol;
    private final int qty;
    public BuyStock(Broker b, String s, int q) { this.broker = b; this.symbol = s; this.qty = q; }
    public void execute() { broker.buy(symbol, qty); }
    public void undo()    { broker.sell(symbol, qty); }
}

class OrderQueue {
    private final Deque<Command> history = new ArrayDeque<>();
    public void submit(Command c) { c.execute(); history.push(c); }
    public void undoLast() { if (!history.isEmpty()) history.pop().undo(); }
}
```

### When to use

- Undo/redo functionality
- Queuing operations
- Logging and replay (event sourcing)
- Macro recording

### Real-world examples

- `Runnable` — a command with just `execute()` (no undo)
- `java.util.concurrent.Callable`
- Swing's `Action` interface
- Undo stacks in editors
- Kafka messages as commands

### Modern Java note

With lambdas, most Command uses collapse to `Runnable` / `Function`. The pattern is more relevant when you need `undo()` or serialization.

---

## 15. Iterator

**Intent:** Provide a way to access elements of a collection sequentially without exposing the underlying representation.

### Structure

```java
public interface Iterator<E> {
    boolean hasNext();
    E next();
}

public interface Iterable<E> {
    Iterator<E> iterator();
}
```

### Note

Java's collections framework is built on this — you rarely implement it manually. Two loops both use it:
```java
for (E element : collection) { ... }      // uses iterator() implicitly
for (Iterator<E> it = collection.iterator(); it.hasNext(); ) { ... }
```

### Real-world examples

- Every `Collection` in the JDK
- `Stream` (a specialized iterator with lazy evaluation)
- Database `ResultSet` (an iterator over rows)

---

## 16. Mediator

**Intent:** Define an object that encapsulates how a set of objects interact. Promotes loose coupling by keeping objects from referring to each other explicitly.

### The problem it solves

N objects that talk to each other → N² dependencies. Add a mediator → each object talks only to the mediator → N dependencies.

### Structure

```java
class ChatRoom {   // mediator
    private final List<User> users = new ArrayList<>();
    public void register(User u) { users.add(u); u.setChatRoom(this); }
    public void send(User from, String message) {
        for (User u : users) if (u != from) u.receive(from, message);
    }
}

class User {
    private ChatRoom room;
    public void send(String msg) { room.send(this, msg); }
    public void receive(User from, String msg) { /* display */ }
    public void setChatRoom(ChatRoom r) { this.room = r; }
}
```

Users don't know each other — they only know the room.

### Real-world examples

- **Spring's `ApplicationEventMulticaster`** — publishers/subscribers don't know each other
- Air traffic control (planes talk to tower, not to each other)
- Message brokers (Kafka, RabbitMQ)
- `java.util.Timer` — coordinates task execution

---

## 17. Memento

**Intent:** Capture and externalize an object's internal state so it can be restored later — without violating encapsulation.

### Structure

```java
class Editor {
    private String content;

    public void write(String text) { content += text; }
    public Memento save()          { return new Memento(content); }
    public void restore(Memento m) { this.content = m.content; }

    public static class Memento {
        private final String content;
        private Memento(String c) { this.content = c; }
    }
}

class History {
    private final Deque<Editor.Memento> mementos = new ArrayDeque<>();
    public void save(Editor.Memento m) { mementos.push(m); }
    public Editor.Memento undo()       { return mementos.pop(); }
}
```

### When to use

- Undo/redo functionality with complex state
- Snapshots (game save states, database transactions)

### Real-world examples

- Text editor undo stacks
- `javax.faces.component.StateHolder`
- Serialization is a lightweight Memento

---

## 18. Observer (aka Publish-Subscribe)

**Intent:** Define a one-to-many dependency so that when one object changes state, all its dependents are notified automatically.

### Structure

```java
interface Observer<T> {
    void update(T event);
}

class Subject<T> {
    private final List<Observer<T>> observers = new ArrayList<>();
    public void subscribe(Observer<T> o)   { observers.add(o); }
    public void unsubscribe(Observer<T> o) { observers.remove(o); }
    protected void notify(T event)         { observers.forEach(o -> o.update(event)); }
}

class StockPrice extends Subject<BigDecimal> {
    public void set(BigDecimal p) { notify(p); }
}

class PriceAlert implements Observer<BigDecimal> {
    public void update(BigDecimal p) { if (p.compareTo(new BigDecimal("100")) > 0) alert(); }
}
```

### Real-world examples

- **`java.util.Observable` / `Observer`** — deprecated since Java 9, don't use
- **Spring `ApplicationEvent`** — `@EventListener` methods
- **JavaFX / Swing** — `ActionListener`, `PropertyChangeListener`
- **Reactive Streams** (`Publisher` / `Subscriber`) — RxJava, Project Reactor
- Kafka consumer groups
- Any pub/sub messaging system

### Modern take: Reactive Streams

`Flow.Publisher` / `Flow.Subscriber` (Java 9+) is Observer with backpressure and cancellation added. Standard in reactive libraries.

---

## 19. State

**Intent:** Allow an object to alter its behavior when its internal state changes. The object appears to change its class.

### The problem it solves — the "long switch/if-else" trap

```java
class Order {
    private String status;
    public void ship() {
        if (status.equals("PENDING"))   throw new IllegalStateException();
        if (status.equals("SHIPPED"))   throw new IllegalStateException();
        if (status.equals("PAID"))      { status = "SHIPPED"; return; }
        // ... this grows every time you add a status
    }
}
```

### Structure

```java
interface OrderState {
    OrderState pay(Order o);
    OrderState ship(Order o);
    OrderState cancel(Order o);
}

class Pending implements OrderState {
    public OrderState pay(Order o)    { return new Paid(); }
    public OrderState ship(Order o)   { throw new IllegalStateException(); }
    public OrderState cancel(Order o) { return new Cancelled(); }
}

class Paid implements OrderState {
    public OrderState pay(Order o)    { throw new IllegalStateException(); }
    public OrderState ship(Order o)   { return new Shipped(); }
    public OrderState cancel(Order o) { return new Refunded(); }
}
// ... etc.

class Order {
    private OrderState state = new Pending();
    public void pay()    { state = state.pay(this); }
    public void ship()   { state = state.ship(this); }
    public void cancel() { state = state.cancel(this); }
}
```

### When to use

- Object behavior differs significantly across states
- You have many conditional branches on a state field
- Modeling formal state machines (workflows, protocols)

### Real-world examples

- TCP connection states (LISTEN, SYN_SENT, ESTABLISHED, ...)
- Order/checkout workflows
- Thread lifecycle (NEW, RUNNABLE, BLOCKED, WAITING, TERMINATED)

---

## 20. Strategy

**Intent:** Define a family of algorithms, encapsulate each one, and make them interchangeable. Strategy lets the algorithm vary independently from clients that use it.

### Structure

```java
interface CompressionStrategy {
    byte[] compress(byte[] data);
}

class GzipCompression implements CompressionStrategy {
    public byte[] compress(byte[] data) { /* gzip */ }
}

class LZ4Compression implements CompressionStrategy {
    public byte[] compress(byte[] data) { /* lz4 */ }
}

class Compressor {
    private CompressionStrategy strategy;
    public Compressor(CompressionStrategy s) { this.strategy = s; }
    public void setStrategy(CompressionStrategy s) { this.strategy = s; }
    public byte[] compress(byte[] data) { return strategy.compress(data); }
}
```

### Modern Java — lambda replaces Strategy in most cases

```java
Function<byte[], byte[]> compressor = data -> gzip(data);   // Strategy as a lambda
```

Any single-method Strategy becomes a `Function`, `Consumer`, or `Predicate`. Anonymous class → lambda → method reference.

### Real-world examples

- `Comparator` — sorting strategies
- `Executor` — task execution strategies (thread pool, sync, scheduled)
- `RejectedExecutionHandler` in `ThreadPoolExecutor`
- Spring's `PasswordEncoder` implementations

### Strategy vs State

| | Strategy | State |
|---|---|---|
| Chosen by | Client (explicit) | Object itself (transition) |
| Purpose | Vary algorithm | Vary behavior across lifecycle |
| Transitions | Client-driven | State-driven |

---

## 21. Template Method

**Intent:** Define the **skeleton of an algorithm** in a base class, deferring some steps to subclasses. Subclasses can redefine specific steps without changing the algorithm's structure.

### Structure

```java
public abstract class HotBeverage {
    // TEMPLATE METHOD — final, subclasses can't break the sequence
    public final void prepare() {
        boilWater();
        brew();
        pourInCup();
        addCondiments();
        serve();
    }

    private void boilWater() { System.out.println("Boiling water"); }
    private void pourInCup() { System.out.println("Pouring"); }
    private void serve()     { System.out.println("Serving"); }

    protected abstract void brew();
    protected abstract void addCondiments();
}

public class Coffee extends HotBeverage {
    protected void brew()          { System.out.println("Brewing coffee"); }
    protected void addCondiments() { System.out.println("Adding sugar & milk"); }
}
```

### Hook methods — optional customization

```java
public final void prepare() {
    boilWater();
    brew();
    if (customerWantsCondiments()) addCondiments();   // ← hook
    serve();
}
protected boolean customerWantsCondiments() { return true; }   // default
```

### Real-world examples

- **`HttpServlet.service()`** → dispatches to `doGet`, `doPost`, etc.
- **`AbstractList`** → `contains`, `iterator` built on abstract `get`, `size`
- **Spring `JdbcTemplate.execute(...)`** → manages connection lifecycle, calls your callback
- **JUnit lifecycle** → `@BeforeEach` → `@Test` → `@AfterEach`
- **`InputStream.read(byte[], int, int)`** → built on single-byte `read()`
- **Spring `AbstractApplicationContext.refresh()`** → 12-step startup with hooks

### Template Method vs Strategy

| | Template Method | Strategy |
|---|---|---|
| Mechanism | Inheritance | Composition |
| Flow control | Fixed in superclass | Client-controlled |
| Java 8+ style | Awkward | Natural (lambdas) |

### Ties into `final`

The template method **should be `final`** — subclasses must not be able to break the algorithm's contract. This is Q8 in the `final` interview file: "why would you make a method `final`?"

---

## 22. Visitor

**Intent:** Represent an operation to be performed on the elements of an object structure. Visitor lets you define a new operation without changing the classes of the elements on which it operates.

### The problem it solves

You have a fixed hierarchy of types (say, AST nodes) and you keep adding new **operations** on them. Without Visitor, every new operation modifies every class.

### Structure

```java
interface Node {
    <T> T accept(Visitor<T> v);
}

class LiteralNode implements Node {
    final int value;
    public LiteralNode(int v) { this.value = v; }
    public <T> T accept(Visitor<T> v) { return v.visitLiteral(this); }
}

class AddNode implements Node {
    final Node left, right;
    public AddNode(Node l, Node r) { this.left = l; this.right = r; }
    public <T> T accept(Visitor<T> v) { return v.visitAdd(this); }
}

interface Visitor<T> {
    T visitLiteral(LiteralNode n);
    T visitAdd(AddNode n);
}

class Evaluator implements Visitor<Integer> {
    public Integer visitLiteral(LiteralNode n) { return n.value; }
    public Integer visitAdd(AddNode n)         { return n.left.accept(this) + n.right.accept(this); }
}

class Printer implements Visitor<String> {
    public String visitLiteral(LiteralNode n)  { return String.valueOf(n.value); }
    public String visitAdd(AddNode n)          { return "(" + n.left.accept(this) + "+" + n.right.accept(this) + ")"; }
}
```

### Modern Java — pattern matching kills Visitor

Java 21+ sealed interfaces + pattern matching switch:
```java
sealed interface Node permits LiteralNode, AddNode {}
record LiteralNode(int value) implements Node {}
record AddNode(Node left, Node right) implements Node {}

int eval(Node n) {
    return switch (n) {
        case LiteralNode(int v)      -> v;
        case AddNode(Node l, Node r) -> eval(l) + eval(r);
    };
}
```

Much cleaner. Visitor was a workaround for lack of pattern matching.

### Real-world examples

- Compilers (AST traversal)
- `java.nio.file.FileVisitor`
- `javax.lang.model.element.ElementVisitor` (annotation processing)

---

# ANTI-PATTERNS TO AVOID

## God Object / Blob
One class does everything. Break it into cohesive units following single-responsibility principle.

## Singleton overuse
Global mutable state. Use dependency injection instead.

## Anemic Domain Model
Objects with only getters/setters and no behavior. Business logic ends up in service classes. Fix: move behavior to the objects themselves.

## Golden Hammer
Applying one pattern (or one language feature) everywhere. "When all you have is a hammer, everything looks like a nail."

## Excessive Getter/Setter
Auto-generating getters and setters for every field breaks encapsulation. Records + immutability are usually better.

---

# INTERVIEW-STYLE QUESTIONS

## Basic

**Q1. What are the three categories of GoF patterns?**
Creational (object creation), Structural (composition), Behavioral (object interaction).

**Q2. Which pattern would you use to create objects without specifying the exact class?**
Factory Method or Abstract Factory.

**Q3. Difference between Factory and Abstract Factory?**
Factory creates one product; Abstract Factory creates a family of related products.

**Q4. Difference between Adapter and Decorator?**
Adapter changes an interface. Decorator adds behavior without changing interface.

**Q5. Difference between Proxy and Decorator?**
Proxy controls access to the same interface (often invisibly). Decorator explicitly extends functionality.

## Intermediate

**Q6. When would you use Template Method vs Strategy?**
Template Method when the algorithm's structure is fixed and only steps vary. Strategy when the whole algorithm is swappable.

**Q7. How does Spring use the Proxy pattern?**
Every `@Transactional`, `@Cacheable`, `@Async` bean is wrapped in a proxy that intercepts method calls to add cross-cutting concerns.

**Q8. Why is the enum Singleton the best implementation?**
Handles serialization, cloning, and reflection attacks automatically. Simplest correct code.

**Q9. Give a real JDK example of Decorator.**
`java.io` — `BufferedReader` wraps `Reader`, `InputStreamReader` wraps `InputStream`, `PrintStream` wraps `OutputStream`.

**Q10. Which pattern does JDBC use?**
Multiple — Bridge (`Connection` abstraction + driver implementation), Factory (`DriverManager.getConnection()`), Adapter (drivers adapt their SQL dialects).

## Senior

**Q11. Explain how Spring's `JdbcTemplate` combines Template Method and Strategy.**
`execute()` is the Template Method (connection lifecycle). Callbacks like `RowMapper` are Strategies (how to process each row). It's a hybrid.

**Q12. When does the Visitor pattern become unnecessary?**
Java 21+ with pattern matching on sealed types. `switch` over sealed hierarchies replaces double-dispatch.

**Q13. What is the "Hollywood Principle"?**
"Don't call us, we'll call you." Frameworks call your code at defined extension points, not the reverse. Foundation of Template Method and IoC.

**Q14. Design a plugin system — which patterns?**
Factory (create plugin instances), Strategy (each plugin implements a common interface), Observer (plugins subscribe to events), Chain of Responsibility (plugin chain).

**Q15. Which patterns are effectively obsolete due to lambdas?**
Strategy → `Function<T,R>`. Command → `Runnable`. Observer → `Consumer<Event>` + list. Iterator → Stream. Anywhere the pattern was "just a single-method interface," a lambda now suffices.

## Framework-specific

**Q16. How does Spring Bean Factory relate to Abstract Factory?**
Spring's `BeanFactory` is the Abstract Factory — it produces families of beans (with their dependencies wired in) without callers needing to know concrete classes.

**Q17. Why does `@Transactional` need CGLIB for non-interface classes?**
JDK dynamic proxies only work on interfaces. CGLIB generates a runtime subclass. Trade-off: CGLIB proxies can't proxy `final` classes or `final` methods (Q10 in the `final` file — final methods can't be overridden = can't be intercepted).

**Q18. What patterns does Hibernate implement?**
Proxy (lazy loading), Unit of Work (transaction/session), Data Mapper (entity ↔ table), Identity Map (session cache), Repository (via Spring Data JPA).

---

# DEBUG SCENARIOS

## D1. The broken Singleton (reflection attack)

```java
public class Config {
    private static final Config INSTANCE = new Config();
    private Config() {
        if (INSTANCE != null) throw new IllegalStateException("Already exists");
    }
    public static Config getInstance() { return INSTANCE; }
}

// Attacker:
Constructor<Config> c = Config.class.getDeclaredConstructor();
c.setAccessible(true);
Config c2 = c.newInstance();   // succeeds — INSTANCE was null during its own construction
```

**Bug:** the guard `if (INSTANCE != null) throw` runs during `INSTANCE = new Config()` — at that moment `INSTANCE` is still `null`, so it passes. Second reflective call sees `INSTANCE` set and throws — but the attacker already got their instance.

**Fix:** use enum Singleton — JVM rejects reflective instantiation.

---

## D2. Decorator forgotten wrapper

```java
BufferedReader reader = new BufferedReader(
    new InputStreamReader(new FileInputStream("data.txt"))
);
// reads work
reader.close();   // closes only BufferedReader — leaks the underlying stream?
```

**Not a bug — but a common misconception.** `close()` on a decorator cascades to the wrapped resource. `BufferedReader.close()` calls `InputStreamReader.close()` which calls `FileInputStream.close()`. Use try-with-resources for safety.

---

## D3. The Observer memory leak

```java
class Subject {
    private final List<Observer> observers = new ArrayList<>();
    public void subscribe(Observer o) { observers.add(o); }
}

class ShortLivedComponent implements Observer {
    public ShortLivedComponent(Subject s) { s.subscribe(this); }
}
```

The component gets garbage collected... except **the Subject holds a strong reference**, keeping it alive forever. Classic memory leak.

**Fix:** `WeakReference<Observer>` in the observer list, or explicit unsubscribe on component destruction. In Spring: use `@EventListener` which manages this via bean lifecycle.

---

## D4. Template Method breaks because the flow isn't `final`

```java
public class HotBeverage {   // NO final on prepare()
    public void prepare() {
        boilWater();
        brew();
        addCondiments();
        serve();
    }
    // ...
}

public class Coffee extends HotBeverage {
    @Override
    public void prepare() {
        addCondiments();   // wrong order
        brew();
    }
}
```

**Bug:** because `prepare()` isn't `final`, a subclass overrode it and broke the entire algorithm.

**Fix:** `public final void prepare()` — this is why the template method must be `final`.

---

## D5. Strategy with shared mutable state

```java
class Sorter {
    private final Comparator<Trade> comparator;
    public Sorter(Comparator<Trade> c) { this.comparator = c; }
    public void sort(List<Trade> trades) { trades.sort(comparator); }
}

class StatefulComparator implements Comparator<Trade> {
    private int calls = 0;
    public int compare(Trade a, Trade b) {
        calls++;   // race condition if sorter is shared across threads
        return a.symbol().compareTo(b.symbol());
    }
}
```

**Bug:** Strategy objects should typically be **stateless**. If they hold mutable state, sharing across threads is a data race.

**Fix:** make comparators pure functions (or convert to lambdas — they're auto-stateless).

---

## D6. Factory that always creates new instances (memory waste)

```java
public class LoggerFactory {
    public static Logger getLogger(String name) {
        return new Logger(name);   // creates a new Logger every call
    }
}
```

**Not necessarily a bug**, but if `Logger` is stateless / immutable, this wastes memory. Real `LoggerFactory` caches instances.

**Fix:** `Map<String, Logger>` cache in the factory:
```java
private static final Map<String, Logger> CACHE = new ConcurrentHashMap<>();
public static Logger getLogger(String name) {
    return CACHE.computeIfAbsent(name, Logger::new);
}
```

---

## D7. Proxy that breaks `equals()` / `hashCode()`

```java
@Entity
class Trade {
    @Id private Long id;
    // ... only getters
    public boolean equals(Object o) { /* compare id */ }
    public int hashCode() { return Objects.hash(id); }
}

Trade a = session.get(Trade.class, 1L);      // real
Trade b = session.load(Trade.class, 1L);      // Hibernate proxy
System.out.println(a.equals(b));              // may be false — proxy comparison surprises
```

**Bug:** Hibernate proxies subclass the entity. `a.getClass() == b.getClass()` returns false. If `equals` uses `instanceof`, safe. If it uses `getClass()` comparison, broken.

**Fix:** use `instanceof` in `equals()`, unwrap the proxy with `Hibernate.unproxy()` before comparison, or convert entity to a record where possible.

---

## D8. Builder that forgets required fields

```java
Pizza p = new Pizza.Builder(null)   // size is null but no check
    .cheese(true)
    .build();
// NPE later when you try p.getSize().name()
```

**Bug:** Builder didn't validate required fields.

**Fix:** validate in `build()`:
```java
public Pizza build() {
    Objects.requireNonNull(size, "size required");
    return new Pizza(this);
}
```

---

# WHEN LAMBDAS REPLACE PATTERNS (Modern Java takeaway)

| Old Pattern | Modern Java |
|---|---|
| Strategy | `Function<T,R>`, `Comparator<T>`, etc. — lambda |
| Command | `Runnable`, `Callable<T>` — lambda |
| Observer | `Consumer<Event>` list, or reactive streams |
| Template Method | Higher-order function taking a `Consumer` for the varying step |
| Iterator | `Stream<T>` |
| Visitor | Sealed types + pattern matching switch (Java 21+) |
| Factory Method | Static factory + method reference |

**Rule of thumb:** if a pattern was "define an interface with one method and pass around implementations," lambdas make it a one-liner.

---

# QUICK-FIRE RAPID ROUND

| Question | Answer |
|---|---|
| Which pattern is `Comparator`? | Strategy |
| Which pattern is `Runnable`? | Command |
| Which pattern is `Iterator`? | Iterator |
| Which pattern is Spring `@Transactional`? | Proxy (Decorator-like) |
| Which pattern is `BufferedReader`? | Decorator |
| Which pattern is `Arrays.asList`? | Adapter |
| Which pattern is `Optional.of`? | Factory Method / Static Factory |
| Which pattern is `HttpServlet.service()`? | Template Method |
| Which pattern is Spring `ApplicationEvent`? | Observer |
| Which pattern is `Files.readAllBytes`? | Facade |
| Which pattern is `Integer.valueOf(5)`? | Flyweight (cached) |
| Which pattern is JDBC's `Connection`? | Bridge |
| Which pattern for undo/redo? | Command + Memento |
| Which pattern for order workflow? | State |
| Which pattern for middleware? | Chain of Responsibility |

---

# THE ONE-SENTENCE SUMMARIES

- **Singleton** — one instance, global access
- **Factory Method** — subclasses decide the concrete class
- **Abstract Factory** — create families of related objects
- **Builder** — construct complex objects step-by-step
- **Prototype** — clone rather than construct
- **Adapter** — convert one interface into another
- **Bridge** — separate abstraction from implementation
- **Composite** — treat parts and wholes uniformly
- **Decorator** — add responsibilities dynamically
- **Facade** — simplified interface to a complex subsystem
- **Flyweight** — share fine-grained objects to save memory
- **Proxy** — placeholder controlling access to the real object
- **Chain of Responsibility** — pass request along handlers
- **Command** — encapsulate a request as an object
- **Iterator** — sequential access without exposing structure
- **Mediator** — reduce inter-object coupling via a central hub
- **Memento** — capture and restore internal state
- **Observer** — one-to-many change notification
- **State** — behavior changes with internal state
- **Strategy** — swap algorithms at runtime
- **Template Method** — fixed algorithm skeleton with pluggable steps
- **Visitor** — add operations to a class hierarchy without modifying it

---

## The interview-safe summary you can drop verbatim

> Design patterns are named, reusable solutions to recurring design problems. The 23 Gang-of-Four patterns fall into three groups: creational (Singleton, Factory, Builder, Prototype, Abstract Factory), structural (Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy), and behavioral (Chain of Responsibility, Command, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method, Visitor). Modern Java has made several of them nearly obsolete — Strategy and Command collapse to lambdas, Iterator into streams, Visitor into pattern-matching switches over sealed types. The patterns still matter because frameworks like Spring, Hibernate, and the JDK are literally built on them, and using the pattern names gives you and your team a shared vocabulary for design discussions.
