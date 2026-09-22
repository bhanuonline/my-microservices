# Java Interview Notes

---

## Q1: Why does HashMap allow null, but ConcurrentHashMap doesn't?

### Short answer
`HashMap` is single-threaded by design, so `null` is unambiguous. `ConcurrentHashMap` is built for concurrent use, where `null` creates an **ambiguity that cannot be resolved safely** without locking — so Doug Lea (the author) banned it.

### The core problem: the "ambiguity"

Consider this code:
```java
V value = map.get(key);
if (value == null) {
    // What does this mean?
    // (a) The key is not in the map, OR
    // (b) The key IS in the map, but its value is null
}
```

#### In `HashMap` (single-threaded)
You can disambiguate with a follow-up call:
```java
if (map.containsKey(key)) {
    // it's case (b) — key exists, value is null
} else {
    // it's case (a) — key missing
}
```
This works because **nothing else can modify the map between the two calls**.

#### In `ConcurrentHashMap` (multi-threaded)
The same pattern is **broken**:
```java
V value = map.get(key);        // returns null
if (map.containsKey(key)) {    // another thread inserted/removed between these lines!
    ...
}
```
Between `get` and `containsKey`, another thread could `put` or `remove` the key. So you can never trust the answer. **The ambiguity is fundamentally unresolvable without external locking** — which defeats the purpose of a concurrent map.

### Why null keys are also banned
Same reasoning applies to keys. In a concurrent structure, hashing and equality checks on `null` complicate the internal CAS (compare-and-swap) operations that make `ConcurrentHashMap` lock-free. Doug Lea's rule: **no nulls, anywhere.**

### What happens if you try?
```java
ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
map.put("key", null);   // throws NullPointerException
map.put(null, "value"); // throws NullPointerException
```

### Interview-worthy nuance
- This is a **deliberate API contract**, not a technical limitation. Doug Lea explicitly said he considers allowing null in `HashMap` a **mistake** (see his email on the concurrency-interest mailing list).
- `Hashtable` (the older synchronized map) also disallows nulls — for the same reason.
- Modern replacement pattern: use a sentinel object, or `Optional`, or just don't store nulls.

---

## Q2: How HashMap Works Internally in Java? (with role of `equals()` and `hashCode()`)

### 1. The Big Picture

`HashMap` is an **array of buckets**, where each bucket is a **linked list** (or red-black tree if it grows large). Each entry is a `Node` holding `(hash, key, value, next)`.

```
HashMap internal structure (default capacity = 16):

  index    table[]
  ─────    ──────────
   [0]  →  null
   [1]  →  Node(hash, "apple", 100) → null
   [2]  →  null
   [3]  →  Node(hash, "banana", 200) → Node(hash, "grape", 300) → null
   [4]  →  null
   [5]  →  Node(hash, "kiwi", 400) → null
   [6]  →  null
   [7]  →  null
   ...
   [15] →  null
```

- Each row of the array is called a **bucket**.
- A bucket can be empty (`null`), hold one node, or hold a chain of nodes (collision).

---

### 2. The Node Structure

```java
static class Node<K,V> {
    final int hash;      // cached hash of the key
    final K key;
    V value;
    Node<K,V> next;      // pointer to next node in the same bucket
}
```

Key insight: **the hash is cached inside the node**. This avoids recomputing `hashCode()` every time we compare during collision walks.

---

### 3. The `put(key, value)` Flow — Step by Step

Let's trace `map.put("apple", 100)`:

```
Step 1: Compute the hash
────────────────────────
  raw = "apple".hashCode()       →  93029210   (some int)
  hash = raw ^ (raw >>> 16)      →  93044951   (spreader — see Q7)

Step 2: Find the bucket index
─────────────────────────────
  index = hash & (table.length - 1)   // capacity = 16, so & 15
        = 93044951 & 15
        = 7

Step 3: Look at bucket[7]
─────────────────────────
  Case A: bucket is empty
     → create new Node, place at bucket[7]. Done.

  Case B: bucket has entries (collision!)
     → walk the linked list:
         for each existing node in the bucket:
           if (existingNode.hash == newHash
               && (existingNode.key == newKey
                   || existingNode.key.equals(newKey))) {
             // KEY ALREADY EXISTS — update value
             existingNode.value = newValue;
             return oldValue;
           }
     → if we reach the end without a match:
         append new Node to the tail. Done.

Step 4: Check size vs threshold
───────────────────────────────
  if (++size > threshold) resize();   // threshold = capacity * loadFactor
```

#### Visual: putting three keys where two collide

```
map.put("apple", 100);
  hash("apple") = 93044951  →  93044951 & 15 = 7

  table:
  [7] → ("apple", 100) → null


map.put("banana", 200);
  hash("banana") = 87921134  →  87921134 & 15 = 14

  table:
  [7]  → ("apple", 100) → null
  [14] → ("banana", 200) → null


map.put("grape", 300);
  hash("grape") = 98547207  →  98547207 & 15 = 7   ← COLLISION with "apple"

  table:
  [7]  → ("apple", 100) → ("grape", 300) → null
  [14] → ("banana", 200) → null
```

---

### 4. The `get(key)` Flow — Step by Step

Let's trace `map.get("grape")`:

```
Step 1: Compute the hash (same as put)
──────────────────────────────────────
  hash = 98547207  (with spreader applied)

Step 2: Find the bucket index
─────────────────────────────
  index = hash & 15 = 7

Step 3: Walk bucket[7]
──────────────────────
  bucket[7] → ("apple", 100) → ("grape", 300) → null

  Node 1: ("apple", 100)
    - hash matches?  Compare cached hash of "apple" vs hash of "grape"
    - Different hashes → skip. Move to next.

  Node 2: ("grape", 300)
    - hash matches? YES.
    - keys equal? "grape".equals("grape") → true
    - RETURN 300.
```

**Critical**: `get()` uses **both** `hashCode()` AND `equals()`:
- `hashCode()` finds the **right bucket**.
- `equals()` finds the **right entry within the bucket**.

---

### 5. Role of `hashCode()` — "Which bucket?"

`hashCode()` is a **fast, coarse locator**. Its ONLY job:
> Given a key, tell me which bucket it goes into.

```
hashCode()  ──►  bucket index
    │
    └── If two keys have the SAME hashCode, they go into the SAME bucket (collision).
        This is OK — HashMap handles it via linked-list chaining.

    └── If two keys have DIFFERENT hashCodes, they MIGHT still land in the same
        bucket (because `hash & (n-1)` only uses low bits). Rare with good hashing.
```

#### Why hashCode() must be fast
Every `put`, `get`, `remove`, `containsKey` calls it. If `hashCode()` is slow, `HashMap` is slow.

#### Why hashCode() must be consistent
If the hashCode changes after a key is inserted, the map **loses the entry**:

```
Bad example — mutable key:

  StringBuilder key = new StringBuilder("abc");
  map.put(key, 1);
  // key.hashCode() at insert time = X → placed in bucket[X & 15]

  key.append("d");   // key is now "abcd"
  // key.hashCode() is now Y → map.get(key) looks in bucket[Y & 15]

  map.get(key);   // returns null! Entry is in the wrong bucket now.
```

**Rule**: keys used in a HashMap should be **immutable** (or at least never mutated in ways that change their hash).

---

### 6. Role of `equals()` — "Is this the same key?"

`equals()` is a **precise identity check**. Its job:
> Given two keys already in the same bucket, tell me if they represent the same logical key.

```
Bucket[7]:
  ("apple", 100) → ("grape", 300) → null

get("grape") arrives at bucket[7]. Now what?
  → Walk the chain, comparing each node's key to "grape" via equals().
  → "apple".equals("grape") → false. Skip.
  → "grape".equals("grape") → true. Return the value.
```

#### Why equals() is needed even after hashCode() matches
Hash collisions exist. Two DIFFERENT keys can end up in the same bucket. Without `equals()`, `get()` would return the **first** node in the bucket, regardless of key.

---

### 7. The Full Picture — `put()` and `get()` Together

```
                    ┌──────────────────┐
                    │  key.hashCode()  │
                    │       + spread   │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ index = hash &   │
                    │   (n - 1)        │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │  Access bucket   │
                    │   table[index]   │
                    └────────┬─────────┘
                             │
                    ┌────────┴────────┐
                    │                 │
              bucket empty       bucket has nodes
                    │                 │
                    ▼                 ▼
             INSERT here      Walk linked list:
                              for each node:
                                 if hash matches
                                 AND (== or equals)
                                   ─► FOUND (update or return)
                              (loop ends)
                                 ─► NOT FOUND (append for put, return null for get)
```

---

### 8. Worked Example — What Breaks If You Override `equals()` But Not `hashCode()`

```java
class Person {
    String name;
    Person(String name) { this.name = name; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Person p)) return false;
        return name.equals(p.name);
    }
    // hashCode() NOT overridden — uses Object's default (based on memory address)
}

Map<Person, Integer> map = new HashMap<>();
Person p1 = new Person("Alice");
Person p2 = new Person("Alice");   // logically "equal" to p1

map.put(p1, 100);
map.get(p2);   // returns null!  Why?
```

#### What happens step-by-step:

```
put(p1, 100):
  p1.hashCode() = 1849904862  (memory address of p1)
  bucket index = 1849904862 & 15 = 14
  → placed in bucket[14]

  table:
  [14] → (p1, 100) → null


get(p2):
  p2.hashCode() = 2027944191  (memory address of p2 — DIFFERENT from p1!)
  bucket index = 2027944191 & 15 = 15
  → look in bucket[15]

  table:
  [15] → null       ← empty! Return null.

  We never even reach bucket[14] where p1 is stored,
  so equals() is never called. The entry is invisible.
```

#### The fix

```java
class Person {
    String name;
    Person(String name) { this.name = name; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Person p)) return false;
        return name.equals(p.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();   // consistent with equals: same name → same hash
    }
}
```

Now `p1.hashCode() == p2.hashCode()` (both derived from "Alice"), so they land in the same bucket, `equals()` matches them, and `get(p2)` returns 100. ✓

---

### 9. The Contract — Cheat Sheet

```
   equals()  says two keys are the same
           ⇓  MUST IMPLY
    hashCode()  returns the same value

But NOT the reverse:
    hashCode()  returning the same value
           ⇏  does NOT imply
   equals()  says they're the same

(Two different keys can share a hash — that's just a collision.)
```

**Violation of this contract = silent data loss in HashMap/HashSet/HashTable.** No exception is thrown. The map just "loses" entries. This is why it's Java's #1 subtle bug source with custom key types.

---

### 10. What Happens in a Bad-Hash Scenario (All Keys Collide)

If someone defined:
```java
@Override
public int hashCode() { return 1; }   // terrible — all keys hash to 1
```

Then ALL entries pile up in bucket[1]:

```
table:
  [1] → (k1, v1) → (k2, v2) → (k3, v3) → ... → (k1000, v1000) → null

get(k500):
  → walks 500 nodes, calling equals() on each.
  → Lookup is now O(n), not O(1).
```

**Java 8 rescue**: once a bucket exceeds 8 entries (with capacity ≥ 64), it **converts to a red-black tree**, restoring O(log n). See Q3.

```
Before treeification:               After treeification:
[7] → n1 → n2 → n3 → n4 →           [7] → Tree
      n5 → n6 → n7 → n8 →                    (self-balancing
      n9 → null                              red-black tree
                                             with O(log n) ops)
```

---

### 11. Interview Summary — "What are equals and hashCode for?"

| Method | Job | When called |
|---|---|---|
| `hashCode()` | Locate the **bucket** | Every `put`, `get`, `remove`, `contains` |
| `equals()` | Match the **exact key** within a bucket | After hashCode narrows to a bucket, walk the chain and compare |

**One-liner**: `hashCode()` is the postal code (finds the right neighborhood); `equals()` is the street address (finds the exact house).

---

# Section: Collections Internals — Design Choices

## Q3: Why does HashMap convert a bucket from linked list to red-black tree at 8 entries? (treeification)

### Short answer
To rescue worst-case lookup performance from **O(n)** to **O(log n)** when many keys hash to the same bucket (poor hash function or hash-collision attack).

### The mechanics
- Each bucket starts as a **singly-linked list** of `Node` entries.
- When a bucket's size reaches `TREEIFY_THRESHOLD = 8` **AND** the table size is ≥ `MIN_TREEIFY_CAPACITY = 64`, the list is converted to a **red-black tree** (`TreeNode`).
- When the bucket shrinks below `UNTREEIFY_THRESHOLD = 6`, it converts back to a list.

### Why exactly 8? (the statistical reason)
Assuming a **well-distributed hash function** (Poisson distribution), the probability of a bucket having `k` entries at load factor 0.75 is:

| k | probability            |
|---|------------------------|
| 0 | 0.60653066             |
| 1 | 0.30326533             |
| 2 | 0.07581633             |
| 3 | 0.01263606             |
| 4 | 0.00157952             |
| 5 | 0.00015795             |
| 6 | 0.00001316             |
| 7 | 0.00000094             |
| 8 | **0.00000006** (~1 in 16M) |

So under normal conditions, **a bucket almost never reaches 8**. Treeification only kicks in when the hash function is bad or under attack — exactly when you need it.

### Why the gap (8 vs 6)?
The gap between `TREEIFY_THRESHOLD` (8) and `UNTREEIFY_THRESHOLD` (6) prevents **thrashing** — bouncing between list and tree on every add/remove near the boundary.

### Why also require `capacity >= 64`?
If the table is small, the fix for a long bucket is to **resize**, not treeify. Treeification is expensive (TreeNodes take ~2x memory of Nodes), so only use it once resizing alone won't help.

---

## Q4: Why is HashMap's default capacity 16 and load factor 0.75?

### Default capacity = 16
- **Power of 2** (see Q6) — enables the fast `hash & (n-1)` bucket index calculation.
- Small enough to not waste memory on empty maps.
- Large enough to avoid immediate resize on first few `put`s.
- 16 = `2^4` — the smallest power of 2 that gives a decent starting distribution.

### Load factor = 0.75
This is a **time-vs-space tradeoff**:

| Load factor | Effect                                                     |
|-------------|------------------------------------------------------------|
| 1.0         | Best space (fewest resizes), but many collisions → slow lookup |
| 0.5         | Best speed (fewest collisions), but wastes 50% of memory   |
| **0.75**    | Sweet spot: ~87% of buckets used, low collision probability |

The 0.75 value comes from Poisson-distribution math — at 0.75, the expected collision count per bucket stays low enough that lookups remain effectively O(1), while memory usage stays reasonable.

### Interview-worthy nuance
The Javadoc explicitly states:
> "As a general rule, the default load factor (.75) offers a good tradeoff between time and space costs."

Don't tune it unless you've measured. Lowering it below 0.5 rarely helps; raising it above 0.85 hurts lookup badly.

---

## Q5: Why does HashMap resize at `capacity * loadFactor` instead of when it's full?

### Short answer
Because **hash tables degrade before they're full**. Waiting until full means every operation is already slow.

### The math
- At load factor 0.75, resize triggers at `16 * 0.75 = 12` entries in a 16-bucket table.
- If we waited until the table was "full" (16 entries in 16 buckets), the birthday-paradox math guarantees many collisions — buckets with 3-4 entries would be common.
- Resizing **early** keeps the average bucket size close to 1, preserving O(1) lookups.

### Why not resize on every insert?
Resizing is O(n) — it rehashes every entry. Doing it too often kills insert performance. The load factor is the **amortization knob**: resize infrequently, but before performance degrades.

### The tradeoff visualized
```
Insert cost:  O(1) amortized  (occasional O(n) resize spread across many inserts)
Lookup cost:  O(1) average    (kept low by resizing before collision density gets bad)
```

---

## Q6: Why does HashMap require capacity to be a power of 2?

### Short answer
To replace an **expensive modulo (`%`) operation** with a **cheap bitwise AND (`&`)** when computing the bucket index.

### The trick
Bucket index = `hash % capacity` (mathematically correct).

But if `capacity` is a power of 2, then:
```java
hash % capacity  ==  hash & (capacity - 1)
```

For `capacity = 16`:
```
capacity - 1 = 15 = 0b00001111

hash = 0b10110111
       & 0b00001111
       -----------
       = 0b00000111  →  bucket index 7
```

The `&` operation is **one CPU cycle**; `%` is often **10-40 cycles**. Over millions of `put`/`get` calls, this matters.

### What if the user passes a non-power-of-2 capacity?
`HashMap` silently rounds it up:
```java
new HashMap<>(13);  // internally becomes 16
new HashMap<>(17);  // internally becomes 32
new HashMap<>(100); // internally becomes 128
```

The rounding is done by `tableSizeFor(int cap)` — a bit-twiddling trick:
```java
static final int tableSizeFor(int cap) {
    int n = cap - 1;
    n |= n >>> 1;
    n |= n >>> 2;
    n |= n >>> 4;
    n |= n >>> 8;
    n |= n >>> 16;
    return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
}
```
This fills all lower bits with 1s, then adds 1 to get the next power of 2.

### Downside
Only the **low bits** of the hash are used for the bucket index. If two keys differ only in **high bits**, they collide. This is exactly the problem Q7 solves.

---

## Q7: Why does HashMap rehash the hashcode using `(h = key.hashCode()) ^ (h >>> 16)`?

### Short answer
Because `hash & (n-1)` only uses the **low bits** of the hash. If a `hashCode()` implementation only varies in the **high bits**, every key collides. XOR-ing high bits into low bits **spreads the variance** so all bits contribute to the bucket index.

### The problem it solves
Say `capacity = 16`, so `n - 1 = 15 = 0b0000_0000_0000_1111`. Only the **bottom 4 bits** of the hash pick the bucket.

Now imagine two keys with these hashcodes:
```
key A: 0x1234_0000  → bottom 4 bits: 0000 → bucket 0
key B: 0x5678_0000  → bottom 4 bits: 0000 → bucket 0  ← COLLISION!
```
Both keys land in bucket 0, even though their hashcodes are wildly different. A bad `hashCode()` (like Java's default `Object.hashCode()` on some JVMs, which uses memory address bits) can produce exactly this pattern.

### The fix
```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

- `h >>> 16` shifts the top 16 bits down to the bottom 16 bits.
- XOR-ing folds the high half into the low half, so high-bit differences now affect the bucket index.

Applied to the example above:
```
key A: 0x1234_0000 XOR 0x0000_1234 = 0x1234_1234 → bottom 4 bits: 0100 → bucket 4
key B: 0x5678_0000 XOR 0x0000_5678 = 0x5678_5678 → bottom 4 bits: 1000 → bucket 8
```
Now they land in different buckets.

### Why not use a better hash (like MurmurHash)?
- **Speed**: `^` and `>>>` are 2 CPU instructions. MurmurHash is dozens.
- **"Good enough"**: For most real-world keys, high-bit-into-low-bit XOR is sufficient. Treeification (Q3) handles the pathological cases.

### Interview-worthy nuance
This is called a **hash "spreader"** or **"perturbation"** function. Java 7 used a much more elaborate one (4 shifts + 4 XORs). Java 8 simplified it after benchmarking showed the single XOR-shift was **almost as good** and much faster, especially when combined with treeification as a safety net.

---

# Section: Deep Concurrency-Model Questions

## Q8: Why is `volatile` not enough for `count++` but works for a flag?

### Short answer
`volatile` guarantees **visibility** but not **atomicity**. A flag write (`flag = true`) is a single atomic operation. But `count++` is actually **three operations** (read → modify → write), and another thread can slip in between them.

### The problem: `count++` is not atomic
```java
volatile int count = 0;

// count++ compiles to:
int temp = count;   // 1. READ (load from main memory)
temp = temp + 1;    // 2. MODIFY (in register)
count = temp;       // 3. WRITE (store to main memory)
```

Two threads running `count++` concurrently:
```
Thread A: reads count = 5
Thread B: reads count = 5     ← reads BEFORE A writes back
Thread A: writes count = 6
Thread B: writes count = 6    ← lost update! should be 7
```

`volatile` doesn't help here because it only guarantees each **individual read** and **write** is visible to other threads — it does NOT guarantee the read-modify-write happens without interruption.

### Why it works for a flag
```java
volatile boolean shutdown = false;

// Thread A:
shutdown = true;      // single atomic write

// Thread B:
while (!shutdown) {   // single atomic read
    doWork();
}
```
There's no read-modify-write. Just a write on one side and a read on the other. `volatile` guarantees Thread B sees Thread A's write **immediately** (no CPU cache staleness). That's all you need.

### The fix for `count++`
Use one of:
- `AtomicInteger` — uses CAS (compare-and-swap) hardware instruction for lock-free atomicity.
- `synchronized` — mutual exclusion around the increment.
- `LongAdder` (Java 8+) — better for high contention, uses striped counters.

```java
AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet();  // atomic — safe from concurrent threads
```

### Interview-worthy nuance
**Rule of thumb**: `volatile` is safe when:
- One thread writes, others read (single-writer pattern), OR
- The variable is set to a **new value that doesn't depend on the old value** (e.g., `flag = true`, `config = newConfig`).

Any operation of the form `x = f(x)` — including `++`, `--`, `+=`, `x = x * 2` — needs stronger guarantees than `volatile`.

---

## Q9: Why does `String` have to be immutable — what breaks if it isn't?

### Short answer
Immutability enables **security, hashcode caching, string interning, and thread safety** — all in one design choice. Making `String` mutable would break the entire Java security model and much of the standard library.

### Reason 1: Security
Consider file I/O:
```java
void openFile(String path) {
    securityManager.checkRead(path);  // validate path
    // ... TOCTOU vulnerability if String is mutable!
    fileSystem.open(path);            // could open a DIFFERENT file
}
```
If `String` were mutable, a malicious thread could change `path` between the security check and the actual file open (**Time-Of-Check to Time-Of-Use** attack). Same problem applies to class loading, reflection, URLs, SQL queries, etc.

### Reason 2: Hashcode caching
`String.hashCode()` is called constantly (every `HashMap` lookup on a String key). Since strings are immutable, the hash can be **computed once and cached**:
```java
public final class String {
    private int hash;  // cached, computed lazily

    public int hashCode() {
        int h = hash;
        if (h == 0 && value.length > 0) {
            h = ... compute ...;
            hash = h;
        }
        return h;
    }
}
```
If `String` were mutable, the cached hash would be wrong after any modification — breaking every `HashMap<String, ?>`.

### Reason 3: String pool / interning
Java maintains a **string pool** in the heap:
```java
String a = "hello";
String b = "hello";
a == b;  // true — both point to the same pooled instance
```
This saves massive memory (all string literals in your codebase are deduplicated). It only works because strings are immutable — if you could change `a`, you'd accidentally change `b` too.

### Reason 4: Thread safety (for free)
Immutable objects are **inherently thread-safe** — no synchronization needed. You can pass a `String` across threads without any locking. This is huge for a type used everywhere.

### Reason 5: `HashMap` keys stay valid
A `HashMap`'s bucket location depends on the key's hashcode. If a key were mutable and someone changed it after insertion:
```java
Map<StringBuilder, String> map = new HashMap<>();
StringBuilder key = new StringBuilder("foo");
map.put(key, "value");
key.append("bar");         // key is now "foobar"
map.get(key);              // returns null — key hashes to a different bucket now!
```
This is exactly the bug you'd hit if `String` were mutable. Immutability makes `String` a safe map key.

### Interview-worthy nuance
`String` is `final` for the same reason. If you could subclass it and override behavior, all the guarantees above collapse. This is why `String.substring()` in Java 7+ copies the char array (in Java 6 it shared the array — leading to memory leaks).

---

## Q10: Why is `Integer` cached from -128 to 127 but not beyond?

### Short answer
Because **small integers are used far more often than large ones**, and caching them saves memory and improves performance. The range -128 to 127 covers the vast majority of real-world use cases while keeping cache size bounded.

### The mechanics
```java
Integer a = 127;
Integer b = 127;
a == b;         // true — both refer to the cached instance

Integer c = 128;
Integer d = 128;
c == d;         // false — new objects, different references
```

This happens because of **autoboxing**, which calls `Integer.valueOf(int)`:
```java
public static Integer valueOf(int i) {
    if (i >= IntegerCache.low && i <= IntegerCache.high)
        return IntegerCache.cache[i + (-IntegerCache.low)];
    return new Integer(i);
}
```

The cache is a pre-populated `Integer[]` array covering -128 to 127 (256 values).

### Why -128 to 127 specifically?
- It's the **range of a signed byte** — a natural boundary in Java.
- **Loop counters, indexes, small IDs, HTTP status codes, ports below 128, boolean-like flags** — the vast majority of integer values in real code fall in this range.
- 256 pre-allocated `Integer` objects = ~4KB. Cheap.

### Why not cache more?
- **Memory cost scales linearly** — caching -32768 to 32767 would be 256KB of preloaded objects, most never used.
- **Diminishing returns** — beyond ±128, the hit rate drops off sharply.
- The upper bound is **configurable** via `-Djava.lang.Integer.IntegerCache.high=N` (rarely used, but exists for tuning).

### The classic bug this causes
```java
Integer x = 200;
Integer y = 200;
if (x == y) { ... }        // FALSE — reference comparison on non-cached Integers
if (x.equals(y)) { ... }   // TRUE — value comparison

Integer x = 100;
Integer y = 100;
if (x == y) { ... }        // TRUE — both from cache
```
**Never use `==` to compare boxed integers.** Always use `.equals()` or unbox to `int`. This is one of the most common interview gotchas.

### Interview-worthy nuance
Other wrapper types have similar caches:
- `Boolean`: `TRUE` and `FALSE` (2 cached instances)
- `Byte`: entire range (-128 to 127) cached
- `Short`, `Long`: -128 to 127 cached
- `Character`: 0 to 127 (ASCII range) cached
- `Float`, `Double`: **no cache** (floating-point equality is unreliable anyway)

---

## Q11: Why does Java 7's HashMap have an infinite-loop bug under concurrent resizing, but Java 8 doesn't?

### Short answer
Java 7 resized buckets using **head insertion** (reverses the list), which can create a **cycle** if two threads resize simultaneously. Java 8 switched to **tail insertion** (preserves order), which eliminates the cycle — though `HashMap` is still not thread-safe.

### The Java 7 bug
During resize, each bucket's linked list is split into two new buckets (based on the new higher bit of the hash). Java 7's `transfer()` method walked the old list and **prepended** each node to the new list:

```java
// Java 7 pseudocode
void transfer(Entry[] newTable) {
    for (Entry e : oldTable) {
        while (e != null) {
            Entry next = e.next;
            int newIndex = hash(e.key) & (newTable.length - 1);
            e.next = newTable[newIndex];   // PREPEND
            newTable[newIndex] = e;
            e = next;
        }
    }
}
```

If two threads run this at the same time on the same map, the `e.next` reassignments race. One thread can see a half-updated list where node A points to node B and node B points back to node A → **cycle**.

Later, any `get()` traversing that bucket **loops forever**, pinning a CPU core at 100%. This was a famous production bug — some companies got paged for it.

### The Java 8 fix
Java 8 rewrote resize to use **tail insertion**, preserving the original order:

```java
// Java 8 pseudocode
Node loHead = null, loTail = null;  // stays in same bucket
Node hiHead = null, hiTail = null;  // moves to bucket + oldCap
do {
    Node next = e.next;
    if ((e.hash & oldCap) == 0) {
        if (loTail == null) loHead = e;
        else loTail.next = e;
        loTail = e;
    } else {
        if (hiTail == null) hiHead = e;
        else hiTail.next = e;
        hiTail = e;
    }
} while ((e = next) != null);
```

Because nodes are always appended in the same order they appeared, no cycle can form even under concurrent resize.

### But is Java 8 HashMap thread-safe now?
**No.** You can still lose data (last-write-wins on `put`), corrupt the size counter, or get inconsistent reads. The Java 8 change only fixed the infinite loop — you still need `ConcurrentHashMap` for concurrent use.

### Interview-worthy nuance
This is a great "why is `HashMap` not thread-safe?" follow-up question. The answer isn't "it might return wrong data" (any map might under contention) — it's "in Java 7, it could burn a CPU core forever, and even in Java 8 it can corrupt internal state."

---

## Q12: Why does `synchronized` need happens-before, and why isn't `volatile` sufficient for mutual exclusion?

### Short answer
`volatile` provides **visibility** (memory ordering) but not **atomicity across multiple statements** or **mutual exclusion**. `synchronized` provides both — plus happens-before, which is the memory model contract that guarantees one thread sees another's changes.

### What `volatile` gives you
- Every read of a `volatile` variable sees the **latest write** from any thread (no CPU cache staleness).
- Reads/writes to `volatile` variables are **not reordered** by the compiler/CPU with respect to other memory operations.

That's it. It's a **memory-ordering primitive**, not a locking primitive.

### What `volatile` does NOT give you
1. **Mutual exclusion** — two threads can still be inside a "critical section" at the same time.
2. **Atomicity across multiple statements** — see Q8, `count++` example.
3. **Bundled visibility of related state** — see below.

### The critical section problem
```java
class BankAccount {
    volatile int balance;

    void withdraw(int amount) {
        if (balance >= amount) {    // Thread A: checks balance = 100, amount = 80
            balance -= amount;      // Thread B: also checks 100, also withdraws 80
        }                           // Result: balance = -60 (overdraft!)
    }
}
```
`volatile` doesn't stop Thread B from reading `balance` between Thread A's check and update. You need **exclusive access** to the critical section — that's what `synchronized` provides.

### What `synchronized` gives you (in addition to volatile-like guarantees)
1. **Mutual exclusion**: only one thread inside the block at a time.
2. **Happens-before**: everything a thread did **before releasing** a lock is visible to the next thread that **acquires** it — even for non-volatile fields.
3. **Atomic composition**: multiple reads/writes together become a single atomic operation from other threads' perspective.

### Happens-before, concretely
```java
class Counter {
    int count;      // NOT volatile
    boolean ready;  // NOT volatile

    synchronized void update() {
        count = 42;
        ready = true;
    }

    synchronized int read() {
        if (ready) return count;   // guaranteed to see 42 if ready is true
        return -1;
    }
}
```
Even though neither field is `volatile`, `synchronized` guarantees that **all writes** inside `update()` are visible to any thread that later enters `read()`. This is the **happens-before edge** established by lock release → lock acquire.

Without happens-before, Thread B might see `ready = true` but `count = 0` (uninitialized) due to CPU reordering or cache staleness.

### Why not just make everything volatile?
Because volatile only gives you happens-before **on that single variable**. To coordinate multiple fields you'd need a locking protocol anyway, and you still couldn't do compound operations atomically.

### Interview-worthy nuance
The classic **double-checked locking** bug demonstrates why `volatile` matters alongside `synchronized`:
```java
class Singleton {
    private static Singleton instance;  // BROKEN without volatile!

    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();  // not atomic!
                }
            }
        }
        return instance;
    }
}
```
Without `volatile`, a thread outside the `synchronized` block might see a **non-null but partially-constructed** `instance`. Fix: `private static volatile Singleton instance;`

This shows the two primitives are **complementary**: `volatile` for lock-free visibility, `synchronized` for exclusion + happens-before.

---

# Section: Iterator & Contract Questions

## Q13: Why is `Iterator.remove()` allowed but `Collection.remove()` during iteration is not?

### Short answer
Because `Iterator.remove()` **knows the iterator's internal state** and updates it in sync with the removal. `Collection.remove()` doesn't — it silently invalidates the iterator, so the JDK detects this and throws `ConcurrentModificationException` (CME) to prevent silent data corruption.

### The problem: iterators cache state
Every iterator tracks:
- **Cursor position** (which element is next)
- **Expected structural state** of the underlying collection (via a counter called `modCount`)

When you call `collection.remove(x)`, the collection's `modCount` increments. But the iterator has no idea — its cursor is now pointing at the wrong index, and the next `next()` call could **skip an element** or **return a stale one**.

### Concrete example — the bug this prevents
```java
List<String> list = new ArrayList<>(List.of("a", "b", "c", "d"));
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String s = it.next();
    if (s.equals("b")) {
        list.remove(s);   // BAD — mutates list behind iterator's back
    }
}
```
Without CME detection, this would skip `"c"`:
- Iterator's cursor was at index 2 (`"c"`) after reading `"b"`.
- `list.remove("b")` shifts everything left → `"c"` is now at index 1, `"d"` at 2.
- Iterator's cursor advances to index 3 → skips `"c"`, reads `"d"`.

To catch this silent bug, `ArrayList.iterator()` checks `modCount == expectedModCount` on every `next()`/`hasNext()` and throws `ConcurrentModificationException` if they differ.

### Why `Iterator.remove()` works
```java
while (it.hasNext()) {
    String s = it.next();
    if (s.equals("b")) {
        it.remove();   // GOOD — iterator updates its own cursor + modCount
    }
}
```

Inside `Iterator.remove()`:
1. It removes the last-returned element from the collection.
2. It **increments `expectedModCount`** to match the collection's new `modCount`.
3. It **adjusts its cursor** to account for the shift.

So the iterator stays consistent with the collection. No CME, no skipped elements.

### The "fail-fast" contract
This is Java's **fail-fast** iterator behavior:
> "Detect concurrent modification quickly and loudly, rather than allowing silent, hard-to-debug corruption."

CME is **best-effort** — it's not guaranteed to fire (race conditions in multi-threaded code can miss it), but for single-threaded misuse it catches the bug immediately.

### The exceptions (fail-safe collections)
Some collections have **fail-safe** (a.k.a. **weakly consistent**) iterators that don't throw CME:
- `ConcurrentHashMap`, `ConcurrentLinkedQueue`, `CopyOnWriteArrayList`
- These iterate over a **snapshot** (or use versioning) and tolerate concurrent modification.
- Tradeoff: you might see stale data, but the iterator won't blow up.

### Interview-worthy nuance
- `Iterator.remove()` is an **optional operation** — some iterators (e.g., over unmodifiable collections) throw `UnsupportedOperationException`.
- In Java 8+, prefer `collection.removeIf(predicate)` — it handles the iterator/removal dance for you and is often faster.
- For streams: **never** modify the source collection inside a stream operation. Streams don't have `Iterator.remove()` equivalent semantics.

### The modern replacement pattern
```java
// Instead of iterator + remove:
list.removeIf(s -> s.equals("b"));

// Or with a stream (creates a new list):
List<String> filtered = list.stream()
                            .filter(s -> !s.equals("b"))
                            .toList();
```

---

## Q14: Why does `equals()` require `hashCode()` to be overridden, but the compiler doesn't enforce it?

### Short answer
Because `equals()` and `hashCode()` **must obey a contract together** — but the contract is a **runtime semantic requirement**, not a syntactic one. The compiler can only enforce syntax; it can't verify "if two objects are `equals`, they produce the same hashcode."

### The contract (from `Object` Javadoc)
1. **Consistency**: `x.equals(y)` implies `x.hashCode() == y.hashCode()`.
2. The reverse is NOT required: equal hashcodes don't imply equal objects (hash collisions are allowed).
3. If you break rule 1, `HashMap`, `HashSet`, and `HashTable` **silently corrupt** — but there's no exception, just wrong behavior.

### What goes wrong if you override only `equals()`
```java
class Point {
    int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point p)) return false;
        return x == p.x && y == p.y;
    }
    // hashCode() NOT overridden — inherits Object.hashCode() (based on memory address)
}
```

Now:
```java
Set<Point> set = new HashSet<>();
set.add(new Point(1, 2));
set.contains(new Point(1, 2));   // returns FALSE!
```

Why? `HashSet` first looks up the bucket by `hashCode()`. Since the two `Point` instances have different memory addresses, they hash to **different buckets**. The lookup never even reaches `equals()`. Your "equal" objects are invisible to each other in the map.

### Why can't the compiler enforce this?
The compiler can enforce **syntactic rules** — e.g., "you must implement all abstract methods." But this contract is **semantic**:
- The compiler would need to prove: "for all pairs `(x, y)` where `x.equals(y)` returns true, `x.hashCode() == y.hashCode()`."
- That's undecidable in general (halting problem territory).

The compiler could add a warning like *"you overrode `equals()` but not `hashCode()` — are you sure?"* and IDEs like IntelliJ do exactly this. But it can't be an **error** because:
- Sometimes overriding only `equals()` is intentional (e.g., an object never used as a map key).
- Sometimes both are overridden in a **superclass** and the subclass's override is safe.

### What tools DO enforce it
- **IntelliJ IDEA**: warns *"equals() and hashCode() not paired"*.
- **SpotBugs / ErrorProne / SonarQube**: flag this as a bug pattern.
- **Lombok `@EqualsAndHashCode`**: generates both together, so you can't forget one.
- **Java records** (Java 14+): automatically generate consistent `equals()`, `hashCode()`, and `toString()` — this is the modern fix.

### Records eliminate the problem
```java
record Point(int x, int y) {}
// equals() and hashCode() are generated together, always consistent.
```
This is why records are recommended for value types in modern Java.

### The subtler contract violations
Even if you override both, you can still break the contract:

**Mutation problem:**
```java
Point p = new Point(1, 2);
Set<Point> set = new HashSet<>();
set.add(p);
p.x = 999;                   // mutated after adding to set
set.contains(p);             // may return FALSE — p hashes to different bucket now
```
Fix: don't mutate objects used as map/set keys. Or make them **immutable** (Q9's whole point).

**Inheritance problem:**
```java
class ColorPoint extends Point {
    Color color;
    // if you override equals to consider color, symmetric/transitive property breaks
}
Point p = new Point(1, 2);
ColorPoint cp = new ColorPoint(1, 2, RED);
p.equals(cp);   // true (Point only checks x, y)
cp.equals(p);   // false (ColorPoint also checks color) — asymmetric!
```
This is why **Effective Java Item 10** recommends: prefer composition over inheritance for value types, or use `getClass() == other.getClass()` instead of `instanceof` in `equals()`.

### Interview-worthy nuance
- The `Object.hashCode()` default returns an int derived from the object's **identity** (memory address on some JVMs, a stable per-instance value on others). This is why unrelated instances get different hashes even when logically "equal."
- `equals()` also has a contract with 5 rules: **reflexive, symmetric, transitive, consistent, non-null**. `x.equals(null)` must return `false` (not throw NPE).
- The `hashCode()` contract has 3 rules: **consistent within a run, equal-objects-equal-hashes, unequal objects should ideally differ (but not required)**.

---

# Section: HashMap in Practice — Usage Patterns

## Q15: How many different ways to use HashMap, and why is it so useful in coding & data structures?

### Part A: Different Ways to Use `HashMap` (API-level)

#### 1. Basic operations
```java
Map<String, Integer> map = new HashMap<>();

map.put("apple", 100);              // insert or update
map.get("apple");                    // retrieve (returns null if absent)
map.remove("apple");                 // remove
map.containsKey("apple");            // check key existence
map.containsValue(100);              // check value existence (O(n)!)
map.size();                          // number of entries
map.isEmpty();                       // check empty
map.clear();                         // remove all
```

#### 2. Null-safe retrieval
```java
map.getOrDefault("missing", 0);      // returns 0 if key absent — no null check
```

#### 3. Conditional insert
```java
map.putIfAbsent("apple", 100);       // only inserts if key not present
```

#### 4. Compute / merge (Java 8+) — **very interview-relevant**
```java
// Increment a counter safely
map.merge("apple", 1, Integer::sum);
// If "apple" absent → put 1. If present → oldValue + 1.

// Same, via compute
map.compute("apple", (k, v) -> v == null ? 1 : v + 1);

// Only if present
map.computeIfPresent("apple", (k, v) -> v + 1);

// Only if absent — great for lazy init
map.computeIfAbsent("apple", k -> new ArrayList<>()).add(item);
```

`computeIfAbsent` is the killer method — replaces 4-5 lines of null-check boilerplate.

#### 5. Iteration patterns
```java
// Over entries (most efficient)
for (Map.Entry<String, Integer> e : map.entrySet()) {
    e.getKey(); e.getValue();
}

// Over keys only
for (String k : map.keySet()) { ... }

// Over values only
for (Integer v : map.values()) { ... }

// Java 8 forEach
map.forEach((k, v) -> System.out.println(k + "=" + v));
```

#### 6. Bulk operations
```java
map.putAll(otherMap);                          // merge another map in
Map<String, Integer> copy = new HashMap<>(map); // copy constructor
Map<String, Integer> immut = Map.of("a", 1, "b", 2);  // Java 9+ immutable
```

#### 7. Streams integration
```java
// Group a list into a map
Map<String, List<Person>> byCity = people.stream()
    .collect(Collectors.groupingBy(Person::city));

// Count occurrences
Map<String, Long> wordCounts = words.stream()
    .collect(Collectors.groupingBy(w -> w, Collectors.counting()));

// Convert list to map
Map<Integer, String> byId = users.stream()
    .collect(Collectors.toMap(User::id, User::name));
```

---

### Part B: How HashMap Solves Coding Problems

#### Pattern 1: Frequency Counting (occurrences)
**Problem**: Count how many times each element appears.

```java
int[] nums = {1, 2, 2, 3, 3, 3};
Map<Integer, Integer> freq = new HashMap<>();
for (int n : nums) freq.merge(n, 1, Integer::sum);
// {1=1, 2=2, 3=3}
```
**Interview problems**: Top-K frequent elements, first non-repeating character, anagram detection, majority element.

---

#### Pattern 2: Lookup Table / Memoization
**Problem**: Avoid recomputing expensive results.

```java
Map<Integer, Long> memo = new HashMap<>();

long fib(int n) {
    if (n < 2) return n;
    if (memo.containsKey(n)) return memo.get(n);
    long result = fib(n-1) + fib(n-2);
    memo.put(n, result);
    return result;
}
```
**Interview problems**: Dynamic programming (any DP with non-array state — string keys, tuple keys), recursive tree problems.

---

#### Pattern 3: Two-Sum Pattern (complement lookup)
**Problem**: Find pairs meeting a condition in O(n) instead of O(n²).

```java
int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int need = target - nums[i];
        if (seen.containsKey(need)) return new int[]{seen.get(need), i};
        seen.put(nums[i], i);
    }
    return new int[]{};
}
```
**Interview problems**: Two Sum, 3Sum, subarray sum equals K, longest subarray with sum X.

---

#### Pattern 4: Grouping (bucket by key)
**Problem**: Group items by a computed property.

```java
// Group anagrams together
Map<String, List<String>> groups = new HashMap<>();
for (String word : words) {
    char[] c = word.toCharArray();
    Arrays.sort(c);
    String key = new String(c);
    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(word);
}
```
**Interview problems**: Group anagrams, group by X property, bucket sort.

---

#### Pattern 5: Adjacency List (graph representation)
**Problem**: Represent a graph efficiently.

```java
Map<Integer, List<Integer>> graph = new HashMap<>();
graph.computeIfAbsent(1, k -> new ArrayList<>()).add(2);
graph.computeIfAbsent(1, k -> new ArrayList<>()).add(3);
// 1 → [2, 3]
```
**Interview problems**: BFS/DFS on graph, shortest path, cycle detection, topological sort. Much more space-efficient than a 2D matrix for sparse graphs.

---

#### Pattern 6: Sliding Window with State
**Problem**: Maintain state over a moving window.

```java
// Longest substring without repeating characters
int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> lastSeen = new HashMap<>();
    int start = 0, max = 0;
    for (int end = 0; end < s.length(); end++) {
        char c = s.charAt(end);
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= start) {
            start = lastSeen.get(c) + 1;
        }
        lastSeen.put(c, end);
        max = Math.max(max, end - start + 1);
    }
    return max;
}
```
**Interview problems**: Longest substring problems, minimum window, permutation in string.

---

#### Pattern 7: Prefix Sum + HashMap (subarray sums)
**Problem**: Find subarrays with a target sum in O(n).

```java
// Count subarrays with sum = k
int subarraySum(int[] nums, int k) {
    Map<Integer, Integer> prefixCount = new HashMap<>();
    prefixCount.put(0, 1);
    int sum = 0, count = 0;
    for (int n : nums) {
        sum += n;
        count += prefixCount.getOrDefault(sum - k, 0);
        prefixCount.merge(sum, 1, Integer::sum);
    }
    return count;
}
```
**Interview problems**: Subarray sum equals K, continuous subarray sum, contiguous array.

---

#### Pattern 8: Caching / LRU
**Problem**: Cache with fast lookup.

`LinkedHashMap` (subclass of HashMap that preserves insertion/access order) is the standard base for LRU caches:

```java
class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;
    public LRUCache(int cap) {
        super(cap, 0.75f, true);  // access-order = true
        this.capacity = cap;
    }
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}
```

---

#### Pattern 9: De-duplication / Uniqueness Tracking
**Problem**: Track "have I seen this before?" — usually `HashSet`, which is a `HashMap` under the hood.

```java
Set<Integer> seen = new HashSet<>();  // internally: HashMap<Integer, Object>
for (int n : nums) {
    if (!seen.add(n)) return true;    // duplicate found
}
```

---

#### Pattern 10: Bidirectional Lookup
**Problem**: Look up by value OR by key.

```java
Map<String, Integer> nameToId = new HashMap<>();
Map<Integer, String> idToName = new HashMap<>();
// Keep both in sync on every put.
```
(Or use Guava's `BiMap`.)

---

### Part C: Why HashMap Is a "Superpower" Data Structure

#### 1. O(1) average lookup
- Array: O(1) if you know the index. HashMap: O(1) even with **any hashable key** (string, object, tuple).
- This is the killer feature — converts many O(n) or O(n²) problems to O(n) or O(1).

#### 2. Turns "search" into "compute"
- Instead of scanning to find something, you **precompute a mapping** and jump to the answer.
- This is the core trick in problems like Two Sum, prefix-sum subarray sum, and memoization.

#### 3. Flexible key types
- Unlike arrays (integer index only), HashMap keys can be:
  - Strings (word counts, symbol tables)
  - Custom objects (grouping, indexing)
  - Tuples via records or arrays wrapped in objects
- This flexibility makes it the go-to data structure for **domain modeling**.

#### 4. Composability
- `Map<K, List<V>>` — grouping
- `Map<K, Set<V>>` — grouping without duplicates
- `Map<K, Map<K2, V>>` — nested lookups (e.g., matrix indexed by two IDs)
- `Map<K, Integer>` — frequency
- `Map<K, Long>` — running totals

#### 5. Foundational to other data structures
Many higher-level structures are **built on top of HashMap**:
- `HashSet` → `HashMap<E, Object>` (values ignored)
- `LinkedHashMap` → HashMap + doubly-linked list (preserves order)
- `LRU cache` → `LinkedHashMap` with access-order + eviction
- Symbol tables in compilers/interpreters
- Database indexes (hash indexes)
- Object property lookups in JavaScript engines

---

### Part D: When NOT to Use HashMap

| Use case | Better choice | Why |
|---|---|---|
| Need sorted keys | `TreeMap` | HashMap has no ordering |
| Need insertion order | `LinkedHashMap` | HashMap iteration order is unpredictable |
| Concurrent access | `ConcurrentHashMap` | HashMap is not thread-safe |
| Small, fixed data | array or `Map.of()` | Overhead not worth it |
| Range queries | `TreeMap` (`subMap`, `headMap`) | HashMap can't range-scan |
| Ordered by value | Sort entries separately | HashMap indexes by key only |

---

### Part E: Interview One-Liner
> "HashMap converts search problems (O(n) scans) into lookup problems (O(1) probes) by trading memory for time. It's the single most useful data structure for interview problems because most problems reduce to 'have I seen this before?' or 'what value did I associate with this key?'"

---

# Section: JVM Internals & Memory Model

## Q16: What happens step-by-step when JVM loads a class?

### Short answer
Class loading is a **3-phase, 6-step** process: **Loading → Linking (Verification → Preparation → Resolution) → Initialization**. Each phase can be lazy — the JVM only initializes a class when it's *actively used*.

### The 6 steps in detail

```
   1. LOADING
        │  Read .class bytes from source (disk, JAR, network, dynamic gen).
        │  ClassLoader delegation: bootstrap → platform → application → custom.
        │  Creates a Class<?> object in the heap; metadata goes to Metaspace.
        ▼
   2. LINKING - VERIFICATION
        │  Validate bytecode structure: valid opcodes, stack integrity,
        │  no illegal type conversions, final classes not subclassed, etc.
        │  Prevents malicious/corrupt bytecode from crashing the JVM.
        ▼
   3. LINKING - PREPARATION
        │  Allocate memory for static fields, set them to DEFAULT values.
        │  int → 0, boolean → false, Object → null.
        │  Note: static final constants get their real value only in Initialization.
        ▼
   4. LINKING - RESOLUTION
        │  Convert symbolic references (like "java/lang/String") into
        │  direct references (memory pointers). Can be lazy.
        ▼
   5. INITIALIZATION
        │  Run <clinit>() — the compiler-generated static initializer.
        │  Executes static blocks and static field assignments in source order.
        │  Triggered by: `new`, static field access, static method call,
        │  reflection, subclass init, Class.forName().
        ▼
   6. USING
        │  Class is now fully ready. Instances can be created.
        ▼
   7. UNLOADING (optional)
        │  If ClassLoader becomes unreachable, the loaded class can be GC'd.
        │  Bootstrap classes are never unloaded.
```

### Concrete example
```java
class Foo {
    static int x = 10;              // Preparation: x=0, Initialization: x=10
    static final int Y = 42;        // Compile-time constant — inlined!
    static { System.out.println("clinit runs"); }
}

// Nothing loaded yet
Class.forName("Foo");   // triggers Loading → Linking → Initialization → prints "clinit runs"
Foo.Y;                  // does NOT trigger init — Y was inlined at compile time
```

### Interview-worthy nuance
- **Class loaders are hierarchical** with **parent delegation**: child asks parent first, only loads itself if parent fails. This prevents `java.lang.String` from being replaced by a malicious classloader.
- **`static final` primitives and String literals** are **compile-time constants** — the compiler inlines them into calling classes. Accessing them does NOT trigger initialization of the declaring class.
- Two classes are **only "the same" if loaded by the same classloader**. Same `.class` file loaded by two classloaders → two different `Class` objects, `instanceof` fails between them. This is the root of many "ClassCastException: Foo cannot be cast to Foo" bugs.

---

## Q17: Explain the difference between Heap, Stack, Metaspace, PermGen, and Code Cache

### Short answer
JVM memory is split into regions with **different lifetimes and access patterns**:
- **Heap** — object instances (shared, GC-managed)
- **Stack** — method frames, locals, return addresses (per-thread, no GC)
- **Metaspace** — class metadata (native memory, since Java 8)
- **PermGen** — legacy pre-Java-8 equivalent of Metaspace (heap-based)
- **Code Cache** — JIT-compiled native code

### Visual layout

```
   ┌─────────────────────────── JVM Memory ────────────────────────────┐
   │                                                                    │
   │  ┌─────────────── HEAP ────────────────┐  (shared across threads) │
   │  │                                     │                          │
   │  │  ┌── Young Gen ──┐  ┌── Old Gen ─┐  │  ← all `new` objects    │
   │  │  │ Eden │ S0 │S1 │  │            │  │    live here             │
   │  │  └───────────────┘  └────────────┘  │                          │
   │  └─────────────────────────────────────┘                          │
   │                                                                    │
   │  ┌── Thread 1 Stack ──┐  ┌── Thread 2 Stack ──┐   (per-thread)   │
   │  │ frame: main()      │  │ frame: run()       │                  │
   │  │  - local vars      │  │  - local vars      │                  │
   │  │  - operand stack   │  │  - operand stack   │                  │
   │  │  - return address  │  │  - return address  │                  │
   │  └────────────────────┘  └────────────────────┘                  │
   │                                                                    │
   │  ┌── Metaspace ──┐    ┌── Code Cache ──┐    ┌── PC Reg ──┐        │
   │  │ Class metadata│    │  JIT-compiled  │    │ per-thread │        │
   │  │ Method info   │    │  native code   │    │ instr ptr  │        │
   │  │ String pool*  │    └────────────────┘    └────────────┘        │
   │  └───────────────┘                                                 │
   │  (*string pool moved to heap in Java 7)                            │
   └────────────────────────────────────────────────────────────────────┘
```

### Where each thing lives

| Data | Location | Lifetime |
|---|---|---|
| `new Object()` | Heap | Until GC'd |
| Local variables (primitives, references) | Stack | Method call |
| `static` fields | Metaspace (Java 8+) / Heap (references still) | Class lifetime |
| Class metadata (bytecode, method table) | Metaspace | Class lifetime |
| String literals (`"hello"`) | Heap (moved from PermGen in Java 7) | Until interned/GC'd |
| JIT-compiled native code | Code Cache | JVM lifetime (can be evicted) |
| Method call frames | Stack | Method call |
| `Thread` objects | Heap | Until GC'd |

### Key sizing flags
```bash
-Xms512m -Xmx4g              # Heap: initial 512MB, max 4GB
-Xss512k                     # Stack size per thread
-XX:MetaspaceSize=128m       # Metaspace initial trigger
-XX:MaxMetaspaceSize=512m    # Metaspace cap (default: unlimited!)
-XX:ReservedCodeCacheSize=240m  # Code cache size
```

### Interview-worthy nuance
- **`StackOverflowError`** = stack full (deep recursion). **`OutOfMemoryError: Java heap space`** = heap full.
- **`OutOfMemoryError: Metaspace`** = too many classes loaded (common in app servers hot-reloading WARs).
- **`OutOfMemoryError: unable to create new native thread`** = OS-level thread limit, NOT JVM heap.
- **Escape analysis** (see Q20) can move some objects from heap to stack.

---

## Q18: Why did Java 8 replace PermGen with Metaspace?

### Short answer
**PermGen was fixed-size and hard to tune** — you had to guess the max class-metadata size at startup, and app servers with hot-redeploys would leak classes until PermGen exploded. Metaspace uses **native memory that grows dynamically**, eliminating the guess.

### The problems with PermGen
1. **Fixed max size** (`-XX:MaxPermSize=256m`) — set too low, get `OutOfMemoryError: PermGen space`; set too high, waste heap.
2. **Shared with heap** — reduced heap available to your app.
3. **Poor GC** — classes were rarely unloaded even when their ClassLoaders were unreachable, causing "PermGen leaks" in Tomcat/JBoss hot-reload scenarios.
4. **Complex tuning** — no clear guidance on the right size.
5. **String pool lived in PermGen (pre-Java-7)** — `String.intern()` could exhaust PermGen unexpectedly.

### What Metaspace changed
- Class metadata now lives in **native memory** (outside the JVM heap).
- **No default max size** — grows as needed, bounded only by system RAM.
- **Faster class unloading** — when a ClassLoader dies, its metaspace region is freed immediately.
- Configurable ceiling via `-XX:MaxMetaspaceSize=<n>` (recommended in production to prevent runaway growth).

### Timeline of related changes
```
Java 6:   Interned strings in PermGen.
Java 7:   Interned strings moved to heap (fixes String.intern() OOM).
Java 8:   PermGen removed entirely. Metaspace introduced.
```

### Interview-worthy nuance
- **Metaspace can still OOM** in classloader-leak scenarios (frameworks like Groovy that generate lots of runtime classes). Always set `-XX:MaxMetaspaceSize` in production.
- The JVM triggers a GC when Metaspace usage exceeds `MetaspaceSize` (initial high-water mark, not a hard cap). Tune this to reduce GC frequency.
- **`OutOfMemoryError: Metaspace`** is a classic symptom of a **ClassLoader leak** — usually a `static` field in an older-loaded class holding a reference to a newly-loaded class, preventing the new ClassLoader from being GC'd.

---

## Q19: How does the JIT compiler decide when to compile a method?

### Short answer
The JVM starts by **interpreting bytecode**, tracks execution frequency via **counters**, and when a method (or loop) becomes "hot" enough, the **JIT compiles it to native code**. Modern HotSpot uses **tiered compilation** with a fast compiler (C1) and an optimizing compiler (C2).

### The tiered compilation flow

```
       ┌─────────────────┐
       │  Interpreter    │  ← always starts here (fast startup)
       │  (Tier 0)       │
       └────────┬────────┘
                │  method invocation counter or
                │  loop back-edge counter passes threshold
                ▼
       ┌─────────────────┐
       │  C1 (Tier 1-3)  │  ← quick compile, light optimization,
       │  "client"       │    starts collecting profiling data
       └────────┬────────┘
                │  method still hot + rich profile data available
                ▼
       ┌─────────────────┐
       │  C2 (Tier 4)    │  ← slow compile, aggressive optimization
       │  "server"       │    (inlining, escape analysis, loop unrolling,
       └─────────────────┘    branch prediction, vectorization)

       (If C2's assumption breaks → DEOPTIMIZATION → back to interpreter)
```

### The counters
- **Invocation counter** — increments each time the method is called.
- **Back-edge counter** — increments each time a loop iterates (catches hot loops in a cold method — enables **on-stack replacement / OSR**).
- Thresholds are set by `-XX:CompileThreshold=10000` (default varies by tier).

### C1 vs C2 tradeoffs

| Compiler | Compile speed | Native code quality | When it's used |
|---|---|---|---|
| C1 (client) | Fast | Basic optimizations | Startup & short-lived methods |
| C2 (server) | Slow | Aggressive optimizations | Long-running "hot" methods |

### Deoptimization
C2 makes **speculative optimizations** based on observed behavior:
- "This virtual method call always resolves to `SubclassA.foo()` → inline it."
- "This branch is never taken → skip it."

If reality later contradicts an assumption (e.g., a new subclass appears), the JVM **deoptimizes** — throws away the compiled code and reverts to interpretation, then eventually recompiles with updated assumptions.

### Useful flags
```bash
-XX:+PrintCompilation           # log every JIT compile
-XX:+PrintInlining              # what got inlined
-XX:-TieredCompilation          # disable C1, use only C2 (rarely needed)
-XX:CompileThreshold=10000      # invocations before compile
```

### Interview-worthy nuance
- **Warm-up matters** — a JVM benchmark's first N iterations run interpreted; only later measurements reflect JIT-compiled performance. This is why **JMH** enforces warm-up cycles.
- **OSR (On-Stack Replacement)** is why a single `while(true)` loop in `main()` still gets JIT-compiled without needing the method to return.
- The JVM stores compiled code in the **Code Cache** — if it fills up, further compilation stops (severe performance regression). Monitor with `-XX:ReservedCodeCacheSize`.

---

## Q20: What is escape analysis and how does it enable stack allocation of objects?

### Short answer
**Escape analysis** is a JIT optimization that determines whether an object's lifetime is **confined to a single method** (doesn't "escape"). If so, the JVM can **allocate it on the stack instead of the heap** — eliminating GC pressure entirely.

### The three levels of escape

```
   1. NoEscape         → object never leaves the method
                         → CAN be stack-allocated / scalar-replaced
                         → CAN eliminate synchronization

   2. ArgEscape        → object passed as arg to another method,
                         but doesn't escape that method either
                         → still eligible for optimization

   3. GlobalEscape     → object stored in a static/instance field,
                         or returned to caller
                         → MUST be heap-allocated
```

### Concrete example

```java
public int calculate(int x) {
    Point p = new Point(x, x * 2);   // does p escape?
    return p.x + p.y;                 // no — never leaves method
}
```

Escape analysis sees `p` doesn't escape. The JIT can:
1. **Stack allocation**: allocate `Point` on stack (auto-freed on return).
2. **Scalar replacement** (even better): eliminate the object entirely — treat `p.x` and `p.y` as two local `int` variables directly.

Result: **zero heap allocation, zero GC pressure**.

### When escape analysis FAILS

```java
static List<Point> globalList = new ArrayList<>();

public void bad(int x) {
    Point p = new Point(x, x * 2);
    globalList.add(p);               // p escapes to static field → GlobalEscape
}
```
`p` must live beyond the method → heap allocation required.

### Sync elimination

Escape analysis also enables **lock elimination**:
```java
public String concat(String a, String b) {
    StringBuffer sb = new StringBuffer();  // never escapes
    sb.append(a);   // append() is synchronized on `sb`
    sb.append(b);
    return sb.toString();
}
```
Since `sb` is thread-local (never escapes), the JIT **removes the synchronization entirely** — no lock overhead.

### Enabling / debugging
```bash
-XX:+DoEscapeAnalysis        # enabled by default in HotSpot
-XX:+EliminateAllocations    # enable scalar replacement
-XX:+PrintEscapeAnalysis     # debug info
```

### Interview-worthy nuance
- Escape analysis is why **short-lived objects are essentially free** in modern JVMs. The old "avoid `new`" performance advice is outdated.
- It's why **StringBuilder in local scope is as fast as manual char manipulation** — the JIT eliminates the object.
- It's why **autoboxing inside a hot loop** isn't always a disaster: if the boxed `Integer` doesn't escape, it can be scalar-replaced back to an `int`.

---

## Q21: Explain the Java Memory Model (JMM) — what does "happens-before" actually guarantee at the CPU level?

### Short answer
The JMM defines the **rules for when one thread's writes become visible to another thread's reads**. "Happens-before" is a formal ordering: if action A happens-before action B, then A's effects are guaranteed visible to B — the JVM/CPU can't reorder them across that boundary.

### Why we need JMM
Modern CPUs and compilers **reorder memory operations** for performance:
- CPU has **store buffers**, **read caches**, and **out-of-order execution**.
- Compiler can **reorder independent statements** for better register allocation.

Without JMM rules, this innocent code would break:
```java
int a = 0;
boolean flag = false;

// Thread 1
a = 42;
flag = true;

// Thread 2
if (flag) {
    print(a);   // could print 0! CPU may reorder writes.
}
```

### The happens-before relationships (memorize these)

1. **Program order**: within a single thread, earlier statements happen-before later ones.
2. **Monitor lock**: unlock happens-before subsequent lock of the same monitor.
3. **Volatile**: write to a volatile field happens-before every subsequent read of that field.
4. **Thread start**: `Thread.start()` happens-before any action in the started thread.
5. **Thread join**: any action in a thread happens-before `join()` returns in another thread.
6. **Interruption**: `interrupt()` happens-before the interrupted thread detects it.
7. **Constructor**: `Object` constructor happens-before finalizer starts.
8. **Transitivity**: if A hb B and B hb C, then A hb C.

### The fix using volatile
```java
int a = 0;
volatile boolean flag = false;

// Thread 1
a = 42;
flag = true;      // volatile write — flushes prior writes to main memory
                  //  AND prevents reordering: `a=42` MUST happen before this

// Thread 2
if (flag) {       // volatile read — refreshes cache from main memory
                  //  AND prevents reordering: subsequent reads see fresh values
    print(a);     // guaranteed to print 42
}
```

### At the CPU level (x86 example)
Volatile writes translate to CPU instructions that emit **memory barriers**:
- **StoreStore barrier** before volatile write — flush store buffer
- **StoreLoad barrier** after volatile write — most expensive; forces subsequent reads to see the write
- **LoadLoad + LoadStore** around volatile reads

On x86, the total-store-order (TSO) model makes many barriers cheap or free. On ARM/POWER (weaker memory models), volatile is much more expensive.

### Interview-worthy nuance
- **Happens-before is a partial order** — not everything is ordered w.r.t. everything else. Unrelated actions can be reordered.
- **Data races** = two conflicting memory accesses without any happens-before edge between them. **All bets are off** — the JMM makes no guarantees. This is why race conditions produce weird "impossible" values.
- **Final fields** get **extra guarantees** (see Q22) — safe publication without synchronization.
- The most-cited JMM specification: **JSR-133**, authored by Doug Lea and others in 2004.

---

## Q22: Why does `final` field initialization have special JMM guarantees?

### Short answer
Because of a special JMM rule: **`final` fields are guaranteed to be fully initialized before any other thread can see the object reference** — provided the reference doesn't leak from the constructor. This enables **safe publication of immutable objects without any synchronization**.

### The problem `final` solves
Without special guarantees, the following ancient bug could happen:
```java
class Point {
    int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }
}

static Point shared;

// Thread 1
shared = new Point(1, 2);

// Thread 2 (running concurrently)
Point p = shared;
if (p != null) {
    print(p.x);   // could print 0! Constructor writes might be reordered
                  // AFTER the reference publication.
}
```
JVM/CPU is free to publish `shared` before fully initializing the fields.

### With `final`
```java
class Point {
    final int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }
}
```
Now the JMM guarantees:
- All `final` field writes in the constructor happen-before the object reference is visible to another thread.
- No thread can see a partially-constructed `Point` with `x=0` or `y=0`.
- **No synchronization needed** for safe publication.

### The "escape" caveat
The guarantee ONLY holds if `this` doesn't escape the constructor:
```java
class Point {
    final int x, y;
    Point(int x, int y) {
        registry.add(this);   // BAD: `this` escapes before constructor finishes
        this.x = x;           // other threads may see this write... or not
        this.y = y;
    }
}
```
Registering `this` inside the constructor breaks the guarantee — another thread could grab it from `registry` before `x` and `y` are set.

### How this enables immutable classes
`String`, `Integer`, `LocalDate`, records, etc. all rely on this. That's why:
```java
public static final String GREETING = "hello";
```
can be safely accessed from any thread with no `volatile` or `synchronized` — the JVM guarantees the object is fully initialized.

### At the CPU level
The JMM inserts a **StoreStore barrier** at the end of any constructor that writes to a `final` field. This ensures the final field writes are flushed before the constructor returns.

### Interview-worthy nuance
- This is why **immutability is a concurrency superpower** — safe publication is FREE for immutable objects.
- Records (Java 14+) automatically make components `final` — one of the reasons they're recommended for value types.
- The pre-JSR-133 memory model (Java 1.4 and earlier) did NOT have this guarantee. `String` immutability was actually broken on multi-core systems.

---

## Q23: What is false sharing and how does `@Contended` fix it?

### Short answer
**False sharing** happens when two threads modify different variables that happen to sit on the **same CPU cache line** — the cache-coherence protocol treats it as contention on the same data, killing performance. `@Contended` fixes it by **padding the variables** so they land on separate cache lines.

### The mechanics: cache lines
Modern CPUs load memory in fixed-size chunks called **cache lines** (typically 64 bytes). When any byte in a cache line is modified, the ENTIRE line is invalidated in other cores' caches — they must re-fetch it.

### The false-sharing scenario

```
Cache line (64 bytes):
  ┌─────────────────────────────────────────────────────────┐
  │ int counterA │ int counterB │ ...................       │
  │  (Thread 1)  │  (Thread 2)  │                            │
  └─────────────────────────────────────────────────────────┘

  Thread 1 (Core 1):  counterA++;
     ↓ invalidates cache line in Core 2

  Thread 2 (Core 2):  counterB++;   ← must re-fetch! Cache miss!
     ↓ invalidates cache line in Core 1

  Thread 1 again: counterA++;   ← must re-fetch! Cache miss!
```

Even though the two threads touch **different variables**, they thrash each other's caches. Performance can drop **10-100x** vs the "obvious" correct performance.

### Reproducing the problem
```java
class Counter {
    long a;   // Thread 1 increments
    long b;   // Thread 2 increments — likely same cache line as `a`!
}
```
Two threads running `a++` and `b++` in tight loops will be slower than expected due to cache-line ping-pong.

### The fix: `@Contended` (Java 8+)
```java
import jdk.internal.vm.annotation.Contended;

class Counter {
    @Contended long a;
    @Contended long b;
}
```

The JVM adds padding around each `@Contended` field, ensuring they land on separate cache lines:
```
  Cache line 1:  [padding][a][padding]   ← Thread 1 owns
  Cache line 2:  [padding][b][padding]   ← Thread 2 owns
```

Now the two counters can be updated concurrently with **no cache-coherence traffic** between them.

### The manual "hand-padded" alternative
Before `@Contended`, engineers manually padded:
```java
class PaddedLong {
    public long value;
    public long p1, p2, p3, p4, p5, p6, p7;  // 56 bytes of padding
    // Total: 64 bytes = 1 cache line
}
```
Ugly but effective. `@Contended` cleans this up.

### Real-world usage
Look at JDK source — `LongAdder`, `ConcurrentHashMap`'s counter cells, and `ForkJoinPool`'s task queues all use `@Contended` internally. This is why `LongAdder` scales better than `AtomicLong` under high contention: it splits the counter into per-thread cells that don't false-share.

### Interview-worthy nuance
- `@Contended` is in `jdk.internal.vm.annotation` — **NOT** in the public API. To use it in your code you need `-XX:-RestrictContended` (or `--add-exports` in modern Java). Most engineers use hand-padding or `LongAdder` instead.
- False sharing is subtle: **it doesn't cause bugs, just performance regressions** that only show up on multi-core hardware under load. Very hard to spot without profiling tools like Intel VTune or Linux `perf`.
- **Cache-friendly data layout** (structs of arrays vs arrays of structs) is a big topic in high-performance Java — see Netty, Chronicle, Aeron for examples.

---

# Section: Garbage Collection

## Q24: Compare Serial, Parallel, CMS, G1, ZGC, Shenandoah — when would you choose each?

### Short answer
Each GC picks a different point on the **throughput vs latency vs footprint** triangle. Modern default is **G1**; for sub-millisecond pauses on large heaps, use **ZGC** or **Shenandoah**.

### The comparison matrix

| GC | Pause time | Throughput | Heap size | Threads | When to use |
|---|---|---|---|---|---|
| **Serial** | High | Low | Small (<100MB) | Single | Client apps, tiny heaps, embedded |
| **Parallel** | High | **Highest** | Small-Medium | Multi | Batch jobs — throughput-only, no user waiting |
| **CMS** (deprecated) | Low | Medium | Medium (~4GB) | Multi | Web apps pre-Java-9. Removed in Java 14. |
| **G1** (default since Java 9) | Medium-Low | High | Medium-Large (4-64GB) | Multi | General purpose, mixed workloads |
| **ZGC** | **Sub-ms** | High | Very Large (up to TB) | Multi | Latency-critical (trading, real-time) |
| **Shenandoah** | **Sub-ms** | High | Very Large | Multi | Similar to ZGC, Red Hat's offering |

### Quick descriptions

**Serial GC** (`-XX:+UseSerialGC`)
- Single-threaded. Stops all app threads during GC.
- Simple, low memory overhead.
- Suitable ONLY for tiny apps.

**Parallel GC** (`-XX:+UseParallelGC`)
- Multi-threaded stop-the-world.
- Maximum throughput — but pauses can be **seconds** on large heaps.
- Default in Java 8. Great for batch/ETL jobs.

**CMS — Concurrent Mark Sweep** (`-XX:+UseConcMarkSweepGC`, **removed in Java 14**)
- Mostly concurrent with app threads — short pauses.
- Suffered from fragmentation (no compaction).
- Superseded by G1.

**G1 — Garbage First** (`-XX:+UseG1GC`, default since Java 9)
- Divides heap into ~2048 **regions** (typically 1-32 MB each).
- Collects the region with the most garbage first (hence "Garbage First").
- **Predictable pause target**: `-XX:MaxGCPauseMillis=200` (default).
- Handles multi-GB heaps well.

**ZGC** (`-XX:+UseZGC`, GA since Java 15)
- **Sub-millisecond pauses regardless of heap size** (10 GB or 10 TB).
- Uses colored pointers + load barriers for concurrent relocation.
- Slightly higher CPU overhead, but pause times are nearly constant.

**Shenandoah** (`-XX:+UseShenandoahGC`, GA in Java 15)
- Similar goals to ZGC (sub-ms pauses).
- Uses **Brooks pointers** (forwarding pointers on each object).
- Developed by Red Hat, included in OpenJDK.

### How to choose
```
   Heap < 1GB?              → Parallel or Serial
   Throughput-critical?     → Parallel
   Latency-sensitive        → G1 (default choice)
   Heap > 32GB or need
   consistent sub-10ms      → ZGC or Shenandoah
```

### Interview-worthy nuance
- **G1 is the sane default** for 99% of modern apps. Only tune away if you have measured evidence.
- **CMS was removed** in Java 14 — don't recommend it. If interviewer asks about it, mention it's deprecated.
- ZGC/Shenandoah trade a bit of throughput for **massive latency wins**. They also require **larger heaps** (headroom for concurrent collection).

---

## Q25: What is the generational hypothesis and why does it work?

### Short answer
**Most objects die young.** Empirical observation: the vast majority of objects created in a program become unreachable shortly after allocation. GCs exploit this by **splitting the heap into "young" and "old" generations** and collecting them differently — cheaply and frequently for young, expensively but rarely for old.

### The observation (in production JVMs)

```
Object lifetime distribution (typical):

  Age (bytes allocated after object)
    0 ────────────────────────────────────────────────────
        │ █████████████████████████████████ ~90% die here (young)
        │
        │ ██                                 ~8% survive to middle age
        │ █                                 ~2% survive long term (old gen)
```

- Short-lived: request-scoped objects, temp strings, iterators, boxed values.
- Long-lived: caches, config, thread-local state, connection pools.

### The heap structure

```
   Young Generation (small, collected often)
   ┌───────────────────────────────────────────────────┐
   │  Eden  │  Survivor 0  │  Survivor 1  │             │
   │ (new)  │              │              │             │
   └───────────────────────────────────────────────────┘
                                     ↓ (aged objects promoted)
   Old Generation (large, collected rarely)
   ┌───────────────────────────────────────────────────┐
   │                                                    │
   │  Long-lived objects                                │
   │                                                    │
   └───────────────────────────────────────────────────┘
```

- **Eden**: where all new objects are allocated.
- **Survivor 0/1**: two smaller spaces used to shuttle survivors during young GC.
- **Old Gen**: objects that survived enough young GCs get **promoted** here.

### Why this design is efficient

1. **Cheap young GC**: since 90% of Eden is garbage, copying the 10% survivors to Survivor space is fast. No mark step needed on the dead objects.
2. **Rare old GC**: old gen fills slowly (only promoted objects). Expensive full GC only needed occasionally.
3. **Cache-friendly allocation**: Eden is a **bump-the-pointer** arena — allocation is 2 machine instructions (increment pointer, check bounds). Faster than malloc.
4. **Better locality**: young objects live together in Eden — good for CPU cache.

### The tenuring process

```
1. Object allocated in Eden.
2. Young GC runs:
   - Live objects in Eden + Survivor0 copied to Survivor1.
   - Object's "age" counter incremented.
3. Repeat.
4. Once age exceeds -XX:MaxTenuringThreshold (default ~15),
   object is promoted to Old Gen.
```

### Interview-worthy nuance
- The generational hypothesis **fails** for some workloads: bulk-load caches, in-memory databases, huge working sets. For these, ZGC (which is generational since Java 21) or a very large young gen may work better.
- **Premature promotion** = objects promoted to old gen before they die → increases old gen GC frequency. Fix: enlarge Young Gen (`-Xmn`).
- **Card tables** track old→young pointer references so young GC doesn't need to scan the entire old gen for roots. Critical to making young GC cheap.

---

## Q26: Walk through a minor GC and a major GC step-by-step

### Short answer
- **Minor GC** (Young GC): copies live objects from Eden + one Survivor space to the other Survivor space, promotes aged objects to Old Gen. Fast, frequent.
- **Major GC** (Old GC / Full GC): reclaims Old Gen, sometimes the entire heap. Slow, rare.

### Minor GC step-by-step

```
  Initial state:
  ┌─────────── Eden ───────────┐  ┌── S0 ──┐  ┌── S1 ──┐
  │ [A] [B] [C] [D] [E] [F]     │  │ (empty)│  │ (empty)│
  └─────────────────────────────┘  └────────┘  └────────┘

  Live: A, C, E   |  Dead: B, D, F

Step 1: STW pause begins (all app threads stop).

Step 2: Roots identified (thread stacks, static refs, JNI refs, card table).

Step 3: Reachable objects from Eden + S0 traced.
        Live objects copied to S1 (with age++ counter).

  ┌─────────── Eden ───────────┐  ┌── S0 ──┐  ┌── S1 ────┐
  │ (will be cleared)           │  │(empty) │  │ [A][C][E]│
  └─────────────────────────────┘  └────────┘  └──────────┘

Step 4: Objects with age > MaxTenuringThreshold promoted to Old Gen.

Step 5: Eden and S0 marked as empty (no explicit deletion — they'll be
        overwritten by new allocations).

Step 6: S0 and S1 roles swap (S1 becomes the "from" space next time).

Step 7: STW pause ends. App threads resume.
```

**Duration**: typically 10-100 ms for a heap of a few GB with Parallel/G1.
**Frequency**: every few seconds under normal load.

### Major GC step-by-step (simplified for CMS/G1)

```
Step 1: Initial Mark (STW) — very short.
        Mark objects directly reachable from GC roots.

Step 2: Concurrent Marking — runs alongside app.
        Trace the reference graph, marking all reachable old-gen objects.

Step 3: Final Mark / Remark (STW) — short.
        Catch up on any references changed by app threads during concurrent marking.

Step 4: Concurrent Cleanup — runs alongside app.
        - CMS: sweeps (deletes) unmarked objects.
        - G1: evacuates (copies) survivors from high-garbage regions,
              then reclaims those regions.

Step 5: (For non-compacting GCs) fragmentation accumulates — may trigger a
        FULL GC compaction pause eventually.
```

### What triggers each

| GC Type | Trigger |
|---|---|
| Minor GC | Eden fills up |
| Major GC (CMS/G1) | Old Gen usage crosses threshold (`-XX:CMSInitiatingOccupancyFraction`, default ~70%) |
| Full GC | Old Gen fills before concurrent GC completes; `System.gc()` called; Metaspace fills |

### The dreaded "Full GC"

A **Full GC** stops the world and collects the entire heap (young + old + metaspace). Can pause the JVM for **seconds** on multi-GB heaps.

Common causes:
- **Promotion failure**: young GC tries to promote an object to old gen, but old gen has no space.
- **Concurrent mode failure**: CMS/G1 concurrent cycle can't finish before old gen fills.
- **Explicit `System.gc()`** (best avoided in production; use `-XX:+DisableExplicitGC`).
- **Metaspace full**.

### Interview-worthy nuance
- Modern G1 tries **very hard** to avoid Full GCs by triggering concurrent cycles early enough. If you see Full GCs in production, your heap is too small OR your concurrent GC can't keep up.
- **Stop-the-world pauses hide behind averages**. A P99 latency spike almost always correlates with GC. Monitor `-Xlog:gc*` output.

---

## Q27: What are GC roots?

### Short answer
**GC roots** are references that the GC treats as **always reachable** — the starting points for the reachability trace. An object is "alive" if and only if some GC root can reach it via a chain of references.

### The main categories of GC roots

```
   Type                          Where they live               Example
   ───────────────────────────   ──────────────────────────    ─────────────────────

   1. Thread Stack References    Local variables & args        for (int i=0; ...)
                                 in each active method frame     Person p = ...;

   2. Static Fields              Loaded classes in Metaspace   class Config {
                                                                  static Cache cache;
                                                                }

   3. JNI References             Native code holding refs      JNI global refs from
                                 via JNI global handles          C/C++ interop

   4. Synchronization Monitors   Objects currently locked      synchronized(obj) { ... }

   5. Thread Objects             Live Thread instances         new Thread(...).start()

   6. System Classes             Bootstrap classloader         String.class, etc.
                                 classes never unload

   7. JVM Internal Structures    ClassLoader references,       Never GC'd until JVM exit
                                 exception handlers, etc.
```

### How reachability works

```
      GC Roots (always alive)
    ┌───────┬────────┬─────────┐
    │Static │ Stack  │  JNI    │
    │ Field │  Var   │ handle  │
    └───┬───┴───┬────┴────┬────┘
        │       │         │
        ▼       ▼         ▼
      Obj1    Obj2      Obj3       ← reachable (marked live)
        │       │
        ▼       ▼
      Obj4    Obj5                 ← reachable (via Obj1, Obj2)

      Obj6    Obj7                 ← NOT reachable → garbage
```

The GC's mark phase does a **BFS/DFS from all GC roots**, marking every visited object as live. Unmarked objects are garbage.

### Classic memory-leak patterns from GC roots

**1. Static field caching**
```java
class Cache {
    static Map<String, HeavyObject> cache = new HashMap<>();
    // If you never remove entries, cache grows forever.
    // The static field is a GC root — nothing can be reclaimed.
}
```

**2. Long-lived thread + local reference**
```java
Thread daemon = new Thread(() -> {
    HeavyObject h = new HeavyObject();
    while (true) { doWork(); }  // h stays alive forever via thread stack
});
```

**3. Unregistered listener**
```java
button.addClickListener(new MyListener(largeContext));
// If you forget to removeListener(), largeContext leaks
// via button (which is reachable) → listener → context.
```

### Interview-worthy nuance
- `ThreadLocal` variables live on the `Thread` object, which is a GC root. In thread pools where threads are reused, **ThreadLocals that aren't cleaned up leak indefinitely** — huge cause of leaks in Tomcat/Spring apps.
- Use **Eclipse MAT's "path to GC roots"** feature to find leaks. It shows why a suspected-leaked object is still reachable.
- **Weak/Soft/Phantom references** (Q29) provide "conditional reachability" — refs that don't count as GC roots.

---

## Q28: How does G1's Remembered Set (RSet) enable region-based collection?

### Short answer
G1 divides the heap into ~2048 **regions** and wants to collect any subset of regions independently. But an object in region A might reference an object in region B. Without help, G1 would need to scan the **entire heap** to find such cross-region references. The **Remembered Set (RSet)** solves this by keeping a **per-region index of "who points to me from other regions."**

### The problem

```
Region A          Region B            Region C
┌──────────┐     ┌──────────┐        ┌──────────┐
│  Obj X   │────▶│  Obj Y   │        │  Obj Z   │
│          │     │          │        │          │
└──────────┘     └──────────┘        └──────────┘

If we want to collect Region B, we need to know:
  "Is Obj Y still reachable?"

Without scanning the entire heap, how do we know that Obj X (in Region A)
references Obj Y (in Region B)?
```

### The solution: RSet per region

Each region maintains an **RSet** — a list of card-table entries pointing into this region:

```
Region B's RSet:
  ┌────────────────────────────────┐
  │ Region A, card 12 → Obj Y      │  ← "Obj X (in region A, card 12) points here"
  │ Region D, card 3  → Obj Y      │
  │ Region E, card 7  → Obj Y2     │
  └────────────────────────────────┘
```

When collecting Region B, G1 checks Region B's RSet to find all external references pointing INTO Region B. Only those specific card regions need to be scanned — not the whole heap.

### How RSets are populated (write barriers)

Every reference assignment `objA.field = objB` triggers a **write barrier**:
```
If objA and objB are in different regions:
  Add "objA's location" to objB's region's RSet.
```

The write barrier is cheap (a few CPU instructions), but its cumulative cost is one of G1's overhead components.

### The card table

To keep RSets compact, they don't track individual objects — they track **cards** (typically 512-byte chunks). An RSet entry means "somewhere in card X of region A, there's a pointer to this region."

During collection, only cards mentioned in the RSet are scanned to find the actual pointers.

### Why this enables "Garbage First"

Because G1 can collect any subset of regions independently, it can pick the regions with the **most garbage first** — maximizing bytes reclaimed per unit of pause time. This is the origin of the name **G1 = Garbage First**.

### Interview-worthy nuance
- **RSet maintenance is expensive** — it's why G1 has slightly lower throughput than Parallel GC (which doesn't need RSets since it does full-heap collections).
- **Humongous objects** (>50% of a region) get special treatment — allocated in contiguous humongous regions, always live during young GC.
- ZGC and Shenandoah **don't use RSets** — they use load barriers and forwarding pointers for a fundamentally different approach.

---

## Q29: SoftReference vs WeakReference vs PhantomReference — with real use cases

### Short answer
Java has three "non-strong" reference types that let the GC reclaim objects at different urgency levels:
- **SoftReference**: reclaimed only when heap is low → **memory-sensitive caches**
- **WeakReference**: reclaimed at next GC cycle → **canonicalizing maps, listener registries**
- **PhantomReference**: never dereferenceable; used for **post-mortem cleanup** (replacement for `finalize()`)

### Reference strength hierarchy

```
   Strong  ────► Never collected (until unreferenced)
   Soft    ────► Collected only when JVM would OOM
   Weak    ────► Collected at next GC cycle
   Phantom ────► Already collected; you're just notified when
```

### SoftReference — memory-sensitive caching

```java
SoftReference<Image> cached = new SoftReference<>(loadImage(url));

// later...
Image img = cached.get();
if (img == null) {
    img = loadImage(url);   // GC reclaimed it under memory pressure
    cached = new SoftReference<>(img);
}
```

**Behavior**: JVM keeps SoftReferences as long as possible. Only clears them when heap is nearly full (right before throwing OOM).

**Real use**: Image caches, computed-result caches, class metadata caches. **`WeakHashMap`** is often the WRONG choice for caching — it clears aggressively; use `Caffeine` or `SoftReference`-based cache instead.

### WeakReference — canonicalizing / listener maps

```java
Map<Person, ExtraInfo> extra = new WeakHashMap<>();
extra.put(person, new ExtraInfo(...));

// If nothing else references `person`, GC will remove it from the map.
```

**Behavior**: cleared at the very next GC that finds it weakly-reachable.

**Real uses**:
- **`WeakHashMap`**: annotations, class metadata that shouldn't prevent class unloading.
- **`ThreadLocal.ThreadLocalMap`**: uses weak references to keys so unreferenced `ThreadLocal` instances can be GC'd.
- **Listener registries** where you don't want the listener registration to keep the listener alive.

### PhantomReference — cleanup after death

```java
ReferenceQueue<MyResource> queue = new ReferenceQueue<>();
PhantomReference<MyResource> ref = new PhantomReference<>(resource, queue);

// In a cleanup thread:
Reference<?> r = queue.remove();   // blocks until resource is GC'd
freeNativeMemory();                // clean up native resources
```

**Behavior**: `get()` **always returns null**. The object is already unreachable; PhantomReference is just a **notification mechanism**.

**Real uses**:
- **Modern replacement for `finalize()`** (which is deprecated in Java 9, removed in Java 18+).
- **`java.lang.ref.Cleaner`** (Java 9+) is built on PhantomReference — used by `DirectByteBuffer` to free native memory.
- Native resource cleanup (file handles, sockets, off-heap memory).

### Comparison table

| | SoftReference | WeakReference | PhantomReference |
|---|---|---|---|
| Cleared by GC | Only under memory pressure | At next GC | Object already gone at time of notify |
| `get()` returns | The object (until cleared) | The object (until cleared) | Always `null` |
| Use case | Memory-sensitive cache | Canonicalizing map, listener refs | Cleanup callbacks |
| Requires ReferenceQueue | Optional | Optional | **Required** |

### Interview-worthy nuance
- **`finalize()` is dead**. Use `Cleaner` + PhantomReference.
- **SoftReference is unpredictable** — different JVMs treat memory pressure differently. Prefer size-bounded caches (Caffeine's `maximumSize()`) over softref caches.
- **WeakHashMap has a subtle bug potential**: entries can disappear between two `get()` calls without warning. Not safe for iteration.

---

## Q30: How would you diagnose a memory leak in a production JVM?

### Short answer
1. **Detect** with monitoring (heap usage grows monotonically, Full GCs get frequent).
2. **Capture** a heap dump (`jmap -dump` or `-XX:+HeapDumpOnOutOfMemoryError`).
3. **Analyze** in Eclipse MAT: look at **dominator tree** and **"path to GC roots"** to find who's holding what.
4. **Fix** the root cause (usually a static collection, ThreadLocal, or listener not removed).

### Step-by-step diagnostic playbook

**Step 1: Confirm it's a leak (not just growth)**
```bash
jstat -gcutil <pid> 5000     # print GC stats every 5s
```
Signs of a leak:
- Old Gen usage keeps climbing after Full GCs.
- Full GC frequency increases over time.
- Eventually: `OutOfMemoryError: Java heap space`.

**Step 2: Capture a heap dump**
```bash
# On demand:
jmap -dump:live,format=b,file=heap.hprof <pid>

# Or auto-dump on OOM (SET THIS IN PRODUCTION):
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/app/heap.hprof
```

**Step 3: Analyze with Eclipse MAT**

Load the `.hprof` file into **Eclipse Memory Analyzer**. Key views:

- **Dominator Tree**: shows objects sorted by "retained heap" (how much memory would be freed if this object were GC'd). The top of the list is usually the leak source.
- **Histogram**: number of instances per class. Suspicious if a class has millions of instances.
- **Path to GC Roots**: for a suspected leak, shows the chain of references keeping it alive.
- **Leak Suspects Report**: MAT's automatic analysis flags common leak patterns.

**Step 4: Common culprits**

| Symptom | Likely cause |
|---|---|
| `HashMap` / `ArrayList` with millions of entries | Unbounded cache, missing eviction |
| Threads with big local retention | ThreadLocal not cleared in pool thread |
| ClassLoader instances not GC'd | App-server hot-reload leak |
| `char[]` / `byte[]` at the top | Log accumulation, huge strings |
| `DirectByteBuffer` growing | Off-heap memory leak (Netty, NIO) |

### Live analysis tools

```bash
jcmd <pid> GC.class_histogram     # class-by-class instance counts (live)
jcmd <pid> Thread.print           # thread dump (find stuck/waiting threads)
jcmd <pid> VM.native_memory       # native memory tracking (needs -XX:NativeMemoryTracking)
jstack <pid>                      # thread dump alternative
jvisualvm / jconsole              # visual live monitoring
```

### Modern alternatives
- **JFR (Java Flight Recorder)** — records events over time with tiny overhead. `-XX:StartFlightRecording=duration=5m,filename=recording.jfr`
- **JMC (Java Mission Control)** — analyzes JFR files.
- **Async profiler** — sampling profiler for hot allocation sites.

### Interview-worthy nuance
- **Enable heap dump on OOM in production** — the dump is often your only way to diagnose an intermittent leak.
- Heap dumps can be **huge** (equal to heap size) and take **minutes** to write — pausing the JVM. Have a plan for space and time.
- **A leak in Metaspace** (not heap) usually means a **ClassLoader leak** — a static field or thread holding a reference to an older-loaded class, preventing the ClassLoader from being GC'd.

---

## Q31: What is a "stop-the-world" pause and how do concurrent GCs minimize it?

### Short answer
A **stop-the-world (STW) pause** is when the JVM halts ALL application threads to safely perform GC work. Concurrent GCs (G1, ZGC, Shenandoah) do most of their work **alongside** the app, keeping STW pauses down to milliseconds.

### Why STW exists
Some GC operations need a **consistent snapshot** of memory. If the app is mutating references while GC is scanning, the GC can miss objects (or worse, corrupt state).

Historically, ALL GC work happened in an STW pause:
- Serial GC: entire GC is STW.
- Parallel GC: entire GC is STW (just runs in parallel across threads).

### STW pauses vs pauseless illusion

```
                    Time →

Serial:      [====== STW ======] [====== STW ======]   ← long, infrequent
Parallel:    [=STW=] [=STW=] [=STW=]                    ← shorter, more frequent
G1:          [S]  [concurrent...][S]  [S] [conc.][S]    ← very short STW + concurrent phases
ZGC:         [.] [......concurrent......] [.]           ← <1ms STW even for TB heaps
```

### How concurrent GCs reduce STW

**G1's technique**:
1. **Initial Mark (STW)** — brief, marks roots.
2. **Concurrent Marking** — traces the graph WHILE the app runs.
3. **Final Mark (STW)** — brief, catches recent mutations.
4. **Cleanup / Evacuation (STW)** — copies survivors, reclaims regions.

Only steps 1, 3, 4 are STW — and each is short (<50ms typically).

**ZGC's technique** (even more aggressive):
- Uses **colored pointers**: some bits of the pointer encode GC state.
- Uses **load barriers**: every reference read checks these bits and can trigger relocation on the fly.
- Result: **object relocation happens CONCURRENTLY** — no STW for it.
- Only very short STW phases for root scanning.

### The "safepoint" problem
Even a "concurrent" GC needs some STW moments. To pause all threads safely, they must reach a **safepoint** — a known-good state where the JVM knows all thread stack contents.

Threads reach safepoints at:
- Method returns
- Loop back-edges (in some JITs)
- Native call boundaries

A thread running a tight loop without safepoints can **delay the STW pause** — this is called **time-to-safepoint (TTSP)**.

### How to minimize STW impact

```bash
# G1 tuning:
-XX:MaxGCPauseMillis=100          # target pause time (soft goal)
-XX:G1HeapRegionSize=16m          # tune region size

# ZGC:
-XX:+UseZGC                       # sub-ms pauses
-XX:+UnlockExperimentalVMOptions  # (pre-Java 15)

# Logging (KNOW YOUR PAUSES):
-Xlog:gc*:file=gc.log             # detailed GC logging
-Xlog:safepoint*                  # time-to-safepoint logging
```

### Interview-worthy nuance
- **P99 latency spikes are almost always GC pauses.** Even a "concurrent" GC has short STW phases; those show up in tail latency.
- **Sub-ms GC (ZGC/Shenandoah) doesn't mean pauseless** — it means each pause is under a millisecond. There can still be many of them.
- **Full GC is a special STW event** — it collects everything. Modern GCs try hard to avoid it; seeing Full GCs in production is a red flag.

---

