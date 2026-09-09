# HashMap — Deep Dive

> Personal study notes for interview + knowledge. Fill in each section in my own words after understanding it.

---

## Table of Contents

**[Phase 1 — Mental Model](#phase-1--mental-model)**
- [1.1 What is hashing?](#11-what-is-hashing)
- [1.2 What is a hash table?](#12-what-is-a-hash-table)
- [1.3 HashMap vs Array vs LinkedList](#13-hashmap-vs-array-vs-linkedlist)
- [1.4 Collision handling strategies (chaining vs open addressing)](#14-collision-handling-strategies-chaining-vs-open-addressing)
- [1.5 Self-check](#15-self-check)

**[Phase 2 — Java-Specific Grounding](#phase-2--java-specific-grounding)**
- [2.1 The `Node<K,V>` inner class](#21-the-nodekv-inner-class)
- [2.2 Fields on the HashMap class](#22-fields-on-the-hashmap-class)
- [2.3 Class hierarchy — Map, AbstractMap, and cousins](#23-class-hierarchy--map-abstractmap-and-cousins)
- [2.4 Key constants](#24-key-constants)
- [2.5 `tableSizeFor()` — power-of-2 capacity rounding](#25-tablesizefor--power-of-2-capacity-rounding)
- [2.6 Complexity table](#26-complexity-table)
- [2.7 Self-check](#27-self-check)

**[Phase 3 — How Operations Work Internally](#phase-3--how-operations-work-internally)**
- [3.1 The hash function — XOR shift and internal `hash()` helper](#31-the-hash-function--xor-shift-and-internal-hash-helper)
- [3.2 Bucket index — `(n - 1) & hash`](#32-bucket-index--n---1--hash)
- [3.3 `put(k, v)` — full step-by-step](#33-putk-v--full-step-by-step)
- [3.4 `get(k)` — full step-by-step](#34-getk--full-step-by-step)
- [3.5 Collision handling — chaining with linked list](#35-collision-handling--chaining-with-linked-list)
- [3.6 Treeification (Java 8+)](#36-treeification-java-8)
- [3.7 Resize (rehashing)](#37-resize-rehashing)
- [3.8 Load factor 0.75 — the space/time trade-off](#38-load-factor-075--the-spacetime-trade-off)
- [3.9 Self-check](#39-self-check)

**[Phase 4 — Build It Yourself](#phase-4--build-it-yourself)**
- [4.1 Design a simple HashMap](#41-design-a-simple-hashmap)
- [4.2 Method-by-method walkthrough](#42-method-by-method-walkthrough)
- [4.3 Full reference implementation (with chaining)](#43-full-reference-implementation-with-chaining)
- [4.4 Adding resize/rehash](#44-adding-resizerehash)
- [4.5 Common bugs (log the ones YOU hit)](#45-common-bugs-log-the-ones-you-hit)
- [4.6 Practice checklist](#46-practice-checklist)

**[Phase 5 — HashMap vs Alternatives](#phase-5--hashmap-vs-alternatives)**
- [5.1 HashMap vs Hashtable](#51-hashmap-vs-hashtable)
- [5.2 HashMap vs LinkedHashMap](#52-hashmap-vs-linkedhashmap)
- [5.3 HashMap vs TreeMap](#53-hashmap-vs-treemap)
- [5.4 HashMap vs ConcurrentHashMap](#54-hashmap-vs-concurrenthashmap)
- [5.5 HashMap vs WeakHashMap](#55-hashmap-vs-weakhashmap)
- [5.6 HashMap vs IdentityHashMap](#56-hashmap-vs-identityhashmap)
- [5.7 HashMap vs EnumMap](#57-hashmap-vs-enummap)
- [5.8 Decision tree — which map when](#58-decision-tree--which-map-when)
- [5.9 When to use `int[]` array instead of HashMap](#59-when-to-use-int-array-instead-of-hashmap)

**[Phase 6 — Interview Deep Cuts](#phase-6--interview-deep-cuts)**
- [6.1 The `equals`/`hashCode` contract — critical for HashMap](#61-the-equalshashcode-contract--critical-for-hashmap)
- [6.2 Iterator + fail-fast + `modCount`](#62-iterator--fail-fast--modcount)
- [6.3 Thread safety — race conditions in single-threaded misuse](#63-thread-safety--race-conditions-in-single-threaded-misuse)
- [6.4 What happens when you mutate a key after inserting](#64-what-happens-when-you-mutate-a-key-after-inserting)
- [6.5 Null keys and null values](#65-null-keys-and-null-values)
- [6.6 Immutable keys — best practice](#66-immutable-keys--best-practice)
- [6.7 Load factor & capacity tuning](#67-load-factor--capacity-tuning)
- [6.8 `Map.of()`, `Map.copyOf()`, `Map.entry()` — Java 9+ factories](#68-mapof-mapcopyof-mapentry--java-9-factories)
- [6.9 Serialization internals](#69-serialization-internals)
- [6.10 Self-check](#610-self-check)

**[Phase 7 — Coding Problems: Core Patterns](#phase-7--coding-problems-core-patterns)**
- [7.1 Two Sum (LC 1)](#71-two-sum-lc-1)
- [7.2 Contains Duplicate (LC 217)](#72-contains-duplicate-lc-217)
- [7.3 Valid Anagram (LC 242)](#73-valid-anagram-lc-242)
- [7.4 Group Anagrams (LC 49)](#74-group-anagrams-lc-49)
- [7.5 First Non-Repeating Character (LC 387)](#75-first-non-repeating-character-lc-387)
- [7.6 Ransom Note (LC 383)](#76-ransom-note-lc-383)
- [7.7 Longest Palindrome (LC 409)](#77-longest-palindrome-lc-409)
- [7.8 Top K Frequent Elements (LC 347)](#78-top-k-frequent-elements-lc-347)
- [7.9 Isomorphic Strings (LC 205)](#79-isomorphic-strings-lc-205)
- [7.10 Word Pattern (LC 290)](#710-word-pattern-lc-290)
- [7.11 Roman to Integer (LC 13)](#711-roman-to-integer-lc-13)
- [7.12 Subarray Sum Equals K (LC 560)](#712-subarray-sum-equals-k-lc-560)
- [7.13 Contains Duplicate II (LC 219)](#713-contains-duplicate-ii-lc-219)
- [7.14 Longest Consecutive Sequence (LC 128)](#714-longest-consecutive-sequence-lc-128)
- [7.15 Happy Number (LC 202)](#715-happy-number-lc-202)
- [7.16 Intersection of Two Arrays II (LC 350)](#716-intersection-of-two-arrays-ii-lc-350)
- [7.17 Sudoku Validator (LC 36)](#717-sudoku-validator-lc-36)
- [7.18 The 6 transferable HashMap techniques](#718-the-6-transferable-hashmap-techniques)
- [7.19 Self-check](#719-self-check)

**[Phase 8 — Coding Problems: Advanced Patterns](#phase-8--coding-problems-advanced-patterns)**
- [8.1 Longest Substring Without Repeating Characters (LC 3)](#81-longest-substring-without-repeating-characters-lc-3)
- [8.2 Find All Anagrams in a String (LC 438)](#82-find-all-anagrams-in-a-string-lc-438)
- [8.3 Longest Repeating Character Replacement (LC 424)](#83-longest-repeating-character-replacement-lc-424)
- [8.4 Longest Substring with At Most K Distinct Characters (LC 340)](#84-longest-substring-with-at-most-k-distinct-characters-lc-340)
- [8.5 Minimum Window Substring (LC 76)](#85-minimum-window-substring-lc-76)
- [8.6 Continuous Subarray Sum (LC 523)](#86-continuous-subarray-sum-lc-523)
- [8.7 4Sum II (LC 454)](#87-4sum-ii-lc-454)
- [8.8 Design HashMap (LC 706)](#88-design-hashmap-lc-706)
- [8.9 Insert Delete GetRandom O(1) (LC 380)](#89-insert-delete-getrandom-o1-lc-380)
- [8.10 Clone Graph (LC 133)](#810-clone-graph-lc-133)
- [8.11 Encode and Decode TinyURL (LC 535)](#811-encode-and-decode-tinyurl-lc-535)
- [8.12 LFU Cache (LC 460)](#812-lfu-cache-lc-460)
- [8.13 The full arsenal (all 28 problems + the 6 techniques)](#813-the-full-arsenal-all-28-problems--the-6-techniques)
- [8.14 Self-check](#814-self-check)

**[Phase 9 — Thread-safe Maps Deep Dive](#phase-9--thread-safe-maps-deep-dive)**
- [9.1 Why HashMap breaks under concurrent use](#91-why-hashmap-breaks-under-concurrent-use)
- [9.2 The famous Java 7 infinite loop bug](#92-the-famous-java-7-infinite-loop-bug)
- [9.3 `Collections.synchronizedMap` wrapper](#93-collectionssynchronizedmap-wrapper)
- [9.4 ConcurrentHashMap — Java 7 segments vs Java 8+ CAS + synchronized](#94-concurrenthashmap--java-7-segments-vs-java-8-cas--synchronized)
- [9.5 ConcurrentHashMap internals (Node, TreeBin, ForwardingNode)](#95-concurrenthashmap-internals-node-treebin-forwardingnode)
- [9.6 ConcurrentSkipListMap — the sorted concurrent map](#96-concurrentskiplistmap--the-sorted-concurrent-map)
- [9.7 Decision guide — which thread-safe map when](#97-decision-guide--which-thread-safe-map-when)
- [9.8 Self-check](#98-self-check)

**[Phase 10 — API Traps & Practical Gotchas](#phase-10--api-traps--practical-gotchas)**
- [10.1 `keySet` / `entrySet` / `values` are VIEWS](#101-keyset--entryset--values-are-views)
- [10.2 `getOrDefault` vs `computeIfAbsent`](#102-getordefault-vs-computeifabsent)
- [10.3 `merge()` — the underused power tool](#103-merge--the-underused-power-tool)
- [10.4 `putIfAbsent` semantics](#104-putifabsent-semantics)
- [10.5 Safely removing during iteration](#105-safely-removing-during-iteration)
- [10.6 Iteration order guarantees (or lack thereof)](#106-iteration-order-guarantees-or-lack-thereof)
- [10.7 Frequency map idioms](#107-frequency-map-idioms)
- [10.8 The `Map<K, List<V>>` multimap pattern](#108-the-mapk-listv-multimap-pattern)
- [10.9 Sorting a HashMap](#109-sorting-a-hashmap)
- [10.10 Self-check](#1010-self-check)

**Final sections:**
- [Quick-recall cheat sheet](#quick-recall-cheat-sheet-fill-in-last-after-everything-else)
- [Open questions / things I still don't understand](#open-questions--things-i-still-dont-understand)

---

## Phase 1 — Mental Model

The goal of this phase: get the picture in your head so clearly that when someone says "HashMap", you *see* buckets, a hash function, and collision chains — before you think about any Java code.

---

### 1.1 What is hashing?

**One-line definition:** hashing is converting an object of any size into a fixed-size integer via a deterministic function.

```
 ┌─────────────┐   hash function     ┌─────────────┐
 │  "hello"    │ ─────────────────▶  │ 99162322    │
 └─────────────┘                     └─────────────┘

 ┌─────────────┐                     ┌─────────────┐
 │  Employee   │ ─────────────────▶  │ -1284673915 │
 │  #42        │                     └─────────────┘
 └─────────────┘
```

**Analogies:**

- **Library card catalog:** books aren't stored alphabetically on shelves — they have call numbers (a computed index). To find a book, look up the call number, walk to that shelf.
- **Phonebook grouped by first letter:** to find "Smith", you don't scan every page — you jump to the "S" section first, then search within.
- **School assigning students to houses by birthday month:** each student is assigned a house by a simple formula (`birthMonth % 4`). Given a student, you can instantly know their house.

**Why is hashing useful?**

Without hashing, finding something in a collection of N items takes O(n) — scan every item and compare. With hashing:

1. Compute the hash of what you're looking for → O(1).
2. Compute where it *should* live in the collection → O(1).
3. Look there → O(1).

**Total: O(1).** That's the entire magic of HashMap.

**Key insight:** hashing turns *"search for this value"* into *"compute this address, then go to that address"*. It's not really searching at all.

**The unavoidable trade-off — collisions:**

A hash function maps an infinite space of possible keys into a finite space of hash values (typically 32-bit ints). By the pigeonhole principle, **two different keys must sometimes produce the same hash.** That's a **collision**, and every hash table implementation is fundamentally about how to handle them.

---

### 1.2 What is a hash table?

A **hash table** is a data structure that combines two things:

1. An **array of buckets** (slots that can hold entries).
2. A **hash function** that maps each key to a bucket index.

```
                        hash("apple") = 3
                                │
                                ▼
   index:   0     1     2     3     4     5     6     7
          ┌───┬─────┬───┬─────┬───┬─────┬───┬─────┐
          │   │     │   │ ●───┼───┼─▶ (apple, 100)
          │   │     │   │     │   │     │   │     │
          └───┴─────┴───┴─────┴───┴─────┴───┴─────┘
```

**The three core operations:**

1. **Compute the hash** of the key: `h = hash(key)`.
2. **Map the hash to a bucket index:** `i = h % capacity` (or `h & (capacity - 1)` when capacity is a power of 2).
3. **Look in that bucket** to find or store the entry.

**Collisions in the picture:**

```
                        hash("apple") = 3
                        hash("cherry") = 3   ← COLLISION!
                                │
                                ▼
   index:   0     1     2     3     4     5     6     7
          ┌───┬─────┬───┬─────┬───┬─────┬───┬─────┐
          │   │     │   │ ●───┼───┼─▶ (apple, 100) ─▶ (cherry, 250)
          │   │     │   │     │   │     │   │     │   [both live in bucket 3]
          └───┴─────┴───┴─────┴───┴─────┴───┴─────┘
```

Bucket 3 now holds a **chain** of two entries. To find "apple", you go to bucket 3 and walk the chain, using `.equals()` to identify the right entry.

**What can go wrong:**

- **Bad hash function** — if every key hashes to the same bucket, all N entries end up in one chain. Lookup becomes O(n). HashMap degrades to a linked list.
- **Too few buckets** — even with a good hash function, if there are only 4 buckets and 1000 entries, chains average 250 long. Solution: resize (grow the array) when the table gets full.
- **Mutable keys** — if you insert a key, then mutate it so its hash changes, the entry is orphaned (in the old bucket but now hashes to a new one).

---

### 1.3 HashMap vs Array vs LinkedList

The three data structures differ in **what you know about the data you want to find**:

| Data Structure | You know...           | Lookup cost                    |
| -------------- | --------------------- | ------------------------------ |
| **Array**      | ...the **index**       | **O(1)** — jump directly       |
| **LinkedList** | ...just the **value**  | O(n) — must walk the list      |
| **HashMap**    | ...the **key**         | **O(1) average**, O(n) worst   |

**Different lookup patterns:**

```
 ARRAY — indexed lookup
   ┌───┬───┬───┬───┬───┐
   │ A │ B │ C │ D │ E │      arr[2] → jump directly → C
   └───┴───┴───┴───┴───┘
     0   1   2   3   4

 LINKEDLIST — walk to find
   head → [A] → [B] → [C] → [D] → [E] → null
                       ↑
             walk 3 steps to find C

 HASHMAP — hash + jump
                   hash("C") = 2
                        │
                        ▼
   ┌───┬───┬─────┬───┬───┐
   │   │   │ (C) │   │   │      one hash + one jump → C
   └───┴───┴─────┴───┴───┘
```

**Full comparison table:**

| Operation                    | Array   | LinkedList | HashMap (avg) | HashMap (worst) |
| ---------------------------- | ------- | ---------- | ------------- | --------------- |
| Get by index                 | O(1)    | O(n)       | N/A           | N/A             |
| Get by key                   | N/A     | N/A        | **O(1)**      | O(n) / O(log n) |
| Search by value              | O(n)    | O(n)       | O(n)          | O(n)            |
| Insert at end                | O(1)*   | O(1)†      | **O(1)**      | O(n) / O(log n) |
| Delete                       | O(n)    | O(n) find + O(1) unlink | **O(1)** | O(n) / O(log n) |
| Ordered iteration            | Yes     | Yes        | **No**        | —               |
| Memory overhead per element  | tiny    | +2 pointers | +node object + pointers | — |

*ArrayList amortized. †LinkedList with tail pointer.

**Real-world use cases for HashMap:**

- **Session storage** — key = session ID, value = user object.
- **Caching** — key = expensive computation input, value = cached result.
- **Indexing** — key = looked-up field, value = database row or record.
- **In-memory databases** — Redis, Memcached are essentially giant HashMaps.
- **Word frequency counting** — key = word, value = count.
- **Graph algorithms** — key = node, value = visited flag or metadata.

**When to use what:**

- **Need index-based access, know the size upfront?** → Array / ArrayList
- **Need ordered traversal (or don't care about lookups)?** → List
- **Need fast lookup by an arbitrary key?** → HashMap
- **Need fast lookup AND sorted iteration?** → TreeMap (Phase 5)

---

### 1.4 Collision handling strategies (chaining vs open addressing)

Every hash table has to answer: *"When two keys hash to the same bucket, what do I do?"* There are two families of answers.

#### Family 1 — Chaining (separate chaining)

Each bucket holds a **chain** of colliding entries. If bucket 3 has 5 entries that all hashed to 3, they live in a linked list (or tree) attached to that bucket.

```
   index:   0     1     2     3     4     5
          ┌───┬─────┬───┬─────┬───┬─────┐
          │   │  ●──┼───┼─▶ (banana, 5)
          │   │     │   │  ●──┼───┼─▶ (apple, 100) ─▶ (cherry, 250) ─▶ (date, 8)
          │   │     │   │     │   │     │   │     │
          └───┴─────┴───┴─────┴───┴─────┘
```

- **Lookup:** hash → bucket → walk the chain, compare with `.equals()`.
- **Insert:** hash → bucket → append to chain.
- **Delete:** hash → bucket → find in chain, unlink.

**Java's HashMap uses this strategy.**

#### Family 2 — Open addressing

**No chains.** If bucket 3 is already occupied, probe forward to find the next empty slot.

Three flavors of probing:
- **Linear:** try bucket 4, then 5, then 6, ...
- **Quadratic:** try bucket 4, then 8, then 13, ... (offsets 1², 2², 3², ...)
- **Double hashing:** try bucket 4, 4+h2(k), 4+2·h2(k), ... using a second hash function.

```
   Before inserting "cherry" (also hashes to 3):

   index:   0     1     2     3     4     5     6     7
          ┌───┬─────┬───┬─────┬───┬─────┬───┬─────┐
          │   │     │   │apple│   │     │   │     │
          └───┴─────┴───┴─────┴───┴─────┴───┴─────┘

   After (linear probing — tries 4, which is empty):

          ┌───┬─────┬───┬─────┬─────┬───┬───┬─────┐
          │   │     │   │apple│cherry│  │   │     │
          └───┴─────┴───┴─────┴─────┴───┴───┴─────┘
```

**Python's `dict`, C++'s `std::unordered_map` (some implementations), Rust's `HashMap`** all use open addressing.

#### Trade-offs — chaining vs open addressing

| Aspect                       | Chaining               | Open addressing                    |
| ---------------------------- | ---------------------- | ---------------------------------- |
| **Load factor tolerance**    | Handles up to 0.75+ well | Degrades badly above 0.7          |
| **Memory per entry**         | Higher — each entry needs a node object with a `next` pointer | Lower — just the entry in the array slot |
| **Cache friendliness**       | Poor — chain nodes scattered in memory | **Great** — contiguous array access |
| **Delete complexity**        | Simple — just unlink   | Complex — needs tombstones to avoid breaking probing chains |
| **Worst-case with bad hash** | O(n) chain (or O(log n) if treeified) | Table fills → catastrophic clustering |
| **Resize sensitivity**       | Tolerates dense tables | Must resize aggressively (usually at 0.5-0.7) |

#### Which does Java's HashMap use? Why?

**Chaining, with a treeification upgrade in Java 8+.**

Why chaining:
- **Simple resize logic** — just rehash each entry to its new bucket. No need to reprobe.
- **Robust to bad hash functions** — a bad hash means long chains, not catastrophic table failure.
- **Handles high load factor (0.75) gracefully** — open addressing would already be struggling at that density.
- **Historical** — HashMap was designed in Java 1.2 (1998); modern open-addressing tricks weren't well-established yet.

Java 8's improvement: when a chain grows past 8 entries **AND** the table has ≥64 buckets, that chain converts to a **red-black tree**. This bounds the worst case at O(log n) instead of O(n) — protecting against pathological hash collisions (or malicious ones, e.g., hash-collision DoS attacks). We'll cover this in detail in Phase 3.6.

---

### 1.5 Self-check (answer without looking)

> Try answering each in your head *first*, then check.

1. **What are the two things a good hash function must do well?**
2. **What's the average-case complexity of `get()` on a HashMap? Worst case?**
3. **What are the two main collision strategies?**
4. **Why does Java use one over the other?**

#### Answers

1. **Deterministic + well-distributed.**
   - **Deterministic:** the same key must always hash to the same value (otherwise `put` and `get` wouldn't agree on which bucket to look in).
   - **Well-distributed:** different keys should produce different hashes, spread evenly across the range. A bad hash (e.g., always returns 0) would put every entry in one bucket → O(n) lookup. Bonus property: the **avalanche effect** — a small change in the key (one bit) should produce a big change in the hash (many bits scattered).

2. **Average: O(1).** Hash the key, jump to the bucket, first entry is a direct hit. **Worst case: O(n) in Java 7, O(log n) in Java 8+.** Worst case happens when many keys collide into the same bucket. Java 8+ mitigates by treeifying long chains — the chain becomes a red-black tree with O(log n) lookup instead of a linked list with O(n).

3. **Chaining** (each bucket holds a chain of colliding entries — linked list or tree) and **open addressing** (no chains; probe to the next available slot using linear, quadratic, or double hashing).

4. **Java uses chaining** because:
   - It's simpler to implement, especially resize (just rehash each entry — no need to re-probe).
   - It's robust to bad hash functions (worst case: long chain, not catastrophic table failure).
   - It handles high load factor (0.75) gracefully — open addressing would already be degrading heavily at that density.
   - It's historical — the design predates modern open-addressing optimizations.

   Java 8 added treeification to bound the worst case at O(log n) for pathological cases (including hash-collision DoS attacks).

---

## Phase 2 — Java-Specific Grounding

Now we ground the mental model in actual Java. By the end you should be able to open `HashMap.java` in the JDK and recognize everything.

---

### 2.1 The `Node<K,V>` inner class

The real inner class from the JDK source (trimmed of nothing important):

```java
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    V value;
    Node<K,V> next;

    Node(int hash, K key, V value, Node<K,V> next) {
        this.hash = hash;
        this.key = key;
        this.value = value;
        this.next = next;
    }

    public final K getKey()        { return key; }
    public final V getValue()      { return value; }
    public final String toString() { return key + "=" + value; }

    public final int hashCode() {
        return Objects.hashCode(key) ^ Objects.hashCode(value);
    }

    public final V setValue(V newValue) {
        V oldValue = value;
        value = newValue;
        return oldValue;
    }

    public final boolean equals(Object o) {
        if (o == this) return true;
        return o instanceof Map.Entry<?, ?> e
            && Objects.equals(key, e.getKey())
            && Objects.equals(value, e.getValue());
    }
}
```

**Four fields — that's the entire atom of HashMap:**

- **`final int hash`** — **cached hash of the key**. Recomputing on every lookup would be wasteful, especially for `String` keys (where `hashCode()` is O(length)). Cached at insert time.
- **`final K key`** — the reference is final (can't repoint), but the object it references might still be mutable — that's a common bug (Phase 6.4).
- **`V value`** — NOT final. `setValue()` mutates it. That's how `put(existingKey, newValue)` updates in place.
- **`Node<K,V> next`** — **chain pointer** for collision handling. If bucket is a linked list, this points to the next entry in the chain.

**Notice:**

- `static` — nested class doesn't need a reference to the outer HashMap. Saves memory (one hidden pointer per node — over millions of nodes, adds up).
- `implements Map.Entry<K,V>` — this is what you get when you iterate `map.entrySet()`. The entry you receive IS the internal node.
- `equals` and `hashCode` follow the `Map.Entry` contract: two entries are equal if both keys and both values are equal. Entry's hashCode is `key.hashCode() ^ value.hashCode()`.

**There's also `TreeNode<K,V>`** — used when a bucket treeifies. It extends `LinkedHashMap.Entry` (which extends `Node`), so it inherits everything above plus red-black tree fields (`parent`, `left`, `right`, `red`). Covered in Phase 3.6.

---

### 2.2 Fields on the `HashMap` class

The HashMap object itself holds only these fields:

```java
transient Node<K,V>[] table;
transient Set<Map.Entry<K,V>> entrySet;
transient int size;
transient int modCount;
int threshold;
final float loadFactor;
```

Explanation:

- **`table`** — the **bucket array**. Each slot is either `null` (empty bucket), a `Node` (start of a chain), or a `TreeNode` (treeified bucket). Starts as `null`; allocated lazily on first `put`.
- **`entrySet`** — lazily-created view returned by `entrySet()`. Same instance reused across calls.
- **`size`** — number of key-value mappings. Cached so `size()` is O(1).
- **`modCount`** — incremented on every **structural modification** (put, remove, resize, treeify). Iterators snapshot this; if it changes during iteration → `ConcurrentModificationException`. Same mechanism as LinkedList.
- **`threshold`** — the resize trigger. When `size > threshold`, HashMap grows. Computed as `capacity × loadFactor`. Default: `16 × 0.75 = 12`, so on the **13th put**, HashMap resizes to capacity 32.
- **`loadFactor`** — set once at construction. Default 0.75. Can be tuned but rarely worth it.

**Why so many `transient` fields?** Because HashMap has custom serialization. The bucket structure is an implementation detail — serialize the entries instead and rebuild the table on deserialization. (Detailed in Phase 6.9.)

**Mental picture of the full structure:**

```
 HashMap object
 ┌─────────────────────┐
 │ size:       4       │
 │ modCount:   4       │
 │ threshold:  12      │
 │ loadFactor: 0.75    │
 │ table: ●────────────┼──▶ Node[16]  (the bucket array)
 └─────────────────────┘        │
                                ▼
             ┌────┬────┬─────────┬────┬─────────┬────┬────┬─────────┬─────────┐
             │null│null│ Node ●──┼null│ Node ●──┼null│null│ Node ●──┼ Node ●──┼── ...
             └────┴────┴────┼────┴────┴────┼────┴────┴────┴────┼────┴────┼────┘
                            ▼              ▼                   ▼         ▼
                       (banana,5)     (apple,100)→(cherry,250) (dog,3)  (egg,7)
```

The HashMap object is **tiny**. All the "weight" is in `table` and its chains.

---

### 2.3 Class hierarchy — Map, AbstractMap, and cousins

```
                    ┌──────────────┐
                    │  Map<K,V>    │  (interface)
                    └──────┬───────┘
                           │
              ┌────────────┼─────────────┬────────────────┐
              │            │             │                │
     ┌────────▼─────┐  ┌───▼──────┐  ┌───▼──────────┐  ┌──▼──────────────┐
     │ AbstractMap  │  │SortedMap │  │ConcurrentMap │  │  Hashtable      │
     └──────┬───────┘  └────┬─────┘  └──────┬───────┘  │ (extends        │
            │               │               │           │  Dictionary)    │
    ┌───────┼──────┐  ┌────▼────────┐   ┌───▼──────────┐└─────────────────┘
    │       │      │  │NavigableMap │   │ConcurrentHash│
    │       │      │  └────┬────────┘   │Map           │
    │       │      │       │            └──────────────┘
    ▼       ▼      ▼   ┌───▼─────┐
 HashMap TreeMap Enum  │ TreeMap │  (implements NavigableMap, extends AbstractMap)
    │            Map   └─────────┘
    │
    ▼
 LinkedHashMap  (extends HashMap; adds doubly-linked entry list for order)
```

**What each layer contributes:**

- **`Map<K,V>` (interface)** — the contract: `put`, `get`, `remove`, `size`, `keySet`, `entrySet`, `values`, `containsKey`, `containsValue`, plus default methods (`getOrDefault`, `computeIfAbsent`, `merge`, etc.).
- **`AbstractMap<K,V>`** — skeletal implementation. Provides slow default implementations of many methods based on `entrySet().iterator()`. Also provides `equals`, `hashCode`, `toString`.
- **`HashMap<K,V>`** — overrides basically everything for O(1) hash-based performance.
- **`LinkedHashMap<K,V>`** — extends `HashMap`; adds a doubly-linked list threading all entries for predictable iteration order.

**Key sibling classes (not ancestors, but related):**

- **`TreeMap`** — separate implementation, red-black tree, implements `NavigableMap` (sorted).
- **`Hashtable`** — legacy from Java 1.0. Extends `Dictionary` (not `AbstractMap`) but also implements `Map`. Synchronized, doesn't allow nulls. **Don't use.**
- **`ConcurrentHashMap`** — thread-safe, implements `ConcurrentMap`. Covered in Phase 9.

**Interview soundbite:** *"HashMap extends AbstractMap and implements Map. LinkedHashMap extends HashMap, adding order tracking. TreeMap is a separate implementation (red-black tree) that adds sorted access via NavigableMap. Hashtable is legacy and unrelated to the modern hierarchy."*

---

### 2.4 Key constants

The magic numbers baked into HashMap. Memorize these — interviewers love asking about them.

| Constant | Value | What it means | Why this value? |
| -------- | ----- | ------------- | --------------- |
| `DEFAULT_INITIAL_CAPACITY` | **16** (1 << 4) | Bucket array size on first put | Small enough to be cheap for tiny maps; big enough that most maps don't resize immediately. Must be power of 2 (Phase 3.2). |
| `MAXIMUM_CAPACITY` | **1 << 30** (~1.07 B) | Upper bound on bucket array size | Capacity must be power of 2 AND fit in a positive `int` (max = 2³¹ - 1). Next power of 2 (2³¹) overflows to negative. |
| `DEFAULT_LOAD_FACTOR` | **0.75f** | Resize when `size > capacity × 0.75` | Poisson distribution math: with 0.75 load, average chain length is ~0.75. Balances space vs. collisions. |
| `TREEIFY_THRESHOLD` | **8** | Chain length triggering tree conversion | With decent hashCode, probability of 8 entries in one bucket is ~10⁻⁸ — reaching 8 means malicious or broken hashCode. Tree overhead only worth it beyond this. |
| `UNTREEIFY_THRESHOLD` | **6** | Tree size that reverts to list on resize | **Hysteresis** — don't flip back and forth at the boundary. Must be strictly less than `TREEIFY_THRESHOLD`. |
| `MIN_TREEIFY_CAPACITY` | **64** | Min table size to allow treeification | If table is small, resize first (cheap) instead of treeifying. Prevents small maps from prematurely treeifying due to natural collision density. |

**Why not 8/8 or 8/7 for treeify/untreeify?** The 8/6 gap prevents thrashing: if a bucket bounces between 7 and 8 entries repeatedly, you don't want to keep converting the structure. Requiring the count to drop all the way to 6 before untreeifying gives some buffer.

**Why `MIN_TREEIFY_CAPACITY = 64`?** If your table has only 16 buckets and one of them has 9 entries, the problem isn't that the chain is too long — it's that the table is too small. Resizing to 32 will spread those 9 entries out (statistically, ~4-5 per bucket). Treeification is expensive; resize is cheap.

---

### 2.5 `tableSizeFor()` — power-of-2 capacity rounding

When you write `new HashMap<>(13)`, the internal table is NOT 13 slots. It's **16**. Whatever capacity you pass, HashMap rounds it up to the next power of 2.

**The JDK source (Java 8 original — more visual):**

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

**Examples:**

| Input | Output | Why |
| ----- | ------ | --- |
| `tableSizeFor(1)`   | 1     | Smallest power of 2 |
| `tableSizeFor(5)`   | 8     | Next power of 2 above 5 |
| `tableSizeFor(13)`  | 16    | Default request when you say `new HashMap<>(13)` |
| `tableSizeFor(16)`  | 16    | Already a power of 2 |
| `tableSizeFor(17)`  | 32    | Just over → double |
| `tableSizeFor(1000)` | 1024 | Next power of 2 |

**The bit-trick walkthrough (with `cap = 17`):**

```
 cap - 1 = 16                     0000 0000 0001 0000
 n |= n >>> 1                     0000 0000 0001 1000
 n |= n >>> 2                     0000 0000 0001 1110
 n |= n >>> 4                     0000 0000 0001 1111
 n |= n >>> 8                     0000 0000 0001 1111  (no more high bits to spread)
 n |= n >>> 16                    0000 0000 0001 1111
 return n + 1                     0000 0000 0010 0000  = 32
```

The trick: **fill every bit below the highest set bit with 1s**, then add 1 → guaranteed power of 2. Why the `cap - 1` at the start? To handle the case where `cap` is already a power of 2 (don't want to bump 16 up to 32).

**Why must capacity always be a power of 2?**

Because HashMap's bucket-index formula is:

```java
index = (capacity - 1) & hash
```

When capacity is a power of 2:
- `capacity - 1` is all-1 bits in the lower positions.
- Example: `capacity = 16`, `capacity - 1 = 15 = 0b01111`.
- `hash & 0b01111` extracts the lower 4 bits of the hash — **exactly equivalent to `hash % 16`**, but computed with a bitwise AND (single CPU instruction).

If capacity weren't a power of 2, you'd need real integer modulo:
- Modulo is ~10× slower than AND on most CPUs.
- Java's `%` on negative numbers returns negative results, which would need extra handling.

**Bottom line:** power-of-2 capacity is a performance optimization that enables the `(n-1) & hash` trick. Everything else (initial 16, doubling on resize) follows from this constraint.

---

### 2.6 Complexity table

| Operation | Average | Worst (Java 7) | Worst (Java 8+) | Why |
| --------- | ------- | -------------- | --------------- | --- |
| `get(k)`             | **O(1)**  | O(n)       | **O(log n)** | Hash → bucket → first entry. Worst = walk chain (Java 7) OR tree lookup (Java 8+). |
| `put(k, v)`          | **O(1)**  | O(n)       | **O(log n)** | Same as get + possibly resize (amortized O(1)). |
| `remove(k)`          | **O(1)**  | O(n)       | **O(log n)** | Same as get + unlink. |
| `containsKey(k)`     | **O(1)**  | O(n)       | **O(log n)** | Same as get. |
| `containsValue(v)`   | **O(n)**  | O(n)       | O(n)         | Must scan every bucket, every entry. Not hashed by value. |
| `size()`             | **O(1)**  | O(1)       | O(1)         | Cached in the `size` field. |
| Iteration (all N entries) | O(n + capacity) | same | same | Must visit every bucket, even empty ones. |
| `clear()`            | O(capacity) | same | same | Nulls out every bucket for GC. |

**Notes:**

- **Amortization on `put`:** over N puts, total work is O(N), even accounting for resize. Individual puts occasionally take O(n) (during resize), but amortized to O(1) each.
- **Iteration is `O(n + capacity)`, not `O(n)`:** if you have capacity 1,048,576 but only 10 entries, iterating still has to walk the whole bucket array to find those 10 entries. Over-sizing HashMap hurts iteration performance.
- **Java 7 vs Java 8+:** The worst case dropped from O(n) to O(log n) thanks to treeification. Big deal for hash-collision DoS resilience.

---

### 2.7 Self-check (answer without looking)

> Try answering each in your head *first*, then check.

1. **What fields does `Node<K,V>` have?**
2. **Why is initial capacity always a power of 2?**
3. **What triggers a resize?**
4. **What's the worst-case complexity of `get()` in Java 8+ vs Java 7?**

#### Answers

1. **Four fields:**
   - `final int hash` — cached hash of the key (avoids recomputing on every lookup).
   - `final K key` — the key reference (the object may be mutable, but the reference is fixed).
   - `V value` — mutable, so `put(existingKey, newValue)` can update in place.
   - `Node<K,V> next` — chain pointer for collision handling.
   Also implements `Map.Entry`, so `getKey`, `getValue`, `setValue`, `equals`, `hashCode` come from the entry contract.

2. **So the bucket index can be computed as `(n - 1) & hash` (bitwise AND) instead of `hash % n` (modulo).** When `n` is a power of 2, `n - 1` is all-1s in the lower bits. Example: `n = 16`, `n - 1 = 15 = 0b01111`. Then `hash & 0b01111` extracts the low 4 bits — mathematically equivalent to `hash % 16`, but ~10× faster (bitwise AND is a single CPU instruction; modulo isn't). Also handles negative hashes cleanly (no signed-modulo issues).

3. **When `size > threshold`**, where `threshold = capacity × loadFactor`. With defaults (capacity 16, loadFactor 0.75), threshold is 12 — so on the **13th put**, HashMap resizes (doubles capacity to 32, rehashes all entries).

4. **Java 7: O(n)** — worst case is a linked-list chain with all N entries in one bucket.
   **Java 8+: O(log n)** — that chain converts to a red-black tree when it exceeds 8 entries in a table with ≥64 buckets. Tree lookup is O(log n).
   The change was largely motivated by hash-collision DoS attacks: attackers could construct thousands of keys that all hash to the same bucket, forcing pre-Java-8 HashMaps into O(n²) behavior on insertion. Treeification bounds this at O(n log n) — still bad, but survivable.

---

## Phase 3 — How Operations Work Internally

This is the heart of understanding HashMap. Master this phase and you understand more about HashMap than 90% of Java developers.

---

### 3.1 The hash function — XOR shift and internal `hash()` helper

**The actual JDK source:**

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

Three lines. Let's dissect them.

**Line-by-line:**

1. **`(key == null) ? 0`** — HashMap allows exactly one null key. Its hash is always 0, so it always lives in bucket 0.
2. **`h = key.hashCode()`** — call the key's own hashCode.
3. **`^ (h >>> 16)`** — XOR the upper 16 bits into the lower 16 bits.

**The problem this solves:**

Bucket index is computed as `(n - 1) & hash`, which uses **only the low bits** of the hash — specifically, `log2(n)` bits. With default capacity 16, only the **low 4 bits** of the hash determine the bucket.

If your hashCode has good high bits but poor low bits, keys will **cluster in just a few buckets**.

**Concrete example — `Integer.hashCode()` returns the int itself:**

Consider inserting multiples of 16: `16, 32, 48, 64, 80, ...`

```
 hashCode:   16 = ...0001 0000
             32 = ...0010 0000
             48 = ...0011 0000
             64 = ...0100 0000
             80 = ...0101 0000

 Low 4 bits: all 0000 !!
```

Without the XOR shift, `hash & 15` gives **0 for every one of them**. All entries collide into bucket 0 → chain of thousands → O(n) lookup → HashMap degraded to LinkedList.

**With the XOR shift:**

```
 h = 16     = 0000 0000 0000 0000 0000 0000 0001 0000
 h >>> 16   = 0000 0000 0000 0000 0000 0000 0000 0000
 XORed      = 0000 0000 0000 0000 0000 0000 0001 0000  (unchanged for small values)

 h = 65536  = 0000 0000 0000 0001 0000 0000 0000 0000
 h >>> 16   = 0000 0000 0000 0000 0000 0000 0000 0001
 XORed      = 0000 0000 0000 0001 0000 0000 0000 0001  ← low bit is now 1
```

High bits leak into low bits → distribution improves. Simple 2-3 cycle operation, big impact.

**The "avalanche effect":**

A well-designed hash function has the property that **changing one bit of the input flips ~half the bits of the output**. The XOR shift is a cheap approximation — not a true avalanche, but enough to spread the entropy.

Cryptographic hashes (SHA, MurmurHash) do this rigorously, but they cost 100+ CPU cycles. HashMap's `hash()` is called on **every** get/put/remove — the XOR shift is a deliberate compromise between distribution quality and speed.

**Why does HashMap have its own `hash()` instead of trusting `key.hashCode()`?**

Three reasons:
1. **Defense against bad hashCodes** — like `Integer.hashCode()` clustering, or malicious keys crafted for DoS attacks.
2. **Handles null explicitly** — user's `hashCode()` would NPE on null; HashMap returns 0.
3. **Bit-spreading for the `(n-1) & hash` formula** — makes the low bits usable regardless of the source distribution.

---

### 3.2 Bucket index — `(n - 1) & hash`

**The formula:**

```java
int i = (n - 1) & hash;   // where n = table.length (always a power of 2)
```

**Why this is equivalent to `hash % n`:**

When `n` is a power of 2, `n - 1` is all-1 bits in the low positions:

```
 n = 16 = 0b0001 0000
 n - 1  = 0b0000 1111  ← low 4 bits are 1

 hash & 0b1111  extracts the low 4 bits
 which IS  hash % 16  when hash is non-negative
```

**Example:**

```
 hash = 0x9E3779B9
      = 1001 1110 0011 0111 0111 1001 1011 1001
 n = 16, n-1 = 15
      = 0000 0000 0000 0000 0000 0000 0000 1111

 hash & (n-1) = ....................1001 = 9

 → this key goes to bucket 9
```

**Why bitwise AND instead of modulo:**

| Approach | Cost | Handles negatives cleanly? |
| -------- | ---- | -------------------------- |
| `hash % n`     | ~10-20 CPU cycles (integer division) | ❌ Returns negative result for negative hashes |
| `(n-1) & hash` | **1 CPU cycle** (single AND instruction) | ✅ Extracts the same low bits regardless of sign |

Modulo on negative numbers in Java is a mess:
```java
-9 % 16 == -9        // negative bucket index — array bounds error
(-9 & 15) == 7       // positive, clean
```

Without the AND trick, HashMap would need `((hash % n) + n) % n` just to get a valid bucket index. Extra math on every lookup.

**Bottom line:** the entire "capacity must be a power of 2" design constraint (Phase 2.5) exists to enable this one optimization.

---

### 3.3 `put(k, v)` — full step-by-step

The actual algorithm from `HashMap.putVal()`, in plain English:

**Step 1 — Lazy-init the table.**
If `table` is `null` (first put ever), call `resize()` — which allocates the initial 16-bucket array.

**Step 2 — Compute the bucket index.**
`i = (n - 1) & hash`.

**Step 3 — Bucket is empty?**
```java
if (tab[i] == null) {
    tab[i] = new Node<>(hash, key, value, null);
}
```
Create a new node, place it in the bucket. Done with the insert. Skip to step 8.

**Step 4 — Bucket has entries. Check the first one.**
```java
if (p.hash == hash && (p.key == key || key.equals(p.key))) {
    e = p;  // First entry matches
}
```
Fast path — most lookups hit this. If matched, we'll update its value in step 7.

**Step 5 — First entry didn't match. Is the bucket a tree?**
```java
else if (p instanceof TreeNode) {
    e = ((TreeNode<K,V>) p).putTreeVal(...);
}
```
Delegate to tree insertion. Returns existing node if key was present, null if new.

**Step 6 — Bucket is a linked list. Walk the chain.**
```java
for (int binCount = 0; ; ++binCount) {
    if ((e = p.next) == null) {
        p.next = new Node<>(hash, key, value, null);   // append to tail
        if (binCount >= TREEIFY_THRESHOLD - 1) {
            treeifyBin(tab, hash);
        }
        break;
    }
    if (e.hash == hash && (e.key == key || key.equals(e.key))) {
        break;   // Found existing key
    }
    p = e;
}
```
Walk the chain. Either find an existing key (break to update) or reach the end (append new node and check for treeification).

**Step 7 — If we found an existing entry, update its value.**
```java
if (e != null) {
    V oldValue = e.value;
    e.value = value;
    return oldValue;   // Note: put returns the OLD value
}
```

**Step 8 — New insertion. Increment `size` and `modCount`. Check for resize.**
```java
++modCount;
if (++size > threshold) {
    resize();
}
return null;   // No previous value
```

**Key optimizations to notice:**

- **Hash comparison before `equals`** — `e.hash == hash` is checked first (single int compare). Only if hashes match do we call `.equals()` (which might be expensive, e.g., String is O(length)).
- **Reference equality shortcut** — `p.key == key` before `equals`. If the key is an interned String or a shared singleton, `==` succeeds instantly.
- **Tail insertion in chains** (Java 8+) — enables the safe rehashing trick (Phase 3.7). Java 7 used head insertion, which caused the infinite-loop bug.

**What `put` returns:** the OLD value if the key existed (null if not present, or if the old value was null). This is why `map.put(k, v)` gives you the previous mapping "for free."

---

### 3.4 `get(k)` — full step-by-step

Simpler than `put` — no growing, no chain restructuring:

**Step 1 — Compute hash and bucket index.**
```java
int hash = hash(key);
int i = (n - 1) & hash;
```

**Step 2 — Bucket empty? Return null.**
```java
if (tab[i] == null) return null;
```

**Step 3 — Fast path: check first entry.**
```java
Node<K,V> first = tab[i];
if (first.hash == hash && (first.key == key || key.equals(first.key))) {
    return first;   // 90%+ of lookups end here
}
```
Same optimization as in put — hash compare first, then reference equality, then equals.

**Step 4 — More than one entry? Walk chain or tree.**
```java
if (first.next != null) {
    if (first instanceof TreeNode) {
        return ((TreeNode<K,V>) first).getTreeNode(hash, key);
    }
    Node<K,V> e = first;
    do {
        e = e.next;
        if (e.hash == hash && (e.key == key || key.equals(e.key))) {
            return e;
        }
    } while (e != null);
}
return null;
```

**Complexity summary:**

- **Best case (empty bucket or first-entry match):** O(1). Real-world default when the map is well-distributed.
- **Chain walk:** O(chain length). Average with 0.75 load factor ≈ O(1) (Poisson distribution says most buckets have 0-2 entries).
- **Treeified bucket:** O(log n).

---

### 3.5 Collision handling — chaining with linked list

Java's HashMap uses **separate chaining** — each bucket is either null, a `Node` chain, or a `TreeNode` tree.

**How chains are built:**

```
 Before inserting (dog, 10) which hashes to bucket 3:

 bucket 3 → [apple, 100] ─▶ [cherry, 250] ─▶ null

 After (tail insertion):

 bucket 3 → [apple, 100] ─▶ [cherry, 250] ─▶ [dog, 10] ─▶ null
```

**Head insertion (Java 7) vs Tail insertion (Java 8+):**

| Aspect | Java 7 (head insertion) | Java 8+ (tail insertion) |
| ------ | ----------------------- | ------------------------ |
| New node placement | Becomes new chain head | Appended to chain tail |
| Insert cost | O(1) — just prepend | O(chain length) — must walk to tail |
| Chain order | Reversed on insert | Insertion order preserved |
| Rehash safety | ❌ Could form cycles under concurrency (infinite loop bug) | ✅ Safe rehashing |

Java 8 traded a small insert-cost regression for two big wins: crash-safe concurrent rehashing (though HashMap is still not thread-safe — see Phase 9), and preservation of chain order for the split-in-half rehash trick (Phase 3.7).

**Chain search cost:**

Walking a chain is O(length), with the two-stage compare (hash first, then equals). Once the chain exceeds 8 entries in a table of ≥64 buckets, HashMap treeifies it — jumping to Phase 3.6.

---

### 3.6 Treeification (Java 8+)

**When it triggers:**

Two conditions, **both required**:
1. A bucket's chain exceeds `TREEIFY_THRESHOLD = 8` (i.e., grows to 9+ entries).
2. The table capacity is at least `MIN_TREEIFY_CAPACITY = 64`.

If chain hits 9 in a small table (< 64 buckets), HashMap **resizes** instead of treeifying. Resize is cheaper and likely spreads the collision.

**Reverse — untreeification:**

During resize, if a tree bucket's node count drops to `UNTREEIFY_THRESHOLD = 6` or below, convert back to linked list. The 8/6 gap is **hysteresis** — prevents thrashing at the boundary (if we used 8/8, a bucket at exactly 8 nodes would keep flipping between structures on each add/remove).

**Why red-black tree specifically?**

- Balanced BST — height bounded at `2 × log₂(n+1)`.
- O(log n) insert, delete, search.
- Simpler to implement than AVL trees (fewer rotations on insert/delete).
- Java already had a proven RB-tree implementation from `TreeMap`.

**How the tree is ordered (crucial detail):**

Since HashMap doesn't require keys to be Comparable, the tree needs a total order to place nodes. Ordering rules, checked in this order:
1. **Primary:** `hash` value comparison.
2. **If hashes equal AND keys implement `Comparable`:** use `compareTo`.
3. **Fallback:** identity hash comparison (arbitrary but consistent).

You don't need `Comparable` keys, but they help tree operations run faster when many keys hash to the same value.

**Complexity impact:**

| Scenario | Pre-treeify chain | Post-treeify tree |
| -------- | ----------------- | ----------------- |
| Lookup | O(chain length) | **O(log n)** |
| Insert | O(chain length) + append | **O(log n)** + balance |
| Delete | O(chain length) + unlink | **O(log n)** + balance |
| Space per entry | 1 `Node` (32 bytes) | 1 `TreeNode` (~56 bytes) |

**Why 8 exactly? The Poisson distribution answer:**

With a well-distributed hashCode and load factor 0.75, chain length k in a bucket follows a Poisson distribution with mean ~0.5:

| Chain length | Probability |
| ------------ | ----------- |
| 0 | 0.60653 |
| 1 | 0.30327 |
| 2 | 0.07582 |
| 3 | 0.01263 |
| 4 | 0.00158 |
| 5 | 0.00016 |
| 6 | 0.00001 |
| 7 | 0.000001 |
| **8** | **~6 × 10⁻⁸** |

Reaching 8 in a bucket is essentially a red flag: something's wrong (broken hashCode or a DoS attempt). At that point, tree overhead is worth it.

---

### 3.7 Resize (rehashing)

**Trigger:** `size > threshold`, where `threshold = capacity × loadFactor`. With defaults, threshold is 12 — the **13th put** triggers resize.

**What happens:**

1. Allocate new bucket array of **double** the old capacity: `newCap = oldCap << 1`.
2. Update threshold: `newThreshold = newCap × loadFactor`.
3. Redistribute every existing entry to its bucket in the new table.

Step 3 is the expensive part — O(n) work touching every entry.

**Java 8+ optimization — the split-in-half trick:**

When capacity doubles, each entry doesn't move to an arbitrary new bucket. It has exactly **two possible destinations**:

- **Stays put** at `oldIndex`, OR
- **Moves to** `oldIndex + oldCapacity`.

Never anywhere else. And which of the two is determined by a **single bit test** on the hash — no need to recompute the bucket index from scratch.

**Why this works:**

```
 oldCap = 16, newCap = 32
 oldMask = 0b0 1111   (uses low 4 bits)
 newMask = 0b1 1111   (uses low 5 bits — one extra)

 The "extra bit" checked is bit-4, which has value 16 (= oldCap)

 For any hash:
   (hash & oldCap) == 0  →  bit 4 is 0  →  new index = old index (stays put)
   (hash & oldCap) != 0  →  bit 4 is 1  →  new index = old index + 16 (moves to lo + oldCap)
```

So the resize walks each bucket's chain and splits it into two mini-chains — a **lo group** and a **hi group** — using just `(hash & oldCap)` as the split test.

**The actual code (simplified):**

```java
Node<K,V> loHead = null, loTail = null;
Node<K,V> hiHead = null, hiTail = null;

Node<K,V> e = oldTable[oldIndex];
while (e != null) {
    Node<K,V> next = e.next;
    if ((e.hash & oldCap) == 0) {
        // Stays put
        if (loTail == null) loHead = e;
        else                loTail.next = e;
        loTail = e;
    } else {
        // Moves to newIndex = oldIndex + oldCap
        if (hiTail == null) hiHead = e;
        else                hiTail.next = e;
        hiTail = e;
    }
    e = next;
}

if (loTail != null) { loTail.next = null; newTable[oldIndex] = loHead; }
if (hiTail != null) { hiTail.next = null; newTable[oldIndex + oldCap] = hiHead; }
```

**Bonus win:** because we walk the chain in order and append to `loTail`/`hiTail` (also in order), **the two new mini-chains preserve original insertion order**. This is why Java 8 changed from head insertion to tail insertion — it enables this ordering guarantee.

**Trees split cleanly too:** if a tree bucket splits and either side has ≤ 6 nodes, untreeify that side. Rare but handled.

**Complexity:**
- Time: **O(n)** to rehash the whole table.
- Amortized per put: **O(1)** — over the map's lifetime, resizes happen O(log n) times, but each new capacity is double the last, so total resize work is O(n) (geometric series).

---

### 3.8 Load factor 0.75 — the space/time trade-off

**Definition:** `loadFactor = size / capacity`. When `size` exceeds `threshold = capacity × loadFactor`, HashMap resizes.

**Why 0.75 specifically?**

From the JDK source comment (paraphrased):
> *"With good hashCode distribution, the number of nodes in a bucket follows a Poisson distribution. With load factor 0.75, the expected chain length stays low, while wasted space stays reasonable."*

**Concrete comparison:**

| Load factor | Wasted space | Average chain length | Behavior |
| ----------- | ------------ | -------------------- | -------- |
| 0.5 | ~50% empty buckets | ~0.5 | Very fast lookups, lots of wasted memory |
| **0.75** | ~25% empty buckets | ~0.75 | **Sweet spot — JDK default** |
| 0.9 | ~10% empty buckets | ~0.9, with longer tail | Denser, more collisions, slower lookups AND iterations |
| 1.0 | 0% wasted, but... | Long chains almost immediately | Bad — resizes only when full → guaranteed collisions before resize |

**When to tune:**

- **Almost never.** 0.75 is right for 99% of use cases.
- **Increase (e.g., 0.9)** if memory is critical AND lookups are cold.
- **Decrease (e.g., 0.5)** if lookups are extremely hot AND memory is abundant.

**Sizing tip — avoid resizes entirely:**

To hold `N` entries without ever resizing, use initial capacity `N / loadFactor + 1`:

```java
Map<String, Integer> map = new HashMap<>(1000 / 0.75f + 1);  // ~1333 → rounded to 2048
```

Or in Java 19+ (cleaner):

```java
Map<String, Integer> map = HashMap.newHashMap(1000);  // does the math for you
```

---

### 3.9 Self-check (answer without looking)

> Try answering each in your head *first*, then check.

1. **Why does `hash()` XOR the upper 16 bits into the lower 16?**
2. **Why is `(n - 1) & hash` used for indexing?**
3. **Under what two conditions does a bucket become a tree?**
4. **When rehashing, why can an entry only stay put or move by `oldCapacity`?**
5. **What's the trade-off if you use load factor 0.5 vs 0.9?**

#### Answers

1. **Because the bucket index uses only the LOW bits of the hash** (`(n-1) & hash` extracts `log₂(n)` low bits). If the source `hashCode()` has good high bits but poor low bits — like `Integer.hashCode()` returning the int itself, or many keys that happen to be multiples of the capacity — buckets will cluster catastrophically. XORing high bits into low bits spreads the entropy in a cheap 2-cycle operation. It's a cheap approximation of the **avalanche effect** — one bit change in the input should ripple into many output bits.

2. **Because `n` is always a power of 2 (Phase 2.5), `n - 1` is all-1s in the low bits** (e.g., 15 = `0b01111`). Bitwise AND extracts those low bits — mathematically equivalent to `hash % n`, but ~10× faster (single AND instruction vs. integer division), and it handles negative hashes cleanly (modulo on negatives returns negative results in Java, which would be an invalid bucket index).

3. **(1)** The chain length exceeds `TREEIFY_THRESHOLD = 8` (i.e., grows to 9+ entries), **AND (2)** the table capacity is at least `MIN_TREEIFY_CAPACITY = 64`. If chain hits 9 in a smaller table, HashMap resizes instead — resizing is cheaper and likely spreads the collision. Reversal (untreeify) happens at `UNTREEIFY_THRESHOLD = 6` during resize; the 8/6 gap is hysteresis to prevent thrashing at the boundary.

4. **When capacity doubles (oldCap → 2 × oldCap), the bucket-index mask gains exactly one new bit** — the bit corresponding to `oldCap` itself. Specifically, going from 16 to 32, the mask goes from `0b01111` to `0b11111`; the newly-checked bit is bit 4 (value 16). For any entry's hash:
   - If `(hash & oldCap) == 0` → new index = old index (stays put).
   - If `(hash & oldCap) != 0` → new index = old index + oldCap.
   
   That's a single-bit test, no recomputing indices from scratch. And because Java 8 uses tail insertion, chain order is preserved during the split.

5. **Load factor 0.5:** wastes ~50% memory, but chains stay short → faster lookups, lower iteration cost, more predictable performance under stress. Use when memory is cheap and lookups are hot.
   **Load factor 0.9:** wastes only ~10% memory, but chains average longer → more collisions → slower lookups AND slower iteration (must walk chains). Also, occasional treeifications become more likely. Use when memory is tight and inserts are cold.
   **0.75 is the JDK default sweet spot**, backed by Poisson-distribution math. Only tune if profiling shows a specific bottleneck.

---

## Phase 4 — Build It Yourself

> **This is the interview gold.** If you can write a working HashMap from scratch on a whiteboard, you've mastered ~80% of what interviewers care about. The remaining 20% is treeification (which they rarely ask you to implement).

---

### 4.1 Design a simple HashMap

**Two classes:**
- `Entry<K,V>` — the atom (equivalent to Java's `Node<K,V>`).
- `MyHashMap<K,V>` — the map itself.

**Fields on the map:**
- `Entry<K,V>[] table` — bucket array.
- `int size` — number of entries.
- `int threshold` — resize trigger.

**Constructor decisions to make:**
- Default capacity: **16** (must be power of 2 for the `(n-1) & hash` optimization).
- Load factor: **0.75f** (either final constant or configurable). Simpler = constant.

**Simplifications vs. real HashMap:**
- **No treeification** — keep chains as linked lists forever. Real HashMap treeifies at 8+ entries.
- **No `Map.Entry` interface** — just a plain internal class.
- **No `keySet` / `entrySet` / `values` views** — just the core operations.

**Methods to implement (in this order — each builds on the last):**

- [ ] `put(K, V)` — the workhorse
- [ ] `get(K)` — easier once put works
- [ ] `containsKey(K)` — trivial helper
- [ ] `remove(K)` — needs the "stop one before" pattern from linked lists
- [ ] `size()`, `isEmpty()` — trivial
- [ ] `resize()` — the tricky part
- [ ] `toString()` — for debugging

---

### 4.2 Method-by-method walkthrough

#### The Entry class (write this first)

```java
static class Entry<K, V> {
    final int hash;
    final K key;
    V value;
    Entry<K, V> next;

    Entry(int hash, K key, V value, Entry<K, V> next) {
        this.hash = hash;
        this.key = key;
        this.value = value;
        this.next = next;
    }
}
```

Mirrors `java.util.HashMap.Node` exactly. Four fields, one constructor.

#### The `hash()` helper (mimic HashMap's XOR shift)

```java
private static int hash(Object key) {
    if (key == null) return 0;    // null always goes to bucket 0
    int h = key.hashCode();
    return h ^ (h >>> 16);        // spread high bits into low bits
}
```

#### `put(K, V)` — the key insight

The tricky part: **before adding a new entry, walk the chain to see if the key already exists.** If yes, replace value (don't add duplicate). If no, append.

```java
public V put(K key, V value) {
    int hash = hash(key);
    int i = (table.length - 1) & hash;

    // Walk chain — key may already exist
    for (Entry<K, V> e = table[i]; e != null; e = e.next) {
        if (e.hash == hash && Objects.equals(e.key, key)) {
            V old = e.value;
            e.value = value;
            return old;
        }
    }

    // New key — insert at head of chain (simpler than tail)
    table[i] = new Entry<>(hash, key, value, table[i]);
    size++;
    if (size > threshold) resize();
    return null;
}
```

**Trap:** if you skip the "walk chain first" step, you'll create **duplicate entries** for the same key. `map.get(k)` will still work (finds the first match), but `size` will lie and the map bloats.

**Note:** we use head insertion here (simpler). Real HashMap uses tail insertion for safe rehashing — see 4.4.

#### `get(K)` — mirror of put's walk

```java
public V get(Object key) {
    int hash = hash(key);
    int i = (table.length - 1) & hash;
    for (Entry<K, V> e = table[i]; e != null; e = e.next) {
        if (e.hash == hash && Objects.equals(e.key, key)) {
            return e.value;
        }
    }
    return null;
}
```

**Optimization: hash compare BEFORE `equals`.** Hash is a single int comparison. `equals` might be expensive (e.g., String.equals is O(length)). Only call equals if hashes match.

#### `containsKey(K)` — trivial once we have entry lookup

**Trap:** you can't use `get(k) != null` — that returns false when the value is null but the key exists. Use a separate helper:

```java
public boolean containsKey(Object key) {
    int hash = hash(key);
    int i = (table.length - 1) & hash;
    for (Entry<K, V> e = table[i]; e != null; e = e.next) {
        if (e.hash == hash && Objects.equals(e.key, key)) return true;
    }
    return false;
}
```

#### `remove(K)` — the "stop one before" pattern (from linked list Phase 3!)

Same trick as removing from a linked list — walk with `prev` pointer, then either update the bucket head (if removing head) or the previous entry's `next`.

```java
public V remove(Object key) {
    int hash = hash(key);
    int i = (table.length - 1) & hash;

    Entry<K, V> prev = null;
    Entry<K, V> curr = table[i];
    while (curr != null) {
        if (curr.hash == hash && Objects.equals(curr.key, key)) {
            if (prev == null) {
                table[i] = curr.next;   // removing chain head
            } else {
                prev.next = curr.next;  // removing middle/tail
            }
            size--;
            return curr.value;
        }
        prev = curr;
        curr = curr.next;
    }
    return null;
}
```

The `if (prev == null)` branch is the elegant way to handle "am I removing the first entry?" — same pattern from linked list Phase 3.5 (doubly-linked `unlink`).

---

### 4.3 Full reference implementation (with chaining + resize)

```java
import java.util.Objects;

public class MyHashMap<K, V> {

    static class Entry<K, V> {
        final int hash;
        final K key;
        V value;
        Entry<K, V> next;

        Entry(int hash, K key, V value, Entry<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private Entry<K, V>[] table;
    private int size;
    private int threshold;

    @SuppressWarnings("unchecked")
    public MyHashMap() {
        table = (Entry<K, V>[]) new Entry[DEFAULT_CAPACITY];
        threshold = (int) (DEFAULT_CAPACITY * LOAD_FACTOR);
    }

    private static int hash(Object key) {
        if (key == null) return 0;
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    public int size()          { return size; }
    public boolean isEmpty()   { return size == 0; }

    public V put(K key, V value) {
        int hash = hash(key);
        int i = (table.length - 1) & hash;

        for (Entry<K, V> e = table[i]; e != null; e = e.next) {
            if (e.hash == hash && Objects.equals(e.key, key)) {
                V old = e.value;
                e.value = value;
                return old;
            }
        }

        table[i] = new Entry<>(hash, key, value, table[i]);
        size++;
        if (size > threshold) resize();
        return null;
    }

    public V get(Object key) {
        int hash = hash(key);
        int i = (table.length - 1) & hash;
        for (Entry<K, V> e = table[i]; e != null; e = e.next) {
            if (e.hash == hash && Objects.equals(e.key, key)) return e.value;
        }
        return null;
    }

    public boolean containsKey(Object key) {
        int hash = hash(key);
        int i = (table.length - 1) & hash;
        for (Entry<K, V> e = table[i]; e != null; e = e.next) {
            if (e.hash == hash && Objects.equals(e.key, key)) return true;
        }
        return false;
    }

    public V remove(Object key) {
        int hash = hash(key);
        int i = (table.length - 1) & hash;

        Entry<K, V> prev = null;
        Entry<K, V> curr = table[i];
        while (curr != null) {
            if (curr.hash == hash && Objects.equals(curr.key, key)) {
                if (prev == null) table[i] = curr.next;
                else              prev.next = curr.next;
                size--;
                return curr.value;
            }
            prev = curr;
            curr = curr.next;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        Entry<K, V>[] oldTable = table;
        int newCap = oldTable.length << 1;   // double capacity
        Entry<K, V>[] newTable = (Entry<K, V>[]) new Entry[newCap];

        // Rehash every entry into the new table
        for (Entry<K, V> head : oldTable) {
            Entry<K, V> e = head;
            while (e != null) {
                Entry<K, V> next = e.next;   // save before overwriting
                int newIndex = (newCap - 1) & e.hash;
                e.next = newTable[newIndex]; // head-insert into new bucket
                newTable[newIndex] = e;
                e = next;
            }
        }

        table = newTable;
        threshold = (int) (newCap * LOAD_FACTOR);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Entry<K, V> head : table) {
            for (Entry<K, V> e = head; e != null; e = e.next) {
                if (!first) sb.append(", ");
                sb.append(e.key).append("=").append(e.value);
                first = false;
            }
        }
        return sb.append("}").toString();
    }

    // --- quick smoke test ---
    public static void main(String[] args) {
        MyHashMap<String, Integer> map = new MyHashMap<>();
        map.put("apple", 100);
        map.put("banana", 50);
        map.put("cherry", 75);
        System.out.println(map);
        System.out.println("size = " + map.size());
        System.out.println("get(apple) = " + map.get("apple"));
        System.out.println("put(apple, 999) returns old = " + map.put("apple", 999));
        System.out.println("get(apple) = " + map.get("apple"));
        System.out.println("remove(banana) = " + map.remove("banana"));
        System.out.println("containsKey(banana) = " + map.containsKey("banana"));
        System.out.println("size = " + map.size());

        // Trigger resize
        for (int i = 0; i < 20; i++) map.put("k" + i, i);
        System.out.println("size after 20 more puts = " + map.size());
        System.out.println("get(k15) = " + map.get("k15"));
    }
}
```

**Expected output:**
```
{cherry=75, banana=50, apple=100}     ← order may vary (unordered!)
size = 3
get(apple) = 100
put(apple, 999) returns old = 100
get(apple) = 999
remove(banana) = 50
containsKey(banana) = false
size = 2
size after 20 more puts = 22
get(k15) = 15
```

---

### 4.4 Adding resize / rehash — the tricky part

**When to trigger:** in `put`, after incrementing size, check if `size > threshold`. If yes, resize.

**How to redistribute entries (naive version — what we have):**

```java
private void resize() {
    Entry<K, V>[] oldTable = table;
    int newCap = oldTable.length << 1;
    Entry<K, V>[] newTable = (Entry<K, V>[]) new Entry[newCap];

    for (Entry<K, V> head : oldTable) {
        Entry<K, V> e = head;
        while (e != null) {
            Entry<K, V> next = e.next;
            int newIndex = (newCap - 1) & e.hash;
            e.next = newTable[newIndex];
            newTable[newIndex] = e;
            e = next;
        }
    }
    table = newTable;
    threshold = (int) (newCap * LOAD_FACTOR);
}
```

This works but recomputes the bucket index for every entry.

**Java 8+ optimization (what real HashMap does):**

When capacity doubles, each entry either **stays put** or **moves to `oldIndex + oldCapacity`** — never anywhere else. Determined by a single hash-bit test:

```java
if ((e.hash & oldCap) == 0) {
    // stays in same bucket in new table
} else {
    // moves to oldIndex + oldCap
}
```

This splits each old bucket into two mini-chains (lo/hi groups) with one bit test per entry — no bucket-index recomputation. Detailed in Phase 3.7.

For a learning implementation, the naive version above is fine. The Java 8+ optimization is an interview-bonus you can mention verbally.

**Complexity:**
- Time: O(n) to rehash every entry.
- Amortized per put: **O(1)** — over the map's lifetime, resizes happen O(log n) times, but total resize work is O(n) (geometric series: 16 + 32 + 64 + ... = 2n).

---

### 4.5 Common bugs (log the ones YOU hit)

Pre-filled with the most common ones — add your own as you code:

- [ ] **Forgot to handle null key** — NPE on `key.hashCode()`. Guard in `hash()`.
- [ ] **Used `%` instead of `& (n - 1)`** — works, but ~10× slower AND returns negative bucket indices for negative hashes.
- [ ] **Missing the `hash ==` compare before `equals`** — works, but calls expensive `equals` on every chain node. Add the hash short-circuit.
- [ ] **`containsKey` uses `get(k) != null`** — false negative when the stored value IS null. Use a dedicated lookup that returns the Entry.
- [ ] **`put()` skips the "check for existing key" walk** — creates duplicate entries for the same key. `size` bloats.
- [ ] **Forgot to update `size` on `remove`** — the operation works but `size()` lies.
- [ ] **Off-by-one in threshold comparison** — using `>=` vs `>`. Real HashMap uses `>` (resize triggers on the entry that would push us over).
- [ ] **`remove` doesn't handle "removing chain head"** — needs the `prev == null` branch.
- [ ] **Resize loses entries** — saving `next` inside the loop is critical. Forgetting to save it before overwriting `e.next` loses the rest of the chain.
- [ ] **Resize creates infinite loop (Java 7 style)** — head insertion during concurrent resize can create cycles. Not a bug in single-threaded code, but famous Java 7 bug (Phase 9.2).
- [ ] Add your own here:
- [ ] ...

---

### 4.6 Practice checklist (do this in order)

- [ ] Type the `Entry` class and `MyHashMap` shell from scratch (no reference).
- [ ] Implement `put` and `get` first — test with a few string keys.
- [ ] Add `containsKey`, `remove`, `size`, `isEmpty`.
- [ ] Write a `main` that put/get/remove/prints — confirm output matches expected.
- [ ] Add `resize` when load exceeded. Verify by adding 20+ entries and confirming all still retrievable.
- [ ] Test null key — should work, always lands in bucket 0.
- [ ] Test with keys that always collide (custom class with `hashCode() = 0`). Confirm still works — should degrade gracefully to O(n) chain, not crash.
- [ ] Compare output with `java.util.HashMap` on the same test case (values should match; iteration order may not).
- [ ] Time challenge: rewrite from empty file in ~20 minutes without peeking.

**That's the interview bar.** Once you can do this from an empty file in 20 minutes, you can walk into any Java interview and hold your own on HashMap questions.

---

## Phase 5 — HashMap vs Alternatives

There are **seven** other Map implementations in the standard JDK, plus the primitive-array alternative. Each has a specific reason to exist. Knowing when to use each is what separates senior engineers from juniors.

---

### 5.1 HashMap vs Hashtable

`Hashtable` is Java 1.0 legacy (1996!). It predates the Collections framework.

**The differences:**

| Feature              | HashMap                | Hashtable                     |
| -------------------- | ---------------------- | ----------------------------- |
| Thread safety        | No                     | Yes (every method `synchronized`) |
| Null key             | 1 allowed              | ❌ Throws `NullPointerException` |
| Null value           | Any                    | ❌ Throws `NullPointerException` |
| Default capacity     | 16 (power of 2)        | 11 (prime)                    |
| Grow factor          | 2× (doubling)          | 2n + 1 (roughly doubles, stays odd) |
| Bucket index formula | `(n - 1) & hash`       | `(hash & 0x7FFFFFFF) % n`     |
| Treeification        | Yes (Java 8+)          | ❌ Chains stay linked lists forever |
| Iteration            | Fail-fast (CME)        | Fail-fast + legacy `Enumeration` |
| Parent class         | `AbstractMap`          | `Dictionary` (legacy)         |

**Why the ancient Hashtable uses primes:** without HashMap's XOR shift, distribution depends entirely on `hashCode()`. Prime moduli spread bad hashes better than powers of 2. Modern HashMap gets away with powers of 2 because the XOR shift compensates.

**Verdict:**

> **Never use Hashtable in new code.** Coarse-grained synchronization is worse than useless — it serializes all access while giving zero benefit over ConcurrentHashMap.

- Single-threaded → **HashMap**
- Multi-threaded → **ConcurrentHashMap**
- Hashtable exists only for backward compatibility with pre-Collections code.

---

### 5.2 HashMap vs LinkedHashMap

`LinkedHashMap` **extends `HashMap`** and adds a doubly-linked list threading through **all** entries.

**What LinkedHashMap adds:**

```java
static class Entry<K,V> extends HashMap.Node<K,V> {
    Entry<K,V> before, after;    // doubly-linked across ALL entries
    ...
}
```

Every entry knows its predecessor and successor **in insertion order** (or access order), across the entire map — not just within its bucket. So iteration walks this linked list, giving predictable order.

**Two modes** (chosen at construction):

- **Insertion order** (default): entries visited in the order they were added. Adding a new key appends; re-putting an existing key does NOT move it.
- **Access order:** entries move to the end on every `get()` or `put()`. Least-recently-used entry is always at the head. **Perfect for LRU caches.**

**Build an LRU cache in ~10 lines:**

```java
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;

    public LRUCache(int capacity) {
        super(capacity, 0.75f, true);   // true = access order
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}
```

Every operation is O(1). This is the production-grade way to build an LRU when you don't need thread safety. (For thread-safe LRU, use `Caffeine` library.)

**Comparison:**

| Feature | HashMap | LinkedHashMap |
| ------- | ------- | ------------- |
| Complexity | O(1) get/put/remove | Same (O(1)) |
| Iteration order | **Unpredictable** | **Insertion or access order** |
| Iteration cost | O(n + capacity) — must scan buckets | O(n) — walk the linked list |
| Memory per entry | Node (32 bytes) | Entry (48 bytes — +2 pointers) |
| Overhead vs HashMap | Baseline | ~50% more memory |

**When to use LinkedHashMap:**
- Need predictable iteration order.
- Building an LRU cache (access-order + `removeEldestEntry`).
- Debugging aid (deterministic output between runs).

Otherwise, HashMap is lighter.

---

### 5.3 HashMap vs TreeMap

`TreeMap` uses a completely different structure: **a red-black tree** (self-balancing BST).

**Complexity comparison:**

| Operation                            | HashMap | TreeMap    |
| ------------------------------------ | ------- | ---------- |
| `get(k)` / `put(k,v)` / `remove(k)`  | O(1)    | O(log n)   |
| `containsKey(k)`                     | O(1)    | O(log n)   |
| `firstKey()` / `lastKey()`           | ❌ N/A  | O(log n)   |
| `floorKey(k)` / `ceilingKey(k)`      | ❌ N/A  | O(log n)   |
| `higherKey(k)` / `lowerKey(k)`       | ❌ N/A  | O(log n)   |
| `headMap(k)` / `tailMap(k)` / `subMap(from, to)` | ❌ N/A | **O(1) view** |
| Iteration                            | Unordered | **Sorted (ascending)** |
| Null key                             | ✅ 1 allowed | ❌ NPE (unless custom Comparator handles null) |

**TreeMap's `NavigableMap` superpowers:**

- `firstKey()` / `lastKey()` — smallest / largest key.
- `floorKey(k)` — largest key ≤ k.
- `ceilingKey(k)` — smallest key ≥ k.
- `higherKey(k)` / `lowerKey(k)` — strict variants (`> k` / `< k`).
- `headMap(k)`, `tailMap(k)`, `subMap(from, to)` — **range views** in O(1). Modifying the view modifies the map.
- `descendingMap()` — reverse-sorted view.

**Example — find nearest hotel by price range:**

```java
TreeMap<Integer, Hotel> hotels = new TreeMap<>();
hotels.put(100, hotelA);
hotels.put(250, hotelB);
hotels.put(500, hotelC);

Integer myBudget = 300;
Map.Entry<Integer, Hotel> best = hotels.floorEntry(myBudget);   // O(log n)
// → returns entry for 250 → hotelB
```

Impossible with HashMap without O(n) scan or additional sorting.

**When to use TreeMap:**
- Need sorted iteration.
- Range queries ("all keys between X and Y").
- Finding closest match (floor / ceiling).
- Otherwise → HashMap (~10× faster in practice due to O(1) + cache friendliness).

---

### 5.4 HashMap vs ConcurrentHashMap

**The Java answer to concurrent map access.** Detailed internals covered in Phase 9; here's the summary.

**Comparison:**

| Feature                    | HashMap                | ConcurrentHashMap              |
| -------------------------- | ---------------------- | ------------------------------ |
| Thread safety              | ❌ No                  | ✅ Yes                          |
| Locking                    | N/A                    | Per-bucket (Java 8+); segments (Java 7) |
| Null key                   | ✅ 1 allowed           | ❌ NPE                          |
| Null value                 | ✅ Any                 | ❌ NPE                          |
| Iterator behavior          | Fail-fast (CME)        | **Weakly consistent** (no CME) |
| Atomic compute methods     | ❌ Not atomic          | ✅ Atomic                       |
| `size()` complexity        | O(1) exact             | O(n) approximate               |

**Why no null keys/values in ConcurrentHashMap?** Because `map.get(k)` returning `null` should unambiguously mean "not present." If null were a valid value, you'd need `containsKey` to distinguish "not present" from "present with null value" — which requires an extra lookup, breaking atomicity guarantees.

**Atomic compute methods (the killer feature):**

```java
// In HashMap: NOT atomic — race between get and put
map.put(key, map.getOrDefault(key, 0) + 1);   // BAD in concurrent code

// In ConcurrentHashMap: atomic — no race possible
chm.merge(key, 1, Integer::sum);              // safe in concurrent code
chm.compute(key, (k, v) -> v == null ? 1 : v + 1);
chm.computeIfAbsent(key, k -> expensiveInit());
```

**When to use ConcurrentHashMap:**
- Any time multiple threads read/write a map.
- Default choice for thread-safe maps.

**Do NOT use `Collections.synchronizedMap(new HashMap<>())`** in new code — coarse-grained locking, no atomic compute methods, iteration still requires manual sync. Almost strictly worse than CHM.

---

### 5.5 HashMap vs WeakHashMap

`WeakHashMap` holds its keys via **weak references** — the garbage collector can reclaim them at any time.

**How it works:**

```java
Map<Object, String> cache = new WeakHashMap<>();
Object key = new Object();
cache.put(key, "some data");
key = null;    // remove the only strong reference
System.gc();   // hint (not guaranteed)
// After next GC cycle, the entry silently disappears from the map
```

Internally, WeakHashMap uses a `ReferenceQueue`. On every operation, it polls the queue for GC'd keys and removes their entries.

**Comparison:**

| Feature                | HashMap                    | WeakHashMap                             |
| ---------------------- | -------------------------- | --------------------------------------- |
| Key reference type     | Strong                     | Weak — GC can reclaim                   |
| Value reference type   | Strong                     | Strong                                  |
| Thread safety          | ❌ No                       | ❌ No                                    |
| Entries can vanish     | Only via explicit remove   | ✅ Anytime GC runs                       |

**Gotchas:**

1. **Values are held strongly.** If a value transitively holds a strong reference back to its key, the key is never GC'd → memory leak. Use `WeakReference` values too if this is a concern.
2. **Entries vanish between operations.** `size()` in a loop can return different values with no `remove` between calls.
3. **Not thread-safe.** No `ConcurrentWeakHashMap` in the JDK (there is `Collections.synchronizedMap(new WeakHashMap<>())`, but concurrent access to weak refs is a minefield).

**When to use:**
- **Framework internals:** listener registrations, ClassLoader-scoped caches, Spring/Hibernate metadata that should be released with the objects that own them.
- **Application code: rarely.** If you catch yourself considering WeakHashMap, first ask if a proper cache library (`Caffeine`) fits better.

---

### 5.6 HashMap vs IdentityHashMap

`IdentityHashMap` uses **reference equality** (`==`) and `System.identityHashCode()` instead of `.equals()` and `.hashCode()`.

**Behavioral difference:**

```java
IdentityHashMap<String, Integer> map = new IdentityHashMap<>();
String a = new String("hello");
String b = new String("hello");   // different object, same value

map.put(a, 1);
System.out.println(map.get(a));   // 1
System.out.println(map.get(b));   // null !!  — a != b (though a.equals(b))
```

In a regular HashMap, `get(b)` would return `1` because `a.equals(b)`. IdentityHashMap doesn't care about `.equals()`.

**Also different internally:** uses **open addressing** (linear probing), not chaining. Different DS entirely from HashMap under the hood.

**When to use IdentityHashMap:**
- **Object-graph traversal** (deep copy, serialization, cycle detection where identity matters, not value).
- **Frameworks tracking visited objects** where two distinct-but-equal objects should be tracked separately.
- **Almost never in application code.** If you're reaching for it, question whether you actually want reference identity semantics — usually you don't.

**Watch out:** iterating an `IdentityHashMap` gives entries in unpredictable order (as always with hash-based maps), and its `entrySet()` returns a special reusable entry — different from HashMap's contract.

---

### 5.7 HashMap vs EnumMap

`EnumMap` is a specialized map where **keys must be values of a single enum type**.

**How it works:** backed by a small `Object[]` array indexed by the enum's `ordinal()`. Zero hashing, zero collision handling — direct array access.

```java
enum Day { MON, TUE, WED, THU, FRI, SAT, SUN }

EnumMap<Day, Integer> hoursWorked = new EnumMap<>(Day.class);
hoursWorked.put(Day.MON, 8);
hoursWorked.put(Day.TUE, 8);

// Internally:  Object[7]  with slot [0] = 8, slot [1] = 8, ...
```

**Comparison:**

| Feature              | HashMap<Day, Integer>   | EnumMap<Day, Integer>            |
| -------------------- | ----------------------- | -------------------------------- |
| Backing structure    | Bucket array + chains   | Simple `Object[]`, size = # enum values |
| get / put / remove   | O(1) with hashing       | O(1), **no hashing** (direct array access) |
| Iteration order      | Undefined               | **Natural enum declaration order** |
| Memory per entry     | ~32 bytes (Node object) | 4-8 bytes (array slot only)      |
| Speed                | Baseline                | **2-3× faster in practice**      |
| Thread safety        | No                      | No                               |
| Null key             | ✅ 1 allowed            | ❌ NPE                            |

**When to use EnumMap:**

> **Any time you have enum keys — use EnumMap. Always. No exceptions.**

There's literally no downside vs HashMap for enum keys. Faster, smaller, order-preserving. It's rare to have a hard rule like this, but this is one of them.

---

### 5.8 Decision tree — which map when

```
Need thread safety?
├── YES →
│   ├── Sorted iteration required? → ConcurrentSkipListMap
│   └── Unordered? → ConcurrentHashMap
└── NO →
    ├── Enum keys? → EnumMap (always, no exceptions)
    ├── Need sorted iteration OR range queries (floor/ceiling)? → TreeMap
    ├── Need insertion/access order (or building LRU)? → LinkedHashMap
    ├── Framework internals — reference-equality keys? → IdentityHashMap
    ├── GC-linked cache — should release with keys? → WeakHashMap
    └── Default → HashMap
```

**One-liner heuristics:**

- **HashMap** — the default. Use unless you have a specific reason not to.
- **LinkedHashMap** — HashMap with predictable order (and LRU superpower).
- **TreeMap** — HashMap traded for O(log n) but sorted with `NavigableMap` API.
- **ConcurrentHashMap** — HashMap for multi-threaded code.
- **EnumMap** — HashMap replacement for enum keys.
- **WeakHashMap** — HashMap where GC can prune entries.
- **IdentityHashMap** — HashMap using `==` instead of `equals()`.
- **Hashtable** — dead. Don't use.

---

### 5.9 When to use `int[]` array instead of HashMap

For **bounded, small, contiguous key domains**, a primitive `int[]` (or `boolean[]`, `char[]`) is often **10-100× faster** than a HashMap.

**Common bounded domains:**
- 26 lowercase letters (`char - 'a'` gives index 0-25)
- 128 or 256 ASCII characters
- Small int ranges (0-100, 1-1000)
- Days of the week, months, bit flags

**Advantages over HashMap:**

- **No hashing computation** — direct array index.
- **No boxing** — primitives (`int`), not `Integer` objects.
- **Cache-friendly** — contiguous memory, one CPU cache line holds ~16 slots.
- **Tiny memory footprint** — 4 bytes per slot vs ~40 bytes per HashMap entry.
- **Simpler code** — `arr[c - 'a']++` vs `map.merge(c, 1, Integer::sum)`.

**Example — character frequency count:**

```java
// HashMap version — ~30-50 ns per character, ~40 bytes per entry
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) {
    freq.merge(c, 1, Integer::sum);
}

// int[] version — ~2-5 ns per character, 4 bytes per slot
int[] freq = new int[26];
for (char c : s.toCharArray()) {
    freq[c - 'a']++;
}
```

**Real interview signal:** when the interviewer sees you reach for `int[]` instead of `HashMap` for character counting, they know you understand the trade-off. This is the correct answer for problems like Valid Anagram (LC 242), First Non-Repeating Character (LC 387), Ransom Note (LC 383) — anything with a fixed small alphabet.

**When `int[]` wins:**
- Fixed, small, contiguous key space (≤ few hundred).
- Performance matters.
- Simple integer counts or boolean flags.

**When HashMap wins:**
- Unbounded or sparse key space.
- Complex value types (objects, lists, custom classes).
- Need actual `Map` API (`values()`, `entrySet()`, streaming).
- Keys aren't naturally integer-ish (Strings, custom objects, etc.).

**Rule of thumb:** if the key domain fits in `[0, 1000]` and you're mapping to a counter or flag → `int[]`. Otherwise → HashMap.

---

## Phase 6 — Interview Deep Cuts

The topics that separate senior candidates from juniors. Master these to walk into any Java interview with confidence on HashMap.

---

### 6.1 The `equals` / `hashCode` contract — **critical** for HashMap

Every Java object inherits `equals()` and `hashCode()` from `Object`. If you override one, you MUST override the other consistently. Nowhere is this more important than for HashMap keys.

#### The five rules (from `Object.hashCode()` JavaDoc)

1. **Reflexive:** `x.equals(x)` is true.
2. **Symmetric:** `x.equals(y)` ⟺ `y.equals(x)`.
3. **Transitive:** `x.equals(y)` ∧ `y.equals(z)` → `x.equals(z)`.
4. **`equals` implies same `hashCode`:** if `x.equals(y)`, then `x.hashCode() == y.hashCode()`.  ← **the big one for HashMap**
5. **Consistent:** repeated calls return the same result if the object hasn't been mutated in a way that affects `equals`.

Note: the converse of rule 4 is NOT required. Two unequal objects CAN have the same `hashCode` (that's just a collision).

#### Why rule 4 is critical for HashMap

HashMap uses `hashCode` to **find the bucket**, then `equals` to **find the entry within the chain**. If two "equal" objects hash to different buckets, `get` will look in the wrong bucket and return `null` — even though the key is present.

#### The classic bug — override `equals` but not `hashCode`

```java
class Employee {
    int id;
    String name;
    Employee(int id, String name) { this.id = id; this.name = name; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Employee e)) return false;
        return id == e.id && Objects.equals(name, e.name);
    }
    // NO hashCode() override — inherits Object.hashCode() (memory-address-based)
}

Map<Employee, String> map = new HashMap<>();
map.put(new Employee(1, "Alice"), "Engineer");

Employee lookup = new Employee(1, "Alice");   // equal by equals(), but different object
System.out.println(map.get(lookup));   // null !!  ← the bug
```

**What happened:**
- `map.put(alice1, ...)` computed hash based on `alice1`'s memory address, placed in bucket X.
- `map.get(alice2)` computed hash based on `alice2`'s memory address (different!), looked in bucket Y.
- Bucket Y is empty → returns null.

The map DOES contain a matching entry — you just can't find it.

#### The fix

```java
@Override
public int hashCode() {
    return Objects.hash(id, name);
}
```

Now both objects produce the same hash → same bucket → `equals` finds a match → `get` returns "Engineer".

#### Rules of thumb

- **Always override both together.** IntelliJ, VS Code, Eclipse all have "Generate `equals()` and `hashCode()`" for this reason.
- **Use the same fields in both.** If `equals` compares `id` and `name`, `hashCode` must combine `id` and `name`.
- **Use `java.util.Objects.hash(...)`** — handles nulls and combines values with a standard formula.
- **Prefer Java records** (Java 16+) — records auto-generate `equals`, `hashCode`, `toString` from all components. Perfect for HashMap keys.

```java
record EmployeeKey(int id, String name) {}   // equals + hashCode auto-generated
```

---

### 6.2 Iterator + fail-fast + `modCount`

Just like LinkedList, HashMap's iterators are **fail-fast**: they detect concurrent structural modifications and throw `ConcurrentModificationException`.

#### How it works

- HashMap has an internal `modCount` counter. Every structural change (put new key, remove, resize) increments it.
- When you create an iterator, it snapshots `modCount` into `expectedModCount`.
- On every `iterator.next()`, it checks: `if (modCount != expectedModCount) throw new ConcurrentModificationException()`.

#### The buggy pattern — modifying inside a for-each loop

```java
Map<String, Integer> map = new HashMap<>();
map.put("a", 1);
map.put("b", 2);
map.put("c", 3);

for (String key : map.keySet()) {          // for-each uses an iterator underneath
    if (key.equals("b")) {
        map.remove(key);                    // ← modifies map, breaks iterator
    }
}
// throws ConcurrentModificationException on the next iteration
```

#### The correct patterns

**Option 1 — use `iterator.remove()` explicitly:**

```java
Iterator<Map.Entry<String, Integer>> it = map.entrySet().iterator();
while (it.hasNext()) {
    Map.Entry<String, Integer> e = it.next();
    if (e.getKey().equals("b")) {
        it.remove();   // iterator stays consistent
    }
}
```

**Option 2 — use `removeIf` on `entrySet` (Java 8+, cleanest):**

```java
map.entrySet().removeIf(e -> e.getKey().equals("b"));
```

**Option 3 — collect keys first, then remove:**

```java
List<String> toRemove = map.keySet().stream()
    .filter(k -> k.equals("b"))
    .toList();
toRemove.forEach(map::remove);
```

#### The trap — CME is NOT about concurrency

Despite the name, `ConcurrentModificationException` fires even in **single-threaded** code (as shown above). The name is historical. A clearer name would be `InterleavedModificationException` — "you're modifying the collection between iterator calls."

Same pattern applies as in LinkedList (see linklist Phase 6.1). Also, HashMap replaces (not structural — same key, new value) do NOT increment `modCount` and thus do NOT trigger CME.

---

### 6.3 Thread safety — race conditions in single-threaded misuse

(Actual multi-threaded concurrency is Phase 9. This subsection is about the *single-threaded* thread-safety traps that catch people.)

#### The pattern that looks safe but isn't

```java
if (!map.containsKey(key)) {
    map.put(key, computeExpensiveValue(key));
}
```

In single-threaded code, this works. In multi-threaded code, two threads can both pass the `!containsKey` check and both call `computeExpensiveValue` — wasted work at best, corruption at worst.

**Fix:** use `computeIfAbsent`:

```java
map.computeIfAbsent(key, k -> computeExpensiveValue(k));
```

For HashMap, this is atomic in the sense that the check-and-put happens in one method call (no interleaved race between the check and the put in single-threaded code). For ConcurrentHashMap, it's truly atomic across threads.

#### The CME trap in "safe-looking" code

```java
map.forEach((k, v) -> {
    if (v == null) {
        map.remove(k);   // CME !!
    }
});
```

`forEach` uses an iterator underneath. Modifying `map` inside the lambda breaks it. Use `removeIf` or `entrySet().removeIf` instead.

#### Recap

- **HashMap is NOT thread-safe** — don't use it for shared state across threads.
- **CME fires single-threaded too** — anytime you modify a map through the map's own API while iterating.
- Use `iterator.remove()`, `removeIf`, or `computeIfAbsent` for safe patterns.

Full concurrency deep-dive is Phase 9.

---

### 6.4 What happens when you mutate a key after inserting

**The orphaned-entry bug.** Silent, no exception, one of the sneakiest data-loss bugs in Java.

#### The scenario

```java
class MutablePoint {
    int x, y;
    MutablePoint(int x, int y) { this.x = x; this.y = y; }

    @Override
    public boolean equals(Object o) {
        return o instanceof MutablePoint p && x == p.x && y == p.y;
    }
    @Override
    public int hashCode() { return Objects.hash(x, y); }
}

Map<MutablePoint, String> map = new HashMap<>();
MutablePoint p = new MutablePoint(1, 2);
map.put(p, "origin");

System.out.println(map.get(p));   // "origin" — works

p.x = 99;                          // MUTATE THE KEY

System.out.println(map.get(p));   // null !!  ← orphaned
System.out.println(map.containsKey(p));   // false !!
System.out.println(map.size());   // 1 (entry still counted)

// Even worse: iterate the map and see the "unfindable" entry
map.entrySet().forEach(e ->
    System.out.println(e.getKey() + " → " + e.getValue()));
// prints: MutablePoint@... → origin (yes, it's there)
```

#### What happened

- `put(p, "origin")` computed hash from `(1, 2)` → placed in bucket X.
- `p.x = 99` changed the state. Now `p.hashCode()` computes from `(99, 2)` → maps to bucket Y (different!).
- `map.get(p)` computes hash from current state → looks in bucket Y → not found.
- The entry still lives in bucket X, but no key value can find it — it's **orphaned**.

#### The fix

**Never use mutable objects as HashMap keys.** Or if you must, never mutate them after inserting.

Use immutable keys:
- `String`, `Integer`, `LocalDate`, `UUID`
- Java records
- Custom classes with `final` fields and no setters

#### Interview soundbite

> *"If a key's hashCode changes after insertion, the map looks in the wrong bucket and reports the key as absent. The entry still lives in the map — you just can't find it. The rule: never use mutable objects as HashMap keys, or never mutate them after inserting."*

---

### 6.5 Null keys and null values

Three maps have three different rules:

| Map                     | null key   | null value  |
| ----------------------- | ---------- | ----------- |
| **HashMap**             | ✅ 1 allowed | ✅ Any      |
| **Hashtable**           | ❌ NPE      | ❌ NPE       |
| **ConcurrentHashMap**   | ❌ NPE      | ❌ NPE       |
| **TreeMap**             | ❌ NPE (unless custom Comparator handles it) | ✅ Any |
| **LinkedHashMap**       | ✅ 1 allowed | ✅ Any      |

#### How HashMap handles the null key

Special-cased at the top of `hash()`:

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

Null → hash 0 → bucket 0. Only ONE null key can exist (any subsequent `put(null, v)` overwrites the value).

#### Why ConcurrentHashMap forbids nulls (design decision)

The problem: `map.get(k)` returning `null` should unambiguously mean "not present." If null were a valid value, you'd need `containsKey` to distinguish "not present" from "present with null" — which requires a second lookup, breaking atomicity guarantees in a concurrent context.

Doug Lea (author of ConcurrentHashMap) explains: *"There is no way to distinguish keys that are absent vs keys that are present but have null values, unless you sync — which defeats the point of a concurrent map."*

HashMap doesn't have this problem because it doesn't guarantee atomicity, so it accepts nulls freely.

#### The ambiguity trap in HashMap (still an issue!)

```java
map.put("a", null);

if (map.get("a") == null) {
    // Is "a" not present? Or present with null value?
    // Impossible to tell from get() alone.
}

if (map.containsKey("a")) {
    // "a" is present; value might be null
}
```

**Rule:** if your map might store null values, always use `containsKey` before treating a null return as "absent."

---

### 6.6 Immutable keys — best practice

**Immutable keys are ideal HashMap keys.** Once an immutable object is placed in a map, its hash cannot change → orphaned-entry bug (Phase 6.4) is impossible.

#### The built-in safe keys

- **`String`** — final, immutable. The single most common map key.
- **`Integer`, `Long`, `Double`, ...** — wrapper types are immutable.
- **`java.time.*`** — `LocalDate`, `LocalDateTime`, `Instant`, etc. All immutable.
- **`UUID`** — final class, immutable state.
- **`Enum` values** — inherently immutable. Prefer `EnumMap` for enum keys (Phase 5.7).

#### Custom class checklist for safe key use

1. **`final class`** — prevents subclasses from breaking immutability.
2. **All fields `private final`** — no setters, no mutations after construction.
3. **Defensive copies for mutable field types** — if you store a `List<T>`, copy it in the constructor AND return an unmodifiable view from getters.
4. **Override `equals` and `hashCode`** — using all identity-defining fields, consistently.
5. **Override `toString`** — helpful for debugging.

#### The easiest way — records (Java 16+)

```java
public record EmployeeKey(int id, String name) {}
```

- `final` implicitly.
- All fields `final` implicitly.
- `equals`, `hashCode`, `toString` auto-generated from components.
- `id()` and `name()` accessors (no setters).

**Records are the gold standard for HashMap keys.** If you need a compound key, reach for a record first.

#### The old way (pre-Java 16)

```java
public final class EmployeeKey {
    private final int id;
    private final String name;

    public EmployeeKey(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId()    { return id; }
    public String getName() { return name; }

    @Override public boolean equals(Object o) {
        return o instanceof EmployeeKey k && id == k.id && Objects.equals(name, k.name);
    }
    @Override public int hashCode() { return Objects.hash(id, name); }
    @Override public String toString() { return "EmployeeKey[id=" + id + ", name=" + name + "]"; }
}
```

Same behavior; ~15 lines instead of 1.

---

### 6.7 Load factor & capacity tuning

Covered technically in Phase 3.8. Here's the practical tuning guide.

#### Sizing tip — avoid resizes entirely

If you know you'll hold N entries, size the map upfront:

```java
// Pre-Java 19
Map<String, Integer> map = new HashMap<>((int) (N / 0.75f) + 1);

// Java 19+ (cleaner and correct)
Map<String, Integer> map = HashMap.newHashMap(N);
```

Without pre-sizing, adding N entries triggers multiple resizes (each O(n)) — waste of work for a known workload.

#### The load-factor knob

- **0.5** — half the space empty. Very fast lookups, wasteful memory. Use for latency-critical hot maps.
- **0.75 (default)** — sweet spot. Poisson math backs this up.
- **0.9** — ~10% wasted, but chains lengthen → slower lookups and iteration.
- **1.0+** — allowed but rarely wise.

#### When to tune

**Almost never.** Cases where it's worth measuring:
1. Fixed-size hot maps that live for the app's lifetime (consider 0.5 for latency).
2. Enormous maps where memory pressure is real (consider 0.85+, profile carefully).
3. Never based on hunches — always with a JMH benchmark.

---

### 6.8 `Map.of()`, `Map.copyOf()`, `Map.entry()` — Java 9+ factories

Modern Java has clean, immutable Map factory methods. Use them everywhere for small constant maps.

#### `Map.of(...)` — inline immutable map

```java
// Up to 10 entries with the varargs-free overloads
Map<String, Integer> ages = Map.of(
    "Alice", 30,
    "Bob", 25,
    "Charlie", 35
);
```

- **Immutable** — any modification throws `UnsupportedOperationException`.
- **Null-hostile** — `Map.of("a", null)` throws NPE. Both keys and values.
- **Duplicate-key hostile** — `Map.of("a", 1, "a", 2)` throws `IllegalArgumentException`.
- **Iteration order NOT specified** — deliberately randomized between JVM instances to prevent code from depending on it.

#### `Map.ofEntries(...)` — for more than 10 entries

```java
Map<String, Integer> ages = Map.ofEntries(
    Map.entry("Alice", 30),
    Map.entry("Bob", 25),
    Map.entry("Charlie", 35),
    // ... any number of entries
    Map.entry("Zoe", 22)
);
```

Same immutability + null-hostility as `Map.of`.

#### `Map.copyOf(existing)` — defensive immutable copy

```java
Map<String, Integer> mutable = new HashMap<>();
mutable.put("a", 1);
mutable.put("b", 2);

Map<String, Integer> snapshot = Map.copyOf(mutable);
// snapshot is immutable and independent
```

Perfect for returning immutable snapshots from methods, guarding against caller mutations.

#### Behavior differences vs `new HashMap<>()`

| Behavior              | `Map.of()`                    | `new HashMap<>()`      |
| --------------------- | ----------------------------- | ---------------------- |
| Mutability            | Immutable (throws on mutation) | Mutable               |
| Null key/value        | ❌ NPE                         | ✅ Allowed              |
| Duplicate keys        | ❌ IllegalArgumentException    | Silently overwrites    |
| Iteration order       | Unspecified, randomized       | Bucket order           |
| Memory footprint      | Minimal, specialized          | Full HashMap structure |

**Use `Map.of` for constants.** Use `new HashMap<>()` when you'll modify the map.

---

### 6.9 Serialization internals

Similar to LinkedList (Phase 2.2 in the linklist file) — HashMap uses custom serialization.

#### Why `table` is `transient`

Look at the JDK source:

```java
transient Node<K,V>[] table;
transient int size;
transient int modCount;
int threshold;         // NOT transient
final float loadFactor; // NOT transient
```

If HashMap serialized `table` as-is, you'd write:
- The whole `Node[]` array (mostly null buckets — wasted bytes).
- Each `Node`'s `next` pointer, `hash` value — all recomputable garbage.
- The exact chain structure, which depends on JDK version's collision handling.

Deserializing that on a different JVM version (or after Java 8's treeification change) could be broken or bloated.

#### The solution — custom `writeObject` / `readObject`

`writeObject` serializes: capacity, size, then each entry's `(key, value)` pair.

`readObject` rebuilds: creates a fresh table of the recorded capacity, then re-puts each entry — computing fresh hashes for THIS JVM version.

Benefits:
- **Portable across JDK versions** — no dependency on internal bucket structure.
- **Compact** — no null-bucket bytes, no `next` pointers.
- **Rebuilds correctly** — the deserialized map uses the CURRENT JDK's hash function and collision strategy.

#### The subtle gotcha

If the key's `hashCode()` implementation differs between the sending and receiving JVMs (e.g., different classpath versions of the key class), the deserialized map may organize entries differently. But `get` will still find them correctly because both `put` and `get` use the same current JVM's hash function.

Real problem: if the key class has non-deterministic `hashCode()` (rare but possible), the entries end up in different buckets on each deserialization. Iteration order will vary — don't depend on it.

---

### 6.10 Self-check (answer without looking)

> Try answering each in your head *first*, then check.

1. **Which rule of the equals/hashCode contract is most critical for HashMap?**
2. **Why does CME fire even in single-threaded code?**
3. **What's the "orphaned entry" bug and how do you avoid it?**
4. **Can HashMap store a null key? Can ConcurrentHashMap? Why the difference?**
5. **Why does `Map.of("a", 1)` differ from `new HashMap<>(Map.of("a", 1))`?**

#### Answers

1. **Rule 4: `x.equals(y)` implies `x.hashCode() == y.hashCode()`.** HashMap uses `hashCode` to find the bucket, then `equals` to find the entry within the chain. If two "equal" objects hash to different buckets, `get` will look in the wrong bucket and return `null` — even though the key IS present in the map. Common bug: overriding `equals` but forgetting `hashCode`. Both must be overridden together, using the same fields consistently. Modern fix: use Java records (auto-generates both).

2. **Because "concurrent" is a misleading name.** CME fires anytime the collection is structurally modified between iterator calls — including single-threaded misuse. The classic example: modifying a map inside a `for-each` loop over its `keySet` or `entrySet`. `for-each` uses an iterator underneath; modifying the map increments `modCount`; next `iterator.next()` sees the mismatch and throws. Fix: use `iterator.remove()`, `entrySet().removeIf(...)`, or collect-then-remove.

3. **A mutation to a key changes its `hashCode()`, so the map looks in a different bucket than the one where the entry lives.** Silent bug — no exception. The entry is still in the map (visible via iteration, counted in `size()`), but `get(k)` returns null and `containsKey(k)` returns false. **Avoid:** never use mutable objects as keys, or never mutate them after inserting. Prefer `String`, `Integer`, `LocalDate`, `UUID`, or immutable Java records.

4. **HashMap: yes (1 null key + any null values). ConcurrentHashMap: NO — throws NPE.** The reason for CHM's restriction: `map.get(k)` returning null must unambiguously mean "not present." If null were a valid value, distinguishing "not present" from "present with null value" would need a second `containsKey` lookup, breaking atomicity guarantees in a concurrent context. HashMap doesn't guarantee atomicity in the first place, so it accepts nulls freely.

5. **`Map.of(...)` returns an IMMUTABLE map — modifications throw `UnsupportedOperationException`. It's also null-hostile, duplicate-key hostile, and has randomized iteration order.** `new HashMap<>(Map.of("a", 1))` creates a MUTABLE HashMap seeded with the same entries — you can add, remove, or replace freely. Use `Map.of` for compile-time constants; use `new HashMap<>()` when you need mutation.

---

## Phase 7 — Coding Problems: Core Patterns

> 17 problems covering the **6 transferable HashMap techniques**. Learn the technique, not just the solution — the same tricks show up in dozens of variants.

---

### 7.1 Two Sum (LC 1)

**Problem:** Given an array `nums` and a target, return indices of two numbers that add up to `target`. Exactly one solution exists; can't reuse the same index.

#### Insight — complementary lookup

For each element `x`, ask: *"Is `target - x` already in the map?"* If yes → found the pair. If no → put `x` in the map (value → index).

One pass. No nested loop needed.

#### Code

```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        if (seen.containsKey(complement)) {
            return new int[]{seen.get(complement), i};
        }
        seen.put(nums[i], i);
    }
    return new int[0];
}
```

**Complexity:** O(n) time, O(n) space.

**Trap:** return **indices**, not values. Also — put `nums[i]` in the map *after* checking, not before. Otherwise `[3, 4]` with `target = 6` would incorrectly pair 3 with itself.

**Technique:** Complementary lookup.

---

### 7.2 Contains Duplicate (LC 217)

**Problem:** Return `true` if any value in the array appears at least twice.

#### Insight — HashSet for O(1) membership

`HashSet.add()` returns `false` if the element was already present. That's your detection.

#### Code

```java
public boolean containsDuplicate(int[] nums) {
    Set<Integer> seen = new HashSet<>();
    for (int n : nums) {
        if (!seen.add(n)) return true;
    }
    return false;
}
```

**Complexity:** O(n) time, O(n) space.

**Trap:** don't over-engineer. Some candidates reach for sorting (O(n log n)) or nested loops (O(n²)) — HashSet is the right tool.

**Technique:** HashSet for O(1) membership.

---

### 7.3 Valid Anagram (LC 242)

**Problem:** Return `true` if `t` is an anagram of `s` (same characters, possibly reordered).

#### Insight — frequency counting

For lowercase English letters, `int[26]` beats `HashMap<Character, Integer>` (Phase 5.9). Increment for `s`, decrement for `t`. If any count ≠ 0 → not an anagram.

#### Code

```java
public boolean isAnagram(String s, String t) {
    if (s.length() != t.length()) return false;
    int[] freq = new int[26];
    for (int i = 0; i < s.length(); i++) {
        freq[s.charAt(i) - 'a']++;
        freq[t.charAt(i) - 'a']--;
    }
    for (int c : freq) if (c != 0) return false;
    return true;
}
```

**Complexity:** O(n) time, O(1) space (fixed 26-int array).

**Trap:** length mismatch → immediate `false`. Also — if the problem allows Unicode, fall back to `HashMap<Character, Integer>`.

**Technique:** Frequency counting.

---

### 7.4 Group Anagrams (LC 49)

**Problem:** Group strings that are anagrams of each other. Return list of groups.

#### Insight — anagrams have the same "signature"

Two options for the signature (= HashMap key):
- **Sorted-string key:** `"eat"` → `"aet"`. O(n · k log k) total.
- **Frequency-array key:** `"eat"` → some string built from a 26-length count array. O(n · k) total.

Sorted key is simpler; frequency key is faster for very long words.

#### Code (sorted-key version)

```java
public List<List<String>> groupAnagrams(String[] strs) {
    Map<String, List<String>> groups = new HashMap<>();
    for (String s : strs) {
        char[] chars = s.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars);
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    return new ArrayList<>(groups.values());
}
```

**Complexity:** O(n · k log k) time (n words, k avg length), O(n · k) space.

**Trap:** for freq-array-key version, use `Arrays.toString(freq)` or build a delimited string — never use the array itself as a HashMap key (array `equals`/`hashCode` are reference-based!).

**Technique:** Frequency counting + custom hash key. Also uses `computeIfAbsent` (Phase 6 idiom).

---

### 7.5 First Non-Repeating Character (LC 387)

**Problem:** Find the first character in `s` that appears only once. Return its index, or -1 if none.

#### Insight — two passes

Pass 1: count frequencies. Pass 2: walk again, return index of first char with count 1.

Single-pass with `LinkedHashMap` is possible but overkill.

#### Code

```java
public int firstUniqChar(String s) {
    int[] freq = new int[26];
    for (int i = 0; i < s.length(); i++) {
        freq[s.charAt(i) - 'a']++;
    }
    for (int i = 0; i < s.length(); i++) {
        if (freq[s.charAt(i) - 'a'] == 1) return i;
    }
    return -1;
}
```

**Complexity:** O(n) time, O(1) space.

**Trap:** must return the **first** such character's index. Sorting or scrambling the count doesn't work — you need to walk the string in order the second time.

**Technique:** Frequency counting.

---

### 7.6 Ransom Note (LC 383)

**Problem:** Can `ransomNote` be built from `magazine`'s letters? (Each magazine letter used at most once.)

#### Insight — frequency count with subtraction

Count magazine's chars. Walk the ransom note; decrement. If any goes negative → magazine ran out of that letter → false.

#### Code

```java
public boolean canConstruct(String ransomNote, String magazine) {
    int[] freq = new int[26];
    for (char c : magazine.toCharArray()) freq[c - 'a']++;
    for (char c : ransomNote.toCharArray()) {
        if (--freq[c - 'a'] < 0) return false;
    }
    return true;
}
```

**Complexity:** O(n + m) time, O(1) space.

**Trap:** don't compare after the loop — check `< 0` inline as you decrement. Early exit is faster and cleaner.

**Technique:** Frequency counting.

---

### 7.7 Longest Palindrome (LC 409)

**Problem:** Given a string, find the length of the longest palindrome that can be built from its chars.

#### Insight — palindrome parity math

- Every **even-count** char contributes fully (all of it).
- Every **odd-count** char contributes `count - 1` (the even part).
- Plus **one extra char at center** if any odd count exists.

Example: `"abccccdd"` → counts: a=1, b=1, c=4, d=2. Contributions: c=4, d=2, a=0+1(center) or b=0+1(center) → 4 + 2 + 1 = 7. Palindrome: `dccaccd`.

#### Code

```java
public int longestPalindrome(String s) {
    int[] freq = new int[128];   // ASCII, handles both cases
    for (char c : s.toCharArray()) freq[c]++;

    int length = 0;
    boolean hasOdd = false;
    for (int count : freq) {
        length += (count / 2) * 2;   // even portion
        if (count % 2 == 1) hasOdd = true;
    }
    return length + (hasOdd ? 1 : 0);
}
```

**Complexity:** O(n) time, O(1) space.

**Trap:** don't confuse with "longest palindromic substring" (LC 5) — very different problem.

**Technique:** Frequency counting + parity math.

---

### 7.8 Top K Frequent Elements (LC 347)

**Problem:** Return the `k` most frequent elements from `nums`.

#### Insight — frequency count + min-heap of size k

Step 1: count frequencies.
Step 2: maintain a min-heap of size k, keyed by frequency. Pop when it exceeds k. What's left is the top k.

Why min-heap? So we can pop the *smallest* element cheaply when a bigger one arrives.

#### Code

```java
public int[] topKFrequent(int[] nums, int k) {
    Map<Integer, Integer> freq = new HashMap<>();
    for (int n : nums) freq.merge(n, 1, Integer::sum);

    PriorityQueue<Integer> heap = new PriorityQueue<>(
        (a, b) -> freq.get(a) - freq.get(b)   // min-heap by frequency
    );
    for (int key : freq.keySet()) {
        heap.offer(key);
        if (heap.size() > k) heap.poll();
    }

    int[] result = new int[k];
    for (int i = k - 1; i >= 0; i--) result[i] = heap.poll();
    return result;
}
```

**Complexity:** O(n log k) time, O(n) space.

**Trap:** don't use a max-heap of ALL elements (O(n log n)). The min-heap-of-k trick is what makes this efficient.

**Alternative:** bucket sort by frequency → O(n) time, O(n) space. Mention it in the interview to signal you know both.

**Technique:** Frequency counting + priority queue.

---

### 7.9 Isomorphic Strings (LC 205)

**Problem:** Are `s` and `t` isomorphic? I.e., can chars in `s` be replaced 1-to-1 to become `t`? `"egg" & "add"` → true. `"foo" & "bar"` → false.

#### Insight — need TWO maps (bijective mapping)

A single map only enforces "each s-char maps to some t-char." It doesn't prevent **two different s-chars from mapping to the same t-char**.

Example why single map fails: `"ab"` & `"aa"` — with single map: `{a→a, b→a}`. Both `a` and `b` map to `a` in `t`. Not a bijection.

Two maps catch this: forward map (s→t) AND reverse map (t→s). If either finds a conflicting existing mapping → false.

#### Code

```java
public boolean isIsomorphic(String s, String t) {
    if (s.length() != t.length()) return false;
    int[] sToT = new int[128];
    int[] tToS = new int[128];
    for (int i = 0; i < s.length(); i++) {
        char sc = s.charAt(i), tc = t.charAt(i);
        if (sToT[sc] == 0 && tToS[tc] == 0) {
            sToT[sc] = tc;
            tToS[tc] = sc;
        } else if (sToT[sc] != tc || tToS[tc] != sc) {
            return false;
        }
    }
    return true;
}
```

**Complexity:** O(n) time, O(1) space.

**Trap:** single-map is the buggy approach interviewers watch for. **Always ask yourself: "does this need to be a bijection?"**

**Technique:** Bijective mapping.

---

### 7.10 Word Pattern (LC 290)

**Problem:** Pattern like `"abba"` and string like `"dog cat cat dog"` — do they follow the same 1-to-1 pattern?

#### Insight — same trap as Isomorphic Strings, char ↔ word

Split string by spaces. Then walk both, using two HashMaps (char→word and word→char) to enforce bijection.

#### Code

```java
public boolean wordPattern(String pattern, String s) {
    String[] words = s.split(" ");
    if (pattern.length() != words.length) return false;

    Map<Character, String> charToWord = new HashMap<>();
    Map<String, Character> wordToChar = new HashMap<>();

    for (int i = 0; i < pattern.length(); i++) {
        char c = pattern.charAt(i);
        String w = words[i];
        if (charToWord.containsKey(c)) {
            if (!charToWord.get(c).equals(w)) return false;
        } else if (wordToChar.containsKey(w)) {
            return false;
        } else {
            charToWord.put(c, w);
            wordToChar.put(w, c);
        }
    }
    return true;
}
```

**Complexity:** O(n) time and space.

**Trap:** length mismatch → immediate false. Same bijection trap as 7.9.

**Technique:** Bijective mapping.

---

### 7.11 Roman to Integer (LC 13)

**Problem:** Convert a Roman numeral string (like `"MCMXCIV"`) to integer (1994). Values: `I=1, V=5, X=10, L=50, C=100, D=500, M=1000`. Subtraction when smaller precedes larger (IV=4, IX=9, XL=40, XC=90, CD=400, CM=900).

#### Insight — HashMap for value lookup + look-ahead

Walk left to right. If current symbol's value is **less than** the next symbol's, subtract; otherwise add.

#### Code

```java
public int romanToInt(String s) {
    Map<Character, Integer> values = Map.of(
        'I', 1, 'V', 5, 'X', 10, 'L', 50,
        'C', 100, 'D', 500, 'M', 1000
    );

    int total = 0;
    for (int i = 0; i < s.length(); i++) {
        int curr = values.get(s.charAt(i));
        int next = (i + 1 < s.length()) ? values.get(s.charAt(i + 1)) : 0;
        total += (curr < next) ? -curr : curr;
    }
    return total;
}
```

**Complexity:** O(n) time, O(1) space.

**Trap:** last char always added (no next to compare). The `(i + 1 < s.length()) ? ... : 0` handles this cleanly.

**Technique:** HashMap for constant lookup.

---

### 7.12 Subarray Sum Equals K (LC 560)

**Problem:** Count the number of contiguous subarrays whose sum equals `k`.

#### Insight — prefix sum + HashMap (the "aha" technique)

If `prefixSum[i] - prefixSum[j] = k`, then the subarray from `j+1` to `i` sums to `k`. So for each `i`, ask: *"How many `j < i` had `prefixSum[j] = currentPrefixSum - k`?"*

HashMap: `prefixSum → count of times we've seen it`.

#### Code

```java
public int subarraySum(int[] nums, int k) {
    Map<Integer, Integer> prefixCount = new HashMap<>();
    prefixCount.put(0, 1);   // CRITICAL — empty prefix has sum 0, seen once

    int sum = 0, count = 0;
    for (int n : nums) {
        sum += n;
        count += prefixCount.getOrDefault(sum - k, 0);
        prefixCount.merge(sum, 1, Integer::sum);
    }
    return count;
}
```

**Complexity:** O(n) time, O(n) space.

**Trap:** **forgetting `prefixCount.put(0, 1)`** at the start. Without it, subarrays that start at index 0 (whose prefix sum before them is 0) aren't counted. Classic off-by-one bug.

**Technique:** Prefix sum + HashMap. **One of the most powerful patterns in HashMap problems.**

---

### 7.13 Contains Duplicate II (LC 219)

**Problem:** Return `true` if there are duplicate values at indices `i, j` such that `|i - j| ≤ k`.

#### Insight — HashMap tracks last-seen index

For each element, check if we've seen it before AND if the current index minus the last-seen index is ≤ k.

#### Code

```java
public boolean containsNearbyDuplicate(int[] nums, int k) {
    Map<Integer, Integer> lastSeen = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        Integer prev = lastSeen.get(nums[i]);
        if (prev != null && i - prev <= k) return true;
        lastSeen.put(nums[i], i);   // update to latest index
    }
    return false;
}
```

**Complexity:** O(n) time, O(n) space.

**Trap:** always update to the LATEST index (overwrite). Keeping the earliest would miss closer duplicates later.

**Alternative:** HashSet as a sliding window of size `k+1`. Also O(n), sometimes cleaner.

**Technique:** HashMap for state tracking (last-seen index).

---

### 7.14 Longest Consecutive Sequence (LC 128)

**Problem:** Given unsorted `nums`, find the length of the longest run of consecutive integers. Must be O(n).

#### Insight — HashSet + "only walk from sequence starts"

Naive: for each element, walk +1, +2, ... counting. O(n²) — quadratic.

**Trick:** only walk from an element `x` if `x-1` is NOT in the set. That way, each sequence is walked exactly once (from its smallest element).

#### Code

```java
public int longestConsecutive(int[] nums) {
    Set<Integer> set = new HashSet<>();
    for (int n : nums) set.add(n);

    int best = 0;
    for (int n : set) {
        if (!set.contains(n - 1)) {   // n is the start of a sequence
            int current = n, length = 1;
            while (set.contains(current + 1)) {
                current++;
                length++;
            }
            best = Math.max(best, length);
        }
    }
    return best;
}
```

**Complexity:** O(n) time, O(n) space.

**Why O(n), not O(n²):** the inner `while` loop only runs for sequence *starts*. Every number is walked at most twice — once as a potential start (fast rejection if `n-1` exists), once during a sequence walk. **Sum of all walks = O(n).**

**Trap:** iterate over the SET, not the original array — avoids re-processing duplicates.

**Technique:** HashSet + smart iteration.

---

### 7.15 Happy Number (LC 202)

**Problem:** A number is "happy" if repeatedly summing squares of its digits eventually reaches 1. Return `true` if `n` is happy.
Example: `19 → 1² + 9² = 82 → 8² + 2² = 68 → 6² + 8² = 100 → 1² + 0² + 0² = 1 ✓`

#### Insight — cycle detection with HashSet

If not happy, the sequence eventually cycles. Use a HashSet: if we ever see a repeat, it's a cycle → not happy.

#### Code

```java
public boolean isHappy(int n) {
    Set<Integer> seen = new HashSet<>();
    while (n != 1 && !seen.contains(n)) {
        seen.add(n);
        n = sumSquares(n);
    }
    return n == 1;
}

private int sumSquares(int n) {
    int sum = 0;
    while (n > 0) {
        int d = n % 10;
        sum += d * d;
        n /= 10;
    }
    return sum;
}
```

**Complexity:** O(log n) per iteration (digit count); total iterations bounded (small).

**Alternative:** Floyd's tortoise & hare (from linklist Phase 4.2!) — same cycle-detection technique, O(1) space. Both are valid; mention both in interviews.

**Technique:** HashSet for cycle detection.

---

### 7.16 Intersection of Two Arrays II (LC 350)

**Problem:** Return the intersection of two arrays. If an element appears X times in `nums1` and Y times in `nums2`, it should appear `min(X, Y)` times in the output.

#### Insight — frequency count with subtraction

Count `nums1`'s freqs. Walk `nums2`; if count > 0, add to result and decrement.

#### Code

```java
public int[] intersect(int[] nums1, int[] nums2) {
    Map<Integer, Integer> freq = new HashMap<>();
    for (int n : nums1) freq.merge(n, 1, Integer::sum);

    List<Integer> result = new ArrayList<>();
    for (int n : nums2) {
        Integer c = freq.get(n);
        if (c != null && c > 0) {
            result.add(n);
            freq.put(n, c - 1);
        }
    }
    return result.stream().mapToInt(Integer::intValue).toArray();
}
```

**Complexity:** O(n + m) time, O(min(n, m)) space (count the smaller array).

**Trap:** intersection I (LC 349) is different — it uses HashSet, no duplicates. II preserves duplicate counts.

**Optimization:** always count the *smaller* array to minimize memory.

**Technique:** Frequency count with subtraction.

---

### 7.17 Sudoku Validator (LC 36)

**Problem:** Validate a 9×9 Sudoku board. Each row, column, and 3×3 box must contain digits 1-9 without repetition (empty cells are `.` and are skipped).

#### Insight — three sets of 9 HashSets

- 9 sets for rows.
- 9 sets for columns.
- 9 sets for 3×3 boxes.

Walk each cell; check if its value is already in any of its three sets.

#### The box-index trick

Box index = `(row / 3) * 3 + (col / 3)`.

- `row / 3` gives 0/1/2 (which row-band).
- `col / 3` gives 0/1/2 (which col-band).
- Combined into a single 0-8 index for the boxes array.

#### Code

```java
public boolean isValidSudoku(char[][] board) {
    Set<Character>[] rows = new HashSet[9];
    Set<Character>[] cols = new HashSet[9];
    Set<Character>[] boxes = new HashSet[9];
    for (int i = 0; i < 9; i++) {
        rows[i] = new HashSet<>();
        cols[i] = new HashSet<>();
        boxes[i] = new HashSet<>();
    }

    for (int r = 0; r < 9; r++) {
        for (int c = 0; c < 9; c++) {
            char v = board[r][c];
            if (v == '.') continue;
            int b = (r / 3) * 3 + (c / 3);
            if (!rows[r].add(v) || !cols[c].add(v) || !boxes[b].add(v)) {
                return false;
            }
        }
    }
    return true;
}
```

**Complexity:** O(1) time and space (fixed 81 cells).

**Trap:** the box-index formula trips people up. Draw a 3×3 grid of boxes on paper — walk through `(r/3, c/3)` for a few cells to internalize it.

**Technique:** Multiple HashSets, `Set.add()` return value trick (from 7.2).

---

### 7.18 The 6 transferable HashMap techniques

Not the 17 problems — the **techniques**. These are what transfer to new problems you've never seen.

| # | Technique | Problems using it |
| - | --------- | ----------------- |
| 1 | **Frequency counting** | 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.16 |
| 2 | **Complementary lookup** (`target - x`) | 7.1 |
| 3 | **Prefix sum + HashMap** | 7.12 |
| 4 | **Bijective mapping** (two maps) | 7.9, 7.10 |
| 5 | **HashSet for O(1) membership** | 7.2, 7.14, 7.15, 7.17 |
| 6 | **HashMap for state tracking** (last-seen, count, visited) | 7.11, 7.13 |

**Interview meta-tip:** when you see a new HashMap problem, ask *"which of these 6 techniques does it need?"* first, before writing code. 80%+ of the time, one of them fits.

Sliding-window + HashMap is the 7th technique — covered in Phase 8.

---

### 7.19 Self-check (answer without looking)

1. **Which technique does Two Sum use?**
2. **Why does Longest Consecutive Sequence achieve O(n) despite the nested loop?**
3. **In Subarray Sum Equals K, why must the map be initialized with `{0: 1}`?**
4. **In Isomorphic Strings, why is a single map insufficient?**
5. **What's the box-index formula for Sudoku, and why does it work?**

#### Answers

1. **Complementary lookup.** For each `x`, check if `target - x` is already in the map (value → index). If yes, we found the pair. If no, add `x` to the map and move on. One pass, O(n). The insight: turning "find two things that sum to X" into "for each element, look up its complement" is what changes O(n²) to O(n).

2. **Because the inner `while` loop only runs for sequence *starts*.** We only walk from `n` if `n - 1` is NOT in the set — meaning `n` is the smallest element of its consecutive run. Every number in the array is either (a) a start (walked once as the outer element) or (b) skipped in the outer loop (fast, one hash lookup) and consumed exactly once during the outer start's inner walk. Total work: **O(n)**, even though it looks like O(n²) at first glance.

3. **Because subarrays starting at index 0 have "empty prefix" before them — their prefix sum equals their sum.** If we're looking for `currentSum - k` in the map and the current running sum equals `k`, then `currentSum - k = 0`. Without `{0: 1}` in the initial map, the lookup for 0 returns 0 → we miss this valid subarray. `{0: 1}` says: *"the empty prefix (sum 0) has been seen once — it counts as a valid starting point."*

4. **A single map only enforces "each s-char maps to some t-char." It doesn't prevent two DIFFERENT s-chars from mapping to the SAME t-char.** Example: `"ab"` & `"aa"`. Single map after step 1: `{a → a}`. Step 2: `s[1]='b'`, `t[1]='a'`. `b` not in map — add `{b → a}`. Both `a` and `b` map to `a` → not a bijection, should return false. Reverse map catches it: forward map records `{a→a}` AND reverse map records `{a→a}`. When we try `b→a`, reverse map sees `a` already mapped from `a`, not from `b` → conflict → return false.

5. **`boxIndex = (row / 3) * 3 + (col / 3)`.** Sudoku has 9 boxes arranged in a 3×3 grid. Integer division `row / 3` gives 0, 1, or 2 (which row-band the cell is in). Similarly `col / 3`. Multiplying the row-band by 3 spaces the boxes out: boxes 0-2 are in row-band 0, boxes 3-5 in row-band 1, boxes 6-8 in row-band 2. Then adding `col / 3` picks the box within that row-band. Example: cell (5, 7) → `(5/3)*3 + (7/3) = 1*3 + 2 = 5` → box 5 (middle-right box).

---

## Phase 8 — Coding Problems: Advanced Patterns

> 12 problems introducing the **7th technique (sliding window + HashMap)** plus design patterns that combine HashMap with other data structures.

---

### 8.1 Longest Substring Without Repeating Characters (LC 3)

**Problem:** Given `s`, find the length of the longest substring with no repeating characters.

#### Insight — sliding window + last-seen HashMap

Two pointers (`left`, `right`) delimit the current window. HashMap tracks the last-seen index of each character. When we see a character that's already **inside the current window**, jump `left` to just past its previous occurrence.

#### Code

```java
public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> lastSeen = new HashMap<>();
    int left = 0, maxLen = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= left) {
            left = lastSeen.get(c) + 1;
        }
        lastSeen.put(c, right);
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

**Complexity:** O(n) time, O(min(n, alphabet size)) space.

**Trap:** the check `lastSeen.get(c) >= left`. Without it, `left` could jump *backwards* if a character was seen before but is now outside the window. Example: `"abba"` — when we hit the last `a`, its lastSeen is 0, but `left` might already be at 2. Don't rewind.

**Technique:** Sliding window + last-seen HashMap.

---

### 8.2 Find All Anagrams in a String (LC 438)

**Problem:** Find all starting indices where an anagram of `p` appears in `s`.

#### Insight — fixed-size sliding window + freq-array equality

Window size = `p.length()`. Slide the window one character at a time; compare the window's frequency array with `p`'s frequency array. Match → record the start index.

#### Code

```java
public List<Integer> findAnagrams(String s, String p) {
    List<Integer> result = new ArrayList<>();
    if (s.length() < p.length()) return result;

    int[] pFreq = new int[26];
    int[] winFreq = new int[26];
    for (char c : p.toCharArray()) pFreq[c - 'a']++;

    int k = p.length();
    for (int i = 0; i < s.length(); i++) {
        winFreq[s.charAt(i) - 'a']++;                    // add right
        if (i >= k) winFreq[s.charAt(i - k) - 'a']--;    // remove left (once window full)
        if (i >= k - 1 && Arrays.equals(winFreq, pFreq)) {
            result.add(i - k + 1);
        }
    }
    return result;
}
```

**Complexity:** O(n) time (`Arrays.equals` on 26-element array is O(26) = O(1)), O(1) space.

**Trap:** don't add-then-remove naively — use the "add right, remove left" pattern once the window is full-size. Also start comparing only when `i >= k - 1` (window is full).

**Technique:** Fixed-size sliding window + freq map.

---

### 8.3 Longest Repeating Character Replacement (LC 424)

**Problem:** Given `s` and `k`, find the longest substring where you can replace at most `k` characters to make all chars the same.

#### Insight — window valid iff `windowSize - maxFreq ≤ k`

If the most-frequent char in the window appears `maxFreq` times, we need `windowSize - maxFreq` replacements. If that's ≤ `k`, the window is valid.

**The subtle trick:** we don't actually need to update `maxFreq` correctly when the window shrinks. Keeping a stale `maxFreq` is fine because the answer (longest window) only ever comes from valid windows, and a stale-high `maxFreq` won't let an invalid window pass the check.

#### Code

```java
public int characterReplacement(String s, int k) {
    int[] freq = new int[26];
    int left = 0, maxFreq = 0, maxLen = 0;
    for (int right = 0; right < s.length(); right++) {
        freq[s.charAt(right) - 'a']++;
        maxFreq = Math.max(maxFreq, freq[s.charAt(right) - 'a']);

        while (right - left + 1 - maxFreq > k) {
            freq[s.charAt(left) - 'a']--;
            left++;
        }
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

**Complexity:** O(n) time, O(1) space.

**Trap:** the "stale maxFreq is OK" is counter-intuitive. Some people try to recompute it as window shrinks (much slower). Trust the math: the answer is still correct.

**Technique:** Sliding window + max-freq check.

---

### 8.4 Longest Substring with At Most K Distinct Characters (LC 340)

**Problem:** Find the length of the longest substring containing at most `k` distinct characters.

#### Insight — variable-size window + HashMap of char counts

Expand `right`. While the window has more than `k` distinct chars, shrink `left` (and remove entries from the map when their count hits 0).

#### Code

```java
public int lengthOfLongestSubstringKDistinct(String s, int k) {
    if (k == 0) return 0;
    Map<Character, Integer> freq = new HashMap<>();
    int left = 0, maxLen = 0;
    for (int right = 0; right < s.length(); right++) {
        freq.merge(s.charAt(right), 1, Integer::sum);
        while (freq.size() > k) {
            char lc = s.charAt(left);
            if (freq.merge(lc, -1, Integer::sum) == 0) {
                freq.remove(lc);
            }
            left++;
        }
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

**Complexity:** O(n) time, O(k) space.

**Trap:** must actually `remove` the entry when count hits 0 — otherwise `freq.size()` overcounts and the window incorrectly stays "over the limit." Common bug.

**Technique:** Variable-size window + freq HashMap.

---

### 8.5 Minimum Window Substring (LC 76)

**Problem:** Find the shortest substring of `s` that contains all characters of `t` (with multiplicity). Return `""` if none.

**This is a HARD classic — the pattern crops up in dozens of variants.**

#### Insight — need/have + `formed` counter

- **`need`:** frequency map of `t`.
- **`have`:** frequency map of the current window.
- **`formed`:** number of DISTINCT characters in the window that meet or exceed their `need` count.

When `formed == need.size()`, the window is valid. Try to shrink from left. When shrinking causes any char to drop below its need count, `formed--` and we expand again.

#### Code

```java
public String minWindow(String s, String t) {
    if (t.length() > s.length()) return "";

    Map<Character, Integer> need = new HashMap<>();
    for (char c : t.toCharArray()) need.merge(c, 1, Integer::sum);

    Map<Character, Integer> have = new HashMap<>();
    int required = need.size();
    int formed = 0;
    int left = 0, bestStart = 0, bestLen = Integer.MAX_VALUE;

    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        int newCount = have.merge(c, 1, Integer::sum);
        if (need.containsKey(c) && newCount == need.get(c)) {
            formed++;
        }

        while (formed == required) {
            if (right - left + 1 < bestLen) {
                bestLen = right - left + 1;
                bestStart = left;
            }
            char lc = s.charAt(left);
            int prev = have.get(lc);
            have.put(lc, prev - 1);
            if (need.containsKey(lc) && prev - 1 < need.get(lc)) {
                formed--;
            }
            left++;
        }
    }
    return bestLen == Integer.MAX_VALUE ? "" : s.substring(bestStart, bestStart + bestLen);
}
```

**Complexity:** O(n + m) time, O(alphabet) space.

**Trap:** `formed` counts **distinct** chars satisfied, NOT total. Increment only when the count hits EXACTLY the required count (not on every add). Also — always shrink from left as long as window is still valid.

**Technique:** Two-map sliding window with formed counter.

---

### 8.6 Continuous Subarray Sum (LC 523)

**Problem:** Return `true` if there's a contiguous subarray of length ≥ 2 whose sum is a multiple of `k`.

#### Insight — prefix sum modulo K

If two prefix sums have the **same modulo K**, their difference is a multiple of K → the subarray between them has sum divisible by K.

Map: `sum % k → earliest index where we saw that mod`.

#### Code

```java
public boolean checkSubarraySum(int[] nums, int k) {
    Map<Integer, Integer> modIndex = new HashMap<>();
    modIndex.put(0, -1);   // empty prefix at index -1

    int sum = 0;
    for (int i = 0; i < nums.length; i++) {
        sum += nums[i];
        int mod = sum % k;
        if (modIndex.containsKey(mod)) {
            if (i - modIndex.get(mod) >= 2) return true;
        } else {
            modIndex.put(mod, i);   // ONLY put if not seen — keep earliest index
        }
    }
    return false;
}
```

**Complexity:** O(n) time, O(k) space.

**Trap:** must `put(0, -1)` initially, so a prefix that itself is divisible by k (from index 0) is caught. And only store the FIRST occurrence of each mod — later, when we see the same mod again, we want the subarray between them to be as long as possible (better chance of hitting the length ≥ 2 requirement).

**Technique:** Prefix sum + modulo + HashMap. Extension of 7.12.

---

### 8.7 4Sum II (LC 454)

**Problem:** Given 4 arrays A, B, C, D of the same length `n`, count tuples `(i, j, k, l)` such that `A[i] + B[j] + C[k] + D[l] == 0`.

#### Insight — split into two pairs, hash the first pair's sums

Naive: 4 nested loops → O(n⁴).

Trick: compute all pair sums `a + b`, count them in a HashMap. Then for each `c + d`, look up how many pairs sum to `-(c + d)`.

**O(n²) instead of O(n⁴).**

#### Code

```java
public int fourSumCount(int[] A, int[] B, int[] C, int[] D) {
    Map<Integer, Integer> abSums = new HashMap<>();
    for (int a : A)
        for (int b : B)
            abSums.merge(a + b, 1, Integer::sum);

    int count = 0;
    for (int c : C)
        for (int d : D)
            count += abSums.getOrDefault(-(c + d), 0);
    return count;
}
```

**Complexity:** O(n²) time, O(n²) space.

**Trap:** counting pairs, not booleans. Use `merge(sum, 1, Integer::sum)` to count each pair's contribution.

**Technique:** Multi-map / composite hashing.

---

### 8.8 Design HashMap (LC 706)

**Problem:** Implement your own HashMap supporting `put(key, value)`, `get(key)`, `remove(key)`. Keys and values are int in [0, 10⁶]. Return `-1` if key not found.

#### Insight — Phase 4 revisited

Chaining with linked lists. Simplified because keys are bounded ints (no need for null handling, resize can be skipped if bucket count is generous).

#### Code

```java
class MyHashMap {
    private static final int SIZE = 1024;   // power of 2, generous
    private final LinkedList<int[]>[] buckets;

    @SuppressWarnings("unchecked")
    public MyHashMap() {
        buckets = new LinkedList[SIZE];
    }

    private int hash(int key) {
        return key & (SIZE - 1);   // bitwise AND, since SIZE is power of 2
    }

    public void put(int key, int value) {
        int i = hash(key);
        if (buckets[i] == null) buckets[i] = new LinkedList<>();
        for (int[] entry : buckets[i]) {
            if (entry[0] == key) { entry[1] = value; return; }
        }
        buckets[i].add(new int[]{key, value});
    }

    public int get(int key) {
        int i = hash(key);
        if (buckets[i] == null) return -1;
        for (int[] entry : buckets[i]) {
            if (entry[0] == key) return entry[1];
        }
        return -1;
    }

    public void remove(int key) {
        int i = hash(key);
        if (buckets[i] == null) return;
        buckets[i].removeIf(e -> e[0] == key);
    }
}
```

**Complexity:** O(1) average, O(n) worst per operation. O(SIZE) space.

**Bridges to Phase 4:** if you did Phase 4, this problem is essentially free — same chaining approach, simpler because of bounded ints.

**Technique:** Chaining implementation.

---

### 8.9 Insert Delete GetRandom O(1) (LC 380)

**Problem:** Design a set-like structure supporting `insert(val)`, `remove(val)`, `getRandom()` — all in O(1).

#### Insight — HashMap for O(1) lookup + ArrayList for O(1) random access

- HashMap: `value → index in the ArrayList`.
- ArrayList: stores the values, indexed as above.

**The remove trick:** ArrayList's `remove(index)` is O(n) unless it's the last index. So on remove:
1. Get the target's index from the map.
2. **Swap with the last element** in the list (O(1)).
3. Pop the last (O(1)).
4. Update the map for the swapped element's new index.

#### Code

```java
class RandomizedSet {
    private final Map<Integer, Integer> valueToIndex = new HashMap<>();
    private final List<Integer> values = new ArrayList<>();
    private final Random rand = new Random();

    public boolean insert(int val) {
        if (valueToIndex.containsKey(val)) return false;
        valueToIndex.put(val, values.size());
        values.add(val);
        return true;
    }

    public boolean remove(int val) {
        Integer idx = valueToIndex.remove(val);
        if (idx == null) return false;
        int last = values.get(values.size() - 1);
        if (idx != values.size() - 1) {
            values.set(idx, last);
            valueToIndex.put(last, idx);
        }
        values.remove(values.size() - 1);
        return true;
    }

    public int getRandom() {
        return values.get(rand.nextInt(values.size()));
    }
}
```

**Complexity:** O(1) all operations.

**Trap:** forgetting the "check if target was already last" guard — if you unconditionally swap and update, and target == last, you'd `set(idx, last)` on the same slot AND update the map to a now-invalid index. Small bug, big consequences.

**Technique:** HashMap + ArrayList composite; swap-with-last remove.

---

### 8.10 Clone Graph (LC 133)

**Problem:** Given a reference to a node in a connected undirected graph, return a deep copy. Nodes have `int val` and `List<Node> neighbors`.

#### Insight — DFS/BFS + HashMap for visited tracking

Cycles exist in graphs → must track visited nodes to avoid infinite recursion. Use HashMap `original → clone`.

#### Code (recursive DFS)

```java
public Node cloneGraph(Node node) {
    if (node == null) return null;
    Map<Node, Node> cloned = new HashMap<>();
    return dfs(node, cloned);
}

private Node dfs(Node original, Map<Node, Node> cloned) {
    if (cloned.containsKey(original)) return cloned.get(original);
    Node copy = new Node(original.val);
    cloned.put(original, copy);                    // mark visited BEFORE recursing
    for (Node nbr : original.neighbors) {
        copy.neighbors.add(dfs(nbr, cloned));
    }
    return copy;
}
```

**Complexity:** O(V + E) time, O(V) space for the map + recursion stack.

**Trap:** put the clone in the map BEFORE recursing into neighbors — otherwise cycles cause infinite recursion.

**Iterative BFS alternative:** same map, use a queue instead of recursion. Same O(V+E), avoids stack overflow on deep graphs.

**Technique:** HashMap for visited state in graph traversal.

---

### 8.11 Encode and Decode TinyURL (LC 535)

**Problem:** Design `encode(longUrl)` and `decode(shortUrl)` such that decode(encode(url)) == url.

#### Insight — two HashMaps (bijective)

Two maps: `longToShort` and `shortToLong`. Choose a short-URL generation scheme (counter, random, hash).

#### Code (counter-based, simplest)

```java
public class Codec {
    private final Map<String, String> longToShort = new HashMap<>();
    private final Map<String, String> shortToLong = new HashMap<>();
    private static final String BASE = "http://tinyurl.com/";
    private int counter = 0;

    public String encode(String longUrl) {
        if (longToShort.containsKey(longUrl)) return longToShort.get(longUrl);
        String shortUrl = BASE + Integer.toString(counter++);
        longToShort.put(longUrl, shortUrl);
        shortToLong.put(shortUrl, longUrl);
        return shortUrl;
    }

    public String decode(String shortUrl) {
        return shortToLong.get(shortUrl);
    }
}
```

**Complexity:** O(1) encode/decode.

**Follow-up interview questions:**
- **Shorter URLs?** Base62-encode the counter (digits + upper + lower letters). 6 chars = 56 billion possibilities. Real TinyURL does this.
- **Persistence?** Use a database (key-value store like Redis fits perfectly).
- **Distributed?** Assign counter ranges to different servers to avoid contention.
- **Random vs counter?** Random hides usage patterns, counter is more predictable.

**Technique:** Bijective mapping (two-way HashMap).

---

### 8.12 LFU Cache (LC 460)

**Problem:** Design a Least Frequently Used cache. On tie in frequency, evict Least Recently Used. All operations O(1).

**The hardest cache problem in the standard interview set.** Contrast with LRU (LinkedList Phase 6.4).

#### Insight — three data structures working together

- `Map<Integer, Node>` — key → Node (holds value, frequency, key).
- `Map<Integer, DoublyLinkedList>` — frequency → LRU-ordered list of keys with that frequency.
- `int minFreq` — smallest frequency currently in the cache (for O(1) eviction).

**On `get`:** find the node, remove it from its current frequency's list, increment its freq, insert into (freq+1)'s list. If the old list became empty AND was `minFreq`, `minFreq++`.

**On `put` (new key):** if at capacity, evict tail of `freqToList[minFreq]`. Insert with freq = 1, `minFreq = 1`.

**On `put` (existing key):** same as `get` + update value.

#### Code

```java
class LFUCache {
    private static class Node {
        int key, value, freq = 1;
        Node prev, next;
        Node(int k, int v) { key = k; value = v; }
    }

    private static class DLL {
        Node head = new Node(0, 0), tail = new Node(0, 0);
        int size;
        DLL() { head.next = tail; tail.prev = head; }

        void addFirst(Node n) {
            n.next = head.next; n.prev = head;
            head.next.prev = n; head.next = n;
            size++;
        }
        void remove(Node n) {
            n.prev.next = n.next; n.next.prev = n.prev;
            size--;
        }
        Node removeLast() {
            if (size == 0) return null;
            Node n = tail.prev;
            remove(n);
            return n;
        }
    }

    private final int capacity;
    private int minFreq = 0;
    private final Map<Integer, Node> keyToNode = new HashMap<>();
    private final Map<Integer, DLL> freqToList = new HashMap<>();

    public LFUCache(int capacity) { this.capacity = capacity; }

    public int get(int key) {
        Node n = keyToNode.get(key);
        if (n == null) return -1;
        touch(n);
        return n.value;
    }

    public void put(int key, int value) {
        if (capacity == 0) return;
        Node existing = keyToNode.get(key);
        if (existing != null) {
            existing.value = value;
            touch(existing);
            return;
        }
        if (keyToNode.size() == capacity) {
            DLL lfu = freqToList.get(minFreq);
            Node evict = lfu.removeLast();
            keyToNode.remove(evict.key);
        }
        Node fresh = new Node(key, value);
        keyToNode.put(key, fresh);
        freqToList.computeIfAbsent(1, x -> new DLL()).addFirst(fresh);
        minFreq = 1;   // new node has freq 1
    }

    private void touch(Node n) {
        DLL oldList = freqToList.get(n.freq);
        oldList.remove(n);
        if (oldList.size == 0 && minFreq == n.freq) minFreq++;
        n.freq++;
        freqToList.computeIfAbsent(n.freq, x -> new DLL()).addFirst(n);
    }
}
```

**Complexity:** O(1) all operations.

**Compare with LRU (from linklist Phase 6.4):**
- **LRU** needs one doubly linked list. Order along the list IS recency. Simple.
- **LFU** needs a map of doubly linked lists (one per frequency), plus a `minFreq` pointer. Two-dimensional priority (frequency, then recency) → can't collapse into one linear structure.

**Trap:** the `minFreq++` reset logic. Only increment when the old list is empty AND it was the current `minFreq`. On new inserts, always reset `minFreq = 1`.

**Technique:** HashMap + doubly linked list + additional HashMap of doubly linked lists.

---

### 8.13 The full arsenal — all 28 problems + 6+1 techniques

**All techniques recap:**

1. **Frequency counting** — 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.16
2. **Complementary lookup** — 7.1
3. **Prefix sum + HashMap** — 7.12, 8.6
4. **Bijective mapping** — 7.9, 7.10, 8.11
5. **HashSet for O(1) membership** — 7.2, 7.14, 7.15, 7.17
6. **HashMap for state tracking** — 7.11, 7.13, 8.10 (visited)
7. **Sliding window + HashMap** — 8.1, 8.2, 8.3, 8.4, 8.5
8. **Design (HashMap + another DS)** — 8.8, 8.9, 8.10, 8.11, 8.12

**Difficulty ranking (roughly):**

| Difficulty | Problems |
| ---------- | -------- |
| Easy       | 7.1, 7.2, 7.3, 7.5, 7.6, 7.11, 7.15, 7.16, 8.11 |
| Medium     | 7.4, 7.7, 7.8, 7.9, 7.10, 7.12, 7.13, 7.14, 7.17, 8.1, 8.2, 8.3, 8.4, 8.6, 8.7, 8.8, 8.9, 8.10 |
| Hard       | 8.5, 8.12 |

**Interview-frequency ranking (top 10 to master first):**

1. **Two Sum (7.1)** — most-asked interview question, period.
2. **Group Anagrams (7.4)** — freq-count + custom hash key.
3. **Longest Substring Without Repeating (8.1)** — sliding window classic.
4. **Subarray Sum Equals K (7.12)** — prefix-sum trick, huge in interviews.
5. **LRU Cache (linklist Phase 6.4)** — every senior interview.
6. **Longest Consecutive Sequence (7.14)** — the "only walk from starts" O(n) trick.
7. **Top K Frequent (7.8)** — heap + freq map.
8. **Valid Anagram (7.3)** — warm-up.
9. **Minimum Window Substring (8.5)** — hard-but-common.
10. **LFU Cache (8.12)** — separates senior candidates.

---

### 8.14 Self-check (answer without looking)

1. **In Minimum Window Substring, what does `formed` count?**
2. **Why does Continuous Subarray Sum use `sum % K` as the key?**
3. **In "Insert Delete GetRandom O(1)", why swap with the last element on remove?**
4. **What's the key data-structure difference between LRU and LFU?**

#### Answers

1. **`formed` counts the number of DISTINCT characters in the window that meet or exceed their required count in `t`.** Not the total character count. `formed` is incremented ONLY when a character's count in the window hits EXACTLY its required count in `t` (not on every add). When `formed == need.size()`, the window is valid. Common bug: incrementing `formed` on every char that's needed → wildly wrong.

2. **Because two prefix sums with the same modulo K mean their difference is a multiple of K.** If `prefixSum[i] % k == prefixSum[j] % k`, then `(prefixSum[i] - prefixSum[j]) % k == 0`, which means the subarray from `j+1` to `i` has a sum divisible by K. This is a direct extension of the prefix-sum-with-HashMap technique (7.12), applied to modular arithmetic. Same idea: turn "find something spanning [j+1, i]" into "for each i, look up something about the prefix state."

3. **Because `ArrayList.remove(index)` is O(n) unless `index` is the last position** — it shifts all subsequent elements left. To keep remove O(1), swap the target with the LAST element (O(1) via `set`), then pop the last (O(1) via `remove(size-1)` — no shifts needed because nothing follows). We also update the map for the swapped element's new index. Without this trick, remove would be O(n) and we'd fail the "O(1) all operations" requirement.

4. **LRU needs ONE doubly linked list — the order along the list IS recency.** Simple linear structure. Evict from tail (least recent), add/promote at head. **LFU needs a MAP of doubly linked lists** — one list per frequency value. Within each list, order is recency (LRU-style tie-breaking). Between lists, priority is frequency (evict from `minFreq` list first). LFU has two-dimensional priority (frequency, then recency), which can't collapse into one linear structure — that's why LFU is significantly harder to implement than LRU.

---

## Phase 9 — Thread-safe Maps Deep Dive

### 9.1 Why HashMap breaks under concurrent use
- Race examples: lost updates, duplicate inserts, wrong size
- Bad outcomes: silent corruption, `ClassCastException` from mid-treeify state
- No exception thrown in most cases — worst kind of bug

### 9.2 The famous Java 7 infinite loop bug
- Only in Java 7 (fixed in Java 8)
- Scenario: two threads triggering resize at the same time
- Old Java 7 rehashing used head-insertion → chain could form a cycle
- Result: `get()` spins forever, CPU pegged at 100%
- Historical famous production incident (search "HashMap infinite loop")

### 9.3 `Collections.synchronizedMap` wrapper
- Every method wrapped in `synchronized(mutex)`:
- Iteration still requires manual sync:
- Verdict: legacy, coarse-grained, rarely the right answer

### 9.4 ConcurrentHashMap — Java 7 segments vs Java 8+ CAS + synchronized
- **Java 7:** array of 16 `Segment`s (each a mini HashMap with its own lock). Concurrency = number of segments.
- **Java 8+:** single table, per-bucket locking. First insert into empty bucket uses CAS (lock-free). Subsequent inserts synchronize on the bucket head.
- Concurrency: as high as the number of buckets.

### 9.5 ConcurrentHashMap internals (Node, TreeBin, ForwardingNode)
- `Node<K,V>` — regular entry (immutable value reference; volatile next)
- `TreeBin<K,V>` — wraps a red-black tree of `TreeNode`s (with a read/write lock)
- `ForwardingNode<K,V>` — placeholder during resize; forwards to the new table
- Resize is cooperative: any thread that finds a ForwardingNode helps migrate

### 9.6 ConcurrentSkipListMap — the sorted concurrent map
- Skip-list based (probabilistic multi-level linked list)
- Lock-free, sorted iteration
- Use when you need thread-safety + sorted ordering
- Cost: O(log n) operations vs CHM's O(1)

### 9.7 Decision guide — which thread-safe map when
```
Need thread-safe map?
├── Sorted iteration required?
│   └── Yes → ConcurrentSkipListMap
├── Very read-heavy, rarely writes?
│   └── CopyOnWriteArrayList-style pattern → immutable copies
└── General use → ConcurrentHashMap
```

### 9.8 Self-check
1. What was the Java 7 infinite loop bug and what fixed it?
2. What's the difference between Java 7 and Java 8+ ConcurrentHashMap architecture?
3. What is a `ForwardingNode` and why does it exist?
4. Why can't ConcurrentHashMap store null keys or values?

---

## Phase 10 — API Traps & Practical Gotchas

### 10.1 `keySet` / `entrySet` / `values` are VIEWS
- Not copies — modifying the returned collection modifies the map
- `map.keySet().remove(k)` == `map.remove(k)`
- Iterating and mutating simultaneously → CME

### 10.2 `getOrDefault` vs `computeIfAbsent`
- `getOrDefault(k, default)` — returns default if absent; does NOT insert
- `computeIfAbsent(k, key -> ...)` — computes AND inserts if absent
- Common mistake: using `getOrDefault` when you meant to lazy-initialize

### 10.3 `merge()` — the underused power tool
- Signature: `merge(k, value, BiFunction remappingFn)`
- Perfect for frequency counting: `map.merge(word, 1, Integer::sum)`
- Also for concatenation, max/min, etc.

### 10.4 `putIfAbsent` semantics
- Puts value only if key absent; returns existing value (or null)
- Different from `computeIfAbsent`: `putIfAbsent` always evaluates the value, `computeIfAbsent` computes lazily
- Prefer `computeIfAbsent` when the value is expensive to build

### 10.5 Safely removing during iteration
- Wrong: `for (K k : map.keySet()) map.remove(k)` → CME
- Right: `map.entrySet().removeIf(e -> ...)`
- Or: use `iterator.remove()` explicitly

### 10.6 Iteration order guarantees (or lack thereof)
- `HashMap`: NO guaranteed order (may change between JVM versions)
- `LinkedHashMap`: insertion order (or access order if configured)
- `TreeMap`: natural key ordering (or custom Comparator)

### 10.7 Frequency map idioms
- **Count occurrences:** `map.merge(word, 1, Integer::sum)`
- **Lazy list init:** `map.computeIfAbsent(k, x -> new ArrayList<>()).add(v)`
- **Increment counter:** compare with old `map.put(k, map.getOrDefault(k, 0) + 1)`

### 10.8 The `Map<K, List<V>>` multimap pattern
- Java stdlib has no `Multimap` — use `Map<K, List<V>>`
- Use `computeIfAbsent` to avoid null-check boilerplate
- Alternative: Guava's `Multimap`

### 10.9 Sorting a HashMap
- You can't sort a HashMap in place (unordered by design)
- Sort the entries: `map.entrySet().stream().sorted(Map.Entry.comparingByValue())...`
- To keep sorted permanently: copy into `TreeMap` (by key) or `LinkedHashMap` (by insertion of sorted stream)

### 10.10 Self-check
1. What's the difference between `getOrDefault` and `computeIfAbsent`?
2. Why is `map.merge(word, 1, Integer::sum)` better than `map.put(word, map.getOrDefault(word, 0) + 1)`?
3. What happens if you call `map.keySet().remove(k)`?
4. How do you sort a HashMap by value?

---

## Quick-recall cheat sheet (fill in last, after everything else)

- **What is HashMap?** (1 sentence)
- **Which interfaces does it implement?**
- **Backing structure?**
- **Default initial capacity and load factor?**
- **What triggers treeification?**
- **What triggers resize?**
- **Big-O for `get`, `put`, `remove`:**
- **When to use HashMap vs TreeMap vs LinkedHashMap:**
- **When NOT to use HashMap:**
- **Real production alternative for concurrent use:**

---

## Open questions / things I still don't understand
-
-
-
