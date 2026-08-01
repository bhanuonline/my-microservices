# Java Interview Notes

## When Should We Not Use `static` in Java?

### 1. When Data Belongs to an Object

**Wrong:**
```java
class Employee {
    static int empId;
    static String name;
}
```

**Correct:**
```java
class Employee {
    int empId;
    String name;
}
```
**Reason:** Static variables are shared across all objects.

### 2. When Method Needs Object State

**Wrong:**
```java
class Employee {
    String name;
    static void display() {
        System.out.println(name);
    }
}
```

**Correct:**
```java
void display() {
    System.out.println(name);
}
```
**Reason:** Static methods cannot access instance variables directly.

### 3. In Spring Boot Services

**Wrong:**
```java
@Service
public class OrderService {
    public static void placeOrder() {}
}
```

**Correct:**
```java
@Service
public class OrderService {
    public void placeOrder() {}
}
```
**Reason:** Static methods are outside the Spring Bean lifecycle.

### 4. For Dependency Injection

**Wrong:**
```java
@Autowired
static ProductService productService;
```

**Correct:**
```java
@Autowired
private ProductService productService;
```
**Reason:** Spring DI works properly with instance variables.

### 5. Shared Mutable Data

**Wrong:**
```java
class Counter {
    static int count = 0;
}
```
**Reason:** Can cause race conditions and thread-safety issues.

### 6. During Unit Testing

**Wrong:**
```java
DateUtil.getCurrentDate();
```
**Reason:** Static methods are hard to mock and test.

### 7. For Business Logic

**Wrong:**
```java
public class OrderCalculator {
    public static double calculate(Order order) {}
}
```

**Correct:**
```java
@Service
public class OrderCalculator {
    public double calculate(Order order) {}
}
```
**Reason:**
- No Dependency Injection
- No AOP
- No Transaction Support
- No Proxy Support

---

## Where Should We Use `static`?

1. **Constants**
   ```java
   public static final String SUCCESS = "SUCCESS";
   ```
2. **Utility Methods**
   ```java
   Math.max(10, 20);
   Collections.sort(list);
   ```
3. **Factory Methods**
   ```java
   LocalDate.now();
   ```
4. **Shared Class-Level Data**
   ```java
   private static int totalUsers;
   ```
5. **Static Blocks**
   ```java
   static {
       // initialization
   }
   ```

### Interview Rule
> Does every object need its own copy?
> - **YES** → Non-Static
> - **NO** → Static

### One-Line Answer
Use static when data or behavior belongs to the class and must be shared across all objects. Avoid static for object-specific data, Spring dependencies, business logic, and mutable shared state.

---

## Why Must `equals()` and `hashCode()` Be Overridden Together?

- For direct object comparison, only `equals()` is executed.
- For `HashMap`/`HashSet` operations, `hashCode()` is executed first to locate the bucket, and `equals()` is executed afterward only if objects exist in the same bucket.
- Override `equals()` and `hashCode()` together.
- Equal objects must have the same `hashCode()`.
- Unequal objects can have the same `hashCode()`.
- `HashMap` and `HashSet` use `hashCode()` first, then `equals()`.
- If only `equals()` is overridden, collections may store duplicates.
- If only `hashCode()` is overridden, equality checks become incorrect.
- `==` → compares memory addresses (references)
- `equals()` → compares object content (logical equality)
- **Why is `equals()` defined in the `Object` class?** Because every Java object should have a default equality behavior.
- `HashMap`: `put(key, value)` → `hashCode()` → Bucket Index → `equals()` (if collision occurs)
- **How does `HashSet` prevent duplicates?** Using `hashCode()` and `equals()`.
- **Hash Collision:** Different objects can have the same hash code. If `hashCode(A) = 100` and `hashCode(B) = 100`, this is a collision. Java then uses `equals()` to determine whether the objects are actually equal.
- **TreeSet vs HashSet:**
    - `HashSet` → Uses `hashCode()` + `equals()` → No ordering
    - `TreeSet` → Uses `compareTo()` / `Comparator` → Sorted order; `TreeSet` does NOT use `hashCode()`
- **Records (Java 16+):**
  ```java
  record Employee(int id, String name) {}
  ```
  Java automatically generates: `equals()`, `hashCode()`, `toString()`
- **`@Data` (Lombok):** Generates `equals()`, `hashCode()`, `toString()`, getters/setters

---

## Difference Between `&&` and `&` Operators

- **`&&` — Logical AND:** `if (a > 0 && b > 0)` checks `a > 0` first. If false, stops immediately (doesn't check `b > 0`). If true, then checks `b > 0`.
- **`&` — Bitwise AND** (or non-short-circuit AND): when used with boolean expressions, both sides are evaluated even if the first condition is false.

---

## Java Memory Model (JMM) and Multi-Threading

> "When Thread A writes a value, when does Thread B actually see it?"

- JMM defines how threads interact with memory and guarantees **visibility**, **atomicity**, and **ordering**.
- Purpose: to ensure predictable behavior in multi-threaded programs and avoid stale data issues.
- `volatile`: provides visibility and ordering only — **no atomicity**.

### JMM Gives You 3 Tools to Fix This

**1. `volatile` → Forces Fresh Read/Write**
```java
private volatile boolean isRunning = true;
//      ↑
//  "Always go to Main Memory, never use cache"

// Thread A:
isRunning = false;        // instantly visible to all threads ✅

// Thread B:
while (isRunning) { }     // always sees the latest value ✅
```

**2. `synchronized` → Lock the Door While Working**
```java
public synchronized void increment() {
    count++;   // only ONE thread at a time, changes visible after unlock ✅
}
// It's like locking the kitchen — one chef at a time
```

**3. `AtomicInteger` → Single Unbreakable Operation**
```java
AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet();  // read + add + write as ONE step ✅
// No lock needed, CPU handles it
```

### Happens-Before Examples

| Happens-Before Relationship | Mechanism |
|---|---|
| `unlock()` happens-before `lock()` | `synchronized` |
| volatile write happens-before volatile read | `volatile` |
| `t1.start()` happens-before `t1`'s code | thread start |
| `t1`'s code happens-before `t1.join()` | thread join |

### Quick Reference

| Question | Answer |
|---|---|
| What is JMM? | Rules for when one thread's writes become visible to other threads |
| Why do we need it? | CPUs cache values locally — threads can see stale data |
| How to fix it? | Use `volatile`, `synchronized`, or Atomic classes |

- **Main Memory (RAM):** slow, far from CPU, big
- **CPU Cache:** fast, inside CPU, small
- **Main Memory:** The shared space (comprising the Heap and Method Areas) where all objects and shared instance variables live.
- **Working Memory:** The private local cache (CPU registers, write buffers, and hardware caches) allocated per thread.

```
You write Java code
    ↓
Variable created in RAM (always)
    ↓
CPU reads it → automatically copies to Cache
    ↓
CPU works with Cache copy (fast)
    ↓
Cache → RAM sync? → NOT guaranteed without volatile/synchronized
```

```
┌─────────────────────────────────────────────────┐     
│                                                   │
│  1. VISIBILITY                                   │
│     "Can Thread B see what Thread A wrote?"       │
│      Fix → volatile                               │
│                                                   │
│  2. ATOMICITY                                     │
│     "Did Thread A finish writing                  │
│      before Thread B reads?"                      │
│      Fix → synchronized / AtomicInteger           │
│                                                   │
│  3. ORDERING                                      │
│     "Did Thread A's steps happen                  │
│      in the order we expect?"                     │
│      Fix → volatile / synchronized                │
│                                                   │
└─────────────────────────────────────────────────┘
```

```
RAM = Entire House
│
├── Stack    = Your Personal Bedroom
│             (your own stuff, no one else touches)
│
├── Heap     = Living Room
│             (everyone/all threads share it)
│
├── Code     = Instruction Manual of the house
│             (your program's bytecode)
│
└── Static   = Notice Board
              (one copy, shared by all)
```

```
Hardware
┌──────────────────────────────────────────┐
│   CPU                                     │
│  ┌────────────────────────┐               │
│  │  L1 Cache  (fastest)   │               │
│  │  L2 Cache              │               │
│  │  L3 Cache  (slowest)   │               │
│  └────────────────────────┘               │
│          ↕ copies data                    │
│   RAM                                     │
│  ┌────────────────────────┐               │
│  │  Stack                 │               │
│  │  Heap                  │               │
│  │  Code Area             │               │
│  │  Static Area           │               │
│  └────────────────────────┘               │
└──────────────────────────────────────────┘
```

### How the CPU Searches for a Value — Step by Step

```
CPU needs value of count = ?

Step 1: Check L1 Cache
  Found? ✅ → use it instantly (1ns)
  Not found? → go to Step 2

Step 2: Check L2 Cache
  Found? ✅ → copy to L1, use it (4ns)
  Not found? → go to Step 3

Step 3: Check L3 Cache
  Found? ✅ → copy to L2 → L1, use it (40ns)
  Not found? → go to Step 4

Step 4: Go to RAM
  Found? ✅ → copy to L3 → L2 → L1, use it (100ns)
  (always found here — RAM has everything)
```

### Where Caches Live

```
CPU Chip
┌─────────────────────────────────┐
│  Core 1          Core 2         │
│ ┌─────────┐    ┌─────────┐      │
│ │L1 Cache │    │L1 Cache │      │  ← each core has OWN L1
│ ├─────────┤    ├─────────┤      │
│ │L2 Cache │    │L2 Cache │      │  ← each core has OWN L2
│ └─────────┘    └─────────┘      │
│ ┌───────────────────────────┐   │
│ │       L3 Cache            │   │  ← ALL cores SHARE L3
│ └───────────────────────────┘   │
└─────────────────────────────────┘
            ↕
┌─────────────────────────────────┐
│             RAM                 │  ← outside CPU, all cores share
└─────────────────────────────────┘
```

### Modern Approach for Synchronization, Locking & Atomicity

| Old Way (avoid) | Modern Way (use this) |
|---|---|
| `synchronized` keyword | `ReentrantLock` |
| `wait()` / `notify()` | `Condition` |
| `volatile` | `AtomicXxx` classes |
| manual thread mgmt | `ExecutorService` |

| What do you need? | Use |
|---|---|
| Simple counter | `AtomicInteger` |
| Simple flag | `AtomicBoolean` |
| Lock with timeout | `ReentrantLock` |
| Many readers, few writers | `ReentrantReadWriteLock` |
| Wait for threads to finish | `CountDownLatch` |
| Sync threads at a point | `CyclicBarrier` |
| Thread safe map | `ConcurrentHashMap` |
| Thread safe list | `CopyOnWriteArrayList` |

### Locking — `ReentrantLock`

```java
// ❌ Old Way
public synchronized void doWork() {
    // only one thread
    // can't timeout
    // can't try without blocking
}

// ✅ Modern Way
import java.util.concurrent.locks.ReentrantLock;
ReentrantLock lock = new ReentrantLock();

// Basic lock
lock.lock();
try {
    // do work safely
} finally {
    lock.unlock();    // always unlock in finally!
}

// Try lock — don't wait forever
if (lock.tryLock()) {           // returns true/false immediately
    try { doWork(); }
    finally { lock.unlock(); }
} else {
    System.out.println("Busy, skipping!");
}

// Try lock with timeout
if (lock.tryLock(2, TimeUnit.SECONDS)) {  // wait max 2 seconds
    try { doWork(); }
    finally { lock.unlock(); }
}
```

### Read Write Lock — `ReentrantReadWriteLock`

```java
// Problem: multiple threads READING is safe
//          but synchronized blocks ALL threads
//          even readers!

// ✅ Modern Way — readers don't block each other
import java.util.concurrent.locks.ReentrantReadWriteLock;

ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

// Many threads can read at the same time
rwLock.readLock().lock();
try {
    return count;       // multiple readers allowed ✅
} finally {
    rwLock.readLock().unlock();
}

// Only ONE thread can write
rwLock.writeLock().lock();
try {
    count++;            // exclusive access ✅
} finally {
    rwLock.writeLock().unlock();
}
```

### Thread Coordination — `CountDownLatch` & `CyclicBarrier`

```java
// CountDownLatch — wait for N threads to finish
CountDownLatch latch = new CountDownLatch(3); // wait for 3

// 3 worker threads
new Thread(() -> {
    doWork();
    latch.countDown();    // signals "I am done"
}).start();

latch.await();                        // main thread waits here
System.out.println("All 3 done!");    // runs after all 3 finish


// CyclicBarrier — all threads wait for each other
CyclicBarrier barrier = new CyclicBarrier(3); // 3 threads

new Thread(() -> {
    doPhase1();
    barrier.await();      // wait for all 3 to reach here
    doPhase2();            // all start phase 2 together
}).start();

// Other modern collections
CopyOnWriteArrayList<String> list  = new CopyOnWriteArrayList<>();  // safe list
ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>(); // safe queue
```

### Primitive Data Type Sizes

| Data Type | Size |
|---|---|
| `byte` | 1 byte |
| `short` | 2 bytes |
| `int` | 4 bytes |
| `long` | 8 bytes |
| `float` | 4 bytes |
| `double` | 8 bytes |
| `char` | 2 bytes |
| `boolean` | 1 byte |

Example: `int[] arr = {1, 2, 3, 4, 5}` → `arr.length * 4 = 5 * 4 = 20` bytes

> Every object has a 16-byte header added automatically by the JVM.

---

## Safe List and Safe Queue

```java
// Modern thread-safe collections
CopyOnWriteArrayList<String> list  = new CopyOnWriteArrayList<>(); // safe list
ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>(); // safe queue

// Normal ArrayList — NOT thread safe
List<String> list = new ArrayList<>();

// Thread A → list.add("A")   ─┐
// Thread B → list.add("B")   ─┤─ same time → data corruption ❌
// Thread C → list.remove(0)  ─┘
```

### `CopyOnWriteArrayList` — How It Works

```
WRITE operation (add/remove)
    ↓
Makes a FRESH COPY of entire array
    ↓
Applies change on new copy
    ↓
Replaces old array with new copy
    ↓
Other threads still read old copy safely ✅

READ operation
    ↓
No lock needed
Multiple threads read same time ✅
```

---

## SOLID Principles

SOLID is a set of 5 object-oriented design principles for writing clean, maintainable code.

- **S — Single Responsibility** → One class = One job
- **O — Open/Closed** → Open for extension, closed for modification
- **L — Liskov Substitution** → Subclass must replace parent without breaking code
- **I — Interface Segregation** → Don't force classes to implement unused methods
- **D — Dependency Inversion** → Depend on interfaces, not concrete classes

**Key Points**
- They are independent — apply only what's relevant
- Not mandatory to use all 5 — use based on your design context
- Goal is flexible, testable, and maintainable code
- Commonly used with Design Patterns in real projects

---

## Composition vs. Inheritance

- **Inheritance:** "is-a" relationship
- **Composition:** "has-a" relationship

> Prefer composition over inheritance for flexibility and testability.

---

## How Is Memory Managed in Java?

*(open question — to be answered)*

## Singleton Pattern — Thread-Safe Implementation

*(open question — to be answered, with a thread-safe example)*

---

## JVM, JRE, JDK

- **JVM (Java Virtual Machine)** is responsible for executing Java bytecode. When we compile a Java program, the `.java` file is converted into a `.class` file (bytecode) using the `javac` compiler. The JVM loads this bytecode and, with the help of the Execution Engine and JIT compiler, converts it into machine code that the operating system can execute.
- **JRE (Java Runtime Environment)** provides the environment required to run Java applications. It includes the JVM and the necessary Java libraries and runtime components. However, it does not include development tools like the Java compiler.
- **JDK (Java Development Kit)** is used for developing Java applications. It contains the JRE along with development tools such as `javac`, `jar`, `jdb`, and `javadoc`. In short: `JDK = JRE + development tools`, and `JRE = JVM + runtime libraries`.

```
                      JDK
       +--------------------------------------+
       |  javac  jar  jdb  javadoc             |
       |                                        |
       |     JRE                                |
       |  +------------------------------+      |
       |  |      JVM                     |      |
       |  |  Class Loader                |      |
       |  |  Memory Areas                |      |
       |  |  Execution Engine            |      |
       |  +------------------------------+      |
       |                                        |
       |   Java Libraries (.jar files)          |
       +--------------------------------------+
```

### Which Memory Areas Are Used?

The JVM uses multiple memory areas, not just the heap:

- **Method Area (Metaspace)** → Stores class metadata, method information, static variables, etc.
- **Heap** → Stores objects.
- **Stack** → Stores local variables, method calls, and object references.
- **PC Register** → Keeps track of the current instruction being executed.
- **Native Method Stack** → Used when native methods are invoked.

### Where Is the Reference Stored?

```java
Employee emp = new Employee();
```
- The `Employee` object is created in the **Heap**.
- The reference variable `emp` is stored in the **Stack**, because it is a local variable inside the `main()` method.

### What Does the JVM Do Internally?

> **Your answer:** "JVM reads class file and execution engine converts to machine language."
> This is correct at a high level, but interviewers expect more detail.

**Interview-Level Answer (9/10)**

Suppose we have:
```java
public class Test {
    public static void main(String[] args) {
        Employee emp = new Employee();
    }
}
```

**Step 1: Class Loader**

When we run `java Test`, the Class Loader loads the `Test` class into memory. If the `Employee` class has not been loaded yet, it is also loaded when first needed.

**Step 2: Method Area (Metaspace)**

The JVM stores class metadata such as:
- Class name
- Method definitions
- Static variables
- Runtime constant pool

in the Method Area (Metaspace).

**Step 3: Stack**

The JVM creates a stack frame for the `main()` method. Inside that frame, local variables are stored:
```java
Employee emp;
```
The variable `emp` is stored in the Stack.

**Step 4: Heap**

When the JVM executes `new Employee();`, memory is allocated in the Heap, and the `Employee` object is created there.

**Step 5: Reference Assignment**

The address (reference) of the object is assigned to the variable `emp` in the Stack.

```
Stack                      Heap
------------------        ------------------
emp ---------------------> Employee Object
```

# Java Interview Notes: `==` vs `equals()` and String Immutability

## `==` Operator

The `==` operator compares values for primitive data types and memory addresses (references) for objects.

### Example with primitives

```java
int a = 10;
int b = 10;
System.out.println(a == b);   // true
```

Here, it compares the actual values (`10 == 10`).

### Example with objects

```java
Employee e1 = new Employee();
Employee e2 = new Employee();
System.out.println(e1 == e2);   // false
```

Although both objects contain the same data, they are stored at different memory locations, so their references are different.

## `equals()` Method

The `equals()` method compares the content (logical equality) of objects.

For `String`, it has been overridden to compare the characters.

```java
String s1 = new String("Java");
String s2 = new String("Java");
System.out.println(s1 == s2);        // false
System.out.println(s1.equals(s2));   // true
```

**Why?**
- `==` compares references.
- `equals()` compares the string content.

## Important Interview Follow-up

### What happens if `equals()` is not overridden?

```java
class Employee {
    int id;

    Employee(int id) {
        this.id = id;
    }
}

Employee e1 = new Employee(1);
Employee e2 = new Employee(1);

System.out.println(e1.equals(e2));
```

Output:

```
false
```

**Why?**

Because the default implementation from the `Object` class behaves like `==`, comparing object references.

To compare based on the `id`, you must override `equals()` (and typically `hashCode()` as well).

`equals()` compares the logical content of objects. For `String`, it compares the characters because `String` overrides the `equals()` method.

## Easy Way to Remember

| `==` | `equals()` |
|---|---|
| Primitive → compares values | Compares object content (if overridden) |
| Object → compares references | Compares logical equality |

| Operator | Method |
|---|---|
| Cannot be overridden | Can be overridden |

---

# Why is `String` Immutable in Java?

## What is Immutable?

An immutable object is an object whose state cannot be changed after it is created. If we modify a `String`, Java creates a new object instead of changing the existing one.

Example:

```java
String s = "Java";
s = s.concat(" 17");
```

Here:
- `"Java"` is not modified.
- A new `String` object `"Java 17"` is created.
- The variable `s` now points to the new object.

## Why is String Immutable?

### 1. Security ⭐⭐⭐⭐⭐

Strings are used for:
- Passwords
- Database URLs
- File paths
- Network connections
- Class loading

If Strings were mutable, malicious code could change these values after they were validated.

Example:

```
Password = "Admin123"
```

If mutable:

```
Password = "Hacker123"
```

This would create serious security issues.

### 2. String Pool

Java stores string literals in the String Pool.

Example:

```java
String a = "Java";
String b = "Java";
```

Both variables point to the same object.

If Strings were mutable:

```java
a = "Python";
```

It could accidentally affect `b`, which would be incorrect.

Immutability makes sharing String objects safe.

### 3. Thread Safety

Because Strings cannot change, multiple threads can use the same String object without synchronization.

No locking is required.

### 4. HashMap Performance

Strings are commonly used as keys in a `HashMap`.

```java
Map<String, Employee> map = new HashMap<>();
```

The hash code of a String never changes because the content never changes.

If Strings were mutable, changing the content would change the hash code, making the key impossible to find efficiently.

### 5. Caching

Since Strings are immutable, Java can cache:
- Hash codes
- String Pool objects

This improves performance.

# Immutable Classes in Java — Interview Notes

## What is an Immutable Class?

- A class whose object's state cannot be changed once created.
- Example: `String`, `Integer`, `LocalDate`, `BigDecimal`.

## Why Do We Create Immutable Classes? (Purpose)

1. To ensure object state cannot be altered after creation.
2. To make objects safe to share across code without side effects.
3. To achieve thread safety without synchronization.
4. To use objects safely as HashMap/HashSet keys.
5. To represent fixed values (e.g., money, dates, IDs) that shouldn't change.

## How Do We Create One? (Quick Recap)

1. Declare class as `final` (no subclassing).
2. Make all fields `private final`.
3. No setter methods.
4. Initialize fields only via constructor.
5. Return defensive copies for mutable fields (Date, List, etc.).

## When Should We Use Immutable Classes?

1. When object represents a fixed record/snapshot (e.g., payslip, transaction).
2. When object will be shared across multiple threads.
3. When object is used as a key in Map/Set.
4. When you want value-object semantics (equality based on data, not identity).
5. When object should be safely cacheable or reusable.

## Benefits (Advantages)

1. Thread-safe by design — no locks/synchronization needed.
2. Safe to share/cache — no risk of unexpected modification.
3. Reliable HashMap keys — hashCode never changes.
4. Predictable behavior — easier to reason about and debug.
5. No defensive copying needed by caller — object can't be corrupted.
6. Simplifies concurrent programming.

## Disadvantages (Trade-offs)

1. New object created for every change → more memory usage / garbage collection overhead.
    - Example: `String` concatenation in a loop creates many objects (`StringBuilder` used instead for performance).
2. Not suitable for objects that change frequently (e.g., a `Counter`, `MutableCart`).
3. More boilerplate code — constructors for every variation, defensive copying logic.
4. Performance overhead when object is large and modified often (copying entire object each time).
5. Less flexible — no setters means some frameworks (like older ORMs needing no-arg constructors + setters) don't work well with it directly.

## One-Line Interview Answer

> "Immutable classes guarantee that once an object is created, its state can never change — making them inherently thread-safe, safe to cache/share, and reliable as map keys, at the cost of creating new objects (and extra memory) whenever a 'change' is needed."

---

# equals() vs hashCode() — When Each Gets Called

## How Comparison Happens

| How comparison happens | What gets called |
|---|---|
| You call `e1.equals(e2)` directly | Only `equals()` — `hashCode()` not involved |
| You call `e1 == e2` | Neither method — just reference comparison |
| `set.add(e1)` / `map.put(key, val)` / `set.contains(e1)` | `hashCode()` first → `equals()` only if collision |
| `ArrayList.contains()` / `ArrayList.indexOf()` | Only `equals()` — lists don't use hashing at all |

## One-Line Interview Answer

> "equals() alone is only called when you invoke it directly or when using non-hash-based collections like ArrayList. The hashCode() → equals() two-step sequence is specific to hash-based collections (HashMap, HashSet, Hashtable) — it's the collection's internal lookup mechanism, not a rule of equals() itself."

## So What Actually Proves a New Object Was Created?

| Check | Reliable proof of new object? |
|---|---|
| `==` comparison | ✅ Yes — this is the real proof (reference comparison) |
| `System.identityHashCode()` differs | ✅ Practically yes, very reliable indicator |
| `hashCode()` differs | ❌ No — hashCode can be overridden and based on content, not identity |
| `hashCode()` is same | ❌ Doesn't prove same object either — could be two different objects with equal content |

# Java Interview Notes: Inheritance, Constructors & Generics

## 1. Constructor Chaining with Inheritance (Upcasting Example)

**Code scenario:**
```java
Animal animal = new Cow();
```

### Output
```
Im AnimalIm Cow Animal
```

### Explanation

This is an example of **inheritance**, where `Animal` is the parent (super) class and `Cow` is the child (sub) class.

In the `main()` method:
```java
Animal animal = new Cow();
```

When an object is created, the constructor is automatically invoked to initialize the object. Here, a `Cow` object is created on the heap, and its reference is assigned to a variable of type `Animal`. This is called **upcasting**, and it is one form of runtime polymorphism.

### What happens internally when `new Cow()` executes

1. Memory is allocated for the `Cow` object.
2. Before the `Cow` constructor executes, Java automatically inserts an implicit `super()` call as the first statement of the constructor.
3. The `super()` call invokes the `Animal` constructor.
4. The `Animal` constructor executes first and prints: `Im Animal`
5. After the parent constructor finishes, control returns to the `Cow` constructor.
6. The `Cow` constructor executes and prints: `Im Cow Animal`

**Therefore, the final output is:**
```
Im AnimalIm Cow Animal
```

---

## 2. When Should We Make a Class Generic?

### Short Interview Answer (Best Answer)

> We make a class generic when the class should work with different data types without duplicating code. Generics provide code reusability, type safety, and eliminate the need for explicit type casting.

Generics can be used with classes, interfaces, methods, and constructors. They improve code reusability and provide compile-time type safety. However, **Java does not allow generic enums**.

### Where is the type changing?

| Condition | Use |
|---|---|
| The whole class depends on the type | ➡️ Generic Class |
| Only one method depends on the type | ➡️ Generic Method |
| All implementations depend on the type | ➡️ Generic Interface |

---

## 3. When Do We Use `T extends SomeClass`?

> We use bounded generics (`T extends SomeClass`) when the generic type must support specific behavior or methods defined in a base class or interface. It restricts the allowed types and lets the compiler safely access members of that base type.

- **`T`** → Accept any type.
- **`T extends Animal`** → Accept only `Animal` and its subclasses, so you can safely use `Animal`'s methods.

---

## 4. Where Can We Use `super`?

`super` is allowed only with **wildcards (`?`)**, not with **type parameters (`T`)**.

### ✅ Valid
```java
List<? super Animal> list;
```

### ❌ Invalid
```java
class Box<T super Animal> {   // ❌
}
```

### Why?

There are two different concepts:

#### 1. Type Parameter (`T`)
Used when declaring a generic class or method.
```java
class Box<T> { }
class Box<T extends Animal> { }
```
Here, Java allows only `extends`.

#### 2. Wildcard (`?`)
Used when using a generic type.
```java
List<? extends Animal>
List<? super Animal>
```
Here, Java allows both:
- `extends`
- `super`

```java
List<? extends Animal> animals;
```
The list may contain `Animal` or any subclass.

```java
List<? super Dog> dogs;
```
The list may contain `Dog` or any superclass of `Dog`.

---

## 5. Why Is `extends` Allowed with `T` but `super` Is Not?

**Answer:**

A type parameter (`T`) defines the type itself. The compiler needs to know the **upper bound** (what methods and fields are guaranteed to exist). An upper bound (`extends`) provides that guarantee. A lower bound (`super`) doesn't help with this.

`extends` is used with type parameters (`T`) to define an upper bound. `super` is not allowed with type parameters; it is only used with wildcards (`? super T`) when **consuming** values from generic collections.

### Comparison Table

| Feature | `T extends Animal` | `? extends Animal` |
|---|---|---|
| What is it? | Type parameter | Wildcard |
| Used when | Defining a generic class/method | Using an existing generic type |
| Has a name? | ✅ Yes (`T`) | ❌ No (`?`) |
| Can be reused in multiple places? | ✅ Yes | ❌ No |
| Typical use | Generic APIs/classes | Method parameters, variables, collections |
| Example | `class Box<T extends Animal>` | `List<? extends Animal>` |

---

## 6. Quick Decision Guide (PECS)

**Whenever you see `T`, ask:**
> Am I defining a generic API?
> ➡️ Yes → Use `T extends`.

**Whenever you see `?`, ask:**
> Am I accepting someone else's generic object?
> ➡️ Yes → Use `? extends` or `? super`.

**Finally ask (PECS — Producer Extends, Consumer Super):**
- Need to **read**? → `? extends`
- Need to **write**? → `? super`