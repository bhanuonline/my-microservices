# Java `final` — Interview Questions & Answers

A ready-to-quiz reference on the `final` keyword. Ordered from basic → tricky → senior-level. Each question comes with a concise answer you can use to self-test.

---

## Basic conceptual

### Q1. What is `final` in Java?

A keyword meaning **"cannot be changed."** It applies in three completely different places:

- **Variable** → cannot be reassigned
- **Method** → cannot be overridden
- **Class** → cannot be extended

### Q2. What's the difference between `final`, `finally`, and `finalize()`?

*(The #1 asked question — every interviewer loves this.)*

| | What it is | What it does |
|---|---|---|
| `final` | keyword | locks down variables / methods / classes |
| `finally` | block | always runs after `try/catch`, used for cleanup |
| `finalize()` | method | runs before garbage collection — **deprecated since Java 9**, removed in modern JDKs |

They only *sound* similar. Different jobs entirely.

### Q3. Can you initialize a `final` variable later, or must it be at declaration?

It can be initialized later, but only **once**, and only before it's used. Three legal patterns:

```java
final int a = 5;              // at declaration
final int b;                  // "blank final" — must be set in constructor
final static int c;           // must be set in a static initializer block
```

---

## Variables & references

### Q4. Does `final` make an object immutable?

**No.** It only prevents *reassignment of the reference*. The object it points to can still be mutated.

```java
final List<String> list = new ArrayList<>();
list.add("x");             // still allowed — mutates the list
list = new ArrayList<>();  // compile error — reassignment forbidden
```

### Q5. Can a `final` variable be `null`?

Yes.

```java
final String name = null;  // legal
```

### Q6. What is a "blank final"?

A `final` field declared **without** an initial value, assigned exactly once inside the constructor.

```java
public class Trade {
    private final BigDecimal price;   // blank final
    public Trade(BigDecimal price) {
        this.price = price;           // set exactly once
    }
}
```

### Q7. Can a local variable be `final`?

Yes. And any local variable used inside a **lambda** or an **anonymous inner class** must be either `final` or "**effectively final**" (never reassigned after initialization).

```java
int count = 0;
Runnable r = () -> System.out.println(count);   // fine — count is effectively final
count = 5;                                       // now the lambda breaks — compile error
```

---

## Methods & classes

### Q8. Why would you make a method `final`?

To prevent subclasses from changing its behavior. Useful for:
- Template method pattern (the algorithm is fixed; only certain hooks are overridable)
- Security-critical methods
- Methods that maintain class invariants

### Q9. Give examples of `final` classes in the JDK.

`String`, `Integer`, `Long`, `Double` (all wrapper classes), `LocalDate`, `LocalDateTime`, `Instant`, `UUID`.

### Q10. Why is `String` final?

Three reasons:
1. **Security** — can't be subclassed to break invariants (e.g., a subclass that logs every string it sees)
2. **Thread safety** — immutable objects are safe to share across threads without synchronization
3. **String pool caching** — interning depends on the guarantee that a `String`'s value never changes
4. String is final so that nobody can extend String and change its behavior. This helps Java guarantee String's immutability, security, and predictable behavior

### Q11. Can an abstract class be `final`?

**No.** `abstract` means "must be extended"; `final` means "cannot be extended." They contradict each other. Compile error.

### Q12. Can a `final` method be overloaded?

**Yes.** `final` prevents *overriding* (same signature in a subclass), not *overloading* (same name, different parameters, in the same class).

```java
public final void print(String s) { ... }
public final void print(int i) { ... }   // fine — overload
```

---

## Inheritance gotchas

### Q13. Can a `final` method be inherited?

**Yes.** Subclasses inherit it and can call it — they just can't override it.

### Q14. Can a constructor be `final`?

**No.** Constructors aren't inherited, so there's nothing to override — the keyword is meaningless there. Compile error.

### Q15. Can a static method be `final`?

**Yes, but it's redundant.** Static methods can't be overridden anyway — they can only be *hidden* by a same-signature static in a subclass. Marking a static method `final` prevents even that hiding.

### Q16. Can you have a `final` interface method?

**No.** All interface methods are implicitly meant to be implemented or overridden — `final` contradicts that.

---

## Immutability

### Q17. What do you need to make a class fully immutable?

- Mark the class `final` (or use a private constructor)
- All fields `private final`
- No setters
- Defensively copy mutable fields in the constructor AND in getters (or use `List.copyOf` etc.)
- Never let `this` escape from the constructor

### Q18. Is a `record` automatically immutable?

**Shallowly, yes. Deeply, no.** If a record holds a mutable field (like an `ArrayList`), the record is mutable through it. You need defensive copy in the compact constructor:

```java
public record Portfolio(List<Trade> trades) {
    public Portfolio {
        trades = List.copyOf(trades);   // now truly frozen
    }
}
```

### Q19. When you write `final List<String> list`, is the reference immutable, the list, or both?

**Only the reference.** The list itself is still mutable — you can call `list.add(...)`. This is the classic **shallow-immutability trap**.

---

## Threading / memory model

### Q20. Why does `final` matter in multi-threaded code?

`final` fields have a special **Java Memory Model** guarantee: once the constructor finishes, any thread that gets a reference to the object is guaranteed to see the fully initialized `final` fields — without any synchronization. Without `final`, another thread could observe partially constructed state.

### Q21. What breaks that guarantee?

Letting `this` **escape from the constructor** — e.g., registering with an event bus, starting a thread from `this`, or calling an overridable method — before the constructor finishes.

```java
public final class Account {
    private final List<Trade> trades = new ArrayList<>();
    public Account(EventBus bus) {
        bus.register(this);   // BUG: this escapes before construction completes
    }
}
```

---

## Trick / gotcha questions

### Q22. What does this print?

```java
final int[] arr = {1, 2, 3};
arr[0] = 99;
System.out.println(arr[0]);
```

**`99`.** `final` locks the reference, not the array's contents. Arrays are mutable no matter what.

### Q23. Is this legal?

```java
final int x;
if (someCondition) x = 5;
else x = 10;
System.out.println(x);
```

**Yes.** A blank final can be assigned via any path, as long as it's definitely assigned exactly once before use.

### Q24. Compile-time constant vs runtime `final` — what's the difference?

```java
final int A = 10;                      // compile-time constant (inlined into bytecode)
final int B = new Random().nextInt();  // final, but computed at runtime
```

**Compile-time constants** (`final` + primitive/`String` + initialized with a literal expression) get **inlined** into the bytecode of anyone using them. Gotcha: if you change `A` in a library and don't recompile the callers, callers keep the old value.

### Q25. Can `final` fields be modified via reflection?

- **JDK 8–16:** technically yes, via `setAccessible(true)` + `Field.set()` — strongly discouraged
- **JDK 17+:** blocked by default without `--add-opens`

Also: `final` fields on records are locked down harder — you cannot mutate them via reflection at all.

---

## Design / open-ended

### Q26. When would you NOT make a class final, even though it looks like a value type?

- When you need extensibility for **testing** (older mocking libraries required non-final classes)
- For **framework integration** — JPA `@Entity` classes cannot be `final` because Hibernate subclasses them for lazy loading; Spring AOP proxies also may need to subclass
- When the design genuinely benefits from subclassing (a base template with hook methods)

### Q27. Trade-offs of making everything `final` by default?

**Pros:**
- Safer, clearer intent
- Easier to reason about
- Thread-safe by construction

**Cons:**
- Harder to mock in tests
- Incompatible with some frameworks (JPA, certain AOP proxies)
- Can feel restrictive during rapid prototyping

### Q28. Why can lambdas only capture `final` or effectively-final local variables?

Because lambdas may **outlive the method** that created them (e.g., passed to another thread, stored in a field). The captured value is a **copy** of the variable at capture time. If the variable could change afterward, the lambda and the outer scope would diverge — Java forbids that ambiguity by requiring the variable to never be reassigned.

---

## Senior-level questions

### Q29. Explain the JMM guarantees around `final` fields.

The JVM guarantees that when a constructor finishes normally, any thread that later observes a reference to the object will see the correctly initialized `final` fields — **without** needing `volatile` or synchronization. This is called **safe publication via final fields** and only holds if `this` does not escape the constructor.

> final solves:"After the object is created, will other threads see the correctly initialized value?"
> volatile solves:"If one thread changes a variable, will other threads see the latest value?"

 |                           | `final`                            | `volatile`                      |
| ------------------------- | ---------------------------------- | ------------------------------- |
| Can value change?         | ❌ No                               | ✅ Yes                           |
| Main purpose              | Safe initialization                | Visibility of changes           |
| Multiple threads          | Safe visibility after construction | Safe visibility of reads/writes |
| Makes object thread-safe? | ❌ No                               | ❌ No                            |
| Example                   | `final int age`                    | `volatile boolean running`      |


### Q30. How does `final` interact with serialization?

During deserialization, `final` fields are set via **reflection** — the constructor is not called. This is one of the rare cases where `final` isn't truly final: a `Serializable` class can have its `final` fields overwritten by `readObject()`. Also relevant: `readObject` should re-validate invariants because you can't trust that the deserialized state came through the constructor.

### Q31. Design a truly immutable class that holds a `Date`.

`Date` is mutable, so you must defensively copy on both sides:

```java
public final class Event {
    private final Date time;

    public Event(Date time) {
        this.time = new Date(time.getTime());   // copy in
    }

    public Date time() {
        return new Date(time.getTime());        // copy out
    }
}
```

**Better answer:** just use `java.time.Instant` — it's already immutable, no defensive copies needed.

```java
public record Event(Instant time) {}   // done
```

### Q32. Can a `final` field be non-`static` and initialized only once at the class level?

Yes — this is a standard **instance final constant**:

```java
public class Session {
    private final UUID id = UUID.randomUUID();   // set once per instance
}
```

Each instance gets its own `id`; the `id` cannot be reassigned after the constructor completes.

### Q33. Why does making a class `final` improve performance? Is it still true today?

Historically: HotSpot could inline `final` method calls and skip virtual dispatch. Modern JIT compilers (C2, Graal) do **class hierarchy analysis** and can inline non-final methods just as effectively when they observe no overrides at runtime — so the perf argument is mostly obsolete. Use `final` for **design/safety**, not performance.

---

## Deep senior-level questions

### Q34. Explain the "final field freeze" in detail.

At the end of a constructor, the JVM emits an implicit **freeze action** on all `final` fields. Any thread that reads the object reference **after** the freeze is guaranteed to see the correctly initialized `final` fields — no `synchronized`, no `volatile` needed.

Formally, the JMM defines a dereference chain: `freeze → publication → read → dereference` such that the read of the `final` field happens-after its initialization. The guarantee **only** holds if:

1. The reference does not escape during construction
2. The field is truly `final` (not modified reflectively later)
3. Any mutable objects the `final` field points to are themselves safely published (this guarantee doesn't transitively deep-freeze)

### Q35. Is there a bytecode difference between `final` and "effectively final"?

**No.** "Effectively final" is a **compile-time** concept — the compiler checks that a local variable is never reassigned after initialization. There is no `final` bit stored in bytecode for local variables. Local variable table entries have no `final` flag; the local is just a slot.

For **fields**, however, `final` sets the `ACC_FINAL` flag (`0x0010`) in the class file. This flag is what the JVM uses to enforce the field-freeze semantics and reject writes outside of `<init>` / `<clinit>`.

**Consequence:** you can't detect "effectively final" via reflection at runtime — it's already erased.

### Q36. Can a `final` field be modified through a `VarHandle`?

Not in general. `MethodHandles.Lookup.unreflectVarHandle(field)` on a `final` field will throw `IllegalAccessException` unless you use a **private lookup** with sufficient module privileges — and even then, the modification bypasses the JMM's final-field guarantees. Other threads may or may not see the change, depending on caching and JIT decisions.

Contrast this with pre-JDK-9 `Field.setAccessible(true) + Field.set()`, which worked but was similarly unsafe. On JDK 17+, both routes are locked down unless the module explicitly opens itself.

### Q37. `sealed` classes vs `final` — when to use each?

- **`final class`** — no subclasses at all. Locks the hierarchy shut.
- **`sealed class ... permits A, B`** — a *closed* set of allowed subclasses. Great for expressing algebraic data types (sum types).

Example — modeling trading signals:
```java
public sealed interface Signal permits Buy, Sell, Hold {}
public record Buy(BigDecimal target) implements Signal {}
public record Sell(BigDecimal stopLoss) implements Signal {}
public record Hold() implements Signal {}
```

Now the compiler knows the exhaustive list — `switch` on `Signal` is exhaustive without a `default` branch. `final` alone can't express "these three subclasses and no others."

### Q38. Can an interface's default method be marked `final`?

**No.** Default methods on interfaces exist precisely to be overridable by implementers — declaring them `final` would defeat their purpose, and the language forbids it.

If you want a non-overridable method on an interface, use a **`static`** method, or move the logic to an abstract class.

### Q39. How do dependency-injection frameworks set `final` fields?

Two mechanisms:

1. **Constructor injection** — the DI container calls a constructor annotated with `@Inject` / `@Autowired`, passing dependencies as arguments. `final` fields are assigned normally inside the constructor. This is the **preferred** pattern — it works with immutability out of the box.

2. **Field injection via reflection** — the container uses `Field.setAccessible(true) + Field.set()` to write to a `final` field after construction. This works on older JDKs but **breaks JMM final-field guarantees** for other threads and is blocked by default on JDK 17+ without `--add-opens`.

**Interview signal:** if the candidate defends field injection with `final` fields, they don't understand safe publication. Constructor injection is the correct answer.

### Q40. What happens when Lombok's `@Value` or Kotlin's `val` is used — is it equivalent to `final`?

- **Lombok `@Value`** generates `final class`, `private final` fields, no setters, all-args constructor, `equals/hashCode/toString`. Bytecode-equivalent to a hand-written immutable class. Since records, `@Value` is largely obsolete in Java 16+.
- **Kotlin `val`** produces a `final` field with only a getter. It is *not* automatically immutable in the deep sense — `val list = mutableListOf(1,2,3); list.add(4)` still works. Same shallow-immutability trap.

### Q41. Does `final` help the JIT with escape analysis?

**Sometimes.** Escape analysis determines whether an allocated object escapes its scope. `final` fields help the JIT more with **inlining** and **constant propagation** (once it knows a field is `final`, it can hoist the read and treat the value as invariant across the method). This can enable further optimizations downstream — scalar replacement, dead-code elimination, loop-invariant code motion.

However, C2 and Graal already do fairly aggressive optimistic assumptions on non-`final` fields via type profiling and deopt. So the gap is smaller than it used to be.

### Q42. `final` and `Cloneable` — what's the tension?

`Object.clone()` creates a bitwise copy of the object **without calling the constructor**. This bypasses `final` field initialization — the clone's `final` fields get their values via memcpy, not assignment. This is legal by JVM spec, but:

1. `final` fields in the clone lose the JMM safe-publication guarantee, because there was no constructor freeze
2. Deep-cloning mutable fields requires overriding `clone()` manually — the default is shallow

**Practical advice:** don't use `Cloneable`. Use a copy constructor or a static `copyOf` factory. Records don't implement `Cloneable` for exactly these reasons.

### Q43. When does `final` participate in constant folding across compilation units?

A `final` field is a **compile-time constant** (per JLS §4.12.4) if:
- It is `static final`
- Its type is a primitive or `String`
- It's initialized with a *constant expression* (literal, or arithmetic on constants)

Such constants are **inlined** into the bytecode of every caller.

**Gotcha:** if a library defines
```java
public static final int MAX_RETRIES = 3;
```
and you compile a caller against version 1.0 of that library, then the library changes `MAX_RETRIES = 5` in v1.1 — **your caller keeps seeing 3** until it's recompiled. The constant is baked into the caller's `.class` file.

Workaround: initialize with a non-constant expression to defeat inlining:
```java
public static final int MAX_RETRIES = Integer.parseInt("3");
```

### Q44. Does `final` show up in the bytecode? Where?

Yes:
- **Class-level `final`** → `ACC_FINAL` flag in the class file's `access_flags`
- **Field-level `final`** → `ACC_FINAL` in the field's `access_flags`
- **Method-level `final`** → `ACC_FINAL` in the method's `access_flags`
- **Local variables** → **not** stored; erased at compilation

You can verify with `javap -v YourClass.class`.

### Q45. Design an immutable `Money` class end-to-end. Walk through it.

Senior interviewers use this to probe: defensive copy, `equals`/`hashCode` correctness on `BigDecimal`, currency handling, and precision rules.

```java
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        // normalise scale so 10.00 USD equals 10.0 USD
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
```

**Critical points to raise:**
1. **`BigDecimal.equals`** compares scale AND value — `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` is **false**. Records' auto-generated `equals` uses `Objects.equals`, which calls `BigDecimal.equals`. Normalise the scale in the compact constructor to fix this.
2. Never store `Money` as `double` — floating point breaks financial precision (0.1 + 0.2 ≠ 0.3).
3. Operations return **new instances** — no in-place mutation.
4. `Currency` from `java.util.Currency` is itself immutable.

### Q46. What does Project Valhalla change about `final`?

**Value classes** (JEP 401) introduce a new kind of class where:
- All fields are implicitly `final`
- Instances have no identity (`==` compares values, not references)
- The JVM can optimize them like primitives — flatten into stack slots or arrays, no heap allocation

For values, `final` becomes the **default and only** mode — you can't have a mutable field in a value class. This is the direction Java is heading for numeric and DTO-style types.

Until Valhalla ships, records are the closest approximation.

### Q47. Do you ever need both `final` and `volatile` on the same field?

**No — they're mutually exclusive.** `final` means "cannot change after construction"; `volatile` means "provides visibility on every read/write, because it changes." A field that never changes has no visibility problem to solve — the JMM's final-field guarantee already ensures safe publication. The compiler will reject `final volatile int x;` as invalid.

The interview trap: candidates sometimes propose `final volatile` for "extra safety." It's not just unnecessary — it's a compile error.

### Q48. Can two different classloaders see different values for the same `public static final` constant?

**Yes.** Class identity in the JVM is `(className, classLoader)`, so `com.example.Config` loaded by classloader A and classloader B are **different classes** with **separate static state**. Their `static final` fields are separate storage.

This bites hardest in application servers, OSGi, and hot-reload environments — a "constant" from a "singleton" library may exist as two independent copies with different values.

### Q49. If you overwrite a `final` field via `Unsafe` or reflection, do other threads see the change?

**Undefined.** The JMM's final-field freeze is a **one-shot** publication event at the end of the constructor. After that, the JIT is free to:
- Hoist reads of the field out of loops
- Treat the value as invariant across method calls
- Fold the value into inlined callers

Any later mutation via `Unsafe.putObject` or reflective `Field.set` bypasses these optimizations without invalidating them. Some threads may see the new value; others may see the stale value forever. This is why `final` should be considered **permanent**, and why frameworks that "mutate finals" are fragile.

### Q50. When would you deliberately NOT mark a field `final` in a value-type class?

Two legitimate cases:
1. **Lazy initialization** — a cached derived value (e.g., a memoized `hashCode`) that is computed on first access. `String`'s `hash` field is exactly this: non-`final`, initialized lazily, but idempotent so the race is benign.
2. **JPA entity fields** — Hibernate mutates them during load, so they must be non-`final`. This is one of the biggest arguments against modeling JPA entities as records.

Rule of thumb: mark `final` unless there's a concrete reason not to, and document the reason when you don't.

### Q51. Explain how `String` achieves immutability despite exposing a `char[]` internally.

Pre-JDK-9, `String` held `private final char[] value`. The array reference is `final`, but arrays are always mutable — so how does `String` stay immutable?

Two mechanisms:
1. `String` **never leaks the array** — every method that could expose it returns a copy (`toCharArray()`, `getBytes()`, etc.)
2. `String` **never modifies the array** internally — every "modifying" method (`toUpperCase()`, `substring()`, etc.) returns a **new** `String` with a new array

JDK 9+ replaced `char[]` with `byte[]` + a coder tag (compact strings), same principles apply.

**Interview extension:** why is this an important design lesson? Because it shows that immutability is enforced by **behavior** (never expose, never mutate the internal reference), not just by `final`.

### Q52. What's the relationship between `final` and pattern matching / switch expressions?

Pattern matching binds a new **effectively final** variable in its scope. You cannot reassign it inside the case block:

```java
switch (shape) {
    case Circle c -> {
        c = null;   // compile error — pattern variables are effectively final
        System.out.println(c.radius());
    }
}
```

This aligns with the general rule: pattern-bound variables behave like function parameters, which are effectively final by convention.

### Q53. What does `final` on a method parameter do? Is it worth using?

```java
public BigDecimal calculatePnl(final Trade trade, final BigDecimal marketPrice) {
    trade = null;   // compile error — parameter is final
    return marketPrice.subtract(trade.price());
}
```

`final` on a parameter prevents **reassignment of that parameter inside the method body**. That's it. Just like a `final` local variable.

**What it does NOT do:**
- Does **not** make the passed-in object immutable — `trade.setPrice(...)` still works if `Trade` has setters. `final` locks the *parameter slot*, not the object.
- Does **not** affect the caller in any way. Parameters are pass-by-value in Java (values for primitives, reference-values for objects). The caller's variable is completely independent.
- Does **not** change the method's signature. Two methods that differ only in `final` on parameters are the **same method** — you can't overload on it, and overriding methods don't need to match the `final` modifier.

**What it does at bytecode:**
- **Nothing visible.** The `ACC_FINAL` flag applies to fields/methods/classes, not to local variable slots (parameters are locals). The compiler enforces the rule at compile time and erases the modifier. `javap` shows no trace of it.

**Why some codebases require it:**

1. **Prevents an accidental class of bugs** — reassigning a parameter and then using the reassigned value later is a common source of confusion, especially in long methods.
   ```java
   public void process(String name) {
       if (name == null) name = "default";   // legal but often a code smell
       // ...long method body using the reassigned name...
   }
   ```
   With `final`, you'd be forced to introduce a new local: `String effective = (name == null) ? "default" : name;` — clearer intent.

2. **Required for inner-class / lambda capture pre-Java 8** — historical reason. Since Java 8 "effectively final" is enough, this justification is obsolete.

3. **Documentation / discipline** — signals "I'm not going to mess with the input." Some teams enforce this via Checkstyle rules.

**Why most teams DON'T bother:**

- Adds visual noise to every parameter list.
- Modern IDEs highlight parameter reassignment already.
- Doesn't prevent the real bug — mutation of the underlying object.
- No runtime or bytecode benefit.
- Java conventions from Google, Oracle, and most open-source projects do **not** use `final` on parameters.

**Interview-safe answer:**

> `final` on a parameter prevents reassignment inside the method body — nothing more. It doesn't make the object immutable, doesn't change the method signature, and produces no bytecode difference. It's a matter of team style; most modern codebases omit it because effectively-final rules and IDE warnings cover the same ground without the visual noise.

**Bonus gotcha:** you *can* override a method and add/remove `final` on parameters freely — it's not part of the signature.
```java
class Base    { void f(int x)       {} }
class Sub extends Base { @Override void f(final int x) {} }   // legal
```

---

## Debugging scenarios — spot the bug

Practice format: read the code, predict what happens, then check the answer. If you can't spot the bug in **D1–D8** in under 60 seconds, you're not yet fluent — those are the ones that show up most in production.

### D1. The "immutable" portfolio that mutates

```java
public record Portfolio(String owner, List<Trade> trades) {}

// caller
List<Trade> src = new ArrayList<>();
src.add(new Trade(...));
Portfolio p = new Portfolio("me", src);

src.add(new Trade(...));
System.out.println(p.trades().size());   // expected 1, got 2
```

**Bug:** Records are only **shallowly immutable**. The `trades` field reference is `final`, but the underlying `ArrayList` is shared with the caller. Mutating `src` mutates `p`.

**Fix:** defensive copy in compact constructor: `trades = List.copyOf(trades);`

---

### D2. The stale constant

```java
// library v1.0
public class Config {
    public static final int TIMEOUT_MS = 5000;
}

// caller — compiled against library v1.0
System.out.println(Config.TIMEOUT_MS);   // prints 5000

// library upgraded to v1.1 (JAR swapped, caller NOT recompiled)
public class Config {
    public static final int TIMEOUT_MS = 30000;
}

// caller runs again — prints 5000 (!)
```

**Bug:** `TIMEOUT_MS` is a **compile-time constant** (JLS §4.12.4). Its value is **inlined** into every caller's bytecode. Swapping the library jar doesn't help — the caller still holds the baked-in `5000`.

**Fix:** either recompile every caller, or defeat inlining: `public static final int TIMEOUT_MS = Integer.parseInt("30000");`

---

### D3. The "safe" publication that isn't

```java
public final class Account {
    private final List<Trade> trades = new ArrayList<>();
    private final String owner;

    public Account(String owner, EventBus bus) {
        this.owner = owner;
        bus.register(this);   // subscribes this account to events
    }

    public void onTrade(Trade t) { trades.add(t); }
    public String owner() { return owner; }
}
```

Another thread calls `account.owner()` and sometimes sees **null**. How?

**Bug:** `this` escapes the constructor before it finishes. `bus.register(this)` publishes the reference to another thread, which may observe the object before `this.owner = owner` completes. Even `final` fields aren't safe from this — the JMM's final-field freeze only kicks in *after* the constructor returns.

**Fix:** move `bus.register(this)` to a separate `init()` method the caller invokes *after* construction.

---

### D4. The `BigDecimal` equals trap

```java
public record Money(BigDecimal amount) {}

Money a = new Money(new BigDecimal("10"));
Money b = new Money(new BigDecimal("10.00"));

System.out.println(a.equals(b));   // expected true, got false
```

**Bug:** `BigDecimal.equals()` compares **both value and scale**. `10` (scale 0) is not `.equals()` to `10.00` (scale 2), even though they represent the same amount. Records' auto-generated `equals` calls `Objects.equals`, which delegates to `BigDecimal.equals`.

**Fix:** normalize scale in the compact constructor, or use `compareTo() == 0` for numeric equality:
```java
public Money {
    amount = amount.setScale(2, RoundingMode.UNNECESSARY);
}
```

---

### D5. The lambda that "captures" the wrong value

```java
List<Runnable> tasks = new ArrayList<>();
for (int i = 0; i < 3; i++) {
    tasks.add(() -> System.out.println(i));   // compile error
}
```

**Bug:** `i` is reassigned by the loop, so it's not effectively final — lambdas can't capture it.

**Workaround (common pattern):**
```java
for (int i = 0; i < 3; i++) {
    final int captured = i;   // new variable per iteration
    tasks.add(() -> System.out.println(captured));
}
```

Or use `IntStream.range(0, 3).forEach(...)`.

---

### D6. The mutable-container escape hatch

```java
int counter = 0;
Runnable r = () -> counter++;   // compile error
```

Same rule as D5. But developers often "fix" it with:

```java
final int[] counter = {0};
Runnable r = () -> counter[0]++;   // compiles, but is it correct?
```

**Bug (subtle):** the code compiles because `counter` (the array reference) is `final`. But the increment isn't atomic — if `r` runs on multiple threads, you have a race condition. `counter[0]++` is read-modify-write, not thread-safe.

**Fix:** use `AtomicInteger` if concurrent, or accept single-threaded use.

---

### D7. The `HashMap` key time bomb

```java
public class Trade {
    private String symbol;
    public Trade(String s) { this.symbol = s; }
    public void setSymbol(String s) { this.symbol = s; }

    @Override
    public boolean equals(Object o) { return o instanceof Trade t && Objects.equals(symbol, t.symbol); }
    @Override
    public int hashCode() { return Objects.hash(symbol); }
}

Map<Trade, BigDecimal> pnl = new HashMap<>();
Trade t = new Trade("AAPL");
pnl.put(t, new BigDecimal("100"));

t.setSymbol("GOOG");
System.out.println(pnl.get(t));   // null (!)
```

**Bug:** `Trade` is mutable, but its `hashCode()` depends on `symbol`. Mutating `symbol` changes the hash, so the map looks in the wrong bucket. The entry becomes **unreachable** — a slow memory leak.

**Fix:** make `Trade` a record, or make `symbol` `final` and remove the setter.

---

### D8. The `unmodifiableList` illusion

```java
List<Trade> src = new ArrayList<>();
src.add(t1);

List<Trade> view = Collections.unmodifiableList(src);
view.add(t2);   // throws UnsupportedOperationException — good
src.add(t2);    // succeeds
view.size();    // 2 — the "unmodifiable" view sees the mutation
```

**Bug:** `Collections.unmodifiableList` is a **view**, not a copy. Anyone still holding the underlying list can mutate it, and the view reflects it.

**Fix:** use `List.copyOf(src)` — that returns an unmodifiable **copy**.

---

### D9. The blank final that won't compile

```java
public class Trade {
    private final BigDecimal price;

    public Trade(BigDecimal price, boolean validate) {
        if (validate && price.signum() < 0) {
            throw new IllegalArgumentException();
        } else if (validate) {
            this.price = price;
        }
        // compile error: variable price might not have been initialized
    }
}
```

**Bug:** the `else if` branch assigns `price`, but the throw-branch is fine (it never returns), while the implicit "validate=false" path doesn't assign at all. Java's **definite assignment** analysis requires `price` to be assigned on every non-throwing path.

**Fix:** assign on all paths, or restructure:
```java
public Trade(BigDecimal price, boolean validate) {
    if (validate && price.signum() < 0) throw new IllegalArgumentException();
    this.price = price;
}
```

---

### D10. The `Cloneable` that skips validation

```java
public final class Positive implements Cloneable {
    private final int value;

    public Positive(int value) {
        if (value <= 0) throw new IllegalArgumentException();
        this.value = value;
    }

    @Override
    public Positive clone() throws CloneNotSupportedException {
        return (Positive) super.clone();
    }
}
```

**Bug:** `Object.clone()` does a **bitwise memcpy** — the constructor is **never called**, so validation is bypassed. Additionally, `final` fields in the clone don't get the JMM safe-publication guarantee (no constructor freeze).

**Fix:** don't implement `Cloneable`. Provide a copy constructor: `public Positive(Positive other) { this(other.value); }` — validation runs, safe publication works.

---

### D11. The record with a mutable getter leak

```java
public record Snapshot(List<Trade> trades) {
    public Snapshot {
        trades = new ArrayList<>(trades);   // defensive copy — safe on the way IN
    }
}

Snapshot s = new Snapshot(List.of(t1, t2));
s.trades().add(t3);   // succeeds — modifies the internal list (!)
```

**Bug:** `new ArrayList<>(trades)` copies on the way in, so the caller's list is disconnected. But the auto-generated getter returns the internal `ArrayList` directly — the caller can mutate it.

**Fix:** use `List.copyOf(trades)` instead. It's an *unmodifiable* copy, so both directions are safe in one line.

---

### D12. The static final constant with two classloaders

```java
// Config.class loaded twice — once by classloader A (app), once by B (plugin)
public class Config {
    public static final long BOOT_TIME = System.currentTimeMillis();
}

// App code, classloader A:
System.out.println(Config.BOOT_TIME);   // 1700000000000

// Plugin code, classloader B, running seconds later:
System.out.println(Config.BOOT_TIME);   // 1700000005000 — different!
```

**Bug:** class identity in the JVM is `(className, classLoader)`. Two classloaders → two independent copies of `Config`, each with its own `BOOT_TIME`. "Singleton" and "constant" both silently mean "per classloader."

**Where it bites:** OSGi, application servers, hot-reload environments, Spring DevTools, plugin architectures.

**Fix:** if you need a truly global constant, put it in a class loaded by the **parent** classloader (bootstrap or app). Or accept the semantics and design for per-classloader state.

---

## Quick-fire rapid round

| Question | Answer |
|---|---|
| Is `final` inherited? | Methods yes (can't be overridden), classes N/A |
| Can `final` local be uninitialized? | Yes, until first use (definite assignment) |
| Is `String` `final`? | Yes |
| Can `main` be `final`? | Yes (unusual but legal) |
| Can an inner class be `final`? | Yes |
| Can a record be non-`final`? | No — records are implicitly `final` |
| Can `enum` constants be `final`? | Yes — enum constants are implicitly `public static final` |
| Does `final` imply immutability? | No — only for reference/reassignment |

---

## The one-sentence summary you can drop in an interview

> `final` is a Java keyword that means "cannot change" — for variables it prevents reassignment, for methods it prevents overriding, and for classes it prevents extension. Combined with private fields and no setters, it's the foundation of immutability, and it has memory-model guarantees that make immutable objects safely shareable across threads without synchronization.
