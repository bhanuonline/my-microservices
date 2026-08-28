# Java Memory Model (JMM) — Deep Dive with Examples & Interview Questions

The most thorough single-file reference on the JMM: what it is, why it exists, the hardware problem it solves, every happens-before rule with examples, safe/unsafe publication, CPU-level memory models, and 50+ interview questions from junior to expert.

**Reading order:** work through sections 1–8 to build the mental model, then use 9–14 for reference, and 15+ for interview practice.

---

## 1. What is the JMM?

The **Java Memory Model (JMM)** is a **formal specification** — part of the Java Language Specification (JLS §17) — that defines:

> **Which values a thread is allowed to observe when it reads a field that other threads may have written.**

That's it. That's the whole model in one sentence. Everything below is the machinery that makes this precise.

The JMM is a **contract** between:

1. **Java source code** — what you write
2. **The Java compiler (javac)** — which can reorder bytecodes
3. **The JIT compiler (HotSpot C2, Graal)** — which can aggressively reorder
4. **The CPU** — which can execute out of order and use per-core caches
5. **The runtime** — which can keep values in registers instead of memory

Without a memory model, every JVM + CPU combination would behave differently, and no portable concurrent Java would exist.

## 2. Why we need it — the hardware problem

Modern CPUs are **not** the sequential-execution machines your intro-to-programming class described. Real hardware does aggressive optimizations that break naive concurrent code:

### 2.1. Per-core caches (L1, L2, L3)

Every CPU core has its own L1 cache. When thread A on core 0 writes to memory, that write goes to core 0's L1. Thread B on core 1 reads from core 1's L1 — which may not have received the update yet. Cache coherence protocols (MESI) fix this **eventually**, but not instantly.

### 2.2. Store buffers

Writes are buffered before being flushed to cache. Core 0 executes `x = 1; read(y)` — but the write to `x` may still be in the store buffer when core 1 reads `x`, so core 1 sees the old value.

### 2.3. Out-of-order execution

CPUs re-order independent instructions for throughput. Given:
```
x = 1;
y = 2;
```
The CPU may execute the store to `y` before the store to `x` — as long as the current thread sees the "correct" final state. Other threads may observe the reversed order.

### 2.4. Compiler reordering (JIT)

The JIT compiler is allowed to reorder instructions if the reordering has no visible effect **on the current thread**. So:
```java
x = 1;
y = 2;
```
may be compiled to bytecode that does `y = 2` first. From another thread's view, it's just gone through a different order.

### 2.5. Registers instead of memory

The JIT can keep a field in a CPU register and never re-read it. Without synchronization hints, it may not notice another thread updated the memory:

```java
boolean stop = false;
while (!stop) { work(); }
```
Without `volatile`, this loop can be hoisted to `while (true) work();` because the JIT sees no writes to `stop` in this method — so it caches the initial `false` in a register forever.

**The JMM's job:** specify exactly which of these optimizations are legal, and give programmers primitives (`volatile`, `synchronized`, `final`, atomics) to constrain them where needed.

---

## 3. What the JMM promises

The JMM makes **two levels of promise**:

### Level 1 — Universal guarantees (apply to ALL programs)

- **Atomic reads/writes** for references and primitives *except* `long`/`double` (which are atomic only when `volatile`)
- Writes to a `final` field, if the constructor completed without letting `this` escape, are visible after publication
- Class initialization is **synchronized on the Class object** by the JVM

### Level 2 — Conditional guarantee (applies to well-synchronized programs)

- **SC-DRF theorem**: If your program is **Data Race Free (DRF)**, it appears to execute with **Sequential Consistency (SC)** — as if all threads' actions were interleaved on a single CPU in program order.

This is the biggest promise: **write your code correctly (no data races), and you don't have to reason about compiler/CPU reorderings.**

---

## 4. Core JMM concepts you must know

### 4.1. Actions

An "action" is a synchronization event the JMM cares about:
- Read of a field
- Write to a field
- Lock acquisition
- Lock release
- Volatile read
- Volatile write
- Thread start
- Thread join
- External actions (I/O)

### 4.2. Program order

The order in which actions appear in the source code of a **single thread**. Doesn't say anything about how they execute.

### 4.3. Synchronization order

A total order over synchronization actions in an execution (locks, volatile ops, thread start/join). All threads agree on this order.

### 4.4. Synchronizes-with

A relationship between specific actions:
- Unlock of monitor M **synchronizes-with** every subsequent lock of M
- Write to volatile V **synchronizes-with** every subsequent read of V
- `Thread.start()` **synchronizes-with** the first action of the started thread
- Last action of a thread **synchronizes-with** any thread's `join()` on it

### 4.5. Happens-before

The heart of the JMM. `A happens-before B` (written `A hb B`) means:
1. A's effects are visible to B
2. Actions must appear (to B) to have occurred in that order

Formally: happens-before is the transitive closure of:
- **Program order** within a thread
- **Synchronizes-with** across threads

If A hb B, then reads in B are guaranteed to see A's writes.

### 4.6. Data race

Two accesses to the same memory location where:
1. At least one is a write
2. They are not ordered by happens-before

**Data-racy programs have essentially undefined behavior.** Fields may hold impossible values; loops may spin forever; reads may return past-or-future values.

---

## 5. All 8 happens-before rules with examples

### Rule 1 — Program order

Within a single thread, statement A that appears before statement B is happens-before B.

```java
// Thread 1
int x = 1;      // A
int y = x + 2;  // B — B happens-before ... within this thread
```

Simple, but be careful: this only orders A hb B **within Thread 1's view**. Another thread may observe A and B in any order unless synchronization exists.

---

### Rule 2 — Monitor lock

An unlock on monitor M happens-before every subsequent lock on M.

```java
// Thread 1
synchronized (lock) {
    data = 42;         // A
}                       // release M

// Thread 2
synchronized (lock) {   // acquire M — sees Thread 1's release
    print(data);       // B — guaranteed to see 42
}
```

**Chain:** Thread 1's write hb Thread 1's release hb Thread 2's acquire hb Thread 2's read. Guaranteed visibility.

---

### Rule 3 — Volatile

A write to a volatile field happens-before every subsequent read of that field.

```java
private int data;               // NOT volatile
private volatile boolean ready;  // volatile

// Thread 1
data = 42;      // A
ready = true;   // B — volatile write

// Thread 2
if (ready) {              // C — volatile read
    print(data);          // D — guaranteed to see 42
}
```

**Chain:** A hb B (program order) hb C (volatile) hb D (program order). This is the classic **piggyback** pattern — `data` gets visibility through the volatile write of `ready`.

---

### Rule 4 — Thread.start()

`Thread.start()` happens-before any action in the started thread.

```java
data = 42;                      // main thread
Thread t = new Thread(() -> {
    print(data);                // guaranteed to see 42
});
t.start();                      // start hb thread's first action
```

This is why worker threads safely see fields the main thread initialized before starting them.

---

### Rule 5 — Thread.join()

Actions in a thread happen-before another thread returns from `join()` on it.

```java
Thread t = new Thread(() -> {
    result = compute();         // A
});
t.start();
t.join();                        // main thread waits
print(result);                   // guaranteed to see A's write
```

Every write the joined thread did before it terminated is visible after join returns.

---

### Rule 6 — Interrupt

A thread calling `interrupt()` on another thread happens-before the interrupted thread detects the interrupt (via `Thread.interrupted()`, `isInterrupted()`, or by throwing `InterruptedException`).

```java
worker.interrupt();      // main thread — establishes hb
// worker sees the interrupt flag / gets InterruptedException
```

---

### Rule 7 — Constructor completion

The end of an object's constructor happens-before the start of the object's finalizer.

```java
public class Foo {
    private final int x;
    public Foo() {
        this.x = 10;      // hb finalize()
    }
    protected void finalize() {
        assert x == 10;   // guaranteed
    }
}
```

Rarely useful because `finalize()` is deprecated. Modern equivalent: `Cleaner`.

---

### Rule 8 — Transitivity

If A hb B and B hb C, then A hb C.

This is what makes complex reasoning possible. Every real-world safe-publication pattern chains multiple hb edges through transitivity.

**Example — chained publication through a volatile:**
```java
// Thread 1
data = expensive_init();  // A
ready = true;             // B — volatile write

// Thread 2
while (!ready) {}         // C — volatile read
work(data);               // D
```

- A hb B (program order)
- B hb C (volatile)
- C hb D (program order)
- ∴ A hb D by transitivity — Thread 2 sees `data`

---

## 6. Reordering: what's allowed, what's forbidden

**The JIT and CPU may reorder** any two actions **unless** there's an explicit happens-before relationship prohibiting it. Specifically:

### Forbidden reorderings (JIT emits barriers to prevent these)

- Program-order actions within a thread that would visibly reorder to that same thread — trivially preserved
- Actions before a volatile write cannot be moved after it
- Actions after a volatile read cannot be moved before it
- Actions before an unlock cannot be moved after the unlock
- Actions after a lock acquire cannot be moved before the acquire
- `final` field writes in a constructor cannot be moved after the constructor completes (the "freeze" action)

### Legal reorderings

Everything else. In particular:

- Two independent reads
- Two independent writes
- A read before an unrelated write
- Two writes to different volatile fields (across the pair — each is individually ordered relative to reads of the same field)

**Interview trap:** two independent volatile writes can be reordered relative to each other by observers, **unless** you use `synchronized` blocks around them. `volatile` orders around a specific field, not all fields globally.

---

## 7. Publication — safe vs unsafe

Publication = making an object visible to other threads.

### Unsafe publication (5 ways to get burned)

**Unsafe #1 — Assigning to a plain field**
```java
public static SomeObject obj;   // NOT volatile
// Thread 1: obj = new SomeObject();
// Thread 2: obj.doWork();      // may see obj != null but obj.x uninitialized
```

**Unsafe #2 — Storing in a non-thread-safe collection**
```java
public static Map<String, Foo> map = new HashMap<>();  // NOT concurrent
// Thread 1: map.put("k", new Foo());
// Thread 2: map.get("k");                              // NPE, or stale, or corruption
```

**Unsafe #3 — `this` escapes during construction**
```java
public Foo(EventBus bus) {
    bus.register(this);       // ← this escapes before construction completes
    this.data = compute();    // Thread that got the reference may see null data
}
```

**Unsafe #4 — Starting a thread from the constructor**
```java
public Foo() {
    new Thread(() -> use(this)).start();  // thread sees Foo mid-construction
    this.data = compute();
}
```

**Unsafe #5 — Calling an overridable method from the constructor**
```java
public Foo() {
    onInit();   // subclass override may see Foo's fields uninitialized
}
```

### Safe publication (5 ways to do it right)

**Safe #1 — Static field during class initialization**
```java
public static final Foo INSTANCE = new Foo();
```
JVM's class-init safety guarantees this. Used in `Bill Pugh` singleton (holder pattern).

**Safe #2 — Volatile field**
```java
private volatile Foo foo;
// Thread 1: foo = new Foo();
// Thread 2: if (foo != null) foo.doWork();  // sees fully constructed Foo
```

**Safe #3 — Final field**
```java
public class Container {
    private final Foo foo;                    // final field
    public Container() { this.foo = new Foo(); }
}
// After Container is safely published, foo is guaranteed initialized
```
Requires: `this` didn't escape the constructor.

**Safe #4 — Thread-safe collection**
```java
private final Map<String, Foo> map = new ConcurrentHashMap<>();
// Thread 1: map.put("k", new Foo());
// Thread 2: map.get("k");                    // sees fully constructed Foo
```
Any operation on a thread-safe container establishes hb.

**Safe #5 — Synchronized publish AND read**
```java
private Foo foo;
public synchronized void set(Foo f) { this.foo = f; }
public synchronized Foo get()       { return this.foo; }
```

---

## 8. Volatile in the JMM — full mental model

A volatile field participates in the JMM at two levels:

### 8.1. Semantic guarantees

- **Atomic** — reads/writes never see torn values (even for `long`/`double`)
- **Visible** — a write is visible to subsequent reads (across threads)
- **Ordered** — cannot be reordered around each other in ways that violate happens-before

### 8.2. What volatile does NOT do

- Does NOT provide atomicity for compound operations (`counter++` still races)
- Does NOT provide mutual exclusion — multiple writers race
- Does NOT deep-freeze the object it references — mutations to the object's fields are not protected

### 8.3. Bytecode/hardware view

**Volatile write:**
1. StoreStore barrier before (no prior writes reorder past)
2. Emit the write to main memory (drain store buffer)
3. StoreLoad barrier after — **the expensive one** (no later loads reorder before)

**Volatile read:**
1. Emit the read (from cache, invalidated by protocol if stale)
2. LoadLoad and LoadStore barriers after (no later loads/writes reorder before)

The StoreLoad barrier is why volatile writes cost ~10-100× more than plain writes on x86 (an `mfence` or `lock`-prefixed instruction).

---

## 9. Synchronized in the JMM

`synchronized` provides everything volatile does, **plus** mutual exclusion and atomicity for the critical section.

### 9.1. What it guarantees

- **Mutual exclusion** — only one thread holds the monitor at a time
- **Visibility** — unlock hb subsequent lock
- **Atomicity** — entire block executes atomically w.r.t. other synchronized blocks on the same monitor

### 9.2. Full memory-model behavior

Entering a synchronized block: acquires the monitor + performs a "read barrier" — subsequent reads see prior unlocks.

Exiting: performs a "write barrier" — all writes in the block are visible to subsequent lock holders.

### 9.3. Which monitor?

Java code | Monitor
--- | ---
`synchronized (x)` | `x` (any object reference)
`synchronized method()` | `this` (or the Class for static methods)
`synchronized static method()` | the enclosing `Class<?>` object

Rule: **all threads must synchronize on the same monitor** for happens-before to hold.

---

## 10. Final fields in the JMM (the "freeze" action)

The most subtle part of the JMM, and the reason `record` and immutable classes work safely without volatile.

### 10.1. The freeze action

At the **end of a constructor**, the JVM inserts a synthetic "freeze" action on every `final` field of the object. Any thread that reads the object reference **after** the freeze is guaranteed to see the correctly initialized `final` fields.

Formally: `constructor completion of X.f` hb `any read of X.f` through any properly published reference.

### 10.2. Prerequisites

The freeze action works ONLY if:
1. **`this` does not escape the constructor** (no `bus.register(this)`, no `new Thread(this).start()`, no calling overridable methods)
2. The field is truly `final` (not modified by reflection later)

### 10.3. Freeze applies to `final` fields only

Non-final fields do NOT get this guarantee, even after the constructor finishes. That's why immutability is powerful — the guarantees are automatic and free (no barriers needed at read time).

### 10.4. Deep chains through final fields

If a `final` field of X points to Y, and Y's fields (whether `final` or not) were written before publication, they are also visible. This is called **freeze transitivity** — the guarantee extends through the reference graph you can reach from `final` fields.

---

## 11. Immutability and the JMM

An **immutable object** — meaning all fields `final`, no leaked mutable state, `this` didn't escape — is:

- **Safely publishable** through any mechanism (even a plain non-volatile field)
- **Thread-safe by construction** — no synchronization needed for reads
- **Safe as a `HashMap` key**
- **Free from data races** even if shared without any synchronization

This is the strongest guarantee the JMM gives. Every "just use immutable objects" recommendation is grounded in this.

**Records** (Java 14+) are the modern shortcut: all fields `private final`, no setters, `this` doesn't escape. Perfect fit.

---

## 12. Data race — the precise definition

Two accesses to the same location constitute a **data race** if:
1. At least one is a **write**
2. The accesses are performed by **different threads**
3. The accesses are **not ordered by happens-before**

### 12.1. Data race vs race condition

Common interview trap:

- **Data race** — memory access anomaly (JMM concept)
- **Race condition** — logical bug where behavior depends on timing (algorithm concept)

You can have:
- Data race with no race condition (rare but possible)
- Race condition with no data race (concurrency bug in synchronized code with wrong logic)
- Both — the classic broken multi-threaded code

### 12.2. Why data races are dangerous

The JMM allows the JIT to assume DRF. In racy code, the JIT can:
- Speculatively read a field once and reuse the value
- Reorder writes freely
- Return values that "materialize from thin air" (technically forbidden by modern JMMs, but historically legal)

**Result:** racy code can produce impossible values, infinite loops, or exceptions in "impossible" places.

---

## 13. SC-DRF theorem — the JMM's biggest guarantee

**"Sequential Consistency for Data-Race-Free programs."**

If your program has **no data races**, it behaves as if executed on a single CPU with strict interleaving of thread actions, respecting each thread's program order.

**Practical consequence:** you never need to reason about JIT reordering, cache behavior, or memory barriers if you write correctly synchronized code. `synchronized`, `volatile`, atomics, and immutability all remove data races — and DRF gives you SC.

---

## 14. CPU memory models & the abstraction

The JMM is an **abstract** model. Real CPUs have their own memory models:

### 14.1. x86 (Intel, AMD) — TSO (Total Store Order)

Strong model:
- Loads are never reordered with earlier loads
- Stores are never reordered with earlier stores
- Stores are never reordered with earlier loads
- **Only** stores can be reordered with later loads (needs a `mfence` to prevent)

**Consequence:** on x86, volatile writes require a full barrier (`mfence` or `lock`-prefix). Volatile reads are nearly free.

### 14.2. ARM (mobile, Apple Silicon) — weak model

Almost everything can be reordered. Barriers must be explicit at every level.

**Consequence:** volatile is more expensive on ARM. This is why JEP 193 (VarHandle) introduced weaker access modes (acquire/release/opaque) — cheaper than volatile on weak hardware.

### 14.3. What this means for you

The JMM abstracts over these differences. Correctly synchronized Java code runs identically on x86 and ARM. But the **cost** of synchronization differs — a `volatile` write on ARM is slower than on x86.

---

## 15. Memory barriers (fences) in detail

Four barrier types the JMM cares about:

| Barrier | Prevents | Emitted for |
|---|---|---|
| **LoadLoad** | reordering of two loads | after volatile read |
| **StoreStore** | reordering of two stores | before volatile write; before final field publication |
| **LoadStore** | reordering read → write | after volatile read |
| **StoreLoad** | reordering write → read | after volatile write (the **expensive** one) |

The `StoreLoad` barrier is why volatile writes are relatively slow — it's the only barrier that forces the store buffer to drain before subsequent loads.

**Advanced note:** modern hardware provides finer-grained barriers (`dmb ishld`, `dmb ishst` on ARM), and VarHandle access modes let you request just the barriers you need.

---

## 16. Modern JMM additions (JEPs)

### 16.1. JSR-133 (Java 5, 2004)

Fixed the previously-broken JMM. Formal happens-before, final field semantics, correct volatile ordering, and the DCL fix. Everything "modern JMM" descends from this.

### 16.2. JEP 171 (Java 8) — Fence intrinsics

Added `Unsafe.loadFence()`, `storeFence()`, `fullFence()` — internal-only until Java 9.

### 16.3. JEP 193 (Java 9) — VarHandle

The public replacement for `sun.misc.Unsafe`. Provides fine-grained memory access modes:

| Mode | Semantics | Cheaper than volatile? |
|---|---|---|
| `getPlain` / `setPlain` | no barriers | yes |
| `getOpaque` / `setOpaque` | ordered w.r.t. same variable, no barriers | yes |
| `getAcquire` / `setRelease` | acquire/release semantics | yes on ARM |
| `getVolatile` / `setVolatile` | full volatile | equivalent |

Used in high-performance libraries (Netty, Disruptor, ConcurrentHashMap internals).

### 16.4. JEP 188 (Java 9) — VarHandle-based `@Contended`

Not really JMM but related to concurrent performance. Pads fields to avoid false sharing (multiple fields sharing a cache line, thrashing under contention).

### 16.5. Java 21+ virtual threads

Do NOT change the JMM. Virtual threads honor happens-before, volatile, synchronized, and all JMM semantics identically to platform threads. What changes is scheduling, not memory model.

---

## 17. Detailed examples — worked step-by-step

### Example 1 — Classic volatile flag with piggybacked data

```java
public class Runner {
    private int data;                    // NOT volatile
    private volatile boolean ready;       // volatile

    public void produce() {
        data = 42;              // A
        ready = true;           // B - volatile write
    }

    public void consume() {
        while (!ready) {}       // C - volatile read (spinning)
        System.out.println(data);   // D
    }
}
```

**Analysis:**
- A hb B (program order in producer)
- B hb C (volatile write hb volatile read)
- C hb D (program order in consumer)
- ∴ A hb D — D is guaranteed to print 42

The consumer sees `data == 42` because visibility for `data` **piggybacks** on the volatile write of `ready`.

**Break it:** remove `volatile` from `ready`. Now:
- No hb from B to C
- No hb chain to D
- Consumer may spin forever (JIT can hoist the check)
- Even if consumer sees `ready == true`, `data` may be 0

---

### Example 2 — Broken Double-Checked Locking

```java
public class Singleton {
    private static Singleton instance;    // NOT volatile

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
}
```

**Why it's broken:**

`instance = new Singleton()` is three steps:
1. Allocate memory
2. Run constructor (write fields)
3. Publish reference

Steps 2 and 3 can be **reordered** by the JIT. Thread B entering `getInstance()` may:
- See `instance != null` (step 3 done)
- But observe uninitialized fields (step 2 not done)
- Dereference `instance` → NullPointerException or worse: crash

**Fix — volatile:**
```java
private static volatile Singleton instance;
```
The volatile write's StoreStore barrier prevents step 3 from moving before step 2.

**Better fix — Bill Pugh holder:**
```java
private Singleton() {}
private static class Holder {
    static final Singleton INSTANCE = new Singleton();
}
public static Singleton getInstance() { return Holder.INSTANCE; }
```
Uses class-init safety — no volatile needed, no synchronization overhead.

---

### Example 3 — Safe immutable object publication

```java
public final class Config {
    private final String host;
    private final int port;
    public Config(String host, int port) {
        this.host = host;
        this.port = port;
    }
    public String getHost() { return host; }
    public int getPort()    { return port; }
}

public static Config config;   // NOT volatile
```

Thread 1:
```java
config = new Config("localhost", 8080);
```

Thread 2:
```java
Config c = config;
if (c != null) print(c.getHost() + ":" + c.getPort());
```

**Analysis:**
- `config` is a plain reference. Thread 2 may see `null` or `non-null` — not guaranteed
- **But**: if Thread 2 sees `config != null`, then `getHost()` and `getPort()` are guaranteed to return `"localhost"` and `8080`

Why? **Final field freeze**. Every read of `host` / `port` through any published reference sees their initialized values, even without volatile publication of `config`.

**Trade-off:** Thread 2 may not see the update at all (may see `null` forever), but if it sees non-null, the fields are correct. If you need visibility guarantees for `config` too, make it `volatile`.

---

### Example 4 — Unsafe publication of a partially-final object

```java
public class Config {
    private final String host;
    private int port;                 // NOT final
    public Config(String host, int port) {
        this.host = host;
        this.port = port;
    }
}

public static Config config;         // plain field
// Thread 1: config = new Config("h", 80);
// Thread 2: if (config != null) print(config.port);
```

**The bug:** `port` is not `final`. Its write is not part of the final-field freeze. Thread 2 may:
- See `config != null` and `host = "h"` (freeze covers `host`)
- But see `port = 0` (freeze does NOT cover `port`)

**Fix:** either make `port` `final`, or make `config` `volatile`, or synchronize both write and read.

---

### Example 5 — the "stop flag" that never stops

```java
public class Worker implements Runnable {
    private boolean running = true;   // NOT volatile
    public void run() {
        while (running) {
            /* do work */
        }
    }
    public void stop() { running = false; }
}
```

Main thread starts the worker, then calls `worker.stop()`. The worker often loops forever.

**Why:** the JIT sees no writes to `running` inside `run()`. It hoists the read of `running` out of the loop into a register:

```java
// JIT-optimized version:
if (running) {
    while (true) { /* do work */ }
}
```

Now `stop()` from the main thread has no way to make the worker exit.

**Fix:** `private volatile boolean running = true;` — the volatile read cannot be hoisted.

---

### Example 6 — a corrupted HashMap

```java
private Map<String, Integer> counts = new HashMap<>();

// Multiple threads:
counts.put(key, counts.getOrDefault(key, 0) + 1);
```

**Observable symptoms:** infinite loops in `get()`, NullPointerException where key existed, lost updates, `ConcurrentModificationException`.

**Why:** `HashMap` internal state has data races. Rehashing during expansion can produce a **circular linked list**, causing `get()` to loop forever (before Java 8; slightly different symptoms after).

**Fix:** `ConcurrentHashMap` + atomic `merge`:
```java
counts.merge(key, 1, Integer::sum);
```
Every operation on `ConcurrentHashMap` establishes hb between put-to-key and later get-of-key.

---

### Example 7 — this escapes during construction

```java
public class Listener {
    private final List<Data> buffer = new ArrayList<>();
    private final String name;

    public Listener(String name, EventBus bus) {
        this.name = name;
        bus.register(this);   // ← this escapes before construction completes
    }

    public void onEvent(Data d) {
        buffer.add(d);
        print(name);          // may print null!
    }
}
```

**Bug:** `bus.register(this)` may hand the reference to another thread that immediately fires an event. That thread sees a partially-constructed `Listener` — `name` may not be assigned yet.

**Fix:** use a factory that constructs then publishes:
```java
public static Listener create(String name, EventBus bus) {
    Listener l = new Listener(name);   // fully constructed
    bus.register(l);                    // safely published now
    return l;
}
```

Or use a `volatile` on `name` — but the factory pattern is cleaner.

---

### Example 8 — the "long" tearing on 32-bit JVMs

```java
private long timestamp;   // NOT volatile

// Thread 1: timestamp = System.nanoTime();
// Thread 2: long t = timestamp;
```

On a 32-bit JVM, non-volatile `long` writes can be **torn** — split into two 32-bit writes. Thread 2 may see the low half of a new value and the high half of an old value: an **impossible** timestamp.

**Fix:** `private volatile long timestamp;` — the JMM guarantees atomic 64-bit access for volatile long/double.

---

## 18. Common patterns and their JMM justification

### Pattern 1 — Volatile shutdown flag

```java
private volatile boolean shutdown = false;

public void run() {
    while (!shutdown) { work(); }
}
public void shutdown() { shutdown = true; }
```
JMM: volatile read hb subsequent reads — worker exits promptly.

### Pattern 2 — Immutable configuration record

```java
public record Config(String host, int port) {}
public static volatile Config config;

// atomic swap:
public void reload(Config newConfig) { this.config = newConfig; }
public Config current() { return this.config; }
```
JMM: volatile publish of a deeply-immutable record. Readers never see torn config.

### Pattern 3 — Copy-on-write list

```java
private volatile List<Listener> listeners = List.of();

public void add(Listener l) {
    List<Listener> copy = new ArrayList<>(listeners);
    copy.add(l);
    listeners = List.copyOf(copy);   // atomic publish of new immutable list
}
public void fireEvent(Event e) {
    for (Listener l : listeners) l.on(e);   // safe iteration
}
```
JMM: readers get a stable snapshot; writers replace atomically via volatile.

### Pattern 4 — Bill Pugh singleton

```java
private Singleton() {}
private static class Holder {
    static final Singleton INSTANCE = new Singleton();
}
public static Singleton getInstance() { return Holder.INSTANCE; }
```
JMM: class-init synchronization by JVM. No volatile, no lock in the fast path. Best-in-class.

### Pattern 5 — AtomicReference for state transitions

```java
private final AtomicReference<State> state = new AtomicReference<>(INITIAL);

public boolean transition(State from, State to) {
    return state.compareAndSet(from, to);
}
```
JMM: CAS operations have volatile semantics + atomicity. Perfect for state machines.

---

# 19. INTERVIEW QUESTIONS

## Junior level

**Q1. What is the JMM?**
A formal specification (JLS §17) defining which values a thread is allowed to observe when reading fields other threads may have written. It's the contract between Java code, the JIT, the CPU, and the runtime.

**Q2. Why do we need a memory model?**
Because modern hardware and compilers reorder instructions and cache values. Without a formal model, correct multi-threaded Java would be impossible to write portably.

**Q3. What is happens-before?**
A partial order over program actions. If A hb B, then A's effects are visible to B, and A is ordered before B. It's the primitive that gives Java's memory model its guarantees.

**Q4. Name three ways to establish happens-before.**
1. Program order within a thread
2. Unlock of monitor M hb every subsequent lock of M
3. Volatile write hb every subsequent volatile read of the same field

**Q5. What does volatile guarantee?**
Visibility (writes seen by other threads) and ordering (no reordering with surrounding operations). Does NOT provide atomicity for compound operations.

**Q6. What does synchronized guarantee?**
Everything volatile does, PLUS mutual exclusion and atomicity for the block, PLUS ordering for all fields written inside.

**Q7. What is safe publication?**
Making an object visible to other threads such that they see it fully constructed. Five ways: static-final field, volatile field, final field (no this-escape), thread-safe collection, synchronized publish+read.

**Q8. What is a data race?**
Two threads accessing the same location, at least one writing, not ordered by happens-before. Racy programs have essentially undefined behavior.

## Mid level

**Q9. What is the difference between volatile and synchronized?**
Volatile is a memory-visibility primitive (single reads/writes visible). Synchronized adds mutual exclusion and atomicity for the entire block. Volatile is cheaper but weaker.

**Q10. Why does Double-Checked Locking need volatile?**
The write `instance = new Singleton()` is 3 steps (allocate, construct, publish). Without volatile, the JIT can reorder them so another thread sees a non-null reference to an uninitialized object. Volatile's StoreStore barrier prevents this.

**Q11. What is piggybacking?**
Using a volatile write (or lock release) to propagate visibility for other non-volatile fields written before it. All safe publication is a form of piggybacking.

**Q12. What is the final-field freeze?**
At the end of a constructor, the JVM inserts a synthetic freeze on every final field. Any thread reading the object reference after publication sees the fully initialized final fields — even without volatile or synchronized.

**Q13. What breaks the final-field freeze guarantee?**
Letting `this` escape during construction (`bus.register(this)`, starting a thread with `this`, calling overridable methods on `this`).

**Q14. What is the SC-DRF theorem?**
Sequential Consistency for Data-Race-Free programs: if your code has no data races, it behaves as if executed on a single CPU with strict interleaving. This is the JMM's biggest guarantee for practitioners.

**Q15. What happens when two threads read a non-volatile boolean set by another thread?**
Without hb, the readers may see the old value indefinitely. The JIT can cache the field in a register. This is the classic "loop that never stops" bug.

## Senior level

**Q16. Explain the 4 memory barriers and where each is emitted.**
LoadLoad (after volatile read), StoreStore (before volatile write, before final freeze), LoadStore (after volatile read), StoreLoad (after volatile write — the expensive one). The JIT emits these to implement JMM semantics.

**Q17. Compare x86 and ARM memory models.**
x86 (TSO): strong model — only Store-Load reorderings possible. Volatile writes need `mfence` (expensive); reads are nearly free. ARM (weak): almost everything reorderable. Barriers needed at every level. Volatile is more expensive.

**Q18. Explain VarHandle access modes.**
Java 9+ VarHandle provides finer access modes: plain (no barriers), opaque (per-variable ordering, no barriers), acquire/release (weaker than volatile — great on ARM), volatile (full guarantees). Used in high-performance libraries.

**Q19. Explain the difference between sequential consistency and release/acquire semantics.**
SC: all threads see writes in the same global order. Release/acquire: only threads that observe a specific release see the writes that led up to it — different threads may observe different orderings across independent chains. Java's volatile is SC; VarHandle's acquire/release is weaker but cheaper.

**Q20. Are `long`/`double` reads atomic in Java?**
Only when marked `volatile`. Otherwise, on 32-bit JVMs, non-volatile long/double reads can be torn (two 32-bit reads observing a partial write). Modern 64-bit JVMs treat them atomically in practice, but the JMM doesn't guarantee it.

**Q21. Explain how a ConcurrentHashMap provides safe publication.**
Every put/get establishes hb between the put of a key and the get of the same key. Objects stored in the map are safely published to threads that retrieve them — no volatile needed for the value's fields.

**Q22. Why can't the JIT hoist a volatile read out of a loop?**
Because a volatile read establishes a happens-before edge with every prior volatile write. Hoisting the read would violate the JMM — the JIT would be pretending later iterations happened at the time of the first read.

## Expert level

**Q23. Explain "out-of-thin-air" values.**
In a racy program, JMM historically allowed reads to observe values no thread ever wrote — invented by speculative execution. Modern JMMs prohibit this in practice, but formalizing it is an open research problem.

**Q24. Given: thread A: x = 1; y = 1;   thread B: r1 = y; r2 = x; — is (r1=1, r2=0) possible?**
Yes, if `x` and `y` are plain fields. The JIT/CPU may reorder A's writes; B may see y=1 while x is still 0. Making both volatile prevents this (SC ordering).

**Q25. Explain the "publication safety" of ThreadLocal.**
ThreadLocal.set stores in a thread-local map indexed by the current thread. Reads are always from the same thread that wrote — no cross-thread visibility needed. Removes the need for JMM synchronization entirely.

**Q26. Design an efficient lock-free counter. What memory ordering does it need?**
`AtomicLong.getAndAdd()`. Internally uses CAS with volatile semantics. Every operation establishes hb with subsequent reads, so counters converge correctly under contention.

**Q27. Under what conditions can a JIT optimization break a "correctly synchronized" program?**
It shouldn't. SC-DRF guarantees that correctly synchronized programs behave sequentially consistent. If it breaks, either (a) the program isn't truly DRF, (b) the JIT has a bug, or (c) you're seeing something like torn long/double without volatile.

**Q28. Explain why immutable objects are safely publishable through a plain field.**
Final-field freeze semantics: after the constructor completes without letting `this` escape, all `final` fields are guaranteed visible to any thread that gets the reference, through any publication mechanism — even a plain non-volatile field. Combined with immutability (no mutation of contents), the whole object graph is safely shareable.

**Q29. How does @Contended work and when do you use it?**
Pads a field to a full cache line (typically 128 bytes with prefetcher padding) to avoid false sharing — when independent fields on the same cache line cause cache-line ping-pong between cores. Used in extreme hot-path counters. Cost: significant memory overhead per padded field.

**Q30. What are the trade-offs of using AtomicReference vs volatile?**
Volatile: cheaper, only supports read/write. AtomicReference: adds compareAndSet, getAndSet, and other atomic compound operations at slight overhead per update. Use volatile for pure publication; use AtomicReference when you need conditional updates.

---

# 20. DEBUG SCENARIOS

## D1. The loop that never stops

```java
private boolean running = true;
public void run() { while (running) work(); }
public void stop() { running = false; }
```

**Bug:** `running` is not volatile. JIT hoists the check out of the loop.

**Fix:** `private volatile boolean running = true;`

---

## D2. Reading stale value forever

```java
private String value = "initial";
public String get() { return value; }
public void set(String v) { value = v; }

// Thread A: config.set("updated");
// Thread B: while (true) print(config.get());   // prints "initial" forever
```

**Bug:** no synchronization. Thread B caches the field in a register.

**Fix:** volatile the field, or synchronize both accessors.

---

## D3. Broken DCL without volatile

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

**Bug:** `instance = new Singleton()` can be reordered so publication precedes construction.

**Fix:** `private static volatile Singleton instance;` OR use Bill Pugh holder pattern.

---

## D4. Publishing partially constructed object

```java
public class Config {
    private final Map<String, String> map;
    public Config() {
        Map<String, String> m = new HashMap<>();
        m.put("host", "localhost");
        this.map = m;
    }
}

public static Config config;
// Thread 1: config = new Config();
// Thread 2: config.map.get("host");   ← may NPE or return null
```

**Bug:** `config` is not volatile. Thread 2 may see `config != null` (partial visibility) but see uninitialized `map`.

Also: `final` field freeze applies to `map`, but the freeze requires safe publication of the reference itself, and a plain field doesn't guarantee visibility of the reference.

**Fix:** `public static volatile Config config;`

---

## D5. Volatile array — element writes are not volatile

```java
private volatile int[] data = new int[10];
// Thread 1: data[3] = 42;
// Thread 2: int v = data[3];   ← may see 0
```

**Bug:** `volatile` applies to the array *reference*, not element access.

**Fix:** `AtomicIntegerArray data = new AtomicIntegerArray(10);`

---

## D6. HashMap corruption

```java
private Map<String, Integer> counts = new HashMap<>();
// Multiple threads: counts.put(key, counts.getOrDefault(key, 0) + 1);
```

**Bug:** HashMap internal state races. Rehash cycles can create circular linked lists → infinite loops in get().

**Fix:** `ConcurrentHashMap` + `merge(key, 1, Integer::sum)`.

---

## D7. Torn long read

```java
private long timestamp;
// Thread 1: timestamp = System.nanoTime();
// Thread 2: long t = timestamp;   ← may see half-old-half-new bytes
```

**Bug:** on 32-bit JVMs, non-volatile long writes are split.

**Fix:** `private volatile long timestamp;`

---

## D8. this escape during construction

```java
public class Listener {
    private final String name;
    public Listener(String name, EventBus bus) {
        this.name = name;
        bus.register(this);   // ← this escapes
    }
    public void onEvent(Event e) { print(name); }   // ← may print null
}
```

**Bug:** the event bus (potentially another thread) sees `this` before `name` is assigned. Final-field freeze hasn't happened yet.

**Fix:** factory method that constructs then publishes.

---

## D9. volatile counter++ — read-modify-write race

```java
private volatile int counter = 0;
// N threads: counter++;   ← lost updates
```

**Bug:** volatile makes each read and write visible, but the sequence is not atomic.

**Fix:** `AtomicInteger counter; counter.incrementAndGet();`

---

## D10. Reordering breaks initialization

```java
class Holder {
    int x, y;
    Holder() { x = 1; y = 2; }
}
static Holder h;
// Thread A: h = new Holder();
// Thread B: if (h != null) print(h.x + h.y);   ← may print 0, 1, 2, or 3
```

**Bug:** `h` is not volatile; fields are not final. Publication may precede construction.

**Fix:** `static volatile Holder h;` OR make x, y `final`.

---

## D11. Wait/notify without holding the lock

```java
private final Object lock = new Object();
private boolean ready = false;
// Thread A: ready = true; lock.notify();   ← IllegalMonitorStateException
```

**Bug:** `wait()` and `notify()` must be called while holding the monitor.

**Fix:**
```java
synchronized (lock) { ready = true; lock.notify(); }
```

---

## D12. wait() without a guard loop

```java
synchronized (lock) {
    lock.wait();          // wakes → immediately proceeds
    consume(data);
}
```

**Bug:** spurious wakeups can occur without a corresponding `notify()`. Thread may proceed before `data` is ready.

**Fix:** always guard with a while loop:
```java
synchronized (lock) {
    while (!ready) lock.wait();
    consume(data);
}
```

---

# QUICK-FIRE RAPID ROUND

| Question | Answer |
|---|---|
| Is `volatile` a synchronization mechanism? | No — it's a memory-visibility mechanism, no exclusion |
| Does `synchronized` provide visibility? | Yes — unlock happens-before subsequent lock |
| Does `volatile` provide atomicity for `++`? | No — RMW is 3 operations |
| What guarantees `long`/`double` atomic writes? | `volatile` only |
| Are final fields safely publishable? | Yes, if `this` didn't escape |
| Are records automatically thread-safe? | Yes (if all fields transitively immutable) |
| Can the JIT hoist a volatile read out of a loop? | No — must re-read every iteration |
| Does synchronized flush all caches? | Common misconception — it establishes hb, JIT emits barriers |
| Is happens-before transitive? | Yes |
| What is SC-DRF? | Sequential Consistency for Data-Race-Free programs |
| Can two independent volatile writes be reordered? | Yes, across different fields |
| What is the cheapest memory barrier? | LoadLoad on x86 (nearly free) |
| Most expensive? | StoreLoad — flushes store buffer |
| Does `Thread.sleep` release locks? | No |
| Does virtual threads change the JMM? | No |
| Is `ConcurrentHashMap.put` a happens-before edge? | Yes, with subsequent `get` on same key |

---

# ONE-SENTENCE SUMMARIES

- **JMM** — the formal contract between Java code, JIT, CPU, and runtime about which values threads can observe
- **Happens-before** — partial ordering that guarantees visibility and ordering; the JMM's core primitive
- **Volatile** — visibility + ordering for a single field, no atomicity for compound ops
- **Synchronized** — mutual exclusion + visibility + ordering + atomicity for the block
- **Final** — freeze semantics guarantee visibility of initialized fields to any thread reading the reference (if `this` didn't escape)
- **Safe publication** — making an object visible fully constructed; 5 mechanisms (static-final, volatile, final field, thread-safe collection, sync publish+read)
- **Data race** — unordered conflicting access; program has undefined semantics
- **SC-DRF** — data-race-free programs behave sequentially consistent — the JMM's biggest practical guarantee
- **Memory barrier** — CPU/JIT instruction preventing specific reorderings across a boundary
- **CPU memory model** — x86 (strong) vs ARM (weak); volatile is more expensive on ARM

---

## The interview-safe summary you can drop verbatim

> The Java Memory Model is a formal specification (JSR-133, JLS §17) defining which values a thread can observe when reading fields other threads have written. Its core primitive is the happens-before relation — a partial order over program actions that guarantees visibility and ordering. The JMM ensures Sequential Consistency for Data-Race-Free programs (SC-DRF): if you avoid data races via `synchronized`, `volatile`, `final` fields (with safe construction), or thread-safe collections, your program behaves as if executed on a single CPU with strict interleaving. Without those guarantees, the JIT and CPU are free to reorder, cache values in registers, and produce impossible states. Volatile provides visibility and ordering for a single field; synchronized adds mutual exclusion and atomicity; final gives you a freeze action that safely publishes immutable objects even without volatile or synchronization. This is why immutability is the strongest concurrency tool in Java — it eliminates the need to reason about the JMM at all for shared state.
