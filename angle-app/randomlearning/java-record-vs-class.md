# Java `record` vs `class` — Complete Guide

A reference covering records, immutability, `final` class vs `final` fields, and private constructors.

---

## 1. What is a `record`?

A `record` is a normal class with a lot of repetitive code auto-generated for you.

```java
public record Trade(Instant timestamp, Signal signal, BigDecimal price) {}
```

is equivalent to writing this by hand:

```java
public final class Trade {
    private final Instant timestamp;
    private final Signal signal;
    private final BigDecimal price;

    public Trade(Instant timestamp, Signal signal, BigDecimal price) {
        this.timestamp = timestamp;
        this.signal = signal;
        this.price = price;
    }

    public Instant timestamp() { return timestamp; }
    public Signal signal() { return signal; }
    public BigDecimal price() { return price; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trade)) return false;
        Trade trade = (Trade) o;
        return Objects.equals(timestamp, trade.timestamp)
            && Objects.equals(signal, trade.signal)
            && Objects.equals(price, trade.price);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, signal, price);
    }

    @Override
    public String toString() {
        return "Trade[timestamp=" + timestamp + ", signal=" + signal + ", price=" + price + "]";
    }
}
```

### Feature comparison

| Feature | `record` | normal `class` |
|---|---|---|
| Fields | `private final` by default | you declare manually |
| Constructor | auto-generated | you write it |
| Getters | auto-generated (`price()`, not `getPrice()`) | you write them |
| `equals()` / `hashCode()` | auto-generated, field-based | you write them (easy to get wrong) |
| `toString()` | auto-generated, readable | you write it |
| Mutability | shallowly immutable (fields can't be reassigned) | mutable unless you make it so |
| Inheritance | can't extend another class (implicitly extends `Record`) | can extend any class |
| Can implement interfaces | yes | yes |

### When to use which

**Use `record` for:**
- Data carriers / DTOs — things that just hold values (`Trade`, `BacktestResult`)
- API responses, value objects, immutable configs
- Anywhere you'd normally write a "dumb" class with getters + equals/hashCode/toString

**Use a normal `class` for:**
- Mutable state (e.g., an `Order` that changes status over time)
- Classes with significant behavior/logic beyond holding data
- When you need inheritance from another class
- Entities with identity that isn't based on field values (e.g., a JPA `@Entity`)

### Records can still have logic

```java
public record Trade(Instant timestamp, Signal signal, BigDecimal price) {
    // compact constructor for validation
    public Trade {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("price cannot be negative");
        }
    }

    // custom method
    public boolean isBuy() {
        return signal == Signal.BUY;
    }
}
```

---

## 2. Nested record vs. two top-level classes

**Keep it nested** when the inner type only ever exists as part of the outer one:

```java
public record BacktestResult(
    String strategyName,
    int totalCandles,
    int buys,
    int sells,
    BigDecimal netProfit,
    List<Trade> trades
) {
    public record Trade(Instant timestamp, Signal signal, BigDecimal price) {}
}
```

**Split into two top-level types** when the inner type is reused elsewhere (e.g., a live trading engine, an exporter, a database entity):

```java
// Trade.java
public record Trade(Instant timestamp, Signal signal, BigDecimal price) {}

// BacktestResult.java
public record BacktestResult(
    String strategyName,
    int totalCandles,
    int buys,
    int sells,
    BigDecimal netProfit,
    List<Trade> trades
) {}
```

> **Rule of thumb:** the moment you find yourself importing `BacktestResult.Trade` from unrelated code, promote it to a standalone top-level record.

**Note on mutability of collections:** `List<Trade> trades` in a record is only as immutable as the list you pass in. For true immutability, defend it in a compact constructor:

```java
public record BacktestResult(..., List<Trade> trades) {
    public BacktestResult {
        trades = List.copyOf(trades);
    }
}
```

---

## 3. "Carrying immutable values around" — simple example

A record's whole job is to hold a fixed set of values as one unit — like a labeled bundle. It doesn't do anything, doesn't change, has no real behavior.

```java
public record Point(int x, int y) {}
```

```java
Point p = new Point(3, 5);
System.out.println(p.x());  // 3
System.out.println(p.y());  // 5
System.out.println(p);      // Point[x=3, y=5]  <- auto-generated toString()
```

**It's immutable:**

```java
p.x = 10; // compile error — x is private (and final), no setter exists
```

To get a "changed" point, create a new one instead:

```java
Point moved = new Point(p.x() + 1, p.y());  // (4, 5) — p itself is untouched
```

**Value-based equality, for free:**

```java
Point a = new Point(3, 5);
Point b = new Point(3, 5);
a == b;        // false — different objects in memory
a.equals(b);   // true  — same field values
```

**Analogy:** think of a record like a printed receipt. Once printed, you don't edit it — if something's wrong, the store issues a *new* corrected receipt. It just holds facts, with no behavior like `receipt.processPayment()`.

**Contrast — something that is NOT just carrying values around** (real behavior + mutable state → stays a normal class):

```java
public class TradingAccount {
    private BigDecimal balance;
    public void deposit(BigDecimal amount)  { balance = balance.add(amount); }
    public void withdraw(BigDecimal amount) { balance = balance.subtract(amount); }
}
```

---

## 4. `final` on the class vs `final` on the fields

These control **two completely different things**.

### `final` on the class → controls inheritance

```java
public final class Point {
    private final int x;
    private final int y;
}
```

```java
public class Point3D extends Point {}   // compile error — cannot extend a final class
```

Without `final class`, someone could subclass `Point` and add mutable fields, silently breaking any assumption that "all `Point`s are immutable":

```java
public class MutablePoint extends Point {
    private int extraData;
    public MutablePoint(int x, int y) { super(x, y); }
    public void setExtraData(int val) { this.extraData = val; }  // mutable!
}
```

### `final` on a field → controls reassignment

```java
public class Point {
    private final int x;
    private final int y;
    public Point(int x, int y) { this.x = x; this.y = y; }
}
```

```java
public class Point3D extends Point {}   // allowed — class itself isn't final
Point p = new Point(3, 5);
p.x = 99;   // compile error — x is private final, no setter exists
```

### Side-by-side of the two "half" versions

| | `final class` only (fields NOT final) | `final` fields only (class NOT final) |
|---|---|---|
| Can be subclassed? | No | Yes |
| Can fields change after creation? | Yes, if setters exist | No |
| Is the object actually immutable? | **No** | **Yes**, for its own fields |

### For TRUE immutability, you need BOTH

```java
public final class Point {         // 1. can't be subclassed
    private final int x;           // 2. field can't be reassigned
    private final int y;           // 2. field can't be reassigned

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    // no setters at all
}
```

This is exactly what `record` gives you automatically, in one line:

```java
public record Point(int x, int y) {}
```

---

## 5. Does immutability need a `private` constructor?

**No.** A private constructor solves a different problem entirely.

### Immutability only needs: `final` class + `final` fields + no setters

```java
public final class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {   // public constructor — perfectly fine
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }
}
```

```java
Point p = new Point(3, 5);  // works fine — this IS fully immutable
p.x = 10;                   // compile error — THIS is what makes it immutable
```

The constructor being `public` doesn't break immutability. Immutability is about *"can you change it after creation,"* not *"can you create it."*

### What a `private` constructor actually controls: who can create instances

```java
public final class Point {
    private final int x;
    private final int y;

    private Point(int x, int y) { this.x = x; this.y = y; }  // hidden

    public static Point of(int x, int y) {   // the only way in
        return new Point(x, y);
    }
}
```

```java
Point p = Point.of(3, 5);   // works
Point p2 = new Point(3, 5); // compile error — constructor not visible
```

### When to actually use a private constructor

1. **Static factory method** — allows validation, caching, or returning a shared instance without the caller knowing (this is how `Optional.of(...)` works).
2. **Utility class** — prevent instantiation entirely:
   ```java
   public final class MathUtils {
       private MathUtils() {}
       public static int square(int n) { return n * n; }
   }
   ```
3. **Singleton pattern** — only one instance should ever exist:
   ```java
   public final class Config {
       private static final Config INSTANCE = new Config();
       private Config() {}
       public static Config getInstance() { return INSTANCE; }
   }
   ```

### Summary table

| Concern | Solved by |
|---|---|
| Can this class be extended? | `final` on the class |
| Can these values change after creation? | `final` on the fields, no setters |
| Who is allowed to create instances? | `private` / `public` constructor |

`record` matches its canonical constructor's access to the record's own access (`public record` → public constructor) — so `public record Point(int x, int y) {}` is fully immutable but still freely constructible with `new Point(3, 5)`.

---

## 6. Immutability in depth — the layers most people miss

The definition:

> **Immutability = after construction, no observer can ever see this object in a different state.**

Notice what that does *not* say. It doesn't say "the reference can't change" (that's just `final`). It doesn't say "no setters exist" (necessary but not sufficient). It says nothing you can observe ever changes.

There are **4 layers** where immutability can break. Most bugs come from confusing them.

### Layer 1 — `final` gives you *reference* immutability, not *object* immutability

```java
public final class Portfolio {
    private final List<Trade> trades;   // "final" — feels immutable
    public Portfolio(List<Trade> trades) { this.trades = trades; }
    public List<Trade> trades() { return trades; }
}
```

Looks locked down. Watch this break:

```java
List<Trade> trades = new ArrayList<>();
trades.add(new Trade(...));
Portfolio p = new Portfolio(trades);

trades.add(new Trade(...));   // caller mutates their list...
p.trades().size();            // ...and p's "immutable" list just grew
```

`final` stops you from doing `this.trades = somethingElse`. It does **nothing** to stop mutation of what `trades` points at. This is called **shallow immutability**.

### Layer 2 — records don't save you here

```java
public record Portfolio(String owner, List<Trade> trades) {}
```

Same bug. Records are shallowly immutable. The field references are `final`, but if the field is a mutable object, the record is mutable through it.

The fix — defensive copy in the compact constructor:

```java
public record Portfolio(String owner, List<Trade> trades) {
    public Portfolio {
        trades = List.copyOf(trades);   // now truly frozen at construction
    }
}
```

`List.copyOf` returns an unmodifiable list AND copies the element references so later caller mutations don't leak in. Two bugs fixed in one line.

**Critical exercise — predict the outcome:**

```java
List<Trade> src = new ArrayList<>(List.of(t1, t2));
Portfolio p = new Portfolio("me", src);
src.add(t3);              // does p.trades() see t3?
p.trades().add(t4);       // what happens here?
```

With `List.copyOf`: `src.add(t3)` does **not** affect `p`. `p.trades().add(t4)` throws `UnsupportedOperationException`. Without `List.copyOf`: both mutations succeed and silently corrupt `p`.

### Layer 3 — the *elements* can still be mutable (deep vs shallow immutability)

Even `List.copyOf` only protects the list itself. What if `Trade` is mutable?

```java
public class Trade {
    private BigDecimal price;
    public void setPrice(BigDecimal p) { this.price = p; }
    public BigDecimal price() { return price; }
}
```

Now:

```java
Trade t1 = new Trade(...);
Portfolio p = new Portfolio("me", List.of(t1));   // p.trades is unmodifiable...
t1.setPrice(new BigDecimal("999"));               // ...but t1 is still mutable
p.trades().get(0).price();                        // 999 — silently corrupted
```

**Deep immutability** requires every element, and every element's element, all the way down, to also be immutable. In practice you get this by making `Trade` a record too — then the whole tree is safe.

**Rule of thumb:** immutability is a property of a *whole graph*, not a single class. One mutable node anywhere in the graph = the whole thing is mutable.

### Layer 4 — Java's "always immutable" vs "usually mutable" types

Know these by heart when reasoning about record fields:

| Type | Immutable? |
|---|---|
| primitives, `String`, `BigDecimal`, `BigInteger` | ✅ yes |
| `Instant`, `LocalDate`, `LocalDateTime`, `Duration` (java.time) | ✅ yes |
| Enums | ✅ yes |
| `List.of(...)`, `Set.of(...)`, `Map.of(...)` | ✅ yes |
| `List.copyOf(x)`, `Set.copyOf(x)`, `Map.copyOf(x)` | ✅ yes |
| `Collections.unmodifiableList(x)` | ⚠️ view only — original still mutable and leaks through |
| `ArrayList`, `HashMap`, `HashSet` | ❌ no |
| `Date`, `Calendar` | ❌ no (legacy — don't use in records) |
| Arrays (`int[]`, `Trade[]`) | ❌ no — always mutable, and `.clone()` is shallow |

The `Collections.unmodifiableList` trap:

```java
List<Trade> src = new ArrayList<>();
List<Trade> view = Collections.unmodifiableList(src);
src.add(t1);          // succeeds
view.size();          // 1 — the "unmodifiable" view sees the mutation
```

Not the same as `List.copyOf`. `unmodifiableList` is a **view**, not a copy. If anyone still holds `src`, your invariant is dead.

### The leak-on-the-way-out bug

Defensive copy on the way in is only half the job. If your getter returns the internal collection directly, callers can mutate it.

```java
public record Portfolio(String owner, List<Trade> trades) {
    public Portfolio { trades = List.copyOf(trades); }   // safe on the way in
}
// Getter is auto-generated → returns the internal list.
// Because it's List.copyOf, it's unmodifiable, so this call throws:
p.trades().add(t5);   // UnsupportedOperationException
```

With `List.copyOf`, the way-out leak is also plugged, because the returned list refuses mutation. If you used defensive copy via `new ArrayList<>(trades)` instead, you'd re-open the way-out leak. **Prefer `List.copyOf`** — it fixes both directions at once.

### The "escape during construction" bug (subtle, real)

```java
public final class Account {
    private final List<Trade> trades = new ArrayList<>();
    public Account(EventBus bus) {
        bus.register(this);     // "this" escapes before construction finishes
        // ...more init
    }
}
```

Another thread now has a reference to a half-built `Account`. Even if all fields are `final`, that thread can observe the object mid-construction, before the JVM's `final`-field visibility guarantees kick in. This is one of the two ways `final` fields can *seem* to change.

**Rule:** never let `this` escape from a constructor — no `bus.register(this)`, no starting a thread with `this`, no calling overridable methods on `this`.

### Why deep immutability is worth the effort

Two payoffs, both quiet — you only notice them when they *don't* fail:

1. **Free thread safety.** No locks, no `volatile`, no memory-model reasoning. Multiple threads can read a deeply immutable object simultaneously with zero synchronization. Huge in a trading/backtest engine where you'd otherwise be locking around `Portfolio` reads.
2. **Safe to cache, share, use as map keys.** A mutable object as a `HashMap` key is a time bomb — mutate a field, `hashCode()` changes, the entry is now unreachable. Deep-immutable objects can't do that.

### Critical example: putting it all together

A backtest engine passes `BacktestResult` from a worker thread to a UI thread. Which of these is safe?

```java
// A — looks safe, is broken
public record BacktestResult(String name, List<Trade> trades) {}

// B — safe against list mutation, still broken if Trade is mutable
public record BacktestResult(String name, List<Trade> trades) {
    public BacktestResult { trades = List.copyOf(trades); }
}

// C — safe end-to-end
public record Trade(Instant t, Signal s, BigDecimal price) {}
public record BacktestResult(String name, List<Trade> trades) {
    public BacktestResult { trades = List.copyOf(trades); }
}
```

- **A** — caller's `ArrayList` bleeds into the result; UI thread sees races.
- **B** — list is frozen, but if `Trade` has setters, the underlying trades can still change from any thread that holds one.
- **C** — every node in the graph is immutable. Zero locks needed. This is what "deeply immutable" means.

### The one-liner takeaways

1. `final` protects the reference, not the object it points at.
2. Records are shallowly immutable — you have to defensively copy mutable fields yourself.
3. `List.copyOf` beats `new ArrayList<>(x)` and beats `Collections.unmodifiableList(x)` — it fixes both incoming and outgoing leaks in one line.
4. Immutability is a property of the whole graph. One mutable node breaks it.
5. `Date`, arrays, and standard collections are mutable. `java.time` types, `String`, `BigDecimal`, `List.of/copyOf` are not.
6. Never let `this` escape from a constructor.
7. Deep immutability = free thread safety + safe to use as map keys + safe to cache.

---

## Quick-reference: the whole picture

```java
// Full manual immutable class — everything spelled out
public final class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point)) return false;
        Point p = (Point) o;
        return x == p.x && y == p.y;
    }

    @Override
    public int hashCode() { return Objects.hash(x, y); }

    @Override
    public String toString() { return "Point[x=" + x + ", y=" + y + "]"; }
}

// Same thing, one line
public record Point(int x, int y) {}
```
