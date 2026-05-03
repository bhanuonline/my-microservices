# Q1. Which partition scheme does Java use : “Java’s Arrays.sort() for primitives uses Dual-Pivot QuickSort, which is more efficient than traditional Lomuto or Hoare. It partitions the array into three parts using two pivots. For objects, Java uses TimSort, 
which is stable.”
   Arrays.sort(Object[])
   Collections.sort(List)
👉 Uses TimSort

Hybrid of Merge Sort + Insertion Sort
Stable sorting algorithm
For primitive :Dual-Pivot QuickSort
🔥 Core Idea Difference
Single Pivot Quick Sort → splits into 2 parts
Dual Pivot QuickSort → splits into 3 parts
Single Pivot:
< pivot | pivot | > pivot

Dual Pivot:
< p1 | between p1 & p2 | > p2

# Q2. 🔥 What is CPU Cache (in 10 seconds)
CPU doesn’t read RAM directly every time (too slow)
It uses cache (L1, L2, L3) → very fast, very small
Data is loaded in cache lines (chunks, e.g., 64 bytes)

👉 If your algorithm accesses memory sequentially, cache works great
👉 If it jumps around → cache misses → slower

# Q3 What is the size of an array?
“In Java, array size usually refers to the number of elements, accessible via arr.length. Memory size depends on the data type and JVM overhead.”
🔹 2. Memory Size (Actual Bytes in RAM)
This depends on:
Data type
Number of elements
int[] arr = new int[7];
Each int = 4 bytes
Total = 7 × 4 = 28 bytes (for elements only)
So real memory ≈ 28 bytes + overhead (~12–16 bytes)

# Explain the internal working of ArrayList resizing
ArrayList uses a dynamic array internally. When capacity is full, it grows by 1.5x, creates a new array, and copies elements. This makes add operation amortized O(1), though individual resize operations take O(n).”
🔹 Big Picture

ArrayList is basically:

Object[] elementData;
int size;
elementData → internal array
size → number of elements actually used

👉 Capacity (array length) ≥ size
🔴 When Resize Happens
Resize triggers when:
size == elementData.length
🔹 Resizing Logic (Core Formula)
newCapacity = oldCapacity + (oldCapacity >> 1);

👉 This means:
newCapacity = oldCapacity * 1.5

Steps:
Create new larger array
Copy all elements (System.arraycopy)
Point elementData to new array
Old array → garbage collected

# Explain why ArrayList grows by 1.5x and not 2x
“Java’s ArrayList grows by 1.5× instead of 2× to balance memory usage and performance. A 2× growth wastes more memory, while 1.5× keeps the array tighter, improving cache locality and reducing memory overhead, 
while still maintaining amortized O(1) insertion.

# Explain why ArrayList is not thread-safe and how to fix it
“ArrayList is not thread-safe because its operations like add and resize are not synchronized, leading to race conditions and data inconsistency. To fix this, we can use Collections.synchronizedList, CopyOnWriteArrayList for read-heavy scenarios, or manual synchronization.”
ArrayList is not thread-safe because it has no internal synchronization. When multiple threads modify it at the same time, its internal state (elementData array + size) can become inconsistent.

🔹 3. Iteration + Modification = Crash
for (Integer x : list) {
list.add(100);
}
👉 Throws:
ConcurrentModificationException
Because ArrayList uses a fail-fast iterator

✅ 2. Use CopyOnWriteArrayList (Best for Read-Heavy)
import java.util.concurrent.CopyOnWriteArrayList;

List<Integer> list = new CopyOnWriteArrayList<>();

✔ Thread-safe without locks during reads
✔ No ConcurrentModificationException

# Explain the difference between fail-fast vs fail-safe iterators
“Fail-fast iterators throw ConcurrentModificationException if the collection is modified during iteration by tracking a modification count. Fail-safe iterators work on a snapshot of the collection, so they allow modification without exceptions but may not reflect the latest changes.”

🔴 Fail-Fast Iterator
👉 Fails immediately if collection is modified during iteration
🔴 Used In
ArrayList
HashMap
HashSet

🔵 Fail-Safe Iterator
👉 Works on a copy (snapshot) of collection
👉 No exception even if modified
🔵 Used In
CopyOnWriteArrayList
ConcurrentHashMap

🔹 How Fail-Safe Works
Iterator uses separate copy of data
Changes go to new array, not the one being iterated

🔹 When to Use What
Use fail-fast → single-threaded, debugging
Use fail-safe → multi-threaded, concurrent apps

# Explain the difference between Iterator vs ListIterator

| Feature            | Iterator        | ListIterator       |
| ------------------ | --------------- | ------------------ |
| Direction          | Forward only    | Forward + Backward |
| Works on           | All collections | Only `List`        |
| Add elements       | ❌ No            | ✅ Yes              |
| Replace elements   | ❌ No            | ✅ Yes (`set()`)    |
| Index access       | ❌ No            | ✅ Yes              |
| Traverse backwards | ❌ No            | ✅ Yes              |

Flexibility
Iterator → read + remove
ListIterator → read + add + update + bidirectional

Collection Support
Iterator → works with:
ArrayList
HashSet
HashMap
ListIterator → works ONLY with:
List (ArrayList, LinkedList)

# Explain the difference between Iterable and Iterator
“Iterable is an interface that represents a collection capable of returning an iterator, while Iterator is used to traverse elements one by one. Iterable enables the for-each loop, and Iterator performs the actual iteration.”
🔹 Core Idea
Iterable → “I can give you an iterator”
Iterator → “I can traverse elements”

🔥 Relationship (Most Important)
Iterable → provides → Iterator → traverses data

🔹 Why Iterable Exists

It enables:
for (Integer x : list) {
System.out.println(x);
}

🔴 2. Iterator (Traversal Engine)
Iterator is used to actually move through elements

Methods:
boolean hasNext();
T next();
void remove();

| Feature             | Iterable                       | Iterator                   |
| ------------------- | ------------------------------ | -------------------------- |
| Purpose             | Provide iterator               | Traverse elements          |
| Method              | `iterator()`                   | `hasNext()`, `next()`      |
| Usage               | for-each loop                  | manual iteration           |
| Multiple traversals | ✅ Yes (new iterator each time) | ❌ No (one-time use)        |
| State               | No state                       | Maintains current position |

🔹 Common Trick Questions
❓ Can you use for-each without Iterable?
👉 ❌ No

❓ Can you have multiple iterators?
👉 ✅ Yes (via Iterable)

❓ Can Iterator reset?
👉 ❌ No (create new one)