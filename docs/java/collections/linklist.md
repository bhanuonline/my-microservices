# LinkedList — Deep Dive

> Personal study notes for interview + knowledge. Fill in each section in my own words after understanding it.

---

## Table of Contents

**[Phase 1 — Mental Model](#phase-1--mental-model)**
- [1.1 What is a Node?](#11-what-is-a-node)
- [1.2 LinkedList vs Array (intuition, not code)](#12-linkedlist-vs-array-intuition-not-code)
- [1.3 The three variants](#13-the-three-variants)
- [1.4 Self-check (answer without looking)](#14-self-check-answer-without-looking)

**[Phase 2 — Java-Specific Grounding](#phase-2--java-specific-grounding)**
- [2.1 The `Node<E>` class inside `java.util.LinkedList`](#21-the-nodee-class-inside-javautillinkedlist)
- [2.2 Fields on the `LinkedList` class](#22-fields-on-the-linkedlist-class)
- [2.3 Class hierarchy — the interview trap](#23-class-hierarchy--the-interview-trap)
- [2.4 Complexity table (memorize)](#24-complexity-table-memorize)
- [2.5 The `nodeAt(index)` optimization](#25-the-nodeatindex-optimization)
- [2.6 Self-check (answer without looking)](#26-self-check-answer-without-looking)

**[Phase 3 — Build It Yourself](#phase-3--build-it-yourself)**
- [3.1 Singly linked list — the design](#31-singly-linked-list--the-design)
- [3.2 Method-by-method walkthrough (the key insights)](#32-method-by-method-walkthrough-the-key-insights)
- [3.3 Full reference implementation — singly linked](#33-full-reference-implementation--singly-linked)
- [3.4 Common bugs (log the ones YOU hit)](#34-common-bugs-log-the-ones-you-hit)
- [3.5 Doubly linked list — what changes](#35-doubly-linked-list--what-changes)
- [3.6 Full reference implementation — doubly linked](#36-full-reference-implementation--doubly-linked)
- [3.7 The mental discipline (two questions before every pointer rewire)](#37-the-mental-discipline-two-questions-before-every-pointer-rewire)
- [3.8 Key comparison — singly vs doubly](#38-key-comparison--singly-vs-doubly)
- [3.9 Practice checklist (do this in order)](#39-practice-checklist-do-this-in-order)

**[Phase 4 — Classic Interview Problems](#phase-4--classic-interview-problems)**
- [4.1 Reverse a linked list](#41-reverse-a-linked-list)
- [4.2 Detect a cycle (Floyd's tortoise & hare)](#42-detect-a-cycle-floyds-tortoise--hare)
- [4.3 Find the middle node](#43-find-the-middle-node)
- [4.4 Merge two sorted lists](#44-merge-two-sorted-lists)
- [4.5 Intersection of two lists](#45-intersection-of-two-lists)
- [4.6 Remove Nth node from end](#46-remove-nth-node-from-end)
- [4.7 The 5 transferable techniques](#47-the-5-transferable-techniques)
- [4.8 Follow-up problems to practice](#48-follow-up-problems-to-practice)
- [4.9 Practice discipline](#49-practice-discipline)

**[Phase 5 — When to Use LinkedList vs ArrayList](#phase-5--when-to-use-linkedlist-vs-arraylist)**
- [5.1 Head-to-head table](#51-head-to-head-table)
- [5.2 The real reason ArrayList wins (the "aha" moment)](#52-the-real-reason-arraylist-wins-the-aha-moment)
- [5.3 Why LinkedList's "wins" are misleading](#53-why-linkedlists-wins-are-misleading)
- [5.4 When LinkedList actually wins (rare)](#54-when-linkedlist-actually-wins-rare)
- [5.5 The interview answer](#55-the-interview-answer)
- [5.6 Decision tree](#56-decision-tree)
- [5.7 Related follow-ups](#57-related-follow-ups-interviewers-often-ask-after-arraylist-vs-linkedlist)

**[Phase 6 — Interview Deep Cuts](#phase-6--interview-deep-cuts)**
- [6.1 Iterator & `ListIterator` internals](#61-iterator--listiterator-internals)
- [6.2 Thread safety & concurrent alternatives](#62-thread-safety--concurrent-alternatives)
- [6.3 Full `Deque` / `Queue` API cheat sheet](#63-full-deque--queue-api-cheat-sheet)
- [6.4 LRU Cache — the classic combined problem](#64-lru-cache--the-classic-combined-problem)
- [6.5 `equals()` and `hashCode()` in LinkedList](#65-equals-and-hashcode-in-linkedlist)
- [6.6 Self-check (Phase 6)](#66-self-check-phase-6)
- [6.7 API traps & practical gotchas](#67-api-traps--practical-gotchas)

**[Phase 7 — Advanced Practice Problems](#phase-7--advanced-practice-problems)**
- [7.1 Palindrome check (LC 234)](#71-palindrome-check-lc-234)
- [7.2 Reorder list (LC 143)](#72-reorder-list-lc-143)
- [7.3 Merge K sorted lists (LC 23)](#73-merge-k-sorted-lists-lc-23)
- [7.4 Reverse in K-groups (LC 25)](#74-reverse-in-k-groups-lc-25)
- [7.5 Sort a linked list (LC 148)](#75-sort-a-linked-list-lc-148)
- [7.6 Add two numbers (LC 2)](#76-add-two-numbers-lc-2)
- [7.7 Copy list with random pointer (LC 138)](#77-copy-list-with-random-pointer-lc-138)
- [7.8 Rotate list by K places (LC 61)](#78-rotate-list-by-k-places-lc-61)
- [7.9 Swap nodes in pairs (LC 24)](#79-swap-nodes-in-pairs-lc-24)
- [7.10 Self-check (Phase 7)](#710-self-check-phase-7)
- [7.11 The complete "linked list interview" arsenal](#711-the-complete-linked-list-interview-arsenal)

**[Phase 8 — Gap-Fill Practice Problems](#phase-8--gap-fill-practice-problems)**
- [8.1 Remove duplicates from sorted list (LC 83)](#81-remove-duplicates-from-sorted-list-lc-83)
- [8.2 Remove duplicates from sorted list II (LC 82)](#82-remove-duplicates-from-sorted-list-ii-lc-82)
- [8.3 Delete node given only that node (LC 237)](#83-delete-node-given-only-that-node-lc-237)
- [8.4 Partition list (LC 86)](#84-partition-list-lc-86)
- [8.5 Reverse linked list II (LC 92)](#85-reverse-linked-list-ii-lc-92)
- [8.6 Odd Even Linked List (LC 328)](#86-odd-even-linked-list-lc-328)
- [8.7 Flatten multilevel doubly linked list (LC 430)](#87-flatten-multilevel-doubly-linked-list-lc-430)
- [8.8 Linked List Random Node (LC 382) — reservoir sampling](#88-linked-list-random-node-lc-382--reservoir-sampling)
- [8.9 Insertion Sort List (LC 147)](#89-insertion-sort-list-lc-147)
- [8.10 Split Linked List in Parts (LC 725)](#810-split-linked-list-in-parts-lc-725)
- [8.11 Self-check (Phase 8)](#811-self-check-phase-8)
- [8.12 The truly complete arsenal (all 26 problems)](#812-the-truly-complete-arsenal-all-26-problems)

**Final sections:**
- [Quick-recall cheat sheet](#quick-recall-cheat-sheet-fill-in-last-after-everything-else)
- [Open questions / things I still don't understand](#open-questions--things-i-still-dont-understand)

---

## Phase 1 — Mental Model

### 1.1 What is a Node?

A **node** is a small container holding two things:

```
 ┌───────┬───────┐
 │ value │ next  │──→ (points to another node)
 └───────┴───────┘
```

- `value` — the actual data (an `int`, `String`, `Object`, whatever).
- `next` — a reference/pointer to another node. If it's the last node, `next = null`.

A linked list is just a **chain of these nodes**, where each one knows where the next one lives:

```
 head
  │
  ▼
 ┌───┬───┐   ┌───┬───┐   ┌───┬───┐   ┌───┬──────┐
 │ 5 │ ●─┼──▶│ 8 │ ●─┼──▶│ 3 │ ●─┼──▶│ 9 │ null │
 └───┴───┘   └───┴───┘   └───┴───┘   └───┴──────┘
```

**Key insight:** the list itself only holds one thing — the `head` (and often `tail`). Everything else is discovered by **following arrows**. There is no "list object" that owns all nodes in one place. Nodes are scattered in memory, held together only by references.

**Why this matters:** to reach the 3rd element, you must start at head and hop `head → next → next`. No shortcut. That's why `get(index)` is O(n).

**Superpower:** if you already have a reference to a node, you can delete it by just rewiring one arrow. No shifting of other elements. That's the O(1) delete.

---

### 1.2 LinkedList vs Array (intuition, not code)

#### Array — contiguous memory

```
 index:   0    1    2    3    4
        ┌────┬────┬────┬────┬────┐
        │ 5  │ 8  │ 3  │ 9  │ 2  │
        └────┴────┴────┴────┴────┘
        ↑ one block of memory, elements sit shoulder-to-shoulder
```

- **Access `arr[3]`:** compute `base_address + 3 × size`. Jump directly. **O(1).**
- **Insert at index 2:** shift elements 2, 3, 4 to the right. **O(n).**
- **Delete at index 2:** shift elements 3, 4 to the left. **O(n).**

#### LinkedList — scattered memory

```
 head → [5|●] → [8|●] → [3|●] → [9|●] → [2|null]
        (each box is somewhere else in RAM, connected by arrows)
```

- **Access 3rd element:** walk from head, follow arrows. **O(n).**
- **Insert after a known node:** rewire two arrows. **O(1).**
- **Delete a known node:** rewire one arrow. **O(1).**

#### The trade-off in one table

| Operation           | Array     | LinkedList |
| ------------------- | --------- | ---------- |
| Access by index     | **O(1)**  | O(n)       |
| Insert at start     | O(n)      | **O(1)**   |
| Insert at end       | O(1)*     | **O(1)**   |
| Insert in middle    | O(n)      | O(n) find + O(1) insert |
| Memory per element  | just data | data + pointer(s) — bigger |
| Cache friendliness  | **great** (contiguous) | poor (scattered) |

*ArrayList insert-at-end is amortized O(1).

#### Why `get(i)` is O(n) in LinkedList

There's no formula from index to memory address. The only way to reach index `i` is to start at head and follow `next` `i` times.

#### Why inserting at head is O(1) in LinkedList but O(n) in Array

- **LinkedList:** create new node, point its `next` at old head, reassign `head` to new node. Two pointer assignments. Done.
- **Array:** every existing element has to shift one slot to the right to make room at index 0. That's `n` copies.

#### Honest answer — why does LinkedList even exist?

In practice, arrays win almost every real workload because CPUs love contiguous memory (cache hits). LinkedList shines only when you're doing many insertions/deletions **and** you already hold a reference to the node (not the index). That's rare.

In Java specifically, `java.util.LinkedList` exists mostly because it implements `Deque` (queue-from-both-ends), not because you'd choose it over `ArrayList` for a plain list.

**Soundbite:** *"ArrayList is what you use. LinkedList is what you talk about."*

---

### 1.3 The three variants

#### (a) Singly linked

Each node has one arrow: `next`.

```
 head → [A|●] → [B|●] → [C|●] → [D|null]
```

- Simplest.
- Can only walk forward.
- To delete node C, you must know node B (its predecessor) — because you need to redirect B's arrow.

#### (b) Doubly linked  ← *this is `java.util.LinkedList`*

Each node has two arrows: `next` and `prev`.

```
 null ← [A] ⇄ [B] ⇄ [C] ⇄ [D] → null
```

- Can walk both directions.
- Deleting a known node is easy: use `C.prev` to reach the predecessor. No traversal from head needed.
- Costs more memory (extra pointer per node).
- Enables efficient operations at both ends → this is why it can be a `Deque`.

#### (c) Circular linked

The last node's `next` points back to the head (instead of `null`).

```
 head → [A] → [B] → [C] → [D] ─┐
         ▲                      │
         └──────────────────────┘
```

- Useful for round-robin scheduling, circular buffers, some game loops.
- Rarely asked directly, but the concept underpins cycle-detection problems (Floyd's algorithm).

#### Which one does `java.util.LinkedList` use? Why?

**Doubly linked.** Because it needs to implement `Deque` — efficient (O(1)) insertion, removal, and access at **both** ends. That requires knowing both the next AND previous node from any position, which only doubly linked gives you.

---

### 1.4 Self-check (answer without looking)

> Try answering each in your head *first*, then check.

1. **What are the two fields inside a node?**
2. **Why is `get(index)` O(n) for a linked list but O(1) for an array?**
3. **Why is inserting at the front of a linked list O(1) but O(n) for an array?**
4. **Which variant does `java.util.LinkedList` use, and why does that matter?**
5. **If arrays are faster for most things, when *would* you actually reach for a linked list?**

#### Answers

1. **`value` (the data) and `next` (a reference/pointer to the next node).** A doubly linked node also has `prev`. Nothing else — no index, no id, no size. A node has no idea where it sits in the list.

2. **Array elements sit in contiguous memory**, so the address of index `i` is a simple formula: `base_address + i × element_size`. The CPU jumps there directly — one operation. **LinkedList nodes are scattered in memory** and only connected by `next` references. To reach index `i`, you must start at `head` and follow `next` `i` times. No shortcut → O(n).

3. **LinkedList — O(1):** create a new node, point its `next` at the old head, reassign `head`. Two pointer assignments. Nothing else moves. **Array — O(n):** every existing element must shift one slot to the right to make room at index 0. That's `n` copies. The cost of maintaining "index 0 = first element" in contiguous memory.

4. **Doubly linked** (each node has both `next` and `prev`). It matters because it enables:
   - **O(1) operations at both ends** — `addFirst`, `addLast`, `removeFirst`, `removeLast`. This is what lets `LinkedList` implement `Deque`.
   - **O(1) removal of a known node** via `Iterator.remove()` — no need to walk from head to find its predecessor.
   - **The `nodeAt(index)` optimization** — walk from `first` or `last` depending on which is closer.
   Singly linked couldn't do any of this efficiently.

5. **Almost never in practice.** Honest cases:
   - You need a **`Deque`** and, for some reason, `ArrayDeque` doesn't fit (e.g., you need to store `null` elements — `ArrayDeque` disallows them, `LinkedList` allows).
   - You need a class that implements **both `List` and `Deque`** in a single object — no other JDK class does.
   - You're doing **massive iterator-based mid-list insertions/deletions** and profiling has shown ArrayList's element-shifting is your bottleneck. Rare in practice; cache-friendliness usually makes ArrayList faster even here.

   **Rule of thumb:** default to `ArrayList` for lists, `ArrayDeque` for queues/stacks. Reach for `LinkedList` only when you have a specific, measured reason.

---

## Phase 2 — Java-Specific Grounding

### 2.1 The `Node<E>` class inside `java.util.LinkedList`

The real inner class from the JDK source:

```java
private static class Node<E> {
    E item;
    Node<E> next;
    Node<E> prev;

    Node(Node<E> prev, E element, Node<E> next) {
        this.item = element;
        this.next = next;
        this.prev = prev;
    }
}
```

That's the entire "atom" of `java.util.LinkedList`. Five lines.

**Things worth noticing:**

- `E item` — the data (generic, so it can hold any type).
- `Node<E> next` and `Node<E> prev` — two arrows. **Confirmed: doubly linked.**
- `private static` — inner class, hidden from outside. You never touch `Node` directly; you only see `E` values.
- **No `id`, no `index`, no `size` on the node.** A node has no idea where it sits. The list is *emergent* from the chain of references.

**Why `private static`?**
- `private` — the outside world (your code) can't create or reference `Node`. It's an implementation detail.
- `static` — nested class doesn't need a reference to the outer `LinkedList` instance. Saves memory (one hidden pointer per node) and lets `Node` be constructed independently.

---

### 2.2 Fields on the `LinkedList` class

The `LinkedList` object itself holds only three fields:

```java
transient int size = 0;
transient Node<E> first;
transient Node<E> last;
```

- **`size`** — cached count. Without it, `size()` would need to walk the whole list to count. Cached = **O(1)** `size()`.
- **`first`** — the head pointer. Entry point into the chain.
- **`last`** — the tail pointer. **This is why `addLast`, `removeLast`, `peekLast` are all O(1).** Without `last`, tail operations would require walking to the end — O(n).

**Mental picture of the full structure:**

```
 LinkedList object
 ┌─────────────┐
 │ size:  4    │
 │ first: ●────┼──▶ [null|A|●] ⇄ [●|B|●] ⇄ [●|C|●] ⇄ [●|D|null]
 │ last:  ●────┼─────────────────────────────────────────▲
 └─────────────┘
```

The list object is **tiny**. All the "weight" is in the chain of nodes.

**Why `transient`?** So the default Java serialization skips these fields — `LinkedList` overrides `writeObject` / `readObject` to serialize only the values (not the node structure). Rebuilding the chain on deserialization is cheaper than serializing all the pointer soup.

---

### 2.3 Class hierarchy — the interview trap

Most people say "LinkedList is a List." That's incomplete and misses the point of why it exists.

```
       ┌──────────────┐
       │  Iterable    │
       └──────┬───────┘
              │
       ┌──────▼───────┐
       │  Collection  │
       └──┬───────┬───┘
          │       │
     ┌────▼──┐  ┌─▼─────┐
     │ List  │  │ Queue │
     └────┬──┘  └───┬───┘
          │         │
          │      ┌──▼────┐
          │      │ Deque │
          │      └───┬───┘
          │          │
          └─────┬────┘
                │
         ┌──────▼──────┐
         │ LinkedList  │  ← implements List AND Deque
         └─────────────┘
```

`LinkedList` implements **three** interesting contracts:

1. **`List<E>`** — indexed sequence. `get(i)`, `add(i, e)`, `remove(i)`. Slow (O(n)), but you can.
2. **`Queue<E>`** — FIFO. `offer(e)`, `poll()`, `peek()`. Fast (O(1)).
3. **`Deque<E>`** — double-ended queue. `addFirst`, `addLast`, `removeFirst`, `removeLast`, `peekFirst`, `peekLast`. All O(1).

#### Why does LinkedList exist beyond being a List?

Because it's the **only concrete class in `java.util` that implements `Deque` via a linked structure.** Before Java 6 added `ArrayDeque`, `LinkedList` was the go-to `Deque`. Today, even for Deque work, `ArrayDeque` is usually faster (better cache behavior). So `LinkedList` is essentially a **legacy Deque** that also pretends to be a `List`.

#### One-liner to remember

> *"LinkedList implements both `List` and `Deque`, but you use `ArrayList` when you want a List and `ArrayDeque` when you want a Deque. LinkedList wins in neither dimension in practice."*

#### Related classes to know (they come up in follow-ups)

- **`ArrayList`** — array-backed List. Your default.
- **`ArrayDeque`** — array-backed Deque. Your default queue/stack.
- **`Stack`** — legacy, don't use. Use `ArrayDeque` for stack behavior.
- **`Vector`** — legacy synchronized ArrayList. Don't use.

---

### 2.4 Complexity table (memorize)

For `java.util.LinkedList<E>`:

| Operation                                | Complexity | Why                                                  |
| ---------------------------------------- | ---------- | ---------------------------------------------------- |
| `addFirst(e)` / `addLast(e)`             | **O(1)**   | `first`/`last` pointers, just rewire                 |
| `add(e)` *(appends to end)*              | **O(1)**   | Same as `addLast`                                    |
| `removeFirst()` / `removeLast()`         | **O(1)**   | Same reason                                          |
| `peek()` / `peekFirst()` / `peekLast()`  | **O(1)**   | Direct pointer access                                |
| `size()`                                 | **O(1)**   | Cached in `size` field                               |
| `get(i)` / `set(i, e)`                   | **O(n)**   | Must walk from `first` (or `last`, whichever closer) |
| `add(i, e)` / `remove(i)`                | **O(n)**   | Walk to index `i`, then O(1) rewire                  |
| `contains(o)` / `indexOf(o)`             | **O(n)**   | Linear scan                                          |
| `iterator().next()`                      | **O(1)**   | Iterator holds current node reference                |

---

### 2.5 The `nodeAt(index)` optimization

`get(i)` is smarter than pure O(n) — it checks whether `i` is closer to `first` or `last`, and walks from the nearer end. So `get(size-1)` is effectively **O(1)**, not O(n).

Still O(n) in the worst case (middle of the list).

The source (paraphrased):

```java
Node<E> node(int index) {
    if (index < (size >> 1)) {
        // walk from first
        Node<E> x = first;
        for (int i = 0; i < index; i++) x = x.next;
        return x;
    } else {
        // walk from last
        Node<E> x = last;
        for (int i = size - 1; i > index; i--) x = x.prev;
        return x;
    }
}
```

- `size >> 1` is just `size / 2` (bitwise shift right by 1 = divide by 2, faster than integer division).
- Walks from whichever end is closer.
- This optimization is **only possible because the list is doubly linked** (needs `.prev` to walk backward from `last`).

**Why `get(size-1)` is faster than `get(size/2)`:**
- `get(size-1)` — enters the `else` branch, starts at `last`, loop runs 0 times. Essentially O(1).
- `get(size/2)` — enters either branch, walks `size/2` steps. O(n/2), which is still O(n) asymptotically but is the actual worst case for this optimization.

---

### 2.6 Self-check (answer without looking)

> Try answering each in your head *first*, then check.

1. **How many fields does `Node<E>` have, and what are they?**
2. **Why does `LinkedList` cache `size`, `first`, and `last`?**
3. **Which two interfaces make `LinkedList` interesting (beyond `List`)?**
4. **Why do people say "use `ArrayDeque` instead of `LinkedList`"?**
5. **Why is `get(size-1)` faster than `get(size/2)`?**

#### Answers

1. **Three fields:** `E item` (the data), `Node<E> next` (reference to the next node), and `Node<E> prev` (reference to the previous node). The `prev` field is what makes it *doubly* linked — a singly linked node would only have `item` and `next`. The node deliberately has no `index`, `id`, or `size` — it has no awareness of its position in the list.

2. Each one avoids an O(n) walk:
   - **`size`** — without it, `size()` would need to walk the whole list counting nodes. Cached → **O(1) `size()`**.
   - **`first`** — the entry point into the chain. Without it, you'd have no way to start iteration.
   - **`last`** — enables **O(1) `addLast`, `removeLast`, `peekLast`**. Without it, tail operations would require walking from `first` to the end → O(n). This is what lets `LinkedList` implement `Deque` efficiently.

3. **`Deque<E>`** (double-ended queue) and **`Queue<E>`** (FIFO).
   - `Queue` gives it `offer`, `poll`, `peek` — makes it usable as a FIFO queue.
   - `Deque` gives it `addFirst`, `addLast`, `removeFirst`, `removeLast`, `peekFirst`, `peekLast` — all O(1).
   - **This is the whole reason `LinkedList` exists in modern Java.** It's the only concrete `java.util` class that implements `Deque` via a linked structure.

4. Because `ArrayDeque` is **faster in practice for the same operations**, even though both are O(1) for queue/deque ops:
   - `ArrayDeque` uses a **contiguous circular array** → cache-friendly memory access.
   - `LinkedList` uses **scattered nodes connected by pointers** → cache misses on every hop.
   - `ArrayDeque` uses **less memory per element** (no per-node object header, no `prev`/`next` pointers).
   - The one edge where `LinkedList` still wins: `ArrayDeque` **doesn't allow `null` elements**; `LinkedList` does. That's about it.

5. Because of the **`nodeAt(index)` optimization** — the JDK checks whether `index < size/2`, and walks from the closer end:
   - **`get(size-1)`** enters the `else` branch → starts at `last` → loop runs 0 iterations → essentially **O(1)**.
   - **`get(size/2)`** is the actual worst case → walks `size/2` steps from either end → **O(n/2)**, still O(n) asymptotically but the practical worst.
   - This optimization is **only possible because the list is doubly linked** (needs `prev` to walk backward from `last`). A singly linked list would always be O(index) from the head.

---

## Phase 3 — Build It Yourself

> **This is the single highest-leverage phase for interviews.** If you can write a linked list from scratch on a whiteboard, you've mastered ~70% of what interviewers care about.

### 3.1 Singly linked list — the design

**Two classes:**
- `Node<E>` — the atom.
- `MyLinkedList<E>` — the list itself.

**Fields on the list:**
- `Node<E> head` — first node (or `null` if empty).
- `Node<E> tail` — last node (or `null` if empty). *Optional but critical for O(1) `addLast`.*
- `int size` — cached count.

**Method implementation order** (each builds on the last):
- [ ] `addFirst(E e)` — the warm-up
- [ ] `addLast(E e)` — teaches traversal
- [ ] `get(int index)` — teaches indexing
- [ ] `add(int index, E e)` — the tricky one (stop-one-before pattern)
- [ ] `removeFirst()` — easy
- [ ] `remove(int index)` — same trick as `add(index, e)`
- [ ] `removeLast()` — O(n) without doubly linked!
- [ ] `size()` — trivial
- [ ] `print` / `toString` — for debugging

---

### 3.2 Method-by-method walkthrough (the key insights)

#### The Node class

```java
static class Node<E> {
    E value;
    Node<E> next;

    Node(E value) {
        this.value = value;
        this.next = null;
    }
}
```

That's it. No `prev` (singly linked). No `id`.

#### `addFirst(E e)` — order matters

Current: `head → [A] → [B] → null`
Want:    `head → [NEW] → [A] → [B] → null`

Two things must happen:
1. NEW's `next` points to current head.
2. `head` is reassigned to NEW.

**Order matters.** If you set `head = new` first, you've lost the reference to `[A]`.

```java
public void addFirst(E value) {
    Node<E> node = new Node<>(value);
    node.next = head;   // step 1 first
    head = node;        // step 2
    size++;
}
```

#### `addLast(E e)` — the traversal pattern

Without a `tail` pointer, you must walk to the end. **Memorize this loop shape:**

```java
Node<E> current = head;
while (current.next != null) {   // NOT current != null
    current = current.next;
}
// current is now the last node
```

**Trap:** `current.next != null` (not `current != null`). If you use the latter, you walk *past* the last node and end up at `null` — can't attach anything to a null.

#### `get(int index)`

```java
Node<E> current = head;
for (int i = 0; i < index; i++) {   // NOT i <= index
    current = current.next;
}
return current.value;
```

**Off-by-one alert:** loop `index` times, not `index+1`. After 0 hops → at index 0. After 3 hops → at index 3.

#### `add(int index, E e)` — the "stop-one-before" pattern

**Insight:** to insert AT index `i`, you need the node AT index `i-1` (the predecessor), so you can rewire its `next`.

Current: `... [prev] → [old] → ...`
Want:    `... [prev] → [NEW] → [old] → ...`

Two rewires:
1. `NEW.next = prev.next` (point NEW at old)
2. `prev.next = NEW`

**Order matters again.** Reverse order loses `old`.

**Edge case:** inserting at index 0 has no predecessor — delegate to `addFirst`.

#### `remove(int index)` — same "stop-one-before" trick

Current: `... [prev] → [target] → [after] → ...`
Want:    `... [prev] → [after] → ...`

One rewire: `prev.next = prev.next.next`.

**Pro tip:** after removal, set `target.next = null` to help GC. Not required, but interviewers notice.

---

### 3.3 Full reference implementation — singly linked

```java
import java.util.NoSuchElementException;

public class MySinglyLinkedList<E> {

    private static class Node<E> {
        E value;
        Node<E> next;
        Node(E value) { this.value = value; }
    }

    private Node<E> head;
    private Node<E> tail;
    private int size;

    public int size()        { return size; }
    public boolean isEmpty() { return size == 0; }

    public void addFirst(E value) {
        Node<E> node = new Node<>(value);
        node.next = head;
        head = node;
        if (tail == null) tail = node;   // empty-list case
        size++;
    }

    public void addLast(E value) {
        Node<E> node = new Node<>(value);
        if (tail == null) {
            head = tail = node;
        } else {
            tail.next = node;
            tail = node;
        }
        size++;
    }

    public void add(int index, E value) {
        checkPositionIndex(index);
        if (index == 0)    { addFirst(value); return; }
        if (index == size) { addLast(value);  return; }

        Node<E> prev = nodeAt(index - 1);
        Node<E> node = new Node<>(value);
        node.next = prev.next;
        prev.next = node;
        size++;
    }

    public E get(int index) {
        checkElementIndex(index);
        return nodeAt(index).value;
    }

    public E removeFirst() {
        if (head == null) throw new NoSuchElementException();
        E value = head.value;
        head = head.next;
        if (head == null) tail = null;   // list became empty
        size--;
        return value;
    }

    public E removeLast() {
        if (tail == null) throw new NoSuchElementException();
        if (head == tail) return removeFirst();   // single element
        // O(n) walk to node just before tail — singly linked's weakness
        Node<E> prev = head;
        while (prev.next != tail) prev = prev.next;
        E value = tail.value;
        prev.next = null;
        tail = prev;
        size--;
        return value;
    }

    public E remove(int index) {
        checkElementIndex(index);
        if (index == 0)        return removeFirst();
        if (index == size - 1) return removeLast();

        Node<E> prev = nodeAt(index - 1);
        Node<E> target = prev.next;
        prev.next = target.next;
        target.next = null;   // help GC
        size--;
        return target.value;
    }

    private Node<E> nodeAt(int index) {
        Node<E> cur = head;
        for (int i = 0; i < index; i++) cur = cur.next;
        return cur;
    }

    private void checkElementIndex(int index) {
        if (index < 0 || index >= size)
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
    }

    private void checkPositionIndex(int index) {
        // Position index allows == size (inserting at the very end)
        if (index < 0 || index > size)
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (Node<E> cur = head; cur != null; cur = cur.next) {
            sb.append(cur.value);
            if (cur.next != null) sb.append(" -> ");
        }
        return sb.append("]").toString();
    }
}
```

---

### 3.4 Common bugs (log the ones YOU hit)

Pre-filled with the most common ones — add your own as you code:

- [ ] **Order-of-assignment bug in `addFirst`** — assigning `head = node` before `node.next = head` loses the rest of the list.
- [ ] **Loop condition off-by-one** — `while (current != null)` walks past the tail; should be `while (current.next != null)`.
- [ ] **Off-by-one in `get`** — looping `<=index` instead of `<index`.
- [ ] **Forgot to update `tail`** on `addFirst` when list was empty, or on `removeLast` when list becomes empty.
- [ ] **Forgot to update `size`** — the operation works but `size()` lies afterward.
- [ ] **NPE on empty list** in `removeFirst` / `removeLast` — always guard.
- [ ] **`add(index, e)` uses `>= size` for bounds** — should be `> size` (you CAN insert AT position `size`, appending to the end).
- [ ] Add your own here:
- [ ] ...

---

### 3.5 Doubly linked list — what changes

#### The new Node

```java
static class Node<E> {
    E value;
    Node<E> next;
    Node<E> prev;   // ← new
    Node(E value) { this.value = value; }
}
```

#### What gets EASIER (this is the payoff)

**Removing a known node becomes O(1)** — no need to walk to find the predecessor.

Singly linked: to remove node `X`, walk from head to find X's predecessor. O(n).

Doubly linked: use `X.prev` directly.

```java
public void unlink(Node<E> x) {
    Node<E> prev = x.prev;
    Node<E> next = x.next;

    if (prev == null) head = next;      // x was the head
    else              prev.next = next;

    if (next == null) tail = prev;      // x was the tail
    else              next.prev = prev;

    x.next = null;   // help GC
    x.prev = null;
    size--;
}
```

Six lines, no traversal. **This is why `java.util.LinkedList` is doubly linked** — needs O(1) removal from both ends and O(1) via iterators.

`removeLast()` becomes O(1) too (just call `unlink(tail)`).

Also enables the `nodeAt(index)` optimization from Phase 2 — walk from `first` or `last` depending on which is closer.

#### What gets HARDER

**Every mutation must keep BOTH pointers consistent.** `addFirst`, `addLast`, `add(index)`, `remove(index)` all need to update `prev` on the affected nodes.

**Example — `addFirst` for doubly linked:**

```java
public void addFirst(E value) {
    Node<E> oldHead = head;
    Node<E> node = new Node<>(null, value, oldHead);
    head = node;
    if (oldHead == null) tail = node;          // empty list case
    else                 oldHead.prev = node;  // ← don't forget this!
    size++;
}
```

Miss the `oldHead.prev = node` line and your `prev` chain is silently broken. Nothing crashes immediately — the bug shows up later when someone walks backward. **These "silent" bugs are what interviewers love to catch.**

---

### 3.6 Full reference implementation — doubly linked

```java
import java.util.NoSuchElementException;

public class MyDoublyLinkedList<E> {

    private static class Node<E> {
        E value;
        Node<E> next;
        Node<E> prev;
        Node(Node<E> prev, E value, Node<E> next) {
            this.prev = prev;
            this.value = value;
            this.next = next;
        }
    }

    private Node<E> head;
    private Node<E> tail;
    private int size;

    public int size()        { return size; }
    public boolean isEmpty() { return size == 0; }

    public void addFirst(E value) {
        Node<E> oldHead = head;
        Node<E> node = new Node<>(null, value, oldHead);
        head = node;
        if (oldHead == null) tail = node;
        else                 oldHead.prev = node;
        size++;
    }

    public void addLast(E value) {
        Node<E> oldTail = tail;
        Node<E> node = new Node<>(oldTail, value, null);
        tail = node;
        if (oldTail == null) head = node;
        else                 oldTail.next = node;
        size++;
    }

    public void add(int index, E value) {
        checkPositionIndex(index);
        if (index == size) { addLast(value); return; }
        // Insert BEFORE the node currently at `index`
        Node<E> succ = nodeAt(index);
        Node<E> pred = succ.prev;
        Node<E> node = new Node<>(pred, value, succ);
        succ.prev = node;
        if (pred == null) head = node;      // inserted at head
        else              pred.next = node;
        size++;
    }

    public E get(int index) {
        checkElementIndex(index);
        return nodeAt(index).value;
    }

    public E removeFirst() {
        if (head == null) throw new NoSuchElementException();
        return unlink(head);
    }

    public E removeLast() {
        if (tail == null) throw new NoSuchElementException();
        return unlink(tail);   // O(1) — this is why doubly linked wins here
    }

    public E remove(int index) {
        checkElementIndex(index);
        return unlink(nodeAt(index));
    }

    private E unlink(Node<E> x) {
        E value = x.value;
        Node<E> prev = x.prev;
        Node<E> next = x.next;

        if (prev == null) head = next;
        else { prev.next = next; x.prev = null; }

        if (next == null) tail = prev;
        else { next.prev = prev; x.next = null; }

        x.value = null;   // help GC
        size--;
        return value;
    }

    private Node<E> nodeAt(int index) {
        // Walk from whichever end is closer — same trick as java.util.LinkedList
        if (index < (size >> 1)) {
            Node<E> cur = head;
            for (int i = 0; i < index; i++) cur = cur.next;
            return cur;
        } else {
            Node<E> cur = tail;
            for (int i = size - 1; i > index; i--) cur = cur.prev;
            return cur;
        }
    }

    private void checkElementIndex(int index) {
        if (index < 0 || index >= size)
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
    }

    private void checkPositionIndex(int index) {
        if (index < 0 || index > size)
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (Node<E> cur = head; cur != null; cur = cur.next) {
            sb.append(cur.value);
            if (cur.next != null) sb.append(" <-> ");
        }
        return sb.append("]").toString();
    }
}
```

---

### 3.7 The mental discipline (two questions before every pointer rewire)

Whenever you rewire pointers, ask yourself **before writing the code**:

1. **What order must I do the assignments in?**
   If I overwrite this pointer first, do I lose a reference I still need? If yes → save it in a local variable first, or reorder.

2. **What are the edge cases?**
   - Empty list?
   - Single-element list?
   - Operating on the head?
   - Operating on the tail?
   - Operating past the end (bounds check)?

**Every linked list bug in every interview I've ever seen falls into one of those categories.** If you consciously check both questions on every method, you'll be in the top 10% of candidates.

---

### 3.8 Key comparison — singly vs doubly

1. **`removeLast()`** — singly: O(n) (walk to find predecessor). Doubly: O(1) (via `tail.prev`). *This is the entire reason `java.util.LinkedList` is doubly linked.*

2. **`unlink(Node)`** — doesn't exist in singly. In doubly, removing an arbitrary known node is O(1). Unlocks efficient iterator-based removal.

3. **`nodeAt(index)` optimization** — doubly walks from whichever end is closer. `get(size-1)` is effectively O(1). Singly can't do this.

4. **Every mutation touches 2 pointers in doubly** — miss one → silent bug (walks forward fine, breaks walking backward).

5. **The elegant `unlink` pattern:**
   ```java
   if (prev == null) head = next;      // handles "am I the head?"
   else              prev.next = next;
   ```
   Handles the head-removal case without a special `if`. Study it — this pattern shows up everywhere.

---

### 3.9 Practice checklist (do this in order)

- [ ] Type the singly linked list from scratch (don't copy — peek only if stuck 5+ min).
- [ ] Write a `main` that adds/removes/prints, confirm output.
- [ ] Intentionally break it: `removeFirst()` on empty, `get(-1)`, `add(size+1, x)`. Confirm exceptions fire cleanly.
- [ ] Rewrite as doubly linked from scratch.
- [ ] Do it again in ~15 min for singly, ~20 min for doubly, from empty file, no reference.

**That's the interview bar. Once you can do this, you're ready for Phase 4.**

---

## Phase 4 — Classic Interview Problems

> These six problems cover ~90% of linked list interview questions. Each teaches a **transferable technique**, not just a solution.

Standard node used throughout (LeetCode style — matches what interviews use):

```java
class ListNode {
    int val;
    ListNode next;
    ListNode(int val) { this.val = val; }
}
```

---

### 4.1 Reverse a linked list

**Problem:** Given `1 → 2 → 3 → 4 → null`, return `4 → 3 → 2 → 1 → null`.

#### Insight (iterative)

Walk once. As you visit each node, **flip its `next` pointer to point at the previous node**. Need three pointers: `prev`, `curr`, and a temp `next` so you don't lose the rest of the list when you flip.

```
 prev  curr  next
  ↓     ↓     ↓
 null  [1] → [2] → [3] → [4] → null

After step 1:
  null ← [1]   [2] → [3] → [4] → null
              ↑    ↑
             prev curr
```

#### Code (iterative)

```java
public ListNode reverse(ListNode head) {
    ListNode prev = null;
    ListNode curr = head;
    while (curr != null) {
        ListNode next = curr.next;   // save before we overwrite
        curr.next = prev;            // flip
        prev = curr;                 // advance prev
        curr = next;                 // advance curr
    }
    return prev;   // prev is the new head
}
```

#### Code (recursive)

```java
public ListNode reverseRec(ListNode head) {
    if (head == null || head.next == null) return head;
    ListNode newHead = reverseRec(head.next);
    head.next.next = head;   // next node points back at me
    head.next = null;        // I now point at nothing
    return newHead;
}
```

#### Complexity

- Iterative: **O(n) time, O(1) space.**
- Recursive: O(n) time, **O(n) space** (call stack). Iterative strictly better.

#### Trap

Forgetting to save `next` before flipping. Once you write `curr.next = prev`, the rest of the list is lost unless you saved it. **Single most common linked list bug.**

#### Why this matters

Reversal is a **building block** for other problems: reverse-in-K-groups, palindrome check, reorder list. Master this → half of "medium" problems become easier.

---

### 4.2 Detect a cycle (Floyd's tortoise & hare)

**Problem:** Does the list have a cycle? (Some node's `next` points back to an earlier node.)

#### Naive approach (know it so you can dismiss it)

`HashSet<ListNode>`. Walk, add each node. If ever see one already in set → cycle. **O(n) time, O(n) space.**

#### Floyd's insight

Two pointers: `slow` moves 1 step, `fast` moves 2 steps.
- **No cycle:** `fast` hits `null` → done.
- **Cycle:** `fast` laps `slow` inside the loop → they eventually meet.

**Intuition:** if you and I run around a circular track and I run twice as fast, I *must* catch up to you exactly once per lap. The gap closes by 1 every step.

#### Code

```java
public boolean hasCycle(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) return true;
    }
    return false;
}
```

**Complexity: O(n) time, O(1) space** — beats the HashSet version.

#### Traps

1. Loop guard must check **both** `fast != null` AND `fast.next != null` — else `fast.next.next` NPEs.
2. Initialize **both** at `head` (not `head` and `head.next`). Simpler, avoids single-node edge cases.

#### Follow-up: find the cycle start

Floyd's part 2:

```java
public ListNode cycleStart(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) {
            // Reset one pointer to head, both move 1 step, they meet at cycle start
            ListNode p = head;
            while (p != slow) { p = p.next; slow = slow.next; }
            return p;
        }
    }
    return null;
}
```

Memorize the pattern first, understand the proof second.

---

### 4.3 Find the middle node

**Problem:** For `1 → 2 → 3 → 4 → 5` return node `3`. For `1 → 2 → 3 → 4` return `3` (second middle).

#### Insight

Same slow/fast trick. When `fast` reaches the end, `slow` is at the middle.

**Why:** `fast` covers 2× the distance of `slow`. When `fast` covers `n`, `slow` covers `n/2` — that's the middle.

#### Code

```java
public ListNode middle(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }
    return slow;
}
```

**Complexity: O(n) time, O(1) space. Single pass.**

#### Naive alternative (dismiss it)

Walk once to count size, walk again to `size/2`. **Two passes.** Slow/fast is one pass — that's the win.

#### Trap — even-length lists have two middles

With the loop above, `1 → 2 → 3 → 4` returns `3` (second middle). If interviewer wants the *first* middle (i.e., `2`), change the loop condition:

```java
while (fast.next != null && fast.next.next != null) { ... }
```

**Always ask which middle they want.** The clarifying question earns points.

---

### 4.4 Merge two sorted lists

**Problem:** Given `1 → 3 → 5` and `2 → 4 → 6`, return `1 → 2 → 3 → 4 → 5 → 6`.

#### Insight — the dummy node trick

The messy part of merging is handling the *first* node (which list starts smaller?). Solution: create a **dummy node** whose `next` will point to the real answer. Every append becomes uniform — no special case for "is this the first node?"

```
 dummy → [1] → [2] → [3] → ...
   ↑
   throw this away at the end, return dummy.next
```

#### Code

```java
public ListNode merge(ListNode a, ListNode b) {
    ListNode dummy = new ListNode(0);
    ListNode tail = dummy;
    while (a != null && b != null) {
        if (a.val <= b.val) {
            tail.next = a;
            a = a.next;
        } else {
            tail.next = b;
            b = b.next;
        }
        tail = tail.next;
    }
    tail.next = (a != null) ? a : b;   // attach whichever list still has nodes
    return dummy.next;
}
```

**Complexity: O(n + m) time, O(1) space** (rewiring, not copying).

#### Why dummy nodes matter (use cases)

The dummy node pattern shows up in:
- Merge two lists (this problem)
- Merge K sorted lists (with a priority queue)
- Remove nodes matching a condition
- Partition a list
- Reverse nodes in K-groups
- Remove Nth from end (Problem 4.6)

**Rule of thumb:** whenever your output is "a new linked list built by appending", reach for a dummy.

#### Trap

Forgetting the last line — `tail.next = (a != null) ? a : b`. Without it, the merged list is truncated at the point where the shorter input ran out.

---

### 4.5 Intersection of two lists

**Problem:** Two lists may share a common tail (not equal *values* — the same *nodes*). Find the first shared node.

```
 A: a1 → a2 ↘
              c1 → c2 → c3
 B: b1 → b2 → b3 ↗
```

Return `c1`.

#### Naive approach

`HashSet` all nodes of A, walk B, return first hit. **O(n+m) time, O(n) space.**

#### The elegant O(1)-space trick

Two pointers `pA` and `pB` starting at `headA` and `headB`. Walk one step at a time. **When a pointer hits `null`, jump it to the other list's head.**

**Why it works:** by the time both pointers switch lists, they've each walked exactly `lenA + lenB` steps. If the lists intersect, they'll meet at the intersection node. If not, they both hit `null` at the same time.

You **equalize the runway** by making both pointers walk both lists.

#### Code

```java
public ListNode getIntersection(ListNode a, ListNode b) {
    if (a == null || b == null) return null;
    ListNode pA = a, pB = b;
    while (pA != pB) {
        pA = (pA == null) ? b : pA.next;
        pB = (pB == null) ? a : pB.next;
    }
    return pA;   // either the intersection node, or null if no intersection
}
```

**Complexity: O(n + m) time, O(1) space.**

#### Traps

1. The check is `(pA == null) ? b`, **NOT** `(pA.next == null)`. The null step is what equalizes the lengths.
2. Comparison is `pA == pB` — **reference equality**. Values don't matter; you're comparing whether they point at the same node.
3. If no intersection, both eventually become `null` at the same time → `pA == pB == null` → loop exits cleanly. Elegant.

---

### 4.6 Remove Nth node from end

**Problem:** Given `1 → 2 → 3 → 4 → 5` and `n = 2`, return `1 → 2 → 3 → 5` (removed the 4).

#### Naive approach

Walk once to count size, walk again to `size - n`. **Two passes.**

#### Insight — the "gap of N" two-pointer trick

**Set up a gap of N nodes between two pointers, then move them together until the leading one falls off the end.**

1. Point `fast` at head. Move it forward `n` steps.
2. Point `slow` at head (with a **dummy** to handle removing the head cleanly).
3. Move both together until `fast` hits `null`.
4. `slow` is now at the node **before** the one to remove. Unlink.

#### Code

```java
public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode dummy = new ListNode(0);
    dummy.next = head;

    ListNode fast = dummy;
    ListNode slow = dummy;

    // Move fast n+1 steps so slow ends up BEFORE the target
    for (int i = 0; i <= n; i++) fast = fast.next;

    while (fast != null) {
        fast = fast.next;
        slow = slow.next;
    }

    slow.next = slow.next.next;   // unlink
    return dummy.next;
}
```

**Complexity: O(n) time, O(1) space. Single pass.**

#### Why the dummy node is essential

Without it, removing the head (n == list size) is a special case. With the dummy, `slow` can always "point at the node before the target" uniformly — even when the target is the real head.

**Dummy nodes eliminate head-removal edge cases.** Remember this pattern.

#### Trap

Off-by-one on how far to move `fast`. Do it on paper first with `n=1` (remove last) and `n=size` (remove head) to convince yourself the gap is right.

---

### 4.7 The 5 transferable techniques

Not the six problems — the **techniques**. These are the actual transferable skills:

1. **In-place pointer flipping** — Problem 4.1 (reverse)
   *Also used in:* palindrome check, reorder list, reverse-in-K-groups

2. **Slow/fast pointers** — Problems 4.2, 4.3 (cycle, middle)
   *Also used in:* split-in-half, palindrome check, find-Kth-from-end (variant)

3. **Dummy node** — Problems 4.4, 4.6 (merge, remove-Nth)
   *Also used in:* any problem building a new list from the head, removing elements matching a condition, partitioning

4. **Two-pointer with a gap** — Problem 4.6 (remove-Nth)
   *Also used in:* "Nth from end" and variants, sliding-window-style list problems

5. **Pointer-switching to equalize length** — Problem 4.5 (intersection)
   *Also used in:* some list-alignment problems

**Interview meta-tip:** when you see a linked list problem, ask *"can one of these five techniques crack it?"* first, before writing any code. 80% of the time, one of them can.

---

### 4.8 Follow-up problems to practice

Direct extensions of the six above. Do these after you can solve all six cold:

- [ ] **Palindrome check** — find middle + reverse second half + compare
- [ ] **Reorder list** `1→2→3→4→5` → `1→5→2→4→3` — middle + reverse + merge
- [ ] **Merge K sorted lists** — merge-two + priority queue (min-heap)
- [ ] **Reverse in K-groups** — reverse pattern + iteration with dummy node

If you can do the six main problems AND these four follow-ups, you're better prepared than 90% of candidates walking into a linked-list interview.

---

### 4.9 Practice discipline

1. **Do each problem on paper first.** Draw nodes, move pointers with a pencil. Don't touch the keyboard until the picture is right.
2. **Code without the reference.** Struggle 15 min per problem before peeking.
3. **Handle empty list and single-node cases** in every solution. Interviewers ask "what if the list is empty?" for every problem.
4. **After you can do all six, do the four follow-ups.**

---

## Phase 5 — When to Use LinkedList vs ArrayList

> **The most interview-frequent question in the entire linked list topic.** Get this right and you sound senior.

### 5.1 Head-to-head table

| Operation                       | ArrayList          | LinkedList          | Winner |
| ------------------------------- | ------------------ | ------------------- | ------ |
| `get(i)` / `set(i, e)`          | **O(1)**           | O(n)                | ArrayList |
| `add(e)` (append at end)        | O(1) amortized     | **O(1)**            | ~tie (ArrayList in practice) |
| `add(0, e)` (insert at head)    | O(n)               | **O(1)**            | LinkedList |
| `add(i, e)` (insert in middle)  | O(n) shift         | O(n) walk + O(1) rewire | ~tie |
| `remove(0)`                     | O(n)               | **O(1)**            | LinkedList |
| `remove(i)`                     | O(n) shift         | O(n) walk + O(1) rewire | ~tie |
| `contains(o)`                   | O(n)               | O(n)                | tie |
| Iteration                       | O(n) fast          | O(n) slower         | **ArrayList** (cache) |
| Memory per element              | just the ref       | ref + 2 pointers + Node object | ArrayList |

**Honest reading:** LinkedList only *strictly* wins at insert/remove at the head. And even for that, `ArrayDeque` beats it.

---

### 5.2 The real reason ArrayList wins (the "aha" moment)

Big-O lies here. **Cache behavior dominates real-world performance**, and Big-O ignores it entirely.

#### What CPUs actually do

When you access memory, the CPU doesn't fetch one byte — it fetches a whole **cache line** (typically 64 bytes). If the next thing you need is nearby, it's already in the fastest cache (L1). If it's far away, the CPU stalls for **hundreds of cycles** waiting for RAM.

#### ArrayList in memory

```
 [refA][refB][refC][refD][refE][refF][refG][refH]  ← one contiguous block
```

When you iterate, the CPU pre-fetches the next chunk. Every access is a cache hit. **Blazing fast.**

#### LinkedList in memory

```
 [Node@0x2A00]   ...gap...   [Node@0x5F80]   ...gap...   [Node@0x1130]
     next ────────────────────────↑
                                   next ─────────────────────↑
```

Every `next` hop is a jump to a random address. **Cache miss. CPU stalls.** Each node object also carries overhead (object header + two pointers) — you might fit **8× fewer** LinkedList nodes in the cache than ArrayList elements.

#### Rough benchmark reality

Iterating a LinkedList of 1M elements is often **10–20× slower** than iterating an ArrayList of the same size, even though both are O(n).

**This is why real production code almost never uses LinkedList.**

---

### 5.3 Why LinkedList's "wins" are misleading

#### "But O(1) `add(0, e)` at head!"

True. But **how often do you actually insert at the head?**

- If yes → you probably want a `Deque` (queue behavior), not a `List`. Use `ArrayDeque`.
- If no → who cares.

#### "But O(1) middle-remove!"

True in theory. But **you're never "just there"** with a `List` API — you have to walk to the index first (O(n)). So `list.remove(50000)` on a LinkedList is still O(n).

The O(1) middle-remove **only** helps when you're iterating and removing via `Iterator.remove()`. Real but narrow.

#### "But no resizing cost!"

True. ArrayList occasionally does an O(n) resize when it fills up. But this cost is **amortized to O(1)** per add, and the memory copy is a cache-friendly contiguous operation. In practice, resizing is barely measurable.

---

### 5.4 When LinkedList actually wins (rare)

Honestly, almost never. But the narrow cases:

1. **You need a queue/deque AND for some reason `ArrayDeque` doesn't fit.**
   `ArrayDeque` doesn't allow `null` elements. `LinkedList` does. That's about it.

2. **You have massive amounts of iterator-based mid-list insertions/deletions** — and profiling has shown ArrayList's shifting is your bottleneck. Extremely rare.

3. **You need a `List` that also implements `Deque`** — no other JDK class does both. Also rare.

**In 15 years of production code, most engineers reach for `LinkedList` ~zero times.**

---

### 5.5 The interview answer

#### Junior answer (avoid saying this)

> *"ArrayList is O(1) for `get`, LinkedList is O(1) for insert/remove."*

Technically correct, misses the point. Sounds like you memorized Big-O.

#### Senior answer (say this instead)

> *"In practice, always default to ArrayList. It's faster even at operations where LinkedList has a better Big-O — because contiguous memory is cache-friendly and pointer-chasing isn't. LinkedList's advantage is theoretical; ArrayList's advantage is measured. The only reason LinkedList exists in most codebases is that it also implements `Deque`, but if you want a Deque, `ArrayDeque` is better still. So: `ArrayList` for lists, `ArrayDeque` for queues/stacks. LinkedList almost never."*

This signals you understand hardware, not just complexity classes.

#### Bonus points

> *"The one exception is if you're doing tons of iterator-based middle removals, since that's actually O(1) on LinkedList. But I'd profile before choosing it — the cache penalty on iteration usually still makes ArrayList faster overall."*

---

### 5.6 Decision tree

```
Do I need a List?
├── Yes → ArrayList. Done.
└── Do I need a Queue or Deque?
    ├── Yes → ArrayDeque. Done.
    └── Do I need a List AND a Deque in one object?
        ├── Yes → LinkedList (rare)
        └── No → you shouldn't be here
```

**Memorize this. It's the entire practical wisdom of the topic.**

---

### 5.7 Related follow-ups (interviewers often ask after ArrayList vs LinkedList)

#### Stack vs ArrayDeque

`Stack` is **legacy** (extends `Vector`, synchronized, slow). Always use `ArrayDeque` for LIFO behavior:
- `push(e)` = `addFirst(e)`
- `pop()` = `removeFirst()`
- `peek()` = `peekFirst()`

#### `synchronizedList` vs `CopyOnWriteArrayList`

- **`Collections.synchronizedList(list)`** — coarse-grained locking; every op takes a monitor. Simple but scales poorly under contention.
- **`CopyOnWriteArrayList`** — every mutation copies the entire underlying array. Great for **read-heavy** workloads (reads are lock-free), expensive on write. Common for listener lists, config that changes rarely.
- Modern concurrent alternatives: `ConcurrentLinkedQueue`, `ConcurrentLinkedDeque` — lock-free, better for high-concurrency queue/deque use.

#### When to use `Vector`

**Never.** Legacy synchronized ArrayList from Java 1.0. Use `ArrayList` + external synchronization, or `CopyOnWriteArrayList`, or a concurrent collection instead.

#### Why LinkedList iteration is fast but `get(i)` is slow

- **Iterator** holds a reference to the current node and only does `.next` — O(1) per step, O(n) total to iterate the whole list.
- **`get(i)`** restarts from `head` (or `tail`, whichever's closer) every call — O(i) per call. Calling `get(i)` in a loop from 0 to n gives you O(n²) total.

**Rule:** if you find yourself writing `for (int i = 0; i < list.size(); i++) list.get(i)` on a LinkedList, you've built an O(n²) algorithm without realizing it. Use an iterator or foreach loop instead.

---

## Phase 6 — Interview Deep Cuts

> Topics that don't fit neatly in Phases 1–5 but come up frequently in interviews. Learn these to go from "solid" to "senior."

---

### 6.1 Iterator & `ListIterator` internals

#### How the iterator works

`LinkedList`'s iterator holds two pieces of state:
- `Node<E> next` — the node it will return on the next `next()` call.
- `Node<E> lastReturned` — the node it just returned (used for `remove()` / `set()`).

Because it holds a **direct reference** to the current node, walking forward via `it.next()` is **O(1) per step**. Iterating the entire list is O(n). This is the fast way.

Compare to `list.get(i)` in a loop — every call restarts from `head` (or `tail`). Same list, same "walk it once" goal, but O(n²) total.

#### `ListIterator` — bidirectional + mutating

`LinkedList` returns `ListIterator` from `listIterator()`, which extends `Iterator` with:

| Method             | What it does                        |
| ------------------ | ----------------------------------- |
| `hasPrevious()`    | Can we walk backward?               |
| `previous()`       | Move backward one step              |
| `nextIndex()` / `previousIndex()` | Current position |
| `add(e)`           | Insert before the current position  |
| `set(e)`           | Replace `lastReturned` value        |
| `remove()`         | Remove `lastReturned`               |

**`Iterator.remove()` on a LinkedList is O(1)** — the iterator already has a reference to the node, so it can `unlink` directly (no walk from head). This is one of the few places LinkedList genuinely wins over ArrayList in practice.

`list.remove(i)` by contrast is O(n) — must walk to index `i` first.

#### Fail-fast behavior & `ConcurrentModificationException`

**The rule:** if you modify a `LinkedList` structurally (add/remove) *while iterating* through any means **other than the iterator itself**, the next `it.next()` call throws `ConcurrentModificationException`.

**How it detects this:**
- `LinkedList` has an internal `modCount` field (inherited from `AbstractList`). Every structural modification increments it.
- When you create an iterator, it snapshots `modCount` into `expectedModCount`.
- On every `next()` / `remove()`, the iterator checks: `if (modCount != expectedModCount) throw new ConcurrentModificationException();`

**Bug — this throws CME:**

```java
List<String> list = new LinkedList<>(List.of("A", "B", "C"));
for (String s : list) {           // for-each uses an iterator under the hood
    if (s.equals("B")) list.remove(s);   // ← modifies list directly, breaks iterator
}
// throws ConcurrentModificationException on the next iteration
```

**Correct — use the iterator's own `remove()`:**

```java
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (it.next().equals("B")) it.remove();   // ← iterator stays consistent
}
```

**Alternative — use `removeIf` (Java 8+):**

```java
list.removeIf(s -> s.equals("B"));   // cleanest, uses iterator internally
```

#### Traps interviewers probe

- **"Is `CME` a concurrency exception?"** — No, despite the name. It's raised even in single-threaded code. It just means "the collection was modified in a way the iterator can't handle." Concurrency is *one* cause, but the more common cause is single-threaded misuse.
- **"When is `CME` thrown?"** — Not at the moment of modification. It's thrown on the *next* iterator operation. So the exception's stack trace points at the wrong line, which trips people up.
- **"What if I want to modify safely from multiple threads?"** — Use a concurrent collection (see 6.2). `synchronizedList` doesn't help here because its iterator is still fail-fast.

---

### 6.2 Thread safety & concurrent alternatives

#### `LinkedList` is NOT thread-safe

If two threads touch a `LinkedList` at the same time without external synchronization, **anything can happen**: lost data, wrong size, infinite loops, silent corruption. Usually no exception is thrown — that's what makes it dangerous.

#### A concrete race — two threads doing `addLast` simultaneously

`LinkedList.addLast` is roughly:

```java
public void addLast(E value) {
    Node<E> oldTail = tail;                            // 1. read tail
    Node<E> node = new Node<>(oldTail, value, null);   // 2. create node
    tail = node;                                       // 3. update tail
    if (oldTail == null) head = node;
    else                 oldTail.next = node;          // 4. link old tail
    size++;                                            // 5. increment size
}
```

**Thread A** adds `X` and **Thread B** adds `Y` at the same time:

```
Time  Thread A                        Thread B
────  ────────                        ────────
t1    Read tail → T
t2                                    Read tail → T   (same!)
t3    Create nodeX, prev = T
t4                                    Create nodeY, prev = T
t5    tail = nodeX
t6                                    tail = nodeY    ← nodeX orphaned
t7    T.next = nodeX
t8                                    T.next = nodeY  ← overwrites A
t9    size++ (reads 5, writes 6)
t10                                   size++ (reads 5, writes 6)  ← lost update
```

**Result:** `X` is completely lost. Size is `6` when it should be `7`. **No exception** — silent data corruption.

#### The full list of failure modes

| Failure                                | What actually happens                                     |
| -------------------------------------- | --------------------------------------------------------- |
| **Lost updates**                       | Two adds → only one visible (as above)                    |
| **Wrong `size`**                       | Increment race → count doesn't match reality              |
| **Broken pointer chain**               | Partial writes visible → `next` points at wrong node      |
| **Infinite loops**                     | Cycle accidentally created → `for-each` never terminates  |
| **`NullPointerException`**             | Thread sees `next` as non-null, then it becomes null      |
| **`ConcurrentModificationException`**  | Iterator detects `modCount` mismatch                      |
| **Memory visibility**                  | Thread B never sees Thread A's writes (no `volatile`/lock) |

**Key insight:** "thread-unsafe" doesn't mean "throws an exception when misused." It means "works fine most of the time, then silently corrupts state under load." That's much worse than an exception.

---

#### Your three options for thread safety

##### Option 1 — External synchronization (manual)

You take responsibility for locking every access:

```java
LinkedList<String> list = new LinkedList<>();

synchronized (list) { list.addLast("X"); }
synchronized (list) { String s = list.removeFirst(); }
```

**Pros:** you control lock granularity.
**Cons:** easy to forget somewhere → same bugs. Every read AND every write must be synchronized.

##### Option 2 — `Collections.synchronizedList` wrapper

```java
List<String> list = Collections.synchronizedList(new LinkedList<>());

list.add("X");             // internally synchronized
String s = list.get(0);    // internally synchronized
```

Wraps every method in `synchronized (mutex)`. **But iteration is not covered** — you must sync manually:

```java
synchronized (list) {         // ← MANDATORY, or CME will strike
    for (String s : list) System.out.println(s);
}
```

**Pros:** simpler than manual.
**Cons:** coarse-grained lock → only one thread at a time can do anything. No parallelism. **Legacy pattern; rarely the right answer today.**

##### Option 3 — Concurrent alternatives (the right answer)

Purpose-built concurrent collections. **Don't use `LinkedList` for multi-threaded code — use one of these instead.**

| Class                       | Locking        | Blocking? | Bounded? | Nulls? |
| --------------------------- | -------------- | --------- | -------- | ------ |
| `ConcurrentLinkedQueue`     | Lock-free (CAS)| No        | No       | No     |
| `ConcurrentLinkedDeque`     | Lock-free (CAS)| No        | No       | No     |
| `LinkedBlockingQueue`       | 2 locks        | Yes       | Optional | No     |
| `LinkedBlockingDeque`       | 1 lock         | Yes       | Optional | No     |

---

#### Deep dive — the four concurrent alternatives

##### A. `ConcurrentLinkedQueue<E>` — lock-free FIFO

- **How it works:** uses **CAS** (Compare-And-Swap) atomic operations instead of locks. Thread does *"if tail is still X, atomically set tail.next to my new node."* If another thread got there first → retry.
- **Non-blocking:** `poll()` returns `null` immediately if empty. `offer()` never blocks (unbounded).
- **Great for:** high-throughput producer/consumer where consumers can tolerate polling.
- **Bad for:** waiting consumers — they'd have to busy-wait.

```java
Queue<String> queue = new ConcurrentLinkedQueue<>();
queue.offer("task1");                // never blocks
String task = queue.poll();          // null if empty (non-blocking)
if (task != null) process(task);
```

**Interview soundbite:** *"Lock-free via CAS. Producers and consumers don't block each other. Best for pure throughput; not ideal when consumers should sleep until work arrives."*

##### B. `ConcurrentLinkedDeque<E>` — lock-free deque

- Same as CLQ but supports both ends (`offerFirst`, `pollLast`, etc.).
- More complex internals (doubly-linked, more CAS work per operation).
- Use only when you actually need both ends. Otherwise CLQ.

##### C. `LinkedBlockingQueue<E>` — blocking FIFO ← *the workhorse*

**The producer/consumer default.** Its power:
- **`put(e)`** blocks if the queue is full (only meaningful if bounded).
- **`take()`** blocks if the queue is empty — consumer sleeps until work arrives. **No polling needed.**
- Optional bounded capacity → **backpressure**: fast producer can't overwhelm slow consumer.

**Why "two locks"?** Internally uses **separate `putLock` and `takeLock`** so producers and consumers don't contend for the same lock. This is why LBQ is often faster than LBD for pure queue use.

```java
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(100);   // bounded

// Producer
new Thread(() -> {
    while (running) {
        Task t = generateTask();
        try {
            queue.put(t);            // blocks if queue is full (backpressure!)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
}).start();

// Consumer
new Thread(() -> {
    while (running) {
        try {
            Task t = queue.take();   // blocks until an item is available
            process(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
}).start();
```

**The four ways to add / remove — pick your failure mode:**

| Style           | Insert                | Remove                | Behavior on failure                |
| --------------- | --------------------- | --------------------- | ---------------------------------- |
| Throws          | `add(e)`              | `remove()`            | `IllegalStateException` / `NoSuchElementException` |
| Returns special | `offer(e)`            | `poll()`              | Returns `false` / `null`           |
| Blocks          | `put(e)`              | `take()`              | Waits indefinitely                 |
| Times out       | `offer(e, t, unit)`   | `poll(t, unit)`       | Waits up to `t`, then gives up     |

For classic producer-consumer: use `put` / `take`.

##### D. `LinkedBlockingDeque<E>` — blocking deque

- Blocking deque (work-stealing, priority-swapping, etc.).
- Uses **one lock** (not two) — producers and consumers *do* contend. Slower than LBQ for pure queue use.
- Use when you need blocking semantics AND both-ended access.

---

#### The 30-second decision guide

```
Multiple threads touching a list-like structure?
├── Need to WAIT for elements? (classic producer-consumer)
│   ├── FIFO only  → LinkedBlockingQueue (bounded for backpressure)
│   └── Both ends  → LinkedBlockingDeque
├── High throughput, consumer can poll?
│   ├── FIFO only  → ConcurrentLinkedQueue
│   └── Both ends  → ConcurrentLinkedDeque
├── Read-heavy, few writes?
│   └── CopyOnWriteArrayList (not linked, but the right alternative)
└── Legacy code you can't refactor?
    └── Collections.synchronizedList(new LinkedList<>()) — last resort
```

---

#### Extra concepts worth knowing

##### 1. Memory visibility (Java Memory Model)

Even without a race, one thread's writes may not be **visible** to another without synchronization. `LinkedList` has no `volatile` fields:

```java
// Thread A
list.add("X");   // writes to some Node object

// Thread B (later, without any lock/volatile)
list.contains("X");   // might return FALSE even after A finished!
```

Concurrent collections handle this via `volatile` fields, locks, or CAS — all of which establish "happens-before" relationships in the JMM.

##### 2. Why concurrent queues don't allow `null`

`ConcurrentLinkedQueue`, `LinkedBlockingQueue`, etc. all reject `null` elements.

**Why?** Because `poll()` returns `null` to signal "queue was empty." If `null` were a valid element, you couldn't distinguish "no element" from "an element that happens to be null." `LinkedList` allows nulls because it uses exceptions to signal empty — but that's the *one* thing regular LinkedList wins at.

##### 3. Iteration in concurrent collections is **weakly consistent**

Unlike LinkedList (fail-fast, throws CME), concurrent collections' iterators are **weakly consistent**:
- They reflect the queue's state at some point during iteration — not necessarily any single instant.
- They **never throw `ConcurrentModificationException`**.
- They may or may not reflect modifications made after iteration started.

Deliberate trade-off: no CME → iteration is safe, but exact contents are approximate.

```java
Queue<String> q = new ConcurrentLinkedQueue<>();
q.offer("A"); q.offer("B");

for (String s : q) {    // no synchronization needed
    q.offer("C");       // no CME! (may or may not appear in this iteration)
}
```

##### 4. Executor framework uses these under the hood

`Executors.newFixedThreadPool(n)` is a `ThreadPoolExecutor` backed by a `LinkedBlockingQueue<Runnable>` as its task queue. When you `submit(task)`, the task goes into the queue; when a worker is free, it does `take()`. Classic producer-consumer at the heart of Java's concurrency library.

---

#### Interview questions on this

1. **"What happens if two threads add to a `LinkedList` at the same time?"**
   → Data loss, wrong size, potentially corrupt pointer chain. No exception — silent corruption. Walk through the race.

2. **"Why does `Collections.synchronizedList` still throw `CME` during iteration?"**
   → The wrapper synchronizes individual method calls, but the iterator holds `modCount` state across `next()` calls. Any modification between the iterator's calls trips CME — even mods that went through the sync wrapper. Fix: sync the whole iteration block.

3. **"When would you pick `LinkedBlockingQueue` over `ConcurrentLinkedQueue`?"**
   → When consumers should sleep until work arrives (`take()` blocks), or when you need bounded capacity for backpressure. CLQ is non-blocking → consumer polls → wastes CPU when idle.

4. **"Why do concurrent queues reject `null`?"**
   → Because `poll()` returns `null` to signal "empty." A `null` element would be indistinguishable from "no element."

5. **"What's the difference between `LinkedBlockingQueue`'s two-lock design and `LinkedBlockingDeque`'s one-lock design?"**
   → LBQ separates put-lock and take-lock so producers and consumers don't contend — big win for FIFO throughput. LBD needs one lock because operations on either end can interact with the other end; two locks would create complex ordering issues.

6. **"Can I iterate a `ConcurrentLinkedQueue` while another thread modifies it?"**
   → Yes, safely. Iterator is weakly consistent — no CME, but you may or may not see modifications made after iteration started.

7. **"Is `Vector` thread-safe? Should I use it instead of a synchronized LinkedList?"**
   → `Vector` is synchronized like `synchronizedList` (coarse-grained, method-level locks). It's a `List`, not a `Deque`, and it's legacy from Java 1.0. **Don't use it.** For a thread-safe list, use `CopyOnWriteArrayList` (read-heavy) or a concurrent queue/deque (mutating).

---

### 6.3 Full `Deque` / `Queue` API cheat sheet

`LinkedList` implements both interfaces, so it has many methods for effectively the same operation. The key distinction: **what happens on failure?**

#### Queue methods (FIFO — insert at tail, remove from head)

| Action  | Throws exception on failure | Returns special value on failure |
| ------- | --------------------------- | -------------------------------- |
| Insert  | `add(e)` — throws if capacity-restricted | `offer(e)` — returns `false` |
| Remove  | `remove()` — throws `NoSuchElementException` if empty | `poll()` — returns `null` if empty |
| Examine | `element()` — throws `NoSuchElementException` if empty | `peek()` — returns `null` if empty |

#### Deque methods (double-ended)

Every operation has a `First` and `Last` variant, and each has both a throwing and a null-returning form:

| Action           | Throws                                | Returns null/false                    |
| ---------------- | ------------------------------------- | ------------------------------------- |
| Insert front     | `addFirst(e)`                         | `offerFirst(e)`                       |
| Insert back      | `addLast(e)`                          | `offerLast(e)`                        |
| Remove front     | `removeFirst()`                       | `pollFirst()`                         |
| Remove back      | `removeLast()`                        | `pollLast()`                          |
| Examine front    | `getFirst()`                          | `peekFirst()`                         |
| Examine back     | `getLast()`                           | `peekLast()`                          |

#### Stack methods (LIFO, from `Deque`)

| Stack term | Deque method                          |
| ---------- | ------------------------------------- |
| push       | `push(e)` — same as `addFirst(e)`     |
| pop        | `pop()` — same as `removeFirst()`     |
| peek       | `peek()` — same as `peekFirst()`      |

> **Use `ArrayDeque` for stacks in modern code**, not `java.util.Stack` (which is legacy synchronized `Vector`).

#### The "which method should I use" guide

- **Building a queue for a producer-consumer scenario:** use `offer` / `poll` / `peek`. The null-returning variants are much easier to reason about than catching `NoSuchElementException`.
- **Enforcing "this list must never be empty here":** use `remove` / `element` / `getFirst`. The exception documents the invariant.
- **Never mix them in the same code path** — pick one style and stick with it. Mixed use is a common source of bugs (you catch the exception one place and check for `null` another).

#### The trap interviewers love

> *"What's the difference between `remove()` and `poll()`?"*

**Answer:** Behavior on empty. `remove()` throws `NoSuchElementException`; `poll()` returns `null`. Same for `add()` vs `offer()` (throws vs returns false) and `element()` vs `peek()` (throws vs returns null). The choice is about how you want to handle the empty/full case — exception-driven or value-driven.

---

### 6.4 LRU Cache — the classic combined problem

**Problem:** Design a data structure that supports:
- `get(key)` — return the value if present, else -1. **O(1).**
- `put(key, value)` — insert/update. If cache is full, evict the **least recently used** entry. **O(1).**

Both operations must be O(1). Any use of the cache (get or put) counts as "using" the key — moves it to "most recently used."

#### Why this problem appears in every senior interview

It's the perfect test of whether you understand two data structures deeply:
- **HashMap** — gives you O(1) lookup by key.
- **Doubly linked list** — gives you O(1) removal of a known node, and O(1) add-to-front.

Neither alone is enough. You need both, working together.

#### The design

```
 HashMap<Key, Node>        Doubly linked list (usage order)
 ┌──────┬───────┐          MRU ← head              tail → LRU
 │  A   │  ●────┼──▶ [A] ⇄ [C] ⇄ [B] ⇄ [D]
 │  B   │  ●────┼─────────────↑
 │  C   │  ●────┼──────↑
 │  D   │  ●────┼───────────────────↑
 └──────┴───────┘
```

- **Map** — key to node reference. O(1) lookup.
- **List** — nodes ordered by recency of use. Head = most recently used. Tail = least recently used (next to evict).

**On `get(key)`:**
1. Look up node in map. If missing → return -1.
2. Move node to head of list (mark as most recently used).
3. Return value.

**On `put(key, value)`:**
1. If key exists → update value, move node to head.
2. If key is new → create node, insert at head, add to map. If over capacity → remove tail from list AND from map.

**Why doubly linked** — move-to-head and evict-tail both need to unlink an arbitrary node in O(1). That requires `prev` pointers.

#### Full implementation

```java
public class LRUCache {

    private static class Node {
        int key, value;
        Node prev, next;
        Node(int key, int value) { this.key = key; this.value = value; }
    }

    private final int capacity;
    private final Map<Integer, Node> map = new HashMap<>();
    private final Node head = new Node(0, 0);   // sentinel: head.next = MRU
    private final Node tail = new Node(0, 0);   // sentinel: tail.prev = LRU

    public LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        Node node = map.get(key);
        if (node == null) return -1;
        moveToHead(node);
        return node.value;
    }

    public void put(int key, int value) {
        Node node = map.get(key);
        if (node != null) {
            node.value = value;
            moveToHead(node);
            return;
        }
        Node fresh = new Node(key, value);
        map.put(key, fresh);
        addToHead(fresh);
        if (map.size() > capacity) {
            Node lru = tail.prev;
            removeNode(lru);
            map.remove(lru.key);   // don't forget to remove from the map too
        }
    }

    private void addToHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(Node node) {
        removeNode(node);
        addToHead(node);
    }
}
```

**Complexity:** `get` and `put` are both **O(1)**. Space: **O(capacity)**.

#### The design decisions worth explaining in an interview

1. **Sentinel head and tail nodes.** They eliminate the need for null checks in `addToHead` / `removeNode`. Every "real" node always has a `prev` and a `next`. This is a common interview optimization — mention it explicitly.

2. **The Node stores the `key` too** (not just the value). This is why: when we evict the LRU, we need to know which key to remove from the map. Without the key on the node, we'd have to search the map — O(n). Storing the key on the node keeps eviction O(1).

3. **Why not `LinkedHashMap`?** Java's `LinkedHashMap` has a built-in mode for exactly this (`accessOrder=true` + override `removeEldestEntry`). In a real project you'd use it. But interviewers want you to **build the mechanism**, so implement it manually.

#### If they let you use `LinkedHashMap`

The 5-line version:

```java
public class LRUCache extends LinkedHashMap<Integer, Integer> {
    private final int capacity;
    public LRUCache(int capacity) {
        super(capacity, 0.75f, true);   // true = access-order
        this.capacity = capacity;
    }
    public int get(int key) { return super.getOrDefault(key, -1); }
    public void put(int key, int value) { super.put(key, value); }
    @Override
    protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
        return size() > capacity;
    }
}
```

Show the manual version *first* to prove you understand it, then mention this as the "in production I'd use `LinkedHashMap`" alternative.

#### Interview tips

- **Draw the diagram before coding.** Two structures + pointers between them are hard to hold in your head. Sketch the HashMap and the doubly linked list, show a get and a put operation on paper.
- **Handle edge cases explicitly:** first insert (empty cache), update existing key (no eviction), eviction at capacity.
- **Explain why doubly linked, not singly** — this is the whole reason the problem exists. If you don't say this, the interviewer will ask.
- **Mention sentinel nodes** — signals you've seen the pattern before.
- **Follow-up they may ask:** thread safety. Answer: wrap in `synchronized` methods (simple, coarse), or use `ConcurrentHashMap` + a proper concurrent LRU (much harder — mention `Caffeine` library as the production answer).

---

### 6.5 `equals()` and `hashCode()` in LinkedList

There are **two separate questions** hiding here — people often confuse them:

1. **How do `equals` / `hashCode` work ON the LinkedList itself?** (Comparing two lists, or storing a list in a HashSet/HashMap.)
2. **How does the ELEMENT's `equals` / `hashCode` affect LinkedList operations?** (`contains`, `remove`, `indexOf`.)

Both matter. Both are frequent interview traps.

---

#### Part 1 — `equals` / `hashCode` ON the LinkedList itself

`LinkedList` doesn't define these methods — it **inherits** them from `AbstractList`.

##### `equals(Object o)` — element-wise, cross-implementation

```java
// AbstractList.equals (paraphrased)
public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof List)) return false;
    ListIterator<E> e1 = listIterator();
    ListIterator<?> e2 = ((List<?>) o).listIterator();
    while (e1.hasNext() && e2.hasNext()) {
        E o1 = e1.next();
        Object o2 = e2.next();
        if (!(o1 == null ? o2 == null : o1.equals(o2))) return false;
    }
    return !(e1.hasNext() || e2.hasNext());   // same length check
}
```

**Rules:**
- Same size AND same elements in same order → equal.
- Compares against **any `List`** — LinkedList can `.equals` an ArrayList!
- Uses each **element's `.equals()`** to compare pairs.

**Example 1 — cross-implementation equality:**

```java
LinkedList<String> ll = new LinkedList<>(List.of("X", "Y", "Z"));
ArrayList<String>  al = new ArrayList<>(List.of("X", "Y", "Z"));

System.out.println(ll.equals(al));   // true !!  — the surprising one
```

**Example 2 — order matters:**

```java
LinkedList<String> a = new LinkedList<>(List.of("X", "Y", "Z"));
LinkedList<String> b = new LinkedList<>(List.of("Z", "Y", "X"));
System.out.println(a.equals(b));   // false — same elements, different order
```

##### `hashCode()` — the standard List hash formula

```java
// AbstractList.hashCode
public int hashCode() {
    int hashCode = 1;
    for (E e : this) {
        hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
    }
    return hashCode;
}
```

- `31` is a prime; `31 * x` compiles to a fast bit-shift on JVMs.
- **Same elements in same order → same hash** — required by the equals/hashCode contract.
- Also cross-implementation: `LinkedList` and `ArrayList` with same contents produce the same hash.

**When does the list's own `hashCode` matter?** Only when the list itself is stored in a hash-based structure:

```java
Set<List<String>> set = new HashSet<>();
set.add(new LinkedList<>(List.of("A", "B")));

System.out.println(set.contains(new ArrayList<>(List.of("A", "B"))));   // true !!
```

---

#### Part 2 — How the ELEMENT's `equals` affects LinkedList operations

Many `LinkedList` methods walk the list and compare elements using `.equals()`:

| Method                      | Uses element's `.equals()`?              |
| --------------------------- | ---------------------------------------- |
| `contains(Object o)`        | ✅ Yes — walks list, calls `e.equals(o)` |
| `indexOf(Object o)`         | ✅ Yes                                   |
| `lastIndexOf(Object o)`     | ✅ Yes                                   |
| `remove(Object o)`          | ✅ Yes — finds first match, unlinks      |
| `removeAll(Collection)`     | ✅ Yes                                   |
| `retainAll(Collection)`     | ✅ Yes                                   |
| `list.equals(otherList)`    | ✅ Yes — pairwise                        |
| `get(int i)` / `add`        | ❌ No — index-based                      |

**Important:** `LinkedList` **does NOT use `hashCode()` on elements** for any of its own operations. It only uses `.equals()`. Element `hashCode` matters only if the element itself is put into a HashMap/HashSet elsewhere.

##### Example 3 — String works (built-in equals override)

```java
LinkedList<String> list = new LinkedList<>();
list.add("hello");

String s = new String("hello");   // NEW object, different address
System.out.println(list.contains(s));   // true — String.equals compares chars
```

##### Example 4 — the classic bug (custom class WITHOUT equals override)

```java
class Employee {
    int id;
    String name;
    Employee(int id, String name) { this.id = id; this.name = name; }
    // NO equals() / hashCode() overridden
}

LinkedList<Employee> list = new LinkedList<>();
list.add(new Employee(1, "Alice"));

Employee lookup = new Employee(1, "Alice");   // same data, different object

System.out.println(list.contains(lookup));    // FALSE !!
System.out.println(list.remove(lookup));      // FALSE — nothing removed
```

**Why?** `Employee` inherits `Object.equals`, which is **reference equality** (`this == that`). The `lookup` object has a different memory address than the one in the list → not equal.

**Interview trap:** if the interviewer gives you `LinkedList<CustomClass>` and asks about `contains` or `remove`, the FIRST question to ask yourself is: *"Does CustomClass override `equals`?"*

##### Example 5 — proper equals + hashCode override

```java
class Employee {
    int id;
    String name;
    Employee(int id, String name) { this.id = id; this.name = name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Employee)) return false;
        Employee e = (Employee) o;
        return id == e.id && Objects.equals(name, e.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}

// Now contains, remove, indexOf all work as expected.
```

---

#### The equals / hashCode contract (memorize)

Java's five rules — violate them and hash-based collections silently break:

1. **Reflexive:** `x.equals(x)` is true.
2. **Symmetric:** `x.equals(y)` iff `y.equals(x)`.
3. **Transitive:** `x.equals(y)` and `y.equals(z)` → `x.equals(z)`.
4. **Consistency with hashCode:** `x.equals(y)` → `x.hashCode() == y.hashCode()`. ← the big one!
5. **Consistent across calls:** returns the same result if the object hasn't changed.

**Rule 4** is why you **always override `hashCode` when you override `equals`**. If you override only `equals`, two "equal" objects can produce different hashes → HashSet/HashMap breaks. `LinkedList` operations don't use element `hashCode` — but if the same object is ever placed in a hash-based structure, missing `hashCode` breaks it. IntelliJ generates both together for this reason.

---

#### Subtle gotcha — using a mutable LinkedList as a HashMap key

```java
LinkedList<String> key = new LinkedList<>(List.of("A", "B"));
Map<List<String>, Integer> map = new HashMap<>();
map.put(key, 100);

System.out.println(map.get(key));   // 100 — works

key.add("C");                        // mutate the key AFTER inserting

System.out.println(map.get(key));   // null !! — hashCode changed, entry orphaned
```

**Why:** the map placed the entry in a bucket based on `hashCode` at insertion time (`["A", "B"]`). After the mutation, `key.hashCode()` computes to a *different* bucket. The entry still exists in the map — you just can't find it.

**Rule:** never mutate an object after using it as a key in a hash-based collection.

---

#### Quick summary — where equals/hashCode show up

| Scenario                                          | Which method matters                        |
| ------------------------------------------------- | ------------------------------------------- |
| `list1.equals(list2)`                             | List-level `equals` + each element's `equals` |
| `list.contains(x)` / `indexOf(x)` / `remove(x)`   | Each element's `equals`                     |
| Storing the list in `HashSet` / `HashMap`         | List-level `hashCode` + `equals`            |
| Comparing `LinkedList` vs `ArrayList` contents    | Cross-implementation via `List` — works!    |
| Using a custom class as an element                | **You must override `equals` (and `hashCode`)** |

---

### 6.6 Self-check (Phase 6)

1. **What is `modCount` and why does it exist?**
2. **What's the difference between `Iterator.remove()` and `list.remove(i)` on a LinkedList?**
3. **Why is `ConcurrentModificationException` misleadingly named?**
4. **When would you pick `LinkedBlockingQueue` over `ConcurrentLinkedQueue`?**
5. **What's the difference between `poll()` and `remove()` on a Queue?**
6. **In LRU Cache, why must the Node store the key (not just the value)?**
7. **Why doesn't a singly linked list work for LRU?**
8. **Can a `LinkedList` be `.equals()` to an `ArrayList`? Why or why not?**
9. **If I have `LinkedList<Employee>` and Employee doesn't override `equals`, what does `list.contains(new Employee(1, "Alice"))` return?**
10. **Why is it dangerous to use a mutable list as a HashMap key?**

#### Answers

1. **`modCount`** is a counter on `AbstractList` (and other collections) that's incremented on every structural modification. Iterators snapshot it when created and check on every `next()`. If it changed, they throw `CME`. Purpose: **fail-fast detection** of concurrent or interleaved modifications, catching bugs early instead of silently returning wrong results.

2. **`Iterator.remove()` is O(1)** on LinkedList — the iterator already holds a direct reference to the node, so it can `unlink` it in constant time. **`list.remove(i)` is O(n)** because it must walk from head to index `i` first. Same effect on the list, dramatically different cost.

3. **Because it's raised even in single-threaded code.** The name suggests it's a threading issue, but the most common cause is modifying a collection through the collection's own API while iterating through it (e.g., inside a for-each loop). No concurrency involved — just a single thread contradicting itself. Better name would be "InterleavedModificationException" but we're stuck with the historical one.

4. **When consumers need to *wait* for an element** (blocking semantics), or when you need a **bounded** queue for backpressure (`put()` blocks if full). `ConcurrentLinkedQueue` is lock-free and non-blocking — `poll()` returns `null` immediately if empty, so the consumer has to poll in a loop (busy-wait). Use blocking queues for classic producer-consumer; use lock-free for high-throughput lossy scenarios where the consumer can retry on its own schedule.

5. **Behavior on empty.** `remove()` throws `NoSuchElementException`; `poll()` returns `null`. Choose based on whether emptiness is exceptional (use `remove`) or normal (use `poll`).

6. **Because when we evict the LRU, we need to remove the entry from the HashMap too.** Without the key on the node, we'd have to scan the map to find which key mapped to this node — O(n). Storing the key on the node makes eviction O(1) end-to-end.

7. **Because we need to remove an arbitrary node (the current one being accessed, or the LRU tail) in O(1).** With a singly linked list, removing a known node requires walking from head to find its predecessor — O(n). With `prev` pointers, we can unlink in O(1). Since every `get` and `put` involves an unlink, the whole cache would degrade to O(n) without doubly linked.

8. **Yes!** Both inherit `equals()` from `AbstractList`, which checks `instanceof List` (not the concrete class) and compares element-wise. If sizes are equal and each pair of elements is `.equals()`, the two lists are considered equal — regardless of whether they're LinkedList, ArrayList, or any other `List` implementation. This is by design in the `List` interface contract.

9. **`false`.** `Employee` inherits `Object.equals`, which is reference equality. The newly-created `Employee(1, "Alice")` has a different memory address than the one inside the list, so `.equals()` returns `false` for every element during the walk → `contains` returns `false`. This is one of the most common "why doesn't my `contains` work?" bugs in Java. Fix: override `equals` and `hashCode` on `Employee`.

10. **Because the map stores the entry in a bucket determined by `hashCode()` at insertion time.** If you mutate the key after insertion, its `hashCode` changes → the map looks in a different bucket on lookup → returns `null`. The entry still exists in the map, just orphaned. This is a silent corruption bug: no exception, just wrong results. **Rule:** never mutate an object after using it as a hash-collection key.

---

### 6.7 API traps & practical gotchas

> Real-world bugs that show up in code review, not just interviews. Each one has bitten someone in production.

---

#### 1. `subList()` returns a VIEW, not a copy

`list.subList(from, to)` doesn't copy the elements — it returns a **live window** backed by the original list.

##### Change via the sub-view mutates the parent

```java
List<Integer> list = new LinkedList<>(List.of(1, 2, 3, 4, 5));
List<Integer> sub = list.subList(1, 4);   // window over [2, 3, 4]
sub.set(0, 99);
System.out.println(list);   // [1, 99, 3, 4, 5]  ← parent modified!
System.out.println(sub);    // [99, 3, 4]
```

This can be intentional (efficient slice manipulation) or a nasty surprise if you thought you were working with a copy.

##### Structurally modifying the PARENT invalidates the sub-view

```java
List<Integer> list = new LinkedList<>(List.of(1, 2, 3, 4, 5));
List<Integer> sub = list.subList(1, 4);
list.add(6);            // structural change to parent
System.out.println(sub.size());   // ConcurrentModificationException !!
```

**Rule:** once you create a `subList`, either only touch the sub-view OR only touch the parent — mixing them breaks things.

##### If you want a real copy, do this

```java
List<Integer> copy = new LinkedList<>(list.subList(1, 4));
// or
List<Integer> copy = new ArrayList<>(list.subList(1, 4));
```

Wrapping in a new collection materializes the copy. Now the sub-view relationship is broken.

##### Interview trap

> *"What's the complexity of `list.subList(0, list.size())`?"*
> **O(1).** It's just constructing a view object — no copying. Some people mis-answer with O(n) thinking it copies.

---

#### 2. `toArray()` variants and the pre-sized array trap

Three forms:

```java
Object[] a = list.toArray();                    // Object[], loses generic type
String[] b = list.toArray(new String[0]);       // typed, preferred idiom (Java 6+)
String[] c = list.toArray(String[]::new);       // constructor reference (Java 11+)
```

##### The "why `new String[0]`?" quiz

Instinct says `new String[list.size()]` avoids reallocation. **Wrong on modern JVMs.**

- **`new String[0]`** — modern JITs recognize the "zero-length array" idiom and often reuse a cached empty array. Also, the pre-sized array must be **zeroed out** before being handed to `toArray`, then partly overwritten — that zeroing is wasted work.
- **`new String[list.size()]`** — no reallocation, but the JVM zeros the entire array before `toArray` fills it. On big lists, the zeroing outweighs any allocation savings.

Josh Bloch (author of *Effective Java*) and Aleksey Shipilev (JVM performance guru) both recommend `new String[0]`. Counter-intuitive, but measured.

##### Array covariance gotcha

```java
List<Object> list = new LinkedList<>();
list.add("hello");
list.add(42);   // Integer, not String

String[] arr = list.toArray(new String[0]);
// ArrayStoreException at runtime — 42 isn't a String
```

`toArray(T[])` does an unchecked cast internally. If the list contains any element not assignable to `T`, you get `ArrayStoreException` — but only at runtime, when the toxic element is copied.

---

#### 3. Stream API + LinkedList — the parallel stream trap

**Rule: NEVER use `parallelStream()` on a `LinkedList`.**

##### Why

`parallelStream()` works by **splitting the source into chunks** (via `Spliterator`) and processing each chunk on a different thread. Splitting requires efficient random access to divide the source in half.

- **`ArrayList.spliterator()`** — splits cheaply. Backed by an array; `split()` just picks a midpoint index. Result: fast, effective parallelism.
- **`LinkedList.spliterator()`** — has to walk the list to split. Each split is O(n). Overhead often exceeds the parallelism gain. And because splits are unbalanced (later halves are cheaper to traverse from a walk position), threads finish at different times → poor utilization.

##### The anti-pattern

```java
LinkedList<Integer> list = new LinkedList<>(...);
long sum = list.parallelStream().mapToInt(Integer::intValue).sum();
// Slower than: list.stream().mapToInt(...).sum()  (sequential!)
// AND slower than: new ArrayList<>(list).parallelStream()...
```

If you *must* process a LinkedList in parallel, copy to `ArrayList` first:

```java
long sum = new ArrayList<>(list).parallelStream()...;
```

##### Even sequential streams are slower on LinkedList

Because of the same cache-miss issue from Phase 5. Sequential `.stream()` on LinkedList works correctly but iterates slower than `ArrayList` would. If stream performance matters, don't use `LinkedList`.

---

#### 4. Bulk operations complexity

Full complexity table for `LinkedList`:

| Operation                      | Complexity                              | Notes                                                    |
| ------------------------------ | --------------------------------------- | -------------------------------------------------------- |
| `addAll(Collection c)`         | O(n + m)                                | `n` = list size, `m` = c size. Appends at end.           |
| `addAll(int i, Collection c)`  | O(i + m)                                | Walk to i (O(i)), then splice in c (O(m)).               |
| `removeAll(Collection c)`      | **O(n × cost_of_c.contains)**           | ⚠️ Depends on `c`'s type                                 |
| `retainAll(Collection c)`      | Same as removeAll                       | ⚠️ Same trap                                             |
| `containsAll(Collection c)`    | O(m × n) if list scan; O(m) if c is small | For each element in c, do `list.contains(e)`             |
| `clear()`                      | O(n)                                    | Nulls out every node's references to help GC             |
| `iterator()` / `listIterator()`| O(1)                                    | Just constructs the iterator                             |

##### The `removeAll` trap — interview classic

```java
LinkedList<Integer> list = new LinkedList<>(...);   // 100,000 elements
List<Integer> toRemove = new ArrayList<>(...);      // 10,000 elements

list.removeAll(toRemove);
// SLOW — for each of 100k list elements, list.remove impl calls toRemove.contains(e)
// which is O(m) on an ArrayList → total O(n × m) = 1 billion operations
```

Fix:

```java
list.removeAll(new HashSet<>(toRemove));
// FAST — HashSet.contains is O(1) → total O(n) = 100k operations
```

**One-liner: whenever you call `removeAll` or `retainAll` with a large collection, wrap it in a `HashSet` first.** 100× to 10,000× speedup for free.

##### Why `clear()` is O(n), not O(1)

You might expect `clear()` to just do `head = tail = null; size = 0;`. But `LinkedList.clear()` walks the list and nulls out every node's `next`, `prev`, and `item` references. This helps garbage collection by breaking the reference chain — without it, if any external code held a reference to *any* node, the whole list would stay alive in memory. Small performance hit, big correctness win.

---

#### Interview quick-fire on this section

1. **"Is `list.subList(0, 5)` a copy or a view?"** → View. Modifying either affects both.
2. **"`toArray(new String[0])` vs `toArray(new String[list.size()])` — which is faster?"** → The zero-length version, counter-intuitively. Zeroing the pre-sized array wastes cycles.
3. **"Why never use `parallelStream()` on a LinkedList?"** → `Spliterator` can't split efficiently without random access. Overhead exceeds any parallelism gain.
4. **"How would you speed up `list.removeAll(otherList)`?"** → Wrap `otherList` in a `HashSet` first. Cuts complexity from O(n × m) to O(n).
5. **"Why does `LinkedList.clear()` bother nulling every node's pointers?"** → To help GC. Otherwise any external reference to a node keeps the entire list alive.

---

## Phase 7 — Advanced Practice Problems

> Extensions of the 5 techniques from Phase 4. None introduce truly new ideas — they combine existing techniques in more elaborate ways. If you can solve all 9, you've mastered linked list interviews.

Standard node (same as Phase 4):

```java
class ListNode {
    int val;
    ListNode next;
    ListNode(int val) { this.val = val; }
}
```

Overview of technique combinations:

| # | Problem                       | Techniques used                                | Difficulty |
| - | ----------------------------- | ---------------------------------------------- | ---------- |
| 1 | Palindrome check              | slow/fast (middle) + reverse                   | Easy-Med   |
| 2 | Reorder list                  | slow/fast + reverse + merge                    | Medium     |
| 3 | Merge K sorted lists          | dummy + priority queue (min-heap)              | Medium     |
| 4 | Reverse in K-groups           | reverse + dummy + counter                      | Hard       |
| 5 | Sort a linked list            | slow/fast (split) + merge sort recursion       | Medium     |
| 6 | Add two numbers               | dummy + carry propagation                      | Medium     |
| 7 | Copy list with random pointer | interleaving trick (or HashMap)                | Medium     |
| 8 | Rotate list by K places       | length count + find pivot + rewire             | Medium     |
| 9 | Swap nodes in pairs           | dummy + local rewiring (or recursion)          | Medium     |

---

### 7.1 Palindrome check (LC 234)

**Problem:** Is the list a palindrome? `1 → 2 → 2 → 1` → true. `1 → 2 → 3` → false.

#### Insight

Three-step combo:
1. **Find the middle** (slow/fast).
2. **Reverse the second half** (in-place).
3. **Compare first half vs reversed second half.**

O(n) time, **O(1) space**. Naive (copy to array, check) is O(n) space — this is a classic "improve space complexity" problem.

#### Code

```java
public boolean isPalindrome(ListNode head) {
    if (head == null || head.next == null) return true;

    // 1. Find first middle
    ListNode slow = head, fast = head;
    while (fast.next != null && fast.next.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }

    // 2. Reverse second half (starting at slow.next)
    ListNode prev = null, curr = slow.next;
    while (curr != null) {
        ListNode next = curr.next;
        curr.next = prev;
        prev = curr;
        curr = next;
    }

    // 3. Compare
    ListNode p1 = head, p2 = prev;
    while (p2 != null) {
        if (p1.val != p2.val) return false;
        p1 = p1.next;
        p2 = p2.next;
    }
    return true;
}
```

#### Trap

Use `fast.next != null && fast.next.next != null` to get the **first middle** — the comparison logic is cleaner for even-length lists this way.

#### Interview bonus

If asked *"restore the list to its original state after checking?"* — yes, just reverse the second half back. No asymptotic cost.

---

### 7.2 Reorder list (LC 143)

**Problem:** `1 → 2 → 3 → 4 → 5` → `1 → 5 → 2 → 4 → 3`. Weave the second half (reversed) into the first half.

#### Insight

Direct chain of three Phase 4 techniques:
1. Find middle.
2. Reverse second half.
3. Merge the two halves alternately (not by value — by position).

#### Code

```java
public void reorderList(ListNode head) {
    if (head == null || head.next == null) return;

    // 1. Find first middle
    ListNode slow = head, fast = head;
    while (fast.next != null && fast.next.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }

    // 2. Reverse second half
    ListNode second = slow.next;
    slow.next = null;   // sever the two halves
    ListNode prev = null;
    while (second != null) {
        ListNode next = second.next;
        second.next = prev;
        prev = second;
        second = next;
    }

    // 3. Merge alternately: first list + reversed second list
    ListNode p1 = head, p2 = prev;
    while (p2 != null) {
        ListNode t1 = p1.next, t2 = p2.next;
        p1.next = p2;
        p2.next = t1;
        p1 = t1;
        p2 = t2;
    }
}
```

#### Trap

**Sever the two halves** with `slow.next = null` before reversing. If you don't, the "reversed second half" still connects back to the first half → infinite loop during merge.

O(n) time, O(1) space.

---

### 7.3 Merge K sorted lists (LC 23)

**Problem:** Given `k` sorted linked lists, merge into one sorted list.

#### Insight — the priority queue approach

Maintain a **min-heap** of the current head node from each list. Repeatedly pop the smallest, append it to the output, and push its `.next` (if not null).

- N = total number of nodes across all lists.
- Heap holds at most k elements. Each push/pop is O(log k).
- Total: **O(N log k) time**, O(k) space.

Compare to naive "merge them all together one by one" — that's O(Nk), much worse for large k.

#### Code

```java
public ListNode mergeKLists(ListNode[] lists) {
    PriorityQueue<ListNode> heap = new PriorityQueue<>((a, b) -> a.val - b.val);

    for (ListNode head : lists) {
        if (head != null) heap.offer(head);
    }

    ListNode dummy = new ListNode(0), tail = dummy;
    while (!heap.isEmpty()) {
        ListNode node = heap.poll();
        tail.next = node;
        tail = node;
        if (node.next != null) heap.offer(node.next);
    }
    return dummy.next;
}
```

#### Alternative — divide and conquer

Merge pairs of lists recursively, halving the count each round: `k → k/2 → k/4 → ... → 1`. Also O(N log k), no priority queue needed. Slightly harder to code but avoids the heap's constant overhead.

#### Trap

Use `Integer.compare(a.val, b.val)` (or `(a, b) -> a.val - b.val` only if values are guaranteed non-negative and can't overflow). Subtracting `int`s can overflow silently.

#### Interview soundbite

> *"Two solutions, both O(N log k): min-heap for elegance, divide-and-conquer for lower constant overhead. Naive pairwise merging is O(Nk) — avoid it."*

---

### 7.4 Reverse in K-groups (LC 25)

**Problem:** Reverse every K consecutive nodes. If the last group has fewer than K, leave it as-is.
`1 → 2 → 3 → 4 → 5`, k=2 → `2 → 1 → 4 → 3 → 5`
`1 → 2 → 3 → 4 → 5`, k=3 → `3 → 2 → 1 → 4 → 5`

#### Insight

The **hardest** of the 9 — pointer choreography is tricky. Two-part approach:
1. **Check** if there are K nodes ahead. If not → return as-is.
2. **Reverse** the first K nodes. Then **recurse** on the (K+1)th onward. Wire the reversed group's tail (which is the original first node) to the recursive result.

#### Code

```java
public ListNode reverseKGroup(ListNode head, int k) {
    // 1. Check if k nodes exist ahead
    ListNode check = head;
    for (int i = 0; i < k; i++) {
        if (check == null) return head;   // fewer than k → leave as-is
        check = check.next;
    }

    // 2. Reverse first k nodes
    ListNode prev = null, curr = head;
    for (int i = 0; i < k; i++) {
        ListNode next = curr.next;
        curr.next = prev;
        prev = curr;
        curr = next;
    }

    // 3. Recurse: original head is now the tail of this group
    head.next = reverseKGroup(curr, k);
    return prev;   // new head of this group
}
```

#### Trap

**Two off-by-one hazards.** The check loop runs `k` times — after it, `check` is at the (k+1)th node (or null). The reverse loop also runs `k` times — after it, `prev` is the new group head, `curr` is the (k+1)th node.

**Iterative version exists** (using a dummy node) and gets O(1) space, but the recursive version is what most interviewers accept. Mention the tradeoff.

#### Complexity

O(n) time. O(n/k) recursive stack space (or O(1) iterative).

---

### 7.5 Sort a linked list (LC 148)

**Problem:** Sort a linked list in O(n log n) time.

#### Why merge sort, not quicksort

- Quicksort needs random access for good partitioning → O(n) per access → bad.
- Merge sort: split is O(n) via slow/fast, merge is O(n) via dummy node, recursion depth O(log n).
- Total: O(n log n) time, O(log n) stack space. Bottom-up gives O(1) space but is much harder.

#### Insight

Standard merge sort, adapted for linked lists:
1. Base case: 0 or 1 nodes → return.
2. Split in half using slow/fast.
3. Recursively sort each half.
4. Merge (Phase 4 problem 4.4).

#### Code

```java
public ListNode sortList(ListNode head) {
    if (head == null || head.next == null) return head;

    // 1. Split in half
    ListNode slow = head, fast = head, prev = null;
    while (fast != null && fast.next != null) {
        prev = slow;
        slow = slow.next;
        fast = fast.next.next;
    }
    prev.next = null;   // ← critical: sever the two halves

    // 2. Recursively sort
    ListNode left = sortList(head);
    ListNode right = sortList(slow);

    // 3. Merge
    return merge(left, right);
}

private ListNode merge(ListNode a, ListNode b) {
    ListNode dummy = new ListNode(0), tail = dummy;
    while (a != null && b != null) {
        if (a.val <= b.val) { tail.next = a; a = a.next; }
        else                { tail.next = b; b = b.next; }
        tail = tail.next;
    }
    tail.next = (a != null) ? a : b;
    return dummy.next;
}
```

#### Trap

**`prev.next = null`** — you MUST sever the two halves before recursing. Without it, the "left half" still points into the right → infinite recursion. #1 bug on this problem.

#### Interview soundbite

> *"Merge sort is the natural fit for linked lists: split is O(n) via slow/fast, merge is O(n) via dummy node, and unlike arrays we pay no memory penalty for the merged output because we rewire in place."*

---

### 7.6 Add two numbers (LC 2)

**Problem:** Two lists represent non-negative numbers in **reverse** digit order.
`2 → 4 → 3` + `5 → 6 → 4` = `7 → 0 → 8` (342 + 465 = 807).

#### Insight

Simulate elementary-school addition with **carry propagation** and a **dummy node** for the result.

#### Code

```java
public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
    ListNode dummy = new ListNode(0), tail = dummy;
    int carry = 0;
    while (l1 != null || l2 != null || carry != 0) {
        int sum = carry;
        if (l1 != null) { sum += l1.val; l1 = l1.next; }
        if (l2 != null) { sum += l2.val; l2 = l2.next; }
        carry = sum / 10;
        tail.next = new ListNode(sum % 10);
        tail = tail.next;
    }
    return dummy.next;
}
```

#### Trap

The loop condition **must include `carry != 0`**. Otherwise `9 → 9` + `1` returns `0` instead of `0 → 1`. Very common bug.

#### Complexity

O(max(n, m)) time, O(max(n, m)) space (for the output list).

#### Follow-up

*"What if the digits are in forward order (most significant first)?"* — reverse both lists, apply this, reverse the result. Or use a stack.

---

### 7.7 Copy list with random pointer (LC 138)

**Problem:** Each node has a `next` pointer AND a `random` pointer (to any node in the list, or `null`). Return a **deep copy**.

Node structure:
```java
class Node {
    int val;
    Node next;
    Node random;
    Node(int val) { this.val = val; }
}
```

#### Solution 1 — HashMap (easy, O(n) space)

Two passes: first create all nodes, then wire pointers.

```java
public Node copyRandomList(Node head) {
    if (head == null) return null;
    Map<Node, Node> map = new HashMap<>();
    for (Node cur = head; cur != null; cur = cur.next) {
        map.put(cur, new Node(cur.val));
    }
    for (Node cur = head; cur != null; cur = cur.next) {
        map.get(cur).next   = map.get(cur.next);
        map.get(cur).random = map.get(cur.random);
    }
    return map.get(head);
}
```

O(n) time, O(n) space. Works, but the interviewer wants better.

#### Solution 2 — Interleaving trick (elegant, O(1) extra space)

**Insight:** stitch each new node into the original list, right after its original. Then the "map old → new" is implicit: for any original node `X`, `X.next` is its clone.

Three passes:
1. **Interleave clones:** `A → B → C` becomes `A → A' → B → B' → C → C'`.
2. **Wire clones' random pointers:** `A'.random = A.random.next` (because `A.random.next` = clone of `A.random`).
3. **Un-interleave** to separate the original and cloned lists.

```java
public Node copyRandomList(Node head) {
    if (head == null) return null;

    // Pass 1: interleave clones
    for (Node cur = head; cur != null; cur = cur.next.next) {
        Node clone = new Node(cur.val);
        clone.next = cur.next;
        cur.next = clone;
    }

    // Pass 2: assign random pointers on clones
    for (Node cur = head; cur != null; cur = cur.next.next) {
        if (cur.random != null) {
            cur.next.random = cur.random.next;
        }
    }

    // Pass 3: un-interleave
    Node dummy = new Node(0);
    Node cloneTail = dummy;
    for (Node cur = head; cur != null; cur = cur.next) {
        cloneTail.next = cur.next;
        cloneTail = cloneTail.next;
        cur.next = cur.next.next;
    }
    return dummy.next;
}
```

O(n) time, O(1) extra space (excluding output).

#### Trap

**Pass 2 must run before pass 3.** Pass 2 depends on the interleaved structure — once un-interleaved, `cur.random.next` no longer points to the clone.

#### Interview soundbite

> *"The HashMap version uses O(n) space. The interleaving trick uses the list itself as the map — every node's `.next` implicitly maps original to clone. Three passes, O(1) extra space."*

Interviewers *love* this answer.

---

### 7.8 Rotate list by K places (LC 61)

**Problem:** `1 → 2 → 3 → 4 → 5`, k=2 → `4 → 5 → 1 → 2 → 3` (rotate right by k).

#### Insight

Rotating right by k means:
- The new head is at position `n - k` (from the original head).
- The old tail connects to the old head.

If `k > n`, we do `k % n` rotations.

Steps:
1. Count length `n` and find the old tail.
2. Close the loop: `oldTail.next = head`.
3. Walk `n - (k % n) - 1` steps from head to find the new tail.
4. Break the loop: `newHead = newTail.next; newTail.next = null;`.

#### Code

```java
public ListNode rotateRight(ListNode head, int k) {
    if (head == null || head.next == null || k == 0) return head;

    // 1. Count length and find old tail
    int n = 1;
    ListNode oldTail = head;
    while (oldTail.next != null) {
        oldTail = oldTail.next;
        n++;
    }

    k = k % n;
    if (k == 0) return head;

    // 2. Close the loop
    oldTail.next = head;

    // 3. Find new tail: (n - k - 1) steps from head
    ListNode newTail = head;
    for (int i = 0; i < n - k - 1; i++) newTail = newTail.next;

    // 4. Break the loop
    ListNode newHead = newTail.next;
    newTail.next = null;
    return newHead;
}
```

#### Trap

**Modulo K by N.** If K > N, you're doing useless full rotations. Without `k %= n`, you might walk past the tail forever.

**Off-by-one on `newTail` position.** Walk `n - k - 1` steps (not `n - k`) — you want the node *before* the new head.

#### Complexity

O(n) time, O(1) space.

---

### 7.9 Swap nodes in pairs (LC 24)

**Problem:** `1 → 2 → 3 → 4` → `2 → 1 → 4 → 3`. Swap every two adjacent nodes.

#### Insight — iterative with dummy

Standard pointer choreography. Dummy node handles the head-swap edge case uniformly.

For each pair `[a, b]` where `prev` is the node before the pair:
- Before: `prev → a → b → rest`
- After:  `prev → b → a → rest`

Three rewires: `prev.next = b`, `a.next = b.next`, `b.next = a`.

#### Code (iterative)

```java
public ListNode swapPairs(ListNode head) {
    ListNode dummy = new ListNode(0);
    dummy.next = head;
    ListNode prev = dummy;

    while (prev.next != null && prev.next.next != null) {
        ListNode a = prev.next;
        ListNode b = a.next;

        a.next = b.next;
        b.next = a;
        prev.next = b;

        prev = a;   // advance to node after the swapped pair
    }
    return dummy.next;
}
```

#### Code (recursive) — elegant one-liner style

```java
public ListNode swapPairs(ListNode head) {
    if (head == null || head.next == null) return head;
    ListNode next = head.next;
    head.next = swapPairs(next.next);
    next.next = head;
    return next;
}
```

Iterative: O(n) time, O(1) space. Recursive: O(n) time, O(n) stack space.

#### Trap

**Order of the three rewires matters.** Do `prev.next = b` FIRST, or save `a` and `b` in locals first. Otherwise you lose references.

**Loop condition needs both `prev.next != null` AND `prev.next.next != null`** — otherwise you swap a pair that doesn't exist and NPE.

---

### 7.10 Self-check (Phase 7)

1. **Which two Phase 4 techniques does the palindrome problem combine, and why is O(1) space possible?**
2. **In "reorder list" and "sort list," why must you `slow.next = null` (or `prev.next = null`) before recursing / reversing?**
3. **What's the complexity of merging K sorted lists with a min-heap, and why is naive pairwise merging worse?**
4. **In "add two numbers," what's the loop condition, and what bug happens if you omit `carry != 0`?**
5. **In "copy list with random pointer," why must pass 2 (assigning random pointers) run BEFORE pass 3 (un-interleaving)?**
6. **In "rotate list," what happens if you forget `k %= n`?**
7. **When would you prefer the iterative "swap pairs" over the recursive version?**

#### Answers

1. **Find middle** (slow/fast pointers) + **reverse** (in-place pointer flipping). O(1) space is possible because both techniques are O(1) — we mutate the list in place instead of copying values to an array. The naive "copy to array and check" would be O(n) space.

2. **To sever the list into two independent halves.** Without severing, the "first half" or "reversed second half" still points into the other half, causing infinite loops in the merge/reverse step or infinite recursion. This is the #1 bug on both problems.

3. **O(N log k)** with a min-heap (N = total nodes across all lists, k = number of lists). Each of N pops/pushes is O(log k). Naive pairwise merging — merge list 1 with list 2, then result with list 3, etc. — is **O(Nk)** because early elements get re-copied on every merge. Divide-and-conquer is also O(N log k) and avoids the heap's constant overhead.

4. **`while (l1 != null || l2 != null || carry != 0)`.** Omitting `carry != 0` fails on inputs like `9 → 9 → 9` + `1` — the result should be `0 → 0 → 0 → 1`, but without the carry check the loop exits after processing the last matched digit and the final carry is dropped. Result becomes `0 → 0 → 0` instead. Very common bug.

5. **Because pass 2 depends on the interleaved structure.** In pass 2 we compute `clone.random = original.random.next`, which relies on the fact that every original node is followed by its clone in the interleaved list. Once pass 3 un-interleaves them, `original.random.next` no longer points to the clone — the mapping is destroyed. Order-sensitive.

6. **You might walk past the end of the list forever** (or at least do wasted work). If `k = 5,000,000` and `n = 5`, you don't need 5 million rotations — you need `5,000,000 % 5 = 0` rotations (no-op). Without the modulo, the loop `for (int i = 0; i < n - k - 1; i++)` uses a huge negative bound or wraps around, causing incorrect behavior.

7. **When stack space matters** (very long lists, deep recursion → risk of `StackOverflowError`). Iterative uses O(1) space; recursive uses O(n) stack. Interviewers may ask *"convert this to iterative"* — that's a signal that stack space is the concern.

---

### 7.11 The complete "linked list interview" arsenal

After Phase 7, you can solve any linked list problem an interviewer throws at you by asking: **which combination of the 5 techniques does this need?**

- **Palindrome check** = middle + reverse
- **Reorder list** = middle + reverse + merge (positional)
- **Merge K sorted lists** = dummy + priority queue
- **Reverse in K-groups** = reverse + recursion / dummy
- **Sort a linked list** = split (slow/fast) + merge + recursion
- **Add two numbers** = dummy + carry state
- **Copy list with random pointer** = interleaving trick
- **Rotate list** = length count + rewire (no fancy trick)
- **Swap pairs** = dummy + local rewiring
- **LRU cache** = doubly linked + HashMap (from Phase 6)

**If you can't decompose a new linked list problem into these primitives, it's probably not really a linked list problem.**

---

## Phase 8 — Gap-Fill Practice Problems

> 10 more problems to reach truly exhaustive LeetCode coverage. Each is smaller than Phase 7 problems but introduces a pattern not seen elsewhere in the file.

New patterns introduced:
- **Two-dummy-nodes** (partitioning into two streams) — problems 8.4, 8.6
- **Value-copy delete** (workaround for missing predecessor) — problem 8.3
- **Head-insertion reversal** (elegant for partial reversal) — problem 8.5
- **Reservoir sampling** (uniform random selection in one pass) — problem 8.8
- **DFS with splicing** (multi-level linked list flattening) — problem 8.7

---

### 8.1 Remove duplicates from sorted list (LC 83)

**Problem:** `1 → 1 → 2 → 3 → 3` → `1 → 2 → 3`. Keep each distinct value once.

#### Insight

Walk once. If `curr.val == curr.next.val`, skip `curr.next`. Only advance `curr` when values differ.

#### Code

```java
public ListNode deleteDuplicates(ListNode head) {
    ListNode curr = head;
    while (curr != null && curr.next != null) {
        if (curr.val == curr.next.val) {
            curr.next = curr.next.next;   // skip duplicate; don't advance curr
        } else {
            curr = curr.next;
        }
    }
    return head;
}
```

#### Trap

**Don't advance `curr` after skipping** — the new `curr.next` might also be a duplicate of `curr`. Common bug: skipping only one of a run of 3+ duplicates.

O(n) time, O(1) space.

---

### 8.2 Remove duplicates from sorted list II (LC 82)

**Problem:** Remove **all** nodes with duplicate values, keeping only distinct values.
`1 → 2 → 3 → 3 → 4 → 4 → 5` → `1 → 2 → 5`

#### Insight

Trickier than 8.1 — you must delete *every* node with a duplicate. Use a **dummy node** because the head itself might be deleted. Look ahead one step to detect duplicates, then inner-loop to skip all of them.

#### Code

```java
public ListNode deleteDuplicates(ListNode head) {
    ListNode dummy = new ListNode(0);
    dummy.next = head;
    ListNode prev = dummy;
    while (head != null) {
        if (head.next != null && head.val == head.next.val) {
            // Skip ALL nodes with this value
            while (head.next != null && head.val == head.next.val) {
                head = head.next;
            }
            prev.next = head.next;   // link prev past the last duplicate
        } else {
            prev = prev.next;
        }
        head = head.next;
    }
    return dummy.next;
}
```

#### Trap

**Dummy node is essential.** The head itself may be a duplicate (e.g., `1 → 1 → 2` should return `2`), so `prev` must exist even before the first real node.

O(n) time, O(1) space.

---

### 8.3 Delete node given only that node (LC 237)

**Problem:** You're given a reference to a node to delete (guaranteed not the tail). You do **not** have access to head.

#### Insight — the value-copy trick

You can't unlink `node` normally because you can't reach its predecessor. **Workaround:** copy the next node's value into this node, then skip the next node. Effectively you're deleting `node.next`, but the effect is that `node`'s original value disappears from the list.

#### Code

```java
public void deleteNode(ListNode node) {
    node.val = node.next.val;
    node.next = node.next.next;
}
```

O(1) time.

#### Why this problem exists

It exposes a **misconception**: people think of linked list deletion as "unlink this specific node." Actually, deletion is about removing a *value* from the sequence — and you can do that by modifying the node's data instead of its pointers. Only works because the problem guarantees "not the tail" (tail has no `next` to copy from).

---

### 8.4 Partition list (LC 86)

**Problem:** Given a list and value `x`, rearrange so all nodes with `val < x` come **before** all nodes with `val >= x`. Preserve relative order within each group.
`1 → 4 → 3 → 2 → 5 → 2`, x=3 → `1 → 2 → 2 → 4 → 3 → 5`

#### Insight — the two-dummy-nodes pattern

Build **two separate lists** as you walk:
- `less` list — nodes with `val < x`
- `ge` list — nodes with `val >= x`

Then concatenate. Two dummy nodes give both lists uniform "always has a predecessor" semantics.

#### Code

```java
public ListNode partition(ListNode head, int x) {
    ListNode lessDummy = new ListNode(0), lessTail = lessDummy;
    ListNode geDummy   = new ListNode(0), geTail   = geDummy;

    while (head != null) {
        if (head.val < x) { lessTail.next = head; lessTail = head; }
        else              { geTail.next   = head; geTail   = head; }
        head = head.next;
    }

    geTail.next = null;               // critical: terminate ge list
    lessTail.next = geDummy.next;     // concatenate
    return lessDummy.next;
}
```

#### Trap

**`geTail.next = null`** — otherwise the last "ge" node might still point at a "less" node from the original list, creating a cycle. Every "two-dummy-nodes" solution needs this termination step.

O(n) time, O(1) space.

---

### 8.5 Reverse linked list II (LC 92)

**Problem:** Reverse the nodes from position `left` to position `right` (1-indexed). Everything outside stays the same.
`1 → 2 → 3 → 4 → 5`, left=2, right=4 → `1 → 4 → 3 → 2 → 5`

#### Insight — the head-insertion reversal

Walk `prev` to the node **before** position `left`. Then repeatedly take the node AFTER `curr` and insert it right after `prev`. Each iteration moves one node from "after curr" to "right after prev" — after `right - left` iterations, the middle segment is reversed.

Visual (left=2, right=4):
```
 Before:  1 → 2 → 3 → 4 → 5
          prev curr

 Iter 1:  1 → 3 → 2 → 4 → 5    (moved 3 to after prev)
              curr

 Iter 2:  1 → 4 → 3 → 2 → 5    (moved 4 to after prev)
                  curr
```

#### Code

```java
public ListNode reverseBetween(ListNode head, int left, int right) {
    if (head == null || left == right) return head;
    ListNode dummy = new ListNode(0);
    dummy.next = head;
    ListNode prev = dummy;

    // 1. Walk prev to node BEFORE position left
    for (int i = 1; i < left; i++) prev = prev.next;

    // 2. Head-insertion: move curr.next to just after prev, right-left times
    ListNode curr = prev.next;
    for (int i = 0; i < right - left; i++) {
        ListNode moved = curr.next;
        curr.next = moved.next;
        moved.next = prev.next;
        prev.next = moved;
    }
    return dummy.next;
}
```

#### Trap

**Dummy node handles `left == 1`** (reversing from the head). Without it, you'd need a special case for "prev doesn't exist."

O(n) time (one walk to `left`, then O(right - left) rewires), O(1) space.

---

### 8.6 Odd Even Linked List (LC 328)

**Problem:** Group all odd-INDEXED nodes together, followed by even-indexed. Preserve order within groups. 1-indexed.
`1 → 2 → 3 → 4 → 5` → `1 → 3 → 5 → 2 → 4`

*(Note: this is about position, not value!)*

#### Insight — two pointer streams, weave in place

Maintain two pointers: `odd` (walks odd-indexed) and `even` (walks even-indexed). Each iteration, jump each pointer forward by 2. At the end, attach the even chain after the odd chain.

#### Code

```java
public ListNode oddEvenList(ListNode head) {
    if (head == null) return null;
    ListNode odd = head, even = head.next, evenHead = even;
    while (even != null && even.next != null) {
        odd.next = even.next;
        odd = odd.next;
        even.next = odd.next;
        even = even.next;
    }
    odd.next = evenHead;   // attach even chain after odd chain
    return head;
}
```

#### Trap

Loop condition must check **both** `even != null` AND `even.next != null` — otherwise `odd.next = even.next` NPEs at the end.

O(n) time, O(1) space. Same family as partition list (8.4), but interleaved in place instead of using dummy nodes.

---

### 8.7 Flatten multilevel doubly linked list (LC 430)

**Problem:** Each node has `prev`, `next`, AND `child` (which points to another doubly linked list, possibly with its own children). Flatten into a single-level doubly linked list, depth-first.

Node structure:
```java
class Node {
    int val;
    Node prev, next, child;
}
```

Example:
```
 1 - 2 - 3 - 4 - 5 - 6
         |
         7 - 8 - 9 - 10
             |
             11 - 12

 Flattened: 1-2-3-7-8-11-12-9-10-4-5-6
```

#### Insight — DFS with splicing (iterative version, uses a stack)

At each node with a child:
1. Push the current `next` onto a stack (to come back to later).
2. Attach the child as the new `next`, clear `child`.
3. Continue walking.

When you hit a `null` next and the stack isn't empty, pop and attach.

#### Code

```java
public Node flatten(Node head) {
    if (head == null) return null;
    Deque<Node> stack = new ArrayDeque<>();
    Node curr = head;
    while (curr != null) {
        if (curr.child != null) {
            if (curr.next != null) stack.push(curr.next);
            curr.next = curr.child;
            curr.child.prev = curr;
            curr.child = null;
        }
        if (curr.next == null && !stack.isEmpty()) {
            Node next = stack.pop();
            curr.next = next;
            next.prev = curr;
        }
        curr = curr.next;
    }
    return head;
}
```

#### Trap

**Don't forget to set `child = null`** on the current node — the output must have no `child` pointers left. Also don't forget the `prev` back-pointers when splicing (this is a doubly linked list).

O(n) time, O(d) space where d = max depth of nesting.

---

### 8.8 Linked List Random Node (LC 382) — reservoir sampling

**Problem:** Design a class initialized with a linked list. Method `getRandom()` returns any node's value with **equal probability**. Constraint: list may be very large; can't fit in memory.

#### Naive approach (works but bad for large lists)

Copy all values to an array in the constructor. `getRandom()` picks a random index. O(n) space upfront, O(1) per query.

#### Insight — reservoir sampling (one-pass, O(1) space)

Walk the list. For the i-th node (1-indexed), replace the current pick with this node's value with probability `1/i`.

**Why every node ends up equally likely:** the i-th node has probability `1/i` of being picked. For it to survive to the end, all subsequent nodes must NOT overwrite it: probabilities `(1 - 1/(i+1)) × (1 - 1/(i+2)) × ... × (1 - 1/n) = i/n`. Combined: `1/i × i/n = 1/n`. Uniform.

#### Code

```java
class Solution {
    private ListNode head;
    private Random rand = new Random();

    public Solution(ListNode head) {
        this.head = head;
    }

    public int getRandom() {
        int result = head.val;
        int i = 2;
        for (ListNode curr = head.next; curr != null; curr = curr.next) {
            if (rand.nextInt(i) == 0) result = curr.val;   // prob 1/i
            i++;
        }
        return result;
    }
}
```

O(n) per `getRandom()` call, **O(1) space**. Trade query time for constant space — right choice when the list is huge.

#### Interview soundbite

> *"Reservoir sampling gives uniform random selection in one pass without knowing the size in advance. The math: the i-th element is picked with probability 1/i, and survives all subsequent overwrites with probability i/n. Product is 1/n — uniform."*

---

### 8.9 Insertion Sort List (LC 147)

**Problem:** Sort a linked list using insertion sort. O(n²) time is fine.

#### Insight

Same as array insertion sort, but adapted for pointers. Maintain a sorted list starting from a `dummy`. For each new node, walk the sorted list from `dummy` to find its insertion point.

#### Code

```java
public ListNode insertionSortList(ListNode head) {
    ListNode dummy = new ListNode(0);
    ListNode curr = head;
    while (curr != null) {
        ListNode next = curr.next;

        // Find insertion point in the sorted list
        ListNode prev = dummy;
        while (prev.next != null && prev.next.val < curr.val) {
            prev = prev.next;
        }

        // Insert curr between prev and prev.next
        curr.next = prev.next;
        prev.next = curr;

        curr = next;
    }
    return dummy.next;
}
```

O(n²) time (worst case), O(1) space.

#### When would you ever use this over merge sort (Phase 7.5)?

Never in practice — merge sort is O(n log n). This problem is asked to test whether you can adapt classic array algorithms to linked structures. The pattern "restart from dummy to find position" is worth knowing even if the specific algorithm isn't.

#### Optimization (interview follow-up)

Keep a `sortedTail` pointer. If `curr.val >= sortedTail.val`, just append — no walk needed. Cuts average-case time significantly on nearly-sorted inputs.

---

### 8.10 Split Linked List in Parts (LC 725)

**Problem:** Split a list into `k` consecutive parts as equal as possible. Later parts can be at most 1 shorter than earlier parts.
`1→2→3→4→5→6→7→8→9→10`, k=3 → `[1→2→3→4]`, `[5→6→7]`, `[8→9→10]`
Length 3, k=5 → `[1]`, `[2]`, `[3]`, `null`, `null`

#### Insight — just math

- Total length `n`. Base size per part = `n / k`. Extra nodes = `n % k`.
- First `extra` parts get `base + 1` nodes. Rest get `base` nodes.
- If `n < k`, some result entries stay `null`.

Not an algorithmic insight — a **spec-parsing** exercise. Interviewers use it to test whether you handle edge cases (`n < k`, `k = 1`, list is null) cleanly.

#### Code

```java
public ListNode[] splitListToParts(ListNode head, int k) {
    // Count length
    int n = 0;
    for (ListNode cur = head; cur != null; cur = cur.next) n++;

    int base = n / k, extra = n % k;
    ListNode[] result = new ListNode[k];
    ListNode curr = head;
    for (int i = 0; i < k && curr != null; i++) {
        result[i] = curr;
        int size = base + (i < extra ? 1 : 0);
        for (int j = 0; j < size - 1; j++) curr = curr.next;
        ListNode next = curr.next;
        curr.next = null;   // sever
        curr = next;
    }
    return result;
}
```

O(n) time, O(1) extra space (excluding the output array).

#### Trap

**Loop must guard `curr != null`** — if `n < k`, `curr` becomes null after the first `n` iterations; the remaining entries stay as `null` in the pre-allocated array (which is the correct answer).

---

### 8.11 Self-check (Phase 8)

1. **In 8.1 (remove duplicates), why don't you always advance `curr`?**
2. **Why does 8.2 require a dummy node but 8.1 doesn't?**
3. **Why does the "delete node given only that node" trick only work when the node isn't the tail?**
4. **In partition list (8.4), what specific bug does forgetting `geTail.next = null` cause?**
5. **In reverse-between (8.5), why does the dummy node matter more here than in a full reverse?**
6. **In reservoir sampling (8.8), what's the intuition for why each element has equal probability?**
7. **Why is insertion sort on a linked list rarely used in practice?**

#### Answers

1. **Because after skipping a duplicate, the new `curr.next` might ALSO be a duplicate of `curr`.** For input `1→1→1→2`, if you advance after each skip, you'd end up with `1→1→2` (only removed one duplicate). Only advance when values differ.

2. **Because 8.2 might need to delete the head itself.** If input is `1→1→2` and 8.2 must return `2`, the head reference changes — you need a `dummy` before the head to have a stable anchor. In 8.1, the head is always kept (only duplicates *after* the first occurrence are removed), so no dummy is needed.

3. **Because you copy the NEXT node's value into the current node, then skip next.** If the current node were the tail, there's no `next` to copy from — you'd NPE. This trick swaps "delete this position" for "delete the value" — clever but only works mid-list.

4. **Potential cycle.** The last "ge" node in your output was likely somewhere in the middle of the original list. Its `next` still points at whatever came after it originally — which might be a "less" node. Without `geTail.next = null`, the "less" section (which you attached earlier) creates a loop back to itself. Result: infinite iteration when printing.

5. **Because `left` can equal 1** (reverse starting from the head). Without a dummy, `prev` for the head doesn't exist — you'd need a special branch. Dummy makes "prev of head" always well-defined, unifying the code.

6. **The i-th node has probability 1/i of being picked when visited AND probability `i/n` of surviving all subsequent overwrites.** Product: `1/i × i/n = 1/n`. Every node — whether first or last — ends up with the same 1/n probability. Elegant.

7. **Because merge sort (Phase 7.5) is O(n log n) and equally applicable to linked lists.** Insertion sort's O(n²) makes it strictly worse for anything but tiny or nearly-sorted inputs. The problem exists mainly to test whether you can adapt classic array algorithms — the "restart from dummy to find position" pattern is the takeaway, not the algorithm itself.

---

### 8.12 The truly complete arsenal (all 26 problems)

**Building the structure (Phase 3):**
- Singly linked list, doubly linked list — from scratch

**Fundamental algorithms (Phase 4):**
- Reverse, detect cycle, find middle, merge two sorted, intersection, remove Nth from end

**Design problems (Phase 6):**
- LRU cache

**Advanced algorithms (Phase 7):**
- Palindrome, reorder, merge K sorted, reverse in K-groups, sort list, add two numbers, copy with random pointer, rotate list, swap pairs

**Gap-fill (Phase 8):**
- Remove duplicates (I & II), delete node given node, partition list, reverse between, odd/even list, flatten multilevel, random node (reservoir), insertion sort, split in parts

**26 problems + full data structure implementation.** At this point, any linked list problem an interviewer throws at you will either be one of these or a small variation. You are done.

---

## Quick-recall cheat sheet (fill in last, after everything else)

- **What is LinkedList?** (1 sentence)
- **Which interfaces does it implement?**
- **Why doubly linked?**
- **Big-O for `addFirst`, `addLast`, `get(i)`, `remove(i)`:**
- **When to use it:**
- **When NOT to use it:**
- **Real production alternative:**

---

## Open questions / things I still don't understand
-
-
-
