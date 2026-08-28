# Java `static` — Interview Questions & Answers

A ready-to-quiz reference covering **every** place `static` appears in Java. Ordered from simple → tricky → senior-level → hands-on debugging. Each question has a concise answer for self-testing.

---

## Where `static` shows up in Java

Before diving in, know the **six places** the keyword appears — every question below fits in one of these buckets:

| Form | Example | What it means |
|---|---|---|
| Static field | `static int counter;` | One shared copy per class, not per instance |
| Static method | `static int square(int n)` | Belongs to the class, called without an instance |
| Static block | `static { ... }` | Runs once when the class is loaded |
| Static nested class | `static class Node { ... }` | Nested type with no reference to the outer instance |
| Static import | `import static java.lang.Math.*;` | Use static members unqualified |
| Static factory method | `public static Foo of(int x)` | Named constructor pattern (`List.of`, `Optional.of`) |

---

## Basic conceptual

### Q1. What is `static` in Java?

`static` means **"belongs to the class, not to any instance."** A static member exists exactly **once**, shared across all instances (and callable without any instance at all).

### Q2. What's the difference between a static (class) variable and an instance variable?

| | Static / class variable | Instance variable |
|---|---|---|
| Storage | one copy per class | one copy per object |
| Initialized | when class is loaded | when constructor runs |
| Accessed via | `ClassName.field` (or instance, discouraged) | `instance.field` |
| Lives in | metaspace / class metadata area | heap (with the object) |

### Q3. Difference between static and non-static context?

- **Static context** — a method or block where there is no `this`. You cannot use instance fields or instance methods directly.
- **Non-static context** — inside an instance method or constructor. `this` is available; you can use both instance and static members freely.

### Q4. Can `this` be used in a static method? Why or why not?

**No.** `this` refers to "the current instance." A static method isn't called on an instance — it's called on the class — so there is no current instance to refer to. Compile error if you try.

---

## Static fields

### Q5. When are static fields initialized?

When the class is **first initialized**. This happens on any of:
- The class is instantiated (`new Foo()`)
- A static method is invoked (`Foo.bar()`)
- A non-constant static field is read or written (`Foo.count`)
- `Class.forName("Foo")` is called
- A subclass is initialized (triggers parent initialization first)

Not triggered by declaring a variable of that type or accessing a **compile-time constant** static field.

### Q6. Are static fields thread-safe?

**No.** Static fields are just shared mutable state — the *worst* kind of thread-unsafe. Multiple threads read/write the same memory. You must synchronize, use `volatile`, or use atomics (`AtomicInteger`, `AtomicReference`) if you want safety.

### Q7. Can static fields be `final`?

Yes — this is the classic "constant" pattern:

```java
public static final int MAX_RETRIES = 3;
```

Combined `static final` primitives/`String` with a literal initializer are **compile-time constants** and get inlined into every caller's bytecode.

### Q8. Can static fields be `private`?

Yes. `private static` is used for internal state that shouldn't leak — e.g., a private cache or counter with public access methods.

### Q9. How is a static field accessed?

Preferred: `ClassName.field`. Discouraged but legal: `instance.field` (compiler resolves it to the static access anyway — the instance is ignored).

```java
Foo f = null;
f.STATIC_FIELD;   // works — no NPE, because no instance access is actually made
```

---

## Static methods

### Q10. What is a static method?

A method that belongs to the class, not to instances. Called as `ClassName.method(...)`. No implicit `this`.

### Q11. Can a static method access instance variables?

**Not directly.** Static context has no `this`. You'd need to pass an instance explicitly:

```java
public static void print(Foo foo) {
    System.out.println(foo.name);   // fine — accessed through the parameter
}
```

### Q12. Can a static method call an instance method?

Same rule — only if you have an instance to call it on. `static void x() { instance.y(); }` works if you have an instance; `static void x() { y(); }` does not compile if `y()` is an instance method.

### Q13. Can a static method be overridden?

**No — it can be *hidden*, not overridden.** More on this in Q26.

### Q14. When would you use a static method?

- **Utility functions** — `Math.max`, `Collections.sort`, `Objects.equals`
- **Static factory methods** — `List.of`, `Optional.empty`, `Instant.now`
- **Pure functions** — no state to share, no instance needed
- **Entry points** — `public static void main(String[] args)`

Not for: anything that has or affects state that should be per-instance.

---

## Static blocks

### Q15. What is a static initializer block?

A block that runs **once** when the class is loaded, used to initialize static state that needs more than a single expression.

```java
public class Config {
    public static final Map<String, String> DEFAULTS;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("host", "localhost");
        m.put("port", "8080");
        DEFAULTS = Collections.unmodifiableMap(m);
    }
}
```

### Q16. When does a static block run?

At **class initialization time** — same trigger conditions as static field initialization (Q5). It runs exactly once per classloader, in the order the blocks appear in source, interleaved with static field initializers.

### Q17. Can there be multiple static blocks?

Yes. They run in **source-order**, top to bottom, interleaved with any static field initializers between them.

### Q18. What is the order of execution: static blocks, instance blocks, constructor?

For a class hierarchy `Grandparent → Parent → Child`, on `new Child()`:

1. `Grandparent` static blocks + static field initializers (once, on first class use)
2. `Parent` static blocks + static field initializers
3. `Child` static blocks + static field initializers
4. `Grandparent` instance blocks + instance field initializers, then constructor body
5. `Parent` instance blocks + instance field initializers, then constructor body
6. `Child` instance blocks + instance field initializers, then constructor body

Static phase happens **once per classloader**. Instance phase happens on **every `new`**.

### Q19. What if a static block throws?

The JVM wraps the exception in `ExceptionInInitializerError`. **Critical:** the class enters a "failed" state — every subsequent use throws `NoClassDefFoundError` (not `ExceptionInInitializerError` — that's only on the *first* attempt). This can look like the class doesn't exist, when really its initializer failed once and left permanent damage.

---

## Static nested classes

### Q20. What is a static nested class?

A class declared inside another class with the `static` modifier. It behaves like a **top-level class** that just happens to live inside another's namespace.

```java
public class Tree {
    public static class Node {
        int value;
        Node left, right;
    }
}
```

You use it as `Tree.Node`.

### Q21. Difference between a static nested class and an (inner) non-static nested class?

| | Static nested | Non-static (inner) |
|---|---|---|
| Holds reference to outer instance | No | Yes (implicit `Outer.this`) |
| Requires outer instance to construct | No | Yes: `outer.new Inner()` |
| Can access outer's instance members | No (only through an explicit outer ref) | Yes |
| Memory overhead | none | one extra reference field |
| Use case | grouping helper types | callbacks, iterators tied to a specific outer instance |

### Q22. When should you prefer a static nested class?

**Effective Java Item 24:** *"Favor static member classes over non-static."* Non-static inner classes leak a reference to the outer instance, which can cause memory leaks (particularly in Android). Use non-static only when you genuinely need the outer instance's state.

### Q23. Can a static nested class access outer class private members?

Yes — nesting grants access to private members of the enclosing class (via a specific instance, since static nested classes have no implicit outer reference).

```java
public class Outer {
    private int x;
    public static class Nested {
        int read(Outer o) { return o.x; }   // fine, private access via nesting
    }
}
```

---

## Static import

### Q24. What is `import static`?

Imports specific static members (or all with `*`) so you can use them **unqualified**.

```java
import static java.lang.Math.*;
import static java.util.Collections.emptyList;

double d = sqrt(pow(x, 2) + pow(y, 2));
List<String> empty = emptyList();
```

Without static import, you'd write `Math.sqrt(Math.pow(...))`.

### Q25. When is static import a good idea, and when is it a bad idea?

**Good:**
- Math functions in numerically dense code (`sin`, `cos`, `sqrt`) — improves readability
- Assertion helpers in tests (`assertEquals`, `assertThat`, `mock`)

**Bad:**
- Overusing them makes it unclear which class a method belongs to (`of(5)` — which library's `of`?)
- Conflicts with methods of the same name in the class you're in — ambiguity errors
- Reader has to jump to imports to understand identifiers

Rule of thumb: static-import only when the qualifier adds noise and the unqualified name is obviously from a well-known helper.

---

## Static and inheritance — the trap

### Q26. Are static methods inherited?

**Kind of — but they aren't polymorphic.** A subclass can call a superclass's static method without qualification, but if the subclass declares a method with the same signature, that's **method hiding**, not overriding.

```java
class Base    { static void greet() { System.out.println("Base"); } }
class Sub extends Base { static void greet() { System.out.println("Sub"); } }

Base b = new Sub();
b.greet();   // prints "Base" — static dispatch based on the compile-time type
```

An instance method call would print "Sub" (dynamic dispatch). Static is resolved at compile time.

### Q27. Method hiding vs method overriding — what's the difference?

| | Hiding | Overriding |
|---|---|---|
| Applies to | static methods | instance methods |
| Dispatch | compile-time, based on declared type | runtime, based on actual type |
| `@Override` allowed? | No — compile error | Yes |
| `super.method()` calls | the superclass's static | the superclass's instance method |

### Q28. Can a static method be `final`?

**Yes, but it's mostly redundant.** Static methods can't be overridden anyway — they can only be *hidden* by a same-signature static in a subclass. Marking a static method `final` prevents even that hiding.

### Q29. Can a static method be `abstract`?

**No.** `abstract` says "no implementation, must be provided by a subclass" — but static methods aren't overridden by subclasses, so there's no mechanism to provide the implementation. Compile error.

---

## Threading / memory model

### Q30. Are static methods thread-safe?

**Not automatically.** A static method is thread-safe **only if it doesn't touch mutable shared state**. Stateless static methods (`Math.max`) are trivially thread-safe. Static methods that read/write static fields need synchronization.

### Q31. What's the memory-visibility guarantee for static field initialization?

Class initialization is **thread-safe by the JVM**. The JLS §12.4.2 guarantees that class initialization is synchronized on the class's `Class` object — only one thread runs the static initializers, and other threads block until it's done. All threads then observe fully initialized static fields.

**Consequence:** the "initialization on demand holder" idiom is safe without any explicit synchronization:

```java
public class Singleton {
    private Singleton() {}
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }
    public static Singleton getInstance() {
        return Holder.INSTANCE;   // thread-safe, lazy — perfect
    }
}
```

### Q32. What's `ExceptionInInitializerError` and when do you get it?

Thrown when a static initializer (static block or static field initializer) throws an exception. Only the **first** access to the class sees this specific error — every subsequent access sees `NoClassDefFoundError` even though the class file exists on the classpath.

---

## Trick / gotcha questions

### Q33. What does this print?

```java
public class Foo {
    static int x = 10;
}
Foo f = null;
System.out.println(f.x);   // NullPointerException?
```

**Prints 10.** Static access via an instance reference is resolved to the class at compile time — the instance is not actually dereferenced. No NPE. Confusing, which is exactly why IDEs warn against `instance.staticField`.

### Q34. Compile-time constant vs runtime static final — what's the difference?

```java
public static final int A = 10;                       // compile-time constant, inlined
public static final int B = new Random().nextInt();   // runtime static final, not inlined
public static final String S = "hello";               // compile-time constant
public static final String T = "hello".toUpperCase(); // runtime, not a compile-time constant
```

Compile-time constants (`static final` + primitive/`String` + constant expression initializer) get baked into callers' bytecode. Same "stale constant across recompilations" trap as with any `final`.

### Q35. Can a static block reference a static field declared **later** in the class?

Yes, but with restrictions. **Reading** a not-yet-initialized static field from a static block gives its default value (0/null). **Writing** to it is always fine.

```java
public class Foo {
    static { System.out.println(x); }   // prints 0
    static int x = 10;
    static { System.out.println(x); }   // prints 10
}
```

### Q36. What is a `static` method's return value used for in an `enum`?

Enums support static methods just like any class. Two common ones auto-generated by the compiler:

- `Signal.values()` — returns all constants
- `Signal.valueOf(String)` — parses a name back to an enum constant

You can also add your own static factories.

### Q37. Can a static field be `volatile`?

Yes. `static volatile` is common in double-checked-locking-style singletons and status flags shared across threads.

```java
private static volatile Singleton instance;
```

---

## Design / open-ended

### Q38. Why do many senior engineers say "avoid static state"?

Static mutable state is:
- **Global** — anything anywhere can mutate it, hard to reason about
- **Hard to test** — state leaks between tests; hard to mock
- **Not injectable** — bypasses dependency injection
- **Thread-unsafe by default** — every access needs synchronization thought
- **Coupled to the classloader** — hard to reset, hard to reload

Rule of thumb: `static final` constants are fine. `static` mutable fields are a design smell.

### Q39. What is a static utility class? How do you write one properly?

A class that holds only static methods (and possibly `static final` constants) — no instances allowed. Pattern:

```java
public final class MathUtils {              // final — no subclassing
    private MathUtils() {                    // private constructor — no instantiation
        throw new AssertionError("no instances");
    }
    public static int square(int n) { return n * n; }
}
```

Examples: `java.util.Collections`, `java.util.Arrays`, `java.util.Objects`, `Math`.

### Q40. What is the static factory method pattern? What are its advantages over constructors?

Named static methods that return instances instead of using `new`:

```java
public static Optional<T> of(T value)  { ... }
public static Optional<T> empty()      { ... }
```

**Advantages (Effective Java Item 1):**
1. **Named** — `Instant.ofEpochMilli(ms)` vs a confusing constructor overload
2. **Can return cached instances** — `Integer.valueOf(5)` returns from a cache for small ints
3. **Can return subtypes** — `EnumSet.of(...)` returns `RegularEnumSet` or `JumboEnumSet` based on size
4. **Reduces verbosity** — pre-Java-7 `Map<String, List<Trade>> map = new HashMap<>()` vs `Map.of(...)`

Disadvantages: no automatic subclass compatibility (subclasses can't inherit static factories usefully), and static factories aren't as discoverable in some IDEs.

### Q41. Why does static state make unit testing hard?

- **Test isolation breaks** — one test mutates a static field; the next test sees the mutation
- **Cannot be mocked easily** — before Mockito 3.4, mocking statics required PowerMock and was slow/fragile
- **Order-dependent tests** — flaky by default
- **No test-scoped instances** — can't create a fresh state per test

Fix: refactor static state into an injectable dependency. If you must have statics, use `mockStatic(...)` in Mockito 3.4+ and always close the scope in `@AfterEach`.

---

## Senior-level questions

### Q42. Where are static fields stored in memory?

- **Java 7 and earlier:** PermGen (Permanent Generation, part of the heap)
- **Java 8+:** Metaspace (native memory, outside the Java heap)

Class metadata, method bytecode, and static field storage all live there. Metaspace grows dynamically by default (bounded by `-XX:MaxMetaspaceSize`).

### Q43. Explain the class initialization procedure per JLS §12.4.

When a class is initialized:
1. **Synchronize on the `Class` object** — only one thread proceeds; others block
2. **If already initialized or being initialized by *this* thread → return** (handles re-entrant static access)
3. **If failed initialization state → throw `NoClassDefFoundError`**
4. **Initialize superclass first** (recursively; for interfaces, only default-methods-containing ones)
5. **Execute static field initializers and static blocks in source order**
6. **Mark class as initialized, notify waiting threads**

This is why the "initialization on demand holder" singleton is safe — the JVM synchronizes for us.

### Q44. How do static fields interact with classloaders?

Class identity in the JVM is `(className, classLoader)`. Two classloaders loading the same `.class` file produce **two distinct classes**, each with its **own set of static fields**.

Where this bites:
- Application servers (WebLogic, Tomcat) — one static instance per deployed app
- OSGi — each bundle has its own classloader
- Spring DevTools / hot reload — reloading may create a second copy
- Plugin architectures

"Singleton" often silently means "per classloader."

### Q45. What is the Bill Pugh / Initialization-on-Demand Holder singleton pattern?

```java
public class Singleton {
    private Singleton() {}

    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

**Why it's the best singleton in Java:**
- **Lazy** — `Holder` isn't loaded until `getInstance()` is called
- **Thread-safe** — JVM guarantees class-init synchronization
- **No `synchronized`** — no lock contention on repeat calls
- **No `volatile`** — no memory barrier needed
- **Simple** — no double-checked locking

### Q46. What is double-checked locking, and why did it require `volatile`?

Pre-Java-5 singleton pattern:

```java
private static Singleton instance;
public static Singleton getInstance() {
    if (instance == null) {
        synchronized (Singleton.class) {
            if (instance == null) {
                instance = new Singleton();
            }
        }
    }
    return instance;
}
```

**Broken before Java 5.** The JVM was allowed to reorder writes such that another thread could see `instance != null` **before** the constructor finished, leading to observations of a half-built object.

**Java 5+ fix:** declare `instance` as `volatile`. `volatile` writes have release semantics, preventing the reorder.

Prefer the Holder pattern (Q45) instead — no `volatile`, no lock in the common path.

### Q47. What are the trade-offs of using `Mockito.mockStatic(...)`?

**Pros:**
- Lets you test code that calls static methods (`Instant.now()`, `UUID.randomUUID()`, legacy statics)
- No PowerMock needed anymore

**Cons:**
- Uses `ByteBuddy` inline mock maker — heavier than proxy-based mocks
- Slower test startup and execution
- Scope is thread-local — must be closed in `@AfterEach` or via try-with-resources
- Encourages designs that should have used dependency injection instead

Better answer for design: inject a `Clock` or `IdGenerator`, don't call static factories directly in production code.

### Q48. Can you have a `static` local variable in Java?

**No.** Unlike C/C++, Java doesn't support static local variables. Locals are per-invocation. If you need persistent per-call state, use a static field on the enclosing class.

### Q49. What is the interaction between `static` and generics?

**Static fields and methods cannot use the enclosing class's type parameters** — the type parameter is per-instance, but static is per-class. Generic static methods declare their own type parameters:

```java
public class Container<T> {
    private static T shared;                            // compile error
    public static <U> void doWork(U u) { ... }          // fine — U is the method's own type param
}
```

`Collections.<T>emptyList()` works the same way — `<T>` is the method's parameter, not the class's.

### Q50. How does `static` interact with reflection?

- `Class.getDeclaredFields()` returns all fields; check `Modifier.isStatic(f.getModifiers())` to filter
- `Field.get(null)` reads a static field (pass `null` as the instance)
- `Method.invoke(null, args)` calls a static method (pass `null` as the receiver)
- On JDK 17+, reflective access to `private static` fields in unopened modules is blocked without `--add-opens`

### Q51. Are `static` fields serialized when the enclosing object is serialized?

**No.** Serialization only writes instance state. Static fields are per-class, not per-object — so they're not part of what's persisted. On deserialization, static fields simply reflect whatever state the class has in the current JVM.

This is often surprising: serialize an object in JVM A with a static counter at 100; deserialize in JVM B where the counter starts at 0 → your restored object appears in a different context.

### Q52. Explain how `enum` internally uses `static`.

`enum` compiles to a class where each constant is a `public static final` instance:

```java
public enum Signal { BUY, SELL, HOLD }
```

is roughly equivalent to:

```java
public final class Signal extends Enum<Signal> {
    public static final Signal BUY  = new Signal("BUY", 0);
    public static final Signal SELL = new Signal("SELL", 1);
    public static final Signal HOLD = new Signal("HOLD", 2);

    private static final Signal[] VALUES = { BUY, SELL, HOLD };
    public static Signal[] values() { return VALUES.clone(); }
    public static Signal valueOf(String n) { ... }
    // private constructor
}
```

The constants are initialized in a synthetic static block. This is why enums are naturally thread-safe singletons — the JVM handles initialization safety.

---

## Deep senior-level questions

### Q53. Explain the bytecode difference between an instance and a static method call.

- Instance methods → `invokevirtual` (or `invokeinterface` for interface methods) — resolves dispatch at runtime based on the receiver's class
- Static methods → `invokestatic` — resolves at link time; no receiver

`invokestatic` is faster (no vtable lookup, no null check on the receiver) and JIT-friendly. Callers of static methods often inline aggressively.

### Q54. Why can't you use a class's type parameter in a static context?

Because the type parameter `T` is bound per-instance — `List<String>` and `List<Integer>` are the same runtime class but have different type parameter bindings that only exist at compile time (type erasure). Static context has no instance, therefore no binding.

```java
public class Container<T> {
    static T shared;                     // which T? per-class, but T is per-instance
    static void set(T value) { ... }     // same problem
}
```

To have a "per-parameterization" static, you'd need reified generics (which Java doesn't have — Valhalla changes this eventually).

### Q55. What happens if you subclass a class with a static block? When does the parent's static block run?

The parent's static block runs when the parent is initialized — which is triggered by initializing the subclass. Order:

1. Load & initialize `java.lang.Object` (already done)
2. Load & initialize `Parent` → runs `Parent`'s static blocks
3. Load & initialize `Child` → runs `Child`'s static blocks

Never in the reverse order — parents always initialize first.

### Q56. What is the "static holder" pattern, and how is it different from an eagerly initialized singleton?

**Eager singleton:**
```java
public class Singleton {
    private static final Singleton INSTANCE = new Singleton();   // created at class load
    public static Singleton getInstance() { return INSTANCE; }
}
```

**Holder pattern:**
```java
public class Singleton {
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();       // created when Holder is first used
    }
    public static Singleton getInstance() { return Holder.INSTANCE; }
}
```

**Difference:** in eager, `Singleton.INSTANCE` is created when `Singleton` itself is loaded (which may happen accidentally via reflection or static field access). In holder, `Holder` is a separate class that's not loaded until `getInstance()` is called — true lazy initialization.

### Q57. What is "class initialization safety" and how does it enable thread-safe lazy initialization?

The JVM (per JLS §12.4.2) guarantees:
- Class initialization is synchronized on the `Class` object
- Any thread observing an initialized class sees all static field writes performed by the initializer
- No thread observes partially-initialized static state

This is why static-final fields set during class initialization are safely visible to all threads without `volatile` or `synchronized`. The Holder pattern exploits this directly.

### Q58. How do frameworks like Spring interact with static state? Why is `@Autowired` on a static field a bad idea?

Spring instantiates beans and populates their instance fields via reflection. It **cannot inject into static fields directly** — Spring's autowiring works on instance context.

Anti-pattern (won't work):
```java
public class Service {
    @Autowired private static Repository repo;   // will be null at runtime
}
```

Common workaround (still discouraged):
```java
public class Service {
    private static Repository repo;
    @Autowired
    public void setRepo(Repository r) { repo = r; }   // hacky — makes state static from a bean
}
```

Why discouraged: it defeats the point of DI (testability, lifecycle management), and creates a per-classloader hidden dependency. Prefer keeping dependencies as instance fields on injectable beans.

### Q59. What is the ACC_STATIC flag in the class file?

`0x0008` — appears in the `access_flags` of fields and methods marked `static`. This is what the JVM uses to decide dispatch (`invokestatic` vs `invokevirtual`) and where to store the field (per-class metadata vs. per-instance layout).

You can inspect via `javap -v`.

### Q60. What's the deal with `static` in interfaces (Java 8+)?

Java 8 added `static` methods to interfaces:

```java
public interface Signal {
    static Signal buy() { return new BuySignal(); }
    static Signal sell() { return new SellSignal(); }
}
```

Rules:
- Static methods on interfaces are **not inherited** by implementing classes
- You call them as `Signal.buy()`, never as `MyImpl.buy()`
- Java 9 also added `private static` methods for internal helpers used by default and static methods on the same interface

This lets utility factories live on the interface instead of a separate `SignalUtils` class.

### Q61. What are the risks of `static { ... }` blocks that do expensive work?

- **Class-load slowdown** — every triggering access blocks until it's done
- **Deadlock risk** — if the static block spawns threads that try to load the same class, they wait on a lock held by the initializing thread
- **Failure is permanent** — throw once, class is broken for the JVM lifetime (`NoClassDefFoundError` forever)
- **Order coupling** — static blocks in different classes can subtly depend on each other

Rule: keep static blocks small and side-effect-free. Push initialization work into explicit `init()` methods or lazy holders.

### Q62. How does `static` show up in the JIT compiler's decisions?

- **Static methods are prime inlining candidates** — no polymorphism, no receiver null-check needed
- **`static final` fields (non-constant)** — HotSpot treats them as **trusted constants** after class initialization; reads get folded into inlined callers, similar to compile-time constants but from JIT rather than javac
- **Static fields with `@Stable` (JDK-internal)** — pushed further; JIT assumes the value won't change even without `final`

This is why `static final Logger LOG = LoggerFactory.getLogger(...)` and similar patterns are fast — the JIT specializes the loggers as constants.

### Q63. Static import ambiguity — what happens with clashes?

```java
import static java.util.Collections.emptyList;
import static com.mylib.Utils.emptyList;

List<String> l = emptyList();   // compile error — reference is ambiguous
```

Compiler refuses. You must qualify one of them: `Collections.emptyList()` or `Utils.emptyList()`.

### Q64. What's the difference between `Class.forName("Foo")` and `Foo.class` in terms of static initialization?

- `Foo.class` — a class literal. **Does NOT trigger initialization** (loads the class but doesn't run static blocks)
- `Class.forName("Foo")` — **triggers initialization by default**
- `Class.forName("Foo", false, classLoader)` — loads without initializing (matches `Foo.class` semantics)

This matters for JDBC-style code where a static block registers a driver on load:

```java
Class.forName("com.mysql.jdbc.Driver");   // runs Driver's static block → registers driver
```

---

## Debugging scenarios — spot the bug

### D1. Two classloaders, two counters

```java
public class Counter {
    public static int value = 0;
    public static int increment() { return ++value; }
}

// classloader A
Counter.increment(); Counter.increment();
System.out.println(Counter.value);   // 2

// same code, run in classloader B (e.g., a plugin, or after Spring DevTools reload)
System.out.println(Counter.value);   // 0 (!)
```

**Bug:** two classloaders → two distinct `Counter` classes → two independent `value` fields. "Static means global" is a lie inside a JVM with multiple classloaders.

**Fix:** put shared state in a class loaded by the parent (bootstrap or app) classloader, or use a truly external store (JVM system properties, external cache).

---

### D2. The static counter race

```java
public class Stats {
    private static int trades = 0;
    public static int recordTrade() { return ++trades; }
}
```

Two threads each call `recordTrade()` 1000 times. Final value is often **less than 2000**.

**Bug:** `++trades` is read-modify-write — not atomic. Threads interleave and lose increments.

**Fix:** `AtomicInteger`:
```java
private static final AtomicInteger trades = new AtomicInteger();
public static int recordTrade() { return trades.incrementAndGet(); }
```

---

### D3. The static block that broke the class forever

```java
public class Config {
    public static final Properties PROPS;
    static {
        try (InputStream in = Config.class.getResourceAsStream("/app.properties")) {
            PROPS = new Properties();
            PROPS.load(in);   // NullPointerException if file missing
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
```

First access throws `ExceptionInInitializerError`. Every subsequent access throws `NoClassDefFoundError: Could not initialize class Config` — even after you've fixed the file.

**Bug:** class initialization failed once → JVM permanently marks the class as errored. There is no re-init; you must restart the JVM (or use a fresh classloader).

**Fix:** don't throw from static blocks. Use lazy initialization:
```java
public class Config {
    private static volatile Properties props;
    public static Properties get() {
        if (props == null) {
            synchronized (Config.class) {
                if (props == null) props = load();
            }
        }
        return props;
    }
}
```

Or wrap the throw so subsequent calls can retry:
```java
static {
    try { PROPS = load(); }
    catch (Exception e) { PROPS = new Properties(); /* fallback */ }
}
```

---

### D4. Method hiding surprise

```java
class Animal {
    public static void describe() { System.out.println("Animal"); }
}
class Dog extends Animal {
    public static void describe() { System.out.println("Dog"); }
}

Animal a = new Dog();
a.describe();   // "Animal" — but developer expected "Dog"
```

**Bug:** `describe` is static. Static methods are **hidden**, not overridden. Dispatch is based on the compile-time type of `a` (`Animal`), not the runtime object (`Dog`).

**Fix:** make the methods non-static if polymorphism is needed. Or call `Dog.describe()` / `Animal.describe()` explicitly and stop pretending it's inheritance.

---

### D5. The stale static-final constant

```java
// library
public class Limits {
    public static final int MAX_CONN = 10;
}

// caller compiled with library v1.0
System.out.println(Limits.MAX_CONN);   // 10

// library upgraded to v1.1 → MAX_CONN = 50, JAR replaced
// caller runs again without recompilation
System.out.println(Limits.MAX_CONN);   // 10 (!)
```

**Bug:** `static final` primitives with literal initializers are **compile-time constants** — inlined into every caller.

**Fix:** recompile callers, or defeat inlining: `public static final int MAX_CONN = Integer.parseInt("50");`

---

### D6. Global state leaks between tests

```java
public class Cache {
    private static final Map<String, Object> CACHE = new HashMap<>();
    public static void put(String k, Object v) { CACHE.put(k, v); }
    public static Object get(String k) { return CACHE.get(k); }
}

// TestA sets a value; TestB (different logic) sees leftover value → false positive
```

**Bug:** static state is shared across all tests in the JVM. Test order affects outcome; tests become flaky.

**Fix:** either add a `Cache.clear()` and call it in `@BeforeEach`, or refactor `Cache` to a non-static injectable service.

---

### D7. Static block reads a field before it's initialized

```java
public class Foo {
    static { System.out.println("x = " + x); }   // prints "x = 0"
    static int x = 10;
    static { System.out.println("x = " + x); }   // prints "x = 10"
}
```

**Bug (not really a bug — a surprise):** static blocks and initializers run **in source order**. Reading `x` in the first block sees its default value (`0`), not `10`.

**Rule:** declare static fields before any static block that depends on them.

---

### D8. Enum with a static field that references the enum

```java
public enum Signal {
    BUY(REGISTRY),   // (!!) forward reference to REGISTRY
    SELL(REGISTRY);

    private static final Map<String, Signal> REGISTRY = new HashMap<>();
    private final Map<String, Signal> ref;

    Signal(Map<String, Signal> r) { this.ref = r; }
}
```

Compile error: "illegal forward reference."

**Bug:** enum constants are initialized *before* any other static fields. `REGISTRY` doesn't exist yet when `BUY` and `SELL` are constructed.

**Fix:** use a static block after the constants:
```java
public enum Signal {
    BUY, SELL;

    private static final Map<String, Signal> REGISTRY = new HashMap<>();
    static {
        for (Signal s : values()) REGISTRY.put(s.name(), s);
    }
}
```

---

### D9. NullPointerException on a static field access — but static!

```java
public class Foo {
    public static final String NAME = compute();
    private static String compute() { return other.value; }   // other is a static field
    private static Other other;
}
```

Class initialization → runs field initializers in order → `NAME = compute()` runs first → `other` is still `null` → NPE inside `compute()` → wrapped in `ExceptionInInitializerError`.

**Bug:** initializer order matters. Fields are initialized top-to-bottom.

**Fix:** reorder declarations so dependencies come first, or move initialization into a static block that runs after all fields are declared.

---

### D10. Static import ambiguity

```java
import static java.util.Collections.emptyList;
import static com.mylib.MyUtils.emptyList;

List<String> l = emptyList();   // compile error: reference is ambiguous
```

**Bug:** two static imports collide.

**Fix:** remove one; or qualify the call: `Collections.emptyList()`.

---

### D11. Serialization drops static state

```java
public class Session implements Serializable {
    private static int totalSessions = 0;
    private final String user;
    public Session(String u) { this.user = u; totalSessions++; }
}

// JVM A: create 5 sessions; totalSessions = 5; serialize one to a file
// JVM B: fresh JVM; deserialize the file; check Session.totalSessions
System.out.println("total = " + Session.totalSessions);   // 0
```

**Bug:** `static` fields are **not serialized**. Only instance state is. `totalSessions` in JVM B reflects JVM B's local state, not JVM A's.

**Fix:** move `totalSessions` to an instance field if it must be persisted, or use an external store.

---

### D12. Double-checked locking without volatile

```java
private static Singleton instance;

public static Singleton getInstance() {
    if (instance == null) {
        synchronized (Singleton.class) {
            if (instance == null) instance = new Singleton();
        }
    }
    return instance;
}
```

**Bug (pre-Java 5, and still fragile without `volatile`):** the constructor's writes can be reordered relative to the `instance = new Singleton()` assignment. Another thread may see `instance != null` while the object's fields are still uninitialized.

**Fix (Java 5+):** declare `instance` as `volatile`. Better: use the Holder pattern (Q45) — no `volatile` needed, no lock in the common path.

---

## Quick-fire rapid round

| Question | Answer |
|---|---|
| Can `main` be non-static? | No — the JVM entry point must be `public static void main` |
| Can a class be `static`? | Only if it's a **nested** class. Top-level classes can't |
| Can a constructor be static? | No — construction is per-instance by definition |
| Can an interface have static methods? | Yes (Java 8+) |
| Can a lambda access static fields? | Yes — no `this` needed |
| Can `synchronized` be static? | Yes — `synchronized (ClassName.class)` under the hood |
| Are static fields shared across instances? | Yes — one copy per class per classloader |
| Do enum constants use static? | Yes — each constant is a `public static final` instance |
| Can you unload a class's static state? | Only by unloading its classloader — very rare in practice |
| Is `static final Logger` inlined by the JIT? | Yes — treated as a trusted constant after init |
| Can static methods throw checked exceptions? | Yes — same rules as any method |
| Can a static nested class extend the outer class? | Yes — nothing prevents it |

---

## The one-sentence summary you can drop in an interview

> `static` in Java means "belongs to the class, not to any instance." It's used on fields (one shared copy), methods (no `this`), blocks (run once at class load), nested classes (no outer reference), imports (unqualified access), and factory methods (a named-constructor pattern). Static state is powerful but dangerous — it's global, thread-unsafe by default, hard to test, and tied to a specific classloader; prefer instance state and dependency injection unless you specifically want class-level semantics.
