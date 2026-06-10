# WHEN SHOULD WE NOT USE STATIC IN JAVA?

1. When Data Belongs to an Object
      Wrong:
      class Employee {
      static int empId;
      static String name;
      }
      Correct:
      class Employee {
      int empId;
      String name;
      }
      Reason: Static variables are shared across all objects.

2. When Method Needs Object State
   Wrong:
   class Employee {
   String name;
   static void display() {
   System.out.println(name);
   }
   }
   Correct:
   void display() {
   System.out.println(name);
   }
   Reason: Static methods cannot access instance variables directly.

3. In Spring Boot Services
   Wrong:
   @Service
   public class OrderService {
   public static void placeOrder() {}
   }
   Correct:
   @Service
   public class OrderService {
   public void placeOrder() {}
   }
   Reason: Static methods are outside Spring Bean lifecycle.

4. For Dependency Injection
   Wrong:
   @Autowired
   static ProductService productService;

Correct:
@Autowired
private ProductService productService;

Reason: Spring DI works properly with instance variables.

5. Shared Mutable Data
   Wrong:
   class Counter {
   static int count = 0;
   }
   Reason: Can cause race conditions and thread-safety issues.

6. During Unit Testing
   Wrong:
   DateUtil.getCurrentDate();
   Reason: Static methods are hard to mock and test.

7. For Business Logic
   Wrong:
   public class OrderCalculator {
   public static double calculate(Order order) {}
   }
   Correct:
   @Service
   public class OrderCalculator {
   public double calculate(Order order) {}
   }
   Reason:
- No Dependency Injection
- No AOP
- No Transaction Support
- No Proxy Support

WHERE SHOULD WE USE STATIC?

1. Constants
   public static final String SUCCESS = "SUCCESS";
2. Utility Methods
   Math.max(10,20);
   Collections.sort(list);
3. Factory Methods
   LocalDate.now();
4. Shared Class-Level Data
   private static int totalUsers;
5. Static Blocks
   static {
   // initialization
   }

INTERVIEW RULE:
Does every object need its own copy?
YES -> Non-Static
NO  -> Static

ONE-LINE ANSWER:
Use static when data or behavior belongs to the class and must be shared across all objects. Avoid static for object-specific data, Spring dependencies, business logic, and mutable shared state.

# Why equals() and hashCode() must be overridden together?
✔ For direct object comparison, only equals() is executed.
✔ For HashMap/HashSet operations, hashCode() is executed first to locate the bucket, and equals() is executed afterward only if objects exist in the same bucket.
✔ Override equals() and hashCode() together.
✔ Equal objects must have same hashCode().
✔ Unequal objects can have same hashCode().
✔ HashMap and HashSet use hashCode() first, then equals().
✔ If only equals() is overridden, collections may store duplicates.
✔ If only hashCode() is overridden, equality checks become incorrect.
✔ == → compares memory addresses (references)
✔ equals() → compares object content (logical equality)
✔ Why is equals() defined in Object class? Because every Java object should have a default equality behavior.
✔ HashMap : put(key, value)->hashCode()->Bucket Index ->equals() (if collision occurs)
✔ How does HashSet prevent duplicates? : Using hashCode() and equals().
✔ Hash Collision : Different objects can have the same hash code. hashCode(A) = 100  and hashCode(B) = 100 ->This is called a collision.Java then uses:equals() to determine whether the objects are actually equal.
✔ TreeSet vs HashSet :HashSet -> Uses hashCode() + equals()->No ordering .TreeSet  Uses compareTo() / Comparator Sorted order ->TreeSet does NOT use hashCode()
✔ Records (Java 16+)-> record Employee(int id, String name) {} -> Java automatically generates: equals(),hashCode(),toString()
✔ @Data->Generate equals(),hashCode(),toString(),getter/setter

# What is the difference between && and & operators?
✔ && — Logical AND -> if (a > 0 && b > 0) Check a > 0 If false, stop immediately (don’t check b > 0) If true, then check b > 0
✔ &  — Bitwise AND (or non-short-circuit AND),When you use & with boolean expressions, both sides are evaluated even if the first condition is false.

# Explain the concept of Java Memory Model and how it relates to multi-threading 
"When Thread A writes a value, when does Thread B actually see it?"
✔ Java Memory Model defines how threads interact with memory and guarantees visibility, atomicity, and ordering.
✔ To ensure predictable behavior in multi-threaded programs and avoid stale data issues.
volatile : It provides visibility and ordering only. No atomicity
JMM Gives You 3 Tools to Fix This
1. volatile → Forces Fresh Read/Write
   javaprivate volatile boolean isRunning = true;
   //      ↑
   //  "Always go to Main Memory, never use cache"

// Thread A:
isRunning = false;        // instantly visible to all threads ✅

// Thread B:
while (isRunning) { }     // always sees the latest value ✅

2. synchronized → Lock the Door While Working
   java public synchronized void increment() {
   count++;   // only ONE thread at a time, changes visible after unlock ✅
   }
   // It's like locking the kitchen — one chef at a time
3. AtomicInteger → Single Unbreakable Operation
      javaAtomicInteger count = new AtomicInteger(0);
      count.incrementAndGet();  // read + add + write as ONE step ✅
      // No lock needed, CPU handles it
4. Examples:
   unlock()   happens-before   lock()       → synchronized
   volatile write  happens-before  volatile read  → volatile
   t1.start() happens-before   t1's code    → thread start
   t1's code  happens-before   t1.join()    → thread join
5. Question           Answer
   What is JMM?       Rules for when one thread's writes become visible to other threads
   Why do we need it? CPUs cache values locally — threads can see stale data
   How to fix it?    Use volatile, synchronized, or Atomic classes
   Main Memory (RAM)    →   slow,  far from CPU,  big
   CPU Cache            →   fast,  inside CPU,    small 
   Main Memory: The shared space (comprising the Heap and Method Areas) where all objects and shared instance variables live.
   Working Memory: The private local cache (CPU registers, write buffers, and hardware caches) allocated per thread
   
   You write Java code
   ↓
   Variable created in RAM (always)
   ↓
   CPU reads it → automatically copies to Cache
   ↓
   CPU works with Cache copy (fast)
   ↓
   Cache → RAM sync? → NOT guaranteed without volatile/synchronized

   ┌─────────────────────────────────────────────────┐     
   │                                                 │
   │  1. VISIBILITY                                  │
   │     "Can Thread B see what Thread A wrote?"     │
   │      Fix → volatile                             │
   │                                                 │
   │  2. ATOMICITY                                   │
   │     "Did Thread A finish writing               │                       
   │      before Thread B reads?"                    │
   │      Fix → synchronized / AtomicInteger         │
   │                                                 │
   │  3. ORDERING                                    │
   │     "Did Thread A's steps happen               │
   │      in the order we expect?"                   │
   │      Fix → volatile / synchronized              │
   │                                                 │
   └─────────────────────────────────────────────────┘
   
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

   Hardware
   ┌──────────────────────────────────────────┐
   │   CPU                                    │
   │  ┌────────────────────────┐              │
   │  │  L1 Cache  (fastest)   │              │
   │  │  L2 Cache              │              │
   │  │  L3 Cache  (slowest)   │              │
   │  └────────────────────────┘              │
   │          ↕ copies data                   │
   │   RAM                                    │
   │  ┌────────────────────────┐              │
   │  │  Stack                 │              │
   │  │  Heap                  │              │
   │  │  Code Area             │              │
   │  │  Static Area           │              │
   │  └────────────────────────┘              │
   └──────────────────────────────────────────┘

   CPU needs value of  count = ?

How CPU Searches — Step by Step
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

Where They Live

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

Modern Approach for Synchronization, Locking & Atomicity
Old Way (avoid)          Modern Way (use this)
─────────────────────    ─────────────────────
synchronized keyword  →  ReentrantLock
wait() / notify()     →  Condition
volatile              →  AtomicXxx classes
manual thread mgmt    →  ExecutorService

What do you need?          Use
──────────────────────────────────────────────────
Simple counter             AtomicInteger
Simple flag                AtomicBoolean
Lock with timeout          ReentrantLock
Many readers, few writers  ReentrantReadWriteLock
Wait for threads to finish CountDownLatch
Sync threads at a point    CyclicBarrier
Thread safe map            ConcurrentHashMap
Thread safe list           CopyOnWriteArrayList

Locking — ReentrantLock
// ❌ Old Way
public synchronized void doWork() {
// only one thread
// cant timeout
// cant try without blocking
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

// Try lock — dont wait forever
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

Read Write Lock — ReentrantReadWriteLock
// Problem: multiple threads READING is safe
//          but synchronized blocks ALL threads
//          even readers!

// ✅ Modern Way — readers dont block each other
import java.util.concurrent.locks.ReentrantReadWriteLock;

ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

// Many threads can read at same time
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

Thread Coordination — CountDownLatch & CyclicBarrier

// CountDownLatch — wait for N threads to finish
CountDownLatch latch = new CountDownLatch(3); // wait for 3

// 3 worker threads
new Thread(() -> {
doWork();
latch.countDown();    // signals "I am done"
}).start();

latch.await();            // main thread waits here
System.out.println("All 3 done!");  // runs after all 3 finish


// CyclicBarrier — all threads wait for each other
CyclicBarrier barrier = new CyclicBarrier(3); // 3 threads

new Thread(() -> {
doPhase1();
barrier.await();      // wait for all 3 to reach here
doPhase2();           // all start phase 2 together
}).start();

// Other modern collections
CopyOnWriteArrayList<String> list  = new CopyOnWriteArrayList<>(); // safe list
ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();// safe queue

6. How to find size
   Data Type    Size
   ─────────────────
   byte      →  1 byte
   short     →  2 bytes
   int       →  4 bytes
   long      →  8 bytes
   float     →  4 bytes
   double    →  8 bytes
   char      →  2 bytes
   boolean   →  1 byte
   
   ex int[] arr = {1, 2, 3, 4, 5} -> arr.length * 4 =5*4 =20
   Every object has 16 bytes header added by JVM automatically on to

# What is safe list and safe queue
// Other modern collections
CopyOnWriteArrayList<String> list  = new CopyOnWriteArrayList<>(); // safe list
ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();// safe queue

// Normal ArrayList — NOT thread safe
List<String> list = new ArrayList<>();

Thread A → list.add("A")   ─┐
Thread B → list.add("B")   ─┤─ same time → data corruption ❌
Thread C → list.remove(0)  ─┘

Safe list
CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
How it work:
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

# SOLID principles
SOLID is a set of 5 object-oriented design principles for writing clean, maintainable code.

**S — Single Responsibility** → One class = One job
**O — Open/Closed** → Open for extension, closed for modification
**L — Liskov Substitution** → Subclass must replace parent without breaking code
**I — Interface Segregation** → Don't force classes to implement unused methods
**D — Dependency Inversion** → Depend on interfaces, not concrete classes

**Key Points**
- They are independent — apply only what's relevant
- Not mandatory all 5 — use based on your design context
- Goal is flexible, testable, and maintainable code
- Commonly used with Design Patterns in real projects

# What is the difference between composition and inheritance?
Inheritance: “is-a” relationship
Composition: “has-a” relationship
Prefer composition over inheritance for flexibility and testability.

# How is memory managed in Java?


