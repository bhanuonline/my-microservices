# Java `volatile` & the Java Memory Model — Interview Questions & Answers

A ready-to-quiz reference on `volatile`, the Java Memory Model (JMM), happens-before, and safe publication. Ordered from basic → deep → hands-on debugging.

**Prerequisite context:** this file assumes you've worked through the `final` and `static` files — `final`-field freeze semantics (Q34 in the final file) and class-initialization safety (Q31 in the static file) are directly referenced here.

---

## Where the JMM shows up in Java

Before the questions, know the **five mechanisms** the JMM gives you for cross-thread coordination — every question below fits in one of these:

| Mechanism | Purpose | Cost |
|---|---|---|
| `synchronized` | mutual exclusion + memory visibility | lock acquisition |
| `volatile` | memory visibility (no exclusion, no atomicity for compound ops) | memory barrier per read/write |
| `final` fields | safe publication if `this` doesn't escape constructor | free after construction |
| Atomics (`AtomicInteger`, etc.) | atomic read-modify-write + volatile semantics | CAS loop |
| `VarHandle` / `Unsafe` (JDK 9+) | fine-grained acquire/release/opaque access | matches the mode chosen |

`volatile` is the middle-ground — cheaper than `synchronized`, safer than a plain field, but weaker than an atomic.

---

## Basic conceptual

### Q1. What is `volatile` in Java?

`volatile` is a field modifier that tells the JVM: **"reads and writes of this field must go directly to main memory and must not be reordered around each other."**

It provides two guarantees:
1. **Visibility** — a write by one thread is immediately visible to reads by other threads.
2. **Ordering** — reads and writes of the field are not reordered with the surrounding code in ways that would violate happens-before.

It does **not** provide:
- Atomicity for compound operations (`counter++` is still broken)
- Mutual exclusion (multiple threads can write concurrently)

### Q2. What is the Java Memory Model?

The **JMM** is the formal specification (JSR-133, integrated into JLS §17) that defines **which values a thread is allowed to observe** when reading fields that other threads have written. It's the contract between:
- Java code
- The JIT compiler (which reorders instructions for performance)
- The CPU (which reorders memory operations for performance)
- The runtime (which caches values in registers and per-CPU caches)

Without the JMM, there'd be no portable way to write correct concurrent Java — every JVM/CPU combination would behave differently.

### Q3. Why can't the JVM just always show every thread the latest value?

Because CPUs have **per-core caches**. When thread A on core 0 writes to memory, that write goes to core 0's cache. Thread B on core 1 reads from core 1's cache — which may not have received the update yet. Memory synchronization between cores is expensive (nanoseconds to microseconds).

Similarly, the JIT compiler is free to hoist reads out of loops, keep values in registers, and reorder independent operations — all for performance. The JMM specifies exactly which of these are allowed and where you need explicit synchronization to constrain them.

### Q4. When does a field need to be `volatile`?

When it is:
1. Written by one thread
2. Read by another thread
3. Without any other synchronization protecting it (no `synchronized`, no `Atomic*`)

Typical use cases: status flags (`running`, `shutdownRequested`), lazily initialized singletons with DCL, publication of immutable objects.

### Q5. Volatile vs synchronized — when do you use each?

| | `volatile` | `synchronized` |
|---|---|---|
| Provides visibility | Yes | Yes |
| Provides atomicity | No (only for single reads/writes) | Yes |
| Provides mutual exclusion | No | Yes |
| Cost | Cheap (memory barrier) | Expensive (lock acquire, potential blocking) |
| Blocks other threads | No | Yes |
| Use case | Single-field flag, publication | Multi-step critical section |

Rule: use `volatile` for **single writes** to be observed by other threads. Use `synchronized` when you need to update multiple fields atomically or perform read-modify-write on a single field.

---

## JMM fundamentals

### Q6. What is happens-before?

Happens-before is a **partial order** the JMM defines over actions in a program. If action A **happens-before** action B, then:
1. A's effects are visible to B
2. A appears to occur before B in the ordering B observes

It's the fundamental primitive for reasoning about concurrent Java. Without a happens-before relationship, two actions can be observed in either order — or one's effects may not be visible to the other at all.

### Q7. What are the happens-before rules?

The core rules you should memorize:

1. **Program order** — within a single thread, actions happen-before later actions in program order
2. **Monitor lock** — an unlock happens-before every subsequent lock on the same monitor
3. **Volatile** — a write to a volatile field happens-before every subsequent read of that field
4. **Thread start** — `Thread.start()` happens-before any action in the started thread
5. **Thread termination** — actions in a thread happen-before another thread detects it terminated (via `join()` or `isAlive()` returning false)
6. **Interruption** — a thread calling `interrupt()` happens-before the interrupted thread detects the interrupt
7. **Constructor completion** — the end of a constructor happens-before the start of a finalizer (rarely useful)
8. **Transitivity** — if A happens-before B and B happens-before C, then A happens-before C

Almost every safe-publication trick is built by chaining these rules through transitivity.

### Q8. What is memory visibility?

**Visibility** = when thread B reads a field, will it see the value thread A wrote?

Without a happens-before relationship between A's write and B's read, the answer is **undefined** — B may see A's write, may see a stale value, or may see a partial write (for `long`/`double` on 32-bit systems).

Visibility is what `volatile`, `synchronized`, and `final` (with safe construction) fundamentally provide.

### Q9. What is instruction reordering?

Both the **compiler** (JIT) and the **CPU** are allowed to execute instructions in a different order than they appear in the source code, as long as the observable behavior of a **single thread** is preserved.

Example — the JIT may reorder these:
```java
x = 1;
y = 2;
```

to:
```java
y = 2;
x = 1;
```

If a single thread reads `x` and `y` afterward, both orders give the same result. But another thread might observe `y == 2` while `x == 0`. This is why we need memory barriers.

### Q10. What is a memory barrier / fence?

A CPU instruction that constrains reordering across a boundary. The four main barrier types:

- **LoadLoad** — reads before the barrier complete before reads after
- **StoreStore** — writes before the barrier complete before writes after
- **LoadStore** — reads before the barrier complete before writes after
- **StoreLoad** — writes before the barrier complete before reads after (the **most expensive** — required after a volatile write)

The JIT emits these barriers automatically based on `volatile`, `synchronized`, and `final` semantics. You never write them directly (unless you're using `VarHandle`).

### Q11. What is safe publication?

**Safely publishing** an object means: after the publish, any thread that gets a reference to it sees a fully constructed, correctly initialized instance.

Four ways to safely publish an object in Java:
1. Assign it to a **`static` field during class initialization** (JVM guarantees class-init safety)
2. Assign it to a **`volatile` field** (or `AtomicReference`)
3. Publish it via **`final` field** in a properly constructed object (`this` didn't escape)
4. Publish it inside a **`synchronized` block** where readers also synchronize

Unsafe publication → readers may see partially constructed objects, stale field values, or nulls when non-null was written.

### Q12. What is a data race?

A **data race** is when two threads access the same memory location, at least one of them is a write, and the accesses are not ordered by happens-before.

**Data-racy code has essentially undefined behavior in Java.** Fields may hold impossible values, reads may return values from the future or past, loops may never terminate. The JMM only makes strong guarantees for programs **without data races** (the "SC-DRF" guarantee — Sequential Consistency for Data-Race-Free programs).

---

## Volatile semantics in depth

### Q13. What exactly does a volatile write do?

At the hardware level:
1. Flushes any pending writes from the CPU's store buffer
2. Emits a **StoreStore** barrier before the write (no earlier stores can be reordered past)
3. Emits the write itself
4. Emits a **StoreLoad** barrier after (no subsequent loads can be reordered before)

At the language level: the write happens-before every subsequent read of that field by any thread.

### Q14. What exactly does a volatile read do?

At the hardware level:
1. Emits the read (from main memory, not a stale cache line if invalidation is pending)
2. Emits a **LoadLoad** and **LoadStore** barrier (no subsequent reads or writes can be reordered before this read)

At the language level: sees the "most recent" volatile write according to happens-before ordering.

### Q15. Does `volatile` make `counter++` thread-safe?

**No.** `counter++` is three operations:
1. Read `counter`
2. Add 1
3. Write `counter`

`volatile` makes each of those individually visible, but doesn't make the sequence atomic. Two threads can both read `5`, both compute `6`, both write `6` — you lose an increment.

**Fix:** use `AtomicInteger.incrementAndGet()`, or `synchronized`.

### Q16. Can two threads write to a volatile field concurrently?

Yes — `volatile` provides no mutual exclusion. Both writes go through, and the "final" observed value is whichever happened later in the happens-before ordering (undefined if concurrent). No exception, no lock — just a last-write-wins race.

### Q17. What are the guarantees of a volatile long or double?

Java guarantees that reads and writes of `volatile long` and `volatile double` are **atomic** — no torn reads even on 32-bit systems.

**Without `volatile`:** on some 32-bit JVMs, a `long` write can be split into two 32-bit writes, and another thread may observe half of one write and half of another. This is one of the very few cases where a plain field access can produce a "impossible" value in Java.

### Q18. What does `volatile` on an array reference NOT do?

Given:
```java
private volatile int[] data;
```

- **Reads/writes of the array *reference*** are volatile — safely publish a whole new array
- **Reads/writes of *array elements*** (`data[i]`) are **NOT volatile** — `data[i] = 5` has no visibility guarantee

**Fix:** use `AtomicIntegerArray` or `VarHandle` array element access.

### Q19. Is a volatile reference to a mutable object safe?

Only for the reference itself — mutations to the referenced object are **not** protected by the volatile. Classic trap:

```java
private volatile Map<String, Integer> cache = new HashMap<>();
// Thread A: cache.put("x", 1);   ← NOT synchronized
// Thread B: cache.get("x");      ← NOT synchronized
```

Data race on `HashMap` internal state → arbitrary corruption (infinite loops from re-hash cycles, `null` returned when key exists, exceptions).

**Fix:** either use `ConcurrentHashMap`, or write a whole new map + reassign the volatile reference (copy-on-write pattern).

---

## Happens-before in practice

### Q20. Give an example of the volatile happens-before rule.

```java
private int x;
private volatile boolean ready;

// Thread A
x = 42;              // 1
ready = true;        // 2 (volatile write)

// Thread B
while (!ready) {}    // 3 (volatile read)
System.out.println(x);   // 4
```

Chain: **1 happens-before 2** (program order in A). **2 happens-before 3** (volatile). **3 happens-before 4** (program order in B). By transitivity, **1 happens-before 4** — so B is guaranteed to print `42`, never `0`.

This is called **piggybacking**: `x` isn't volatile, but you get visibility for it *through* the volatile write of `ready`.

### Q21. What is the "piggyback" pattern?

Using a `volatile` (or synchronization) to ensure visibility for **other, non-volatile** fields written before it. The example in Q20 is the canonical form. Every safe publication in Java is a variation on this.

### Q22. What is `Thread.start()`'s happens-before guarantee?

Everything the parent thread did **before calling `start()`** is visible to the new thread when it begins running. This is why worker threads can safely read fields the main thread initialized:

```java
config.load();       // main thread
executor.submit(worker);   // main thread — happens-before guarantees worker sees config
```

### Q23. What is `Thread.join()`'s happens-before guarantee?

Everything the joined thread did **before it terminated** is visible to the caller of `join()` after `join()` returns. This is the "collect worker's results safely" primitive.

### Q24. How does `final` fit into happens-before?

`final` fields have a special guarantee (JMM §17.5): if the constructor completes without letting `this` escape, then any thread that reads the reference is guaranteed to see the correctly-initialized `final` fields — no explicit synchronization needed.

This is a **weaker** form of happens-before (only applies to `final` fields, and only from construction to publication of the reference), but it's why immutable objects don't need volatile/synchronized for their fields.

### Q25. Is `synchronized` strictly stronger than `volatile`?

Almost. `synchronized` provides everything `volatile` does (visibility, ordering) **plus** mutual exclusion and atomicity for the entire critical section. But `synchronized` is more expensive and can block, so use `volatile` when you don't need exclusion.

One subtlety: `synchronized` also provides ordering on all fields written inside the critical section (relative to the lock). `volatile` only orders around the volatile field itself.

---

## Threading gotchas

### Q26. Why is this loop broken?

```java
class Worker implements Runnable {
    private boolean running = true;
    public void stop() { running = false; }
    public void run() {
        while (running) { /* do work */ }
    }
}
```

`running` is not volatile, and there's no synchronization. The JIT is allowed to **hoist the read out of the loop** and turn it into `if (running) while (true) { ... }`. Calling `stop()` from another thread may have no effect — the worker loops forever.

**Fix:** `private volatile boolean running = true;`

### Q27. Why does double-checked locking need `volatile`?

Broken version:
```java
private static Singleton instance;   // NOT volatile
public static Singleton getInstance() {
    if (instance == null) {
        synchronized (Singleton.class) {
            if (instance == null) instance = new Singleton();
        }
    }
    return instance;
}
```

The write `instance = new Singleton()` is **not one operation**. It's:
1. Allocate memory
2. Run constructor (write fields)
3. Publish reference to `instance`

Steps 2 and 3 can be **reordered by the JIT**. Another thread entering `getInstance()` may see `instance != null` (step 3 done) but observe uninitialized fields (step 2 not done).

**Fix:** `private static volatile Singleton instance;` — the volatile write of the reference guarantees the constructor's writes are visible before the reference is.

Even better: use the Holder pattern (no volatile needed).

### Q28. Why does `wait()` require the caller to hold a monitor?

`wait()` is designed to work with the "wait/notify + guard variable" pattern:

```java
synchronized (lock) {
    while (!ready) lock.wait();
    // use whatever ready==true implies
}
```

The `synchronized` block provides both:
- Mutual exclusion around checking `ready`
- Memory visibility for the read of `ready`

The `while` loop guards against spurious wakeups. Calling `wait()` without owning the lock throws `IllegalMonitorStateException`.

### Q29. What is a "safe publication idiom"?

Any of these patterns:

1. **Static initialization:**
   ```java
   public static final Config INSTANCE = load();
   ```
2. **Volatile field:**
   ```java
   private volatile Config config;
   ```
3. **Immutable object with `final` fields:**
   ```java
   public record Config(int retries, Duration timeout) {}
   ```
4. **Concurrent collection:**
   ```java
   private final Map<String, Config> cache = new ConcurrentHashMap<>();
   ```
5. **Synchronized publish + read:**
   ```java
   synchronized (this) { this.config = load(); }
   // readers:
   synchronized (this) { return this.config; }
   ```

---

## Design patterns

### Q30. What's the correct pattern for a shutdown flag?

```java
private volatile boolean shutdown = false;

public void run() {
    while (!shutdown) { /* work */ }
}

public void shutdown() {
    shutdown = true;
    // interrupt any blocked threads if needed:
    workerThread.interrupt();
}
```

Volatile ensures the worker sees the update. Adding `interrupt()` handles the case where the worker is blocked on `wait()`/`sleep()` and can't check the flag until it wakes.

### Q31. `volatile` vs `AtomicReference` — when do you use each?

| | `volatile T` | `AtomicReference<T>` |
|---|---|---|
| Simple read/write | Yes | Yes |
| Atomic compare-and-set | No | Yes (`compareAndSet`) |
| Atomic get-and-set | No | Yes (`getAndSet`) |
| Cost | Slightly cheaper | Adds CAS overhead on updates |

Use `volatile` for pure publication/visibility. Use `AtomicReference` when you need conditional updates (`compareAndSet`) or fetch-and-update patterns.

### Q32. Why should you prefer `ConcurrentHashMap` over a synchronized `HashMap`?

- `Collections.synchronizedMap(hashMap)` — a single lock around every operation. High contention.
- `ConcurrentHashMap` — lock striping / lock-free reads. Much better throughput under contention.

Also: `ConcurrentHashMap` has atomic compound operations like `computeIfAbsent` and `merge`, which are hard to do correctly by hand.

### Q33. What does "immutable objects are automatically thread-safe" actually mean?

If an object is **deeply immutable** (all fields `final`, no leaked mutable state, `this` doesn't escape the constructor), then:
- Any thread can read any field without synchronization
- The object can be shared freely without volatile publication
- Publication is safe via `final` field semantics

This is why record-based DTOs are the concurrency-safe default for value types.

### Q34. What is compare-and-swap (CAS)?

An atomic CPU primitive: "if the current value at address X is *expected*, write *newValue*, otherwise fail." Available in Java as:
- `AtomicInteger.compareAndSet(expect, update)`
- `AtomicReference.compareAndSet(expect, update)`

Used to implement lock-free algorithms — writers loop until their CAS succeeds. Contention manifests as retries rather than blocking. Backbone of every `java.util.concurrent.atomic` class.

---

## Senior-level questions

### Q35. What is JSR-133 and why does it matter?

JSR-133 (Java Memory Model and Thread Specification Revision) was integrated into Java 5. It fixed the previously broken JMM, giving formal guarantees about happens-before, `final` field semantics, and volatile ordering.

Before Java 5: `volatile` didn't prevent all reorderings, DCL was fundamentally broken, and `final` didn't have publication guarantees.

After Java 5: all of these work correctly. Any interview reference to "the modern JMM" means JSR-133-compliant behavior.

### Q36. Define happens-before formally.

Two actions A and B are ordered by happens-before if:
1. A precedes B in some **synchronization order** (via one of the rules in Q7), OR
2. A is a program-order predecessor of some action C, and C happens-before some action D that program-order precedes B (transitivity)

A read is allowed to see a write only if the write happens-before the read (or the read and write are unordered but the JMM permits observing that specific value). The full definition includes "action" (read/write/lock/unlock/etc.) and "well-formed executions" — but the intuition above is enough for practical purposes.

### Q37. What is sequential consistency?

**Sequential consistency (SC)** — every execution is equivalent to some interleaving of the threads' individual actions, and each thread's actions appear in program order.

The JMM does **not** guarantee sequential consistency in general — it only guarantees it for **data-race-free (DRF)** programs. This is the "SC-DRF" theorem: **if your program has no data races, it behaves as if all threads were interleaved on a single CPU in program order**.

Every other correctness reasoning in concurrent Java flows from this.

### Q38. What are "out-of-thin-air" values?

In a data-racy program, the JMM historically allowed reads to return values that were **never written** by any thread — literally invented out of thin air by aggressive speculation. Modern implementations disallow this in practice, but the JMM specification's exclusion of OOTA values is subtle and an active research area.

Interview signal: this comes up when discussing the fine print of the JMM or JVM implementer perspectives.

### Q39. What is the "atomic write to reference" guarantee?

Reads and writes of **reference variables** and **all primitives except long/double** are guaranteed to be atomic (never torn) even without `volatile`. You'll always read either the old or the new value, never a mix.

`long` and `double` are the exception on 32-bit systems — non-volatile reads can be torn. Modern 64-bit JVMs treat all writes atomically, but the spec still requires `volatile` for portability.

### Q40. Explain `VarHandle` and its access modes.

`VarHandle` (Java 9+, JEP 193) provides fine-grained memory access modes finer than `volatile`:

| Mode | Semantics | Analogous to |
|---|---|---|
| `getPlain` / `setPlain` | no barriers | plain field access |
| `getOpaque` / `setOpaque` | no reordering by JIT, but no barriers | (no direct volatile equivalent) |
| `getAcquire` / `setRelease` | acquire/release semantics (weaker than volatile) | C++11 atomic acquire/release |
| `getVolatile` / `setVolatile` | full volatile semantics | `volatile` field |

`getAcquire` + `setRelease` is enough for the DCL/holder pattern and is cheaper than full volatile on ARM. Used by high-performance libraries.

### Q41. What's the difference between volatile and `Atomic*` classes?

- `volatile` provides visibility and ordering for the field itself
- `Atomic*` classes provide the same, **plus** atomic compound operations (`incrementAndGet`, `compareAndSet`, `getAndUpdate`)

Under the hood, `AtomicInteger` wraps a `volatile int` and adds CAS-based methods. If you only need a flag, use `volatile`. If you need atomic increments, use `AtomicInteger`.

### Q42. Piggybacking — describe how it enables safe publication.

Given a volatile field `ready` and a non-volatile field `data`:

```java
data = expensiveInit();   // any number of non-volatile writes
ready = true;             // one volatile write
```

The volatile write "carries" all the earlier writes with it. Any thread that observes `ready == true` is guaranteed to see the writes to `data`. This is how you publish a fully constructed object cheaply — one volatile write instead of synchronizing every field.

### Q43. Is `String` thread-safe? Why or why not?

Yes — `String` is **immutable**. All its fields are `final`, and the class never mutates them internally (see final-file Q51). Once safely published (via a `final` field, `static` initialization, or `volatile`), a `String` is safe to share across any number of threads.

`StringBuilder` is **not** thread-safe. `StringBuffer` is (via `synchronized`), but the modern advice is to use `StringBuilder` in single-threaded code and manage synchronization externally for shared use.

### Q44. Are `wait()` and `notify()` still relevant in modern Java?

Rarely. In practice:
- Use `java.util.concurrent.BlockingQueue` for producer-consumer patterns
- Use `CountDownLatch`, `CyclicBarrier`, `Semaphore` for coordination
- Use `Condition` with `Lock` for fine-grained wait/notify (better than `Object.wait`)
- Reserve raw `wait()`/`notify()` for classes that need to implement custom locking primitives

Interviewers ask about `wait/notify` because it teaches JMM fundamentals, not because you should write it.

---

## Deep senior-level questions

### Q45. Explain the four memory barriers and where each is emitted.

| Barrier | Prevents | Emitted for |
|---|---|---|
| LoadLoad | reordering of two reads | after volatile read |
| StoreStore | reordering of two writes | before volatile write, before final field publication |
| LoadStore | reordering of read → write | after volatile read |
| StoreLoad | reordering of write → read | after volatile write (**the expensive one**) |

The **StoreLoad** barrier after a volatile write is why volatile writes are relatively expensive — it's the only barrier that forces the store buffer to drain before subsequent loads.

### Q46. Compare x86 and ARM memory models.

- **x86 (TSO — Total Store Order):** strong memory model. Only reorders stores→loads (which is why StoreLoad is the interesting barrier). Loads always see stores in program order.
- **ARM (weak memory model):** reorders almost everything. Requires more explicit barriers.

**Consequence:** volatile is nearly free on x86 (one `mfence` or `lock`-prefix), but expensive on ARM. This is why acquire/release semantics (JEP 193) became more important with ARM's rise (mobile, Apple Silicon).

### Q47. Sequential consistency vs release/acquire — what's the difference?

- **Sequential consistency (SC):** every thread sees writes in the same global order. Java's `volatile` provides SC.
- **Release/acquire:** a *release* store synchronizes only with *acquire* loads that observe it. Writes across independent release/acquire chains may be observed in different orders by different threads.

Release/acquire is cheaper but weaker. Java 9's `VarHandle.setRelease` / `getAcquire` gives you this weaker semantic when you don't need full SC.

### Q48. What's cache coherence vs memory ordering?

- **Cache coherence:** a write to memory eventually becomes visible to all cores (via MESI protocol or similar). Modern CPUs guarantee coherence — you don't have to worry about it directly.
- **Memory ordering:** the order in which writes become visible relative to each other. This is what memory barriers control.

**Common misconception:** "volatile flushes the cache." False — the cache is coherent already. `volatile` controls **ordering** and **visibility timing** through barriers, not cache flushing.

### Q49. What is false sharing?

When two threads modify **different fields** that happen to live in the **same CPU cache line** (typically 64 bytes), the cache line ping-pongs between cores, killing performance. Neither thread has a logical dependency on the other's data — the hardware forces them to coordinate anyway.

**Mitigation:** pad fields to cache-line boundaries, or use `@jdk.internal.vm.annotation.Contended` (JDK-internal — use `sun.misc.Contended` in older JDKs).

### Q50. What is `@Contended` and when do you use it?

Annotation that tells the JVM to pad a field to avoid false sharing:

```java
@jdk.internal.vm.annotation.Contended
private volatile long counter;
```

Used only in extremely hot paths where profiling shows false sharing is a bottleneck — e.g., high-frequency counters, ring-buffer heads/tails. Cost: 128 bytes per padded field. Not for casual use.

### Q51. Can `volatile` be used on 32-bit long/double for atomicity?

Yes. Without `volatile`, a 64-bit `long` write on a 32-bit JVM can be split into two 32-bit writes, and another thread may observe a torn value. `volatile long` (or `volatile double`) forces atomic access.

On 64-bit JVMs this is generally atomic anyway, but the JMM only guarantees it with `volatile`. Portable code uses `volatile` for shared `long`/`double` regardless.

### Q52. What's the JMM guarantee for `finalize()`?

The end of a constructor happens-before the start of `finalize()` for that object. So `finalize()` can see the fully constructed state — but this is rarely useful because `finalize()` is deprecated (since Java 9, removed from removal deadline in Java 18+).

Use `Cleaner` (Java 9+) or `try-with-resources` instead.

### Q53. What's the JMM guarantee for lambda capture?

A lambda captures **effectively final** locals **by value** (a copy at capture time). Since the captured value can never change, there's no visibility problem — every thread that reads the lambda's captured slot reads the same immutable value.

If the lambda accesses a **field** of an enclosing object (not a local), normal JMM rules apply — the field needs `volatile` or synchronization for visibility.

### Q54. How do concurrent collections give you happens-before?

Every operation on `ConcurrentHashMap`, `ConcurrentSkipListMap`, `BlockingQueue`, etc. establishes happens-before between "the operation that put a value in" and "the operation that read it out." So:

```java
map.put("k", buildResult());       // thread A
Result r = map.get("k");           // thread B — sees the fully built result
```

You get piggyback-style publication for free through the collection.

### Q55. Do virtual threads (Java 21) change the JMM?

**No.** Virtual threads are still Java threads — they honor happens-before, `volatile`, `synchronized`, and all JMM semantics identically to platform threads. What changes is scheduling: virtual threads are cheap and park cheaply on I/O.

One caveat: pinning to carrier threads by `synchronized` blocks was an issue in early Java 21; JDK 24+ improvements largely remove the pinning penalty.

### Q56. What is SC-DRF?

**Sequential Consistency for Data-Race-Free programs.** The core JMM guarantee: **if your program has no data races, it behaves as if executed on a single processor with strict interleaving**.

This is the entire justification for writing "normal" concurrent code — as long as you use `synchronized`/`volatile`/atomics/immutability correctly enough to eliminate races, you don't have to reason about compiler and CPU reorderings. The JMM handles it for you.

### Q57. What is "safe publication via a concurrent collection"?

Putting an object into a thread-safe collection publishes it safely. Getting it out establishes happens-before with the put. So:

```java
BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
queue.put(new Task(data));   // producer publishes safely
Task t = queue.take();       // consumer sees fully constructed Task
```

The queue's internal synchronization does the piggyback for you.

---

## Debugging scenarios — spot the bug

### D1. The loop that never stops

```java
public class Worker implements Runnable {
    private boolean running = true;
    public void stop() { running = false; }
    public void run() {
        while (running) { /* do work */ }
    }
}
```

Main thread calls `worker.stop()`. Worker loops forever.

**Bug:** `running` is not volatile. The JIT hoists the read out of the loop.

**Fix:** `private volatile boolean running = true;`

---

### D2. Reading stale value forever

```java
public class Config {
    private String value = "initial";
    public String getValue() { return value; }
    public void setValue(String v) { value = v; }
}

// Thread A: config.setValue("updated");
// Thread B: while (true) { print(config.getValue()); }
// B may print "initial" forever
```

**Bug:** no synchronization, no volatile. Thread B may cache the field in a register.

**Fix:** make `value` volatile, or synchronize both accessors.

---

### D3. Double-checked locking without volatile

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

Another thread sees `instance != null` and dereferences it — sometimes observes uninitialized fields.

**Bug:** the reference publication (`instance = new Singleton()`) and the constructor's field writes can be reordered.

**Fix:** `private static volatile Singleton instance;` — or better, use the Holder pattern.

---

### D4. Publishing a "safely constructed" object unsafely

```java
public class Config {
    private final Map<String, String> map;
    public Config() {
        Map<String, String> m = new HashMap<>();
        m.put("host", "localhost");
        this.map = m;
    }
}

// Global publication
public static Config config;
// Thread A: config = new Config();
// Thread B: config.map.get("host");   ← sometimes NPE
```

**Bug:** `config` is not volatile. Thread B may see `config != null` (partial visibility from the assignment) but see `config.map == null` (or worse, an uninitialized HashMap).

**Fix:** `public static volatile Config config;` — the volatile publish carries the constructor's writes with it.

---

### D5. Volatile array doesn't do what you think

```java
private volatile int[] data = new int[10];

// Thread A: data[3] = 42;
// Thread B: int v = data[3];   ← may see 0
```

**Bug:** `volatile` applies to the *array reference*, not to element accesses. `data[3] = 42` has no visibility guarantee.

**Fix:** `AtomicIntegerArray data = new AtomicIntegerArray(10);` and use `data.set(3, 42)` / `data.get(3)`.

---

### D6. HashMap corruption from concurrent modification

```java
private Map<String, Integer> counts = new HashMap<>();

// Multiple threads: counts.put(key, counts.getOrDefault(key, 0) + 1);
```

Observable symptoms: infinite loops in `get()` (from a corrupted internal linked list — pre-Java 8), lost updates, `NullPointerException`, `ConcurrentModificationException`.

**Bug:** `HashMap` is not thread-safe. Concurrent structural mutation corrupts its internal state.

**Fix:** `ConcurrentHashMap` + atomic `merge`:
```java
counts.merge(key, 1, Integer::sum);
```

---

### D7. Torn long read on 32-bit JVM

```java
private long timestamp;  // not volatile

// Thread A: timestamp = System.nanoTime();
// Thread B: long t = timestamp;   ← may see half-old, half-new
```

**Bug:** on a 32-bit JVM, non-volatile `long` writes can be split into two 32-bit writes. Thread B may observe an "impossible" value.

**Fix:** `private volatile long timestamp;` — makes reads/writes atomic.

---

### D8. Reordering breaks a "safe" publication

```java
class Holder {
    int x;
    int y;
    Holder() {
        x = 1;
        y = 2;
    }
}
static Holder h;

// Thread A: h = new Holder();
// Thread B: if (h != null) { print(h.x + h.y); }   ← may print 0, 1, 2, or 3
```

**Bug:** `h` is not volatile. Reordering may publish `h` before the constructor finishes.

**Fix:** `static volatile Holder h;` OR make `x`, `y` `final` (final-field freeze guarantees).

---

### D9. Wait/notify without holding the lock

```java
private final Object lock = new Object();
private boolean ready = false;

// Thread A: 
ready = true;
lock.notify();   // IllegalMonitorStateException

// Thread B:
while (!ready) lock.wait();   // IllegalMonitorStateException
```

**Bug:** `wait()` and `notify()` must be called while holding the monitor of the object.

**Fix:**
```java
// Thread A:
synchronized (lock) { ready = true; lock.notify(); }
// Thread B:
synchronized (lock) { while (!ready) lock.wait(); }
```

---

### D10. `volatile` counter increment

```java
private volatile int counter = 0;

// 10 threads: counter++;   ← lost updates
```

**Bug:** `volatile` doesn't make `++` atomic. Read-modify-write races persist.

**Fix:** `AtomicInteger counter; counter.incrementAndGet();`

---

### D11. Concurrent StringBuilder corruption

```java
StringBuilder sb = new StringBuilder();

// Multiple threads: sb.append("x");
// Observed: index out of bounds, weird content, occasional length errors
```

**Bug:** `StringBuilder` is not thread-safe.

**Fix:** use `StringBuffer` (synchronized), or serialize appends externally, or use per-thread StringBuilders + combine.

---

### D12. Wait without a guard — spurious wakeup ignored

```java
synchronized (lock) {
    lock.wait();   // wake up → immediately proceed
    consume(data);
}
```

**Bug:** `wait()` can wake up **spuriously** (without a corresponding `notify()`). The thread may proceed before `data` is actually ready.

**Fix:** always wait in a **guarded loop**:
```java
synchronized (lock) {
    while (!ready) lock.wait();
    consume(data);
}
```

---

## Quick-fire rapid round

| Question | Answer |
|---|---|
| Does volatile provide atomicity? | Only for single reads/writes, not for compound ops like `++` |
| Is `volatile` faster than `synchronized`? | Yes — no lock acquisition |
| Can `volatile` fields be `final`? | No — they'd never change, defeating volatile's purpose |
| Can `static` fields be `volatile`? | Yes |
| Does `synchronized` provide happens-before? | Yes — lock release happens-before subsequent acquisition |
| Do reads/writes of references need `volatile` to be atomic? | Atomicity is guaranteed; visibility requires volatile/sync |
| Is `long`/`double` write atomic without volatile? | Not guaranteed by JMM (may be torn on 32-bit) |
| Does `Thread.sleep()` release locks? | No — releases nothing |
| Does `Thread.yield()` guarantee anything? | No — advisory hint only |
| Is `volatile` cross-platform? | Yes — JMM guarantees identical semantics on all JVMs |
| Do lambdas need `volatile` on captured locals? | No — captured locals are effectively final (immutable value) |
| Are `AtomicInteger` reads volatile? | Yes — `get()` has volatile-read semantics |

---

## The one-sentence summary you can drop in an interview

> `volatile` guarantees visibility and ordering for a single field (reads/writes to that field are seen immediately by other threads, and are not reordered around by the JIT or CPU), but it does not provide atomicity for compound operations or mutual exclusion. It's the cheapest way to safely share a flag or publish an immutable object; anything more complex than a single-field read/write needs `synchronized`, atomics, or a concurrent collection. All of these mechanisms work by establishing happens-before relationships under the Java Memory Model — which is the formal contract that makes concurrent Java portable across CPUs and JVMs.
