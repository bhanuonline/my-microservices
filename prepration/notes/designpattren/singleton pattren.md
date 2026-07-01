**Singleton Pattern**
The Singleton pattern is a creational design pattern that ensures a class has only one instance throughout the application's lifecycle, while providing a global access point to that instance.
Key Characteristics

Private constructor (prevents external instantiation)
Static instance variable (holds the single instance)
Static accessor method (provides global access)

When to use it

Managing shared resources (DB connections, thread pools, config settings)
Logging systems
Caches or registries

**Eager initialization Not lazy**
The instance is created at class loading time, before it is ever requested. Simple, but wastes memory if the instance is never used.

Problem: Instance is created even if your app never calls getInstance(). Wastes resources for heavy objects (DB connections, large configs).

# No, not at all! Static alone does NOT make something thread-safe
For this need to understand
1. What is thread?
2. The race condition problem
3. static vs thread safe
4. How syncronisation fix it ?

###### Think of threads like workers in a kitchen

A thread is just a sequence of instructions running inside your program. Most real applications run multiple threads at the same time — one thread handles a web request, another logs data, another checks for updates.
Real example: A bank app has 1000 users clicking "transfer money" at the same time. The server runs 1000 threads simultaneously — one per user. All 1000 threads share the same memory (the account balances).

###### The race condition — two threads, one variable Broken

Imagine counter = 0 and two threads both do counter++. You expect the result to be 2. But it can become 1.

// UNSAFE — no thread safety
static int counter = 0;

// Thread A and Thread B both run this:
counter++;
Why it breaks: counter++ is actually 3 steps: (1) READ the value, (2) ADD 1, (3) WRITE back. If both threads READ at the same time before either WRITES, both see 0, both write 1, and the second write erases the first. You lost an increment.

###### Does static make something thread-safe? No!

static means there is one copy shared across the whole program. That actually makes thread safety more important — because every thread is touching the exact same variable.

static makes the problem worse, not better. With a non-static variable, each object has its own copy, so different threads using different objects don't interfere. With static, all threads share one single variable — so a race condition affects everyone.
NON-STATIC
int count = 0;
// Each object has
// its own copy.
// Thread A's object
// doesn't affect B's.
STATIC (shared!)
static int count = 0;
// ONE copy for ALL
// threads. Thread A
// and Thread B fight
// over the same int.
What actually makes code thread-safe?
Three things: synchronized (only one thread at a time), volatile (forces threads to read from main memory, not cache), or immutability (never change the value after creation — nothing to race over).
// All three options for a counter:

// 1. synchronized method
synchronized void increment() { count++; }

// 2. AtomicInteger (built-in thread-safe int)
AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet(); // atomic — no race

// 3. Immutable (never changes — always safe)
static final int MAX = 100; // read-only, no race

#### How synchronized fixes the race condition Fixed
synchronized puts a lock on the method or block. When Thread A enters, it grabs the lock. Thread B has to wait outside until A is done and releases the lock. No overlap, no race.

// SAFE — synchronized protects the 3 steps as one unit
static int counter = 0;

synchronized static void increment() {
counter++;  // READ + ADD + WRITE = atomic now
}
Key insight: counter++ is 3 CPU instructions. synchronized wraps all 3 into one unbreakable unit called an atomic operation. No thread can interrupt in the middle.

Back to Singleton: The Singleton's getInstance() has this exact problem. Two threads checking if (instance == null) at the same time can both see null and both create an instance. That's two singletons — exactly what we're trying to prevent. synchronized, volatile, or the JVM class-loading trick (Bill Pugh) all solve this.

### What problem does each keyword solve?
They solve two different problems. You cannot swap one for the other.

SYNCHRONIZED
Solves the race condition — prevents two threads from running the same code block at the same time.

VOLATILE
Solves the visibility problem — forces every thread to read the variable fresh from RAM, not from its own CPU cache.

The CPU cache problem — why volatile exists
Your computer has multiple CPU cores. Each core has its own tiny fast memory called a cache. For speed, Java lets each thread keep a local copy of a variable in its core's cache instead of always going to RAM.

This means Thread A can write a new value to RAM, but Thread B is still reading the old stale value from its own cache — and never knows the value changed.

WITHOUT volatile — Thread B reads stale cache
CPU Core 1 (Thread A)
cache: instance = Object@123
|
RAM (actual truth)
instance = Object@123
|
CPU Core 2 (Thread B)
cache: instance = null !!!
Thread A already created the instance and wrote it to RAM. But Thread B's cache still says null — so Thread B creates a second instance.

WITH volatile — every thread reads directly from RAM
CPU Core 1 (Thread A)
no cache — reads RAM
↔
RAM (single truth)
instance = Object@123
↔
CPU Core 2 (Thread B)
no cache — reads RAM
Both threads always go directly to RAM. Thread B sees Object@123 immediately after Thread A writes it.

Can you use them on the variable declaration?
volatile static Singleton instance = null;   // YES — valid

synchronized static Singleton instance = null; // NO — compile error!
synchronized cannot go on a variable. It only goes on methods or code blocks. It controls who can enter a section of code, not who can access a variable directly.
volatile goes directly on the variable. It is a property of the variable itself — "always read me fresh from RAM".

### Bill Pugh is safe from threads — but not from reflection attack possible

Java has a feature called Reflection that lets you reach inside any class and call its private constructor directly, bypassing all your protection. Here is what the attack looks like:

Singleton s1 = Singleton.getInstance();  // normal way

// ATTACK — using reflection to call private constructor
Constructor<Singleton> c = Singleton.class
.getDeclaredConstructor();
c.setAccessible(true);                   // bypass private!
Singleton s2 = c.newInstance();          // second object!

System.out.println(s1 == s2);           // false — TWO singletons!
What happened: setAccessible(true) tells Java "ignore the private keyword." Now anyone can call your constructor as many times as they want. Bill Pugh's class-loading trick does nothing to stop this — it only protects against threads.

How to defend Bill Pugh against reflection fix
Add a guard inside the constructor. If someone tries to call it a second time, throw an exception immediately.

### The serialization fix — readResolve()
If your Singleton is ever saved to a file or sent over a network (serialized), Java's default behavior creates a brand new object when reading it back. Add this method to prevent that:

public final class Singleton implements Serializable {

    private static final long serialVersionUID = 1L;

    private Singleton() { ... }

    private static final class SingletonHolder {
        private static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return SingletonHolder.INSTANCE;
    }

    // Called by Java during deserialization — return existing instance
    protected Object readResolve() {
        return SingletonHolder.INSTANCE;
    }
}
What readResolve does: When Java finishes deserializing the object, it calls readResolve() and uses whatever you return instead of the newly created object. You return the existing instance, so the new one is thrown away immediately.

### So which approach is truly unbreakable?
Enum Singleton. It is immune to both reflection and serialization attacks at the JVM level — no extra code needed.

public enum Singleton {
INSTANCE;

    public void doWork() { ... }
}

// Reflection attack FAILS automatically:
// Constructor c = Singleton.class.getDeclaredConstructor();
// c.newInstance() → throws IllegalArgumentException:
//   "Cannot reflectively create enum objects"

// Serialization is safe automatically:
// Java serializes enums by name, deserializes back to
// the existing constant — never creates a new object.
Bottom line: Enum needs zero extra code. Bill Pugh needs the constructor guard + readResolve() to be fully safe. In practice, most apps use Bill Pugh (more readable, can extend classes) and add the guard. Use Enum when the class is serialized.

### 

| Approach                | Lazy? | Thread-safe? | Reflection-safe? | Serialization-safe? | Overhead | Use when                          |
|-------------------------|-------|--------------|------------------|---------------------|----------|-----------------------------------|
| Eager init              | No    | Yes          | No               | No                  | None     | Always needed, cheap to create    |
| Synchronized method     | Yes   | Yes          | No               | No                  | High     | Simple, don't care about perf     |
| Double-checked locking  | Yes   | Yes          | No               | No                  | Low      | Lazy + performance matters        |
| Bill Pugh               | Yes   | Yes          | No*              | No*                 | None     | Most production code              |
| Enum                    | No    | Yes          | Yes              | Yes                 | None     | Serialization needed, fully safe  |

* Bill Pugh becomes safe with constructor guard + readResolve() added manually.

| Attack          | What it does                      | Bill Pugh fix              | Enum?          |
|-----------------|-----------------------------------|----------------------------|----------------|
| Reflection      | Calls private constructor         | Guard in constructor       | Immune by JVM  |
| Serialization   | Creates new object on deserialize | Add readResolve()          | Immune by JVM  |
| Cloning         | Clones the object                 | Override clone() to throw  | Immune by JVM  |

One line rule: Use Bill Pugh for most cases. Use Enum when the object is serialized.

    // 1. EAGER
    public final class Singleton {
    private static final Singleton INSTANCE = new Singleton();
    private Singleton() {}
    public static Singleton getInstance() { return INSTANCE; }
    }

    // 2. SYNCHRONIZED METHOD
    public final class Singleton {
    private static Singleton instance;
    private Singleton() {}
    public static synchronized Singleton getInstance() {
    if (instance == null) instance = new Singleton();
    return instance;
    }
    }
###

    // 3. DOUBLE-CHECKED LOCKING
    public final class Singleton {
    private static volatile Singleton instance;
    private Singleton() {}
    public static Singleton getInstance() {
    if (instance == null) {
    synchronized (Singleton.class) {
    if (instance == null) instance = new Singleton();
    }
    }
    return instance;
    }
    }

###
    // 4. BILL PUGH (recommended)
    public final class Singleton {
    private Singleton() {
    if (SingletonHolder.INSTANCE != null)
    throw new IllegalStateException("Use getInstance()");
    }
    private static final class SingletonHolder {
    private static final Singleton INSTANCE = new Singleton();
    }
    public static Singleton getInstance() { return SingletonHolder.INSTANCE; }
    protected Object readResolve() { return SingletonHolder.INSTANCE; }
    }
###

    // 5. ENUM (serialization-safe)
    public enum Singleton {
    INSTANCE;
    public void doWork() {}
    }

###
Key differences:

┌────────────────┬─────────────────────┬────────────────────────┐
│                │  Classic Singleton  │    Spring @Service     │
├────────────────┼─────────────────────┼────────────────────────┤
│ Who creates it │ You (getInstance()) │ Spring container       │
├────────────────┼─────────────────────┼────────────────────────┤
│ Scope          │ One per JVM         │ One per Spring context │
├────────────────┼─────────────────────┼────────────────────────┤
│ Testability    │ Hard to mock        │ Easy to mock/inject    │
├────────────────┼─────────────────────┼────────────────────────┤
│ Thread safety  │ You must handle it  │ Spring handles it      │
└────────────────┴─────────────────────┴────────────────────────┘

