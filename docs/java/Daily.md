**1. What is the difference between == and equals()?**

| `==`                       | `equals()`                       |
|----------------------------| -------------------------------- |
| Compares memory reference  | Compares content (if overridden) |
|(address in RAM).           |  values inside the object                                |
| For primitives → value     | For objects → logic              |
| Cannot be overridden       | Can be overridden                |

**2. Why is String immutable in Java?**

A String object cannot be changed once created. Any modification creates a new object.
Thread-safe
Security (used in ClassLoader, DB URLs)
String pool caching
Hashcode caching → faster Map lookups

**3. Difference between String, StringBuilder, StringBuffer**

| String      | StringBuilder   | StringBuffer        |
| ----------- | --------------- | ------------------- |
| Immutable   | Mutable         | Mutable             |
| Thread-safe | Not thread-safe | Thread-safe         |
| Slow        | Fastest         | Slower than Builder |


**4. What is hashCode() contract?**

If obj1.equals(obj2) → obj1.hashCode() == obj2.hashCode()
But
obj1.hashCode() == obj2.hashCode() does NOT guarantee equals


**5. Why override equals() and hashCode() together?**

Used by HashMap, HashSet.
If not overridden correctly → duplicate keys, retrieval failure.

**8. Why multiple inheritance is not supported?**

Because of Diamond Problem.
Solved using interface with default methods.

**9. Difference between throw and throws**

| throw                              | throws              |
| ---------------------------------- | ------------------- |
| Used to explicitly throw exception | Declares exception  |
| Inside method                      | In method signature |


**10. Why HashMap is not thread-safe?**

Multiple threads can modify same bucket → data corruption.
Use:
Collections.synchronizedMap()
ConcurrentHashMap

**11. Difference between Runnable and Callable**

| Runnable                       | Callable      |
| ------------------------------ | ------------- |
| No return value                | Returns value |
| Cannot throw checked exception | Can throw     |
| run()                          | call()        |

**12. What is volatile?**

Guarantees visibility of variable across threads.

**13. Difference between sleep() and wait()**

| sleep()               | wait()          |
| --------------------- | --------------- |
| Thread method         | Object method   |
| Does not release lock | Releases lock   |
| No notify required    | Requires notify |

**14. Stream vs Collection**

| Stream       | Collection |
| ------------ | ---------- |
| Process data | Store data |
| Lazy         | Eager      |
| One-time use | Reusable   |

**1. What are the four pillars of OOPs in Java?**
   Answer: Java follows Object-Oriented Programming (OOP) principles, which include:

Encapsulation – Wrapping data (variables) and code (methods) together in a class.
Inheritance – Allowing one class to inherit properties from another.
Polymorphism – One interface, multiple implementations (method overloading/overriding).
Abstraction – Hiding implementation details and exposing only the necessary functionality.


3️⃣ Is arr[i]++ atomic? no
read → increment → write

4️⃣ Can ++ be overloaded in Java?
❌ No
Java does not support operator overloading (except + for String)

5️⃣ Bytecode for ++i
JVM uses iinc instruction
No actual ++ instruction exists

6️⃣ Is ++i faster than i = i + 1?
✅ Slightly
++i uses iinc → fewer bytecode instructions

🔹 Arrays vs Map
7️⃣ Why is int[] faster than HashMap?
No hashing
No objects
No boxing/unboxing
Direct memory access

8️⃣ How HashMap handles collisions?
Java 8+
Linked List → Tree (Red-Black Tree) if bucket > 8

9️⃣ Why Map needs boxing?
Collections store objects
int → Integer, char → Character

🔟 What happens during HashMap resize?
Capacity doubles
All entries rehashed
Expensive operation

Why does HashMap use power-of-two capacity?

1️⃣2️⃣ Why array access is O(1)?
Direct memory offset calculation
address = base + (index × size)

1️⃣3️⃣ Where are arrays stored?
Heap memory

1️⃣4️⃣ Stack vs Heap

| Stack           | Heap    |
| --------------- | ------- |
| Local variables | Objects |
| Fast            | Slower  |
| Thread-safe     | Shared  |

1️⃣5️⃣ Is char ASCII or Unicode?
Unicode (UTF-16)

1️⃣6️⃣ Why char is 2 bytes?
To support Unicode characters

1️⃣8️⃣ How JVM checks bounds?
Runtime check before memory access

2️⃣0️⃣ Why Integer slower than int?
Object creation
Heap allocation
Extra memory

2️⃣4️⃣ Why Map<Character, Integer> overhead?
Hashing
Object creation
Boxing/unboxing

🔹 Streams vs Loops
2️⃣5️⃣ Why streams slower?
Lambda calls
Object creation
Boxing

2️⃣7️⃣ When parallelStream helps?
Large data
CPU-intensive tasks
Stateless operations

2️⃣8️⃣ Why counting() returns Long?
Safer for large data sets

2️⃣9️⃣ What happens in groupingBy()?
HashMap creation
Hashing keys
Aggregation per key

3️⃣1️⃣ Make it thread-safe?
Use AtomicInteger
Or synchronize access

3️⃣2️⃣ synchronized vs AtomicInteger

| synchronized | Atomic    |
| ------------ | --------- |
| Blocking     | Lock-free |
| Slower       | Faster    |

3️⃣3️⃣ Why volatile not enough?
Visibility ≠ atomicity

3️⃣5️⃣ What is JIT?
Compiles bytecode → native code at runtime

3️⃣6️⃣ Loop optimization?
Unrolling
Inlining
Bounds check elimination


3️⃣8️⃣ Bounds check elimination?
JVM removes redundant checks in loops

3️⃣9️⃣ Loop unrolling?
Reduces loop overhead

4️⃣0️⃣ Method inlining?
Removes call overhead
Improves performance

4️⃣4️⃣ Modify array in method?
Changes visible (arrays are mutable)

4️⃣5️⃣ Arrays passed by?
Value of reference (effectively reference)
“Java is pass-by-value. For arrays, the value passed is the reference, so modifying elements affects the original array, but reassigning the reference does not.”

Core difference between Comparable and Comparator

| Aspect                 | Comparable       | Comparator            |
| ---------------------- | ---------------- | --------------------- |
| Who compares?          | Object itself    | Separate object       |
| Method                 | `compareTo(T o)` | `compare(T o1, T o2)` |
| Part of data?          | Yes              | No                    |
| Used in `max(List<T>)` | ✅                | ❌                     |

| Question                      | Comparable | Comparator |
| ----------------------------- | ---------- | ---------- |
| Comparison code inside class? | ✅ Yes      | ❌ No       |
| Object compares itself?       | ✅ Yes      | ❌ No       |
| Extra object needed?          | ❌ No       | ✅ Yes      |



🧩 Real-life analogy
Comparable → Student compares marks with another student
Comparator → Teacher compares two students

🧠 One-line memory trick
Comparable → compareTo → this vs other
Comparator → compare → other vs other

Hash-based collections
HashSet, HashMap
Use 👉 equals() + hashCode()
Ignore compareTo()

Sorted collections
TreeSet, TreeMap
Use 👉 compareTo() or Comparator
Ignore equals()

⚠️ If inconsistent → data loss
🔑 Comparable uses compareTo() only, but compareTo() must agree with equals() to avoid bugs.

Comparable → uses compareTo only
equals() / hashCode() → used by HashSet / HashMap
TreeSet / TreeMap → use compareTo or Comparator

One-line memory trick 🧠
Sorting uses compareTo, hashing uses equals + hashCode


### Spring Singleton & Thread Safety — Memory Notes 🧠
1️⃣ Spring singleton ≠ thread-safe
Singleton only means one object
It does NOT guarantee thread safety

2️⃣ Threads do NOT create objects
Objects are created only using new
Multiple threads can use the same object

3️⃣ Singleton is thread-safe when:
❇ No instance variables (stateless)
❇ Only local variables
❇ Immutable fields (final, not modified)
❇ Uses thread-safe classes (ConcurrentHashMap)

4️⃣ Singleton is NOT thread-safe when:
❌ Has mutable instance variables
❌ Uses HashMap, ArrayList, etc.
❌ Shared data is modified by threads

5️⃣ Instance variables = shared in singleton
All threads see same instance variables
Leads to race condition if not protected

6️⃣ static makes it worse
static = shared across JVM
static HashMap + threads = ❌ danger

7️⃣ Best fixes (in order)
Use ConcurrentHashMap
Synchronize access
Avoid shared mutable state
