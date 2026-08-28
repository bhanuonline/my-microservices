Legend:  ■ interface   ■ abstract class   ■ concrete class   ↕ multi-parent (noted inline)

Map<K,V>  java.util
│
├── AbstractMap<K,V>  java.util
│    │
│    ├── HashMap<K,V>
│    │    ├── LinkedHashMap<K,V>
│    │    └── PrinterStateReasons              javax.print.attribute
│    │
│    ├── TreeMap<K,V>                         also implements NavigableMap
│    │
│    ├── EnumMap<K,V>
│    │
│    ├── WeakHashMap<K,V>
│    │
│    ├── IdentityHashMap<K,V>
│    │
│    ├── ConcurrentHashMap<K,V>              also implements ConcurrentMap
│    │
│    └── ConcurrentSkipListMap<K,V>          also implements ConcurrentNavigableMap
│
├── SortedMap<K,V>  java.util
│    └── NavigableMap<K,V>  java.util
│         ├── TreeMap<K,V>                   also extends AbstractMap
│         └── ConcurrentSkipListMap<K,V>     also extends AbstractMap
│
├── ConcurrentMap<K,V>  java.util.concurrent
│    ├── ConcurrentNavigableMap<K,V>         extends ConcurrentMap + NavigableMap
│    │    └── ConcurrentSkipListMap<K,V>
│    └── ConcurrentHashMap<K,V>
│
├── Bindings  javax.script                    extends Map<String,Object>
│    └── SimpleBindings                       backed by HashMap or LinkedHashMap
│
└── Dictionary<K,V>  java.util               legacy — does NOT extend Map
└── Hashtable<K,V>                        implements Map separately
├── Properties
│    └── Provider                    java.security
└── UIDefaults                      javax.swing


─────────────────────────────────────────────────────────────
Note: Dictionary is abstract and predates Map (Java 1.0).
Hashtable implements Map but also extends Dictionary.
Provider and UIDefaults are rarely used directly.

| Class             | Ordered           | Sorted       | Null Key | Thread Safe |
| ----------------- | ----------------- | ------------ | -------- | ----------- |
| HashMap           | ❌                 | ❌            | ✅ 1      | ❌           |
| LinkedHashMap     | ✅ Insertion Order | ❌            | ✅ 1      | ❌           |
| TreeMap           | ❌                 | ✅ Key Sorted | ❌        | ❌           |
| Hashtable         | ❌                 | ❌            | ❌        | ✅           |
| ConcurrentHashMap | ❌                 | ❌            | ❌        | ✅           |
| WeakHashMap       | ❌                 | ❌            | ✅        | ❌           |
| IdentityHashMap   | ❌                 | ❌            | ✅        | ❌           |
| EnumMap           | Enum Order        | ❌            | ❌        | ❌           |


Interview Notes
HashMap → Most commonly used.
LinkedHashMap → Preserves insertion order.
TreeMap → Keys are automatically sorted.
Hashtable → Legacy, synchronized class.
ConcurrentHashMap → Best choice for concurrent applications.
WeakHashMap → Entries are removed when keys are no longer strongly referenced by the JVM.
IdentityHashMap → Compares keys using == instead of equals().
EnumMap → Optimized for enum keys.

Iterable means:
"I contain a sequence of elements, so you can visit them one by one."
What should the for-each loop return?

for ( ? x : map ) {
...
}

Should x be:
1, 2 (keys)? 🤔
"Apple", "Mango" (values)? 🤔
1->Apple, 2->Mango (entries)? 🤔

There is no single correct answer.
That's why Java designers didn't make Map implement Iterable.

PROBLEM: Map stores PAIRS (key → value), not a single sequence.
What would forEach iterate? Keys? Values? Entries?
No single obvious answer → designers left it out.

SOLUTION: You choose explicitly what to iterate:

map.keySet()    → Set<K>              (implements Iterable)
map.values()     → Collection<V>       (implements Iterable)
map.entrySet()   → Set<Map.Entry<K,V>> (implements Iterable)


SECTION 2 — SAFE GET & PUT METHODS  (Java 8+)
───────────────────────────────────────────────────────────────

METHOD                                    DESCRIPTION
──────────────────────────────────────────────────────────
map.getOrDefault(k, defaultVal)          returns defaultVal if key missing
map.putIfAbsent(k, v)                    insert only if key NOT present
map.computeIfAbsent(k, k -> newVal)      compute & insert if key missing
map.computeIfPresent(k, (k,v) -> newV)  compute & update only if key exists
map.compute(k, (k, v) -> newV)          compute for key always (v=null if missing)
map.merge(k, v, (old, nw) -> result)    merge new value with existing

EXAMPLES:

// safe counter (getOrDefault)
map.put(word, map.getOrDefault(word, 0) + 1);

// safe counter (merge) ← cleaner
map.merge(word, 1, Integer::sum);

// group into list (computeIfAbsent)
map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);

// double all values (computeIfPresent)
map.computeIfPresent("Rahul", (k, v) -> v * 2);

▌ SECTION 3 — REPLACE METHODS
───────────────────────────────────────────────────────────────

map.replace(k, newV)            update value only if key exists
map.replace(k, oldV, newV)      update only if key maps to oldV exactly
map.replaceAll((k, v) -> newV)  update ALL values using function

EXAMPLES:

map.replace("Rahul", 80000);                   // update salary
map.replace("Rahul", 70000, 80000);            // conditional update
map.replaceAll((name, sal) -> sal + 5000);    // give everyone a raise

▌ SECTION 4 — ITERATION METHODS
───────────────────────────────────────────────────────────────

map.forEach((k, v) -> ...)      iterate all entries (lambda)
map.entrySet()                  Set<Entry<K,V>> — key + value both
map.keySet()                    Set<K>          — keys only
map.values()                    Collection<V>   — values only

EXAMPLES:

// print all
map.forEach((k, v) -> System.out.println(k + " → " + v));

// for-each on entrySet
for (Map.Entry<String, Integer> e : map.entrySet())
System.out.println(e.getKey() + " → " + e.getValue());

// safe remove during iteration
Iterator<Map.Entry<K,V>> it = map.entrySet().iterator();
while (it.hasNext()) { if (condition) it.remove(); }

▌ SECTION 5 — STREAM PATTERNS  (Java 8+)
───────────────────────────────────────────────────────────────

// filter by value
map.entrySet().stream()
.filter(e -> e.getValue() > 50000)
.forEach(e -> System.out.println(e.getKey()));

// find max by value
map.entrySet().stream()
.max(Map.Entry.comparingByValue())
.orElseThrow();

// find min by value
map.entrySet().stream()
.min(Map.Entry.comparingByValue())
.orElseThrow();

// sort by value ascending
map.entrySet().stream()
.sorted(Map.Entry.comparingByValue())
.forEach(System.out::println);

// sort by value descending
map.entrySet().stream()
.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
.forEach(System.out::println);

// sort by key
map.entrySet().stream()
.sorted(Map.Entry.comparingByKey())
.forEach(System.out::println);

// collect to new map
Map<String, Integer> filtered = map.entrySet().stream()
.filter(e -> e.getValue() > 50000)
.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

// reverse map (swap K↔V)
Map<Integer, String> rev = map.entrySet().stream()
.collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

// sum all values
int total = map.values().stream()
.mapToInt(Integer::intValue).sum();

// average of values
double avg = map.values().stream()
.mapToInt(Integer::intValue).average().orElse(0);

// group by some property
Map<Integer, List<String>> grouped = map.entrySet().stream()
.collect(Collectors.groupingBy(
Map.Entry::getValue,
Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

▌ SECTION 7 — UTILITY / FACTORY METHODS
───────────────────────────────────────────────────────────────

Map.of(k1,v1, k2,v2 ...)        immutable map, up to 10 entries (Java 9+)
Map.ofEntries(entry(...), ...)  immutable map, unlimited entries (Java 9+)
Map.copyOf(map)                 immutable copy of existing map (Java 10+)
Map.entry(k, v)                 create a single immutable entry (Java 9+)
Collections.unmodifiableMap(m) wrap map as read-only
Collections.synchronizedMap(m) wrap map as thread-safe (legacy)
Collections.emptyMap()         return empty immutable map
Collections.singletonMap(k,v)  return immutable single-entry map


▌ WHAT IS A LAMBDA?
───────────────────────────────────────────────────────────────

A lambda is just a short anonymous function you can pass around.
It has no name, no class, no boilerplate

▌ SYNTAX — 3 FORMS
───────────────────────────────────────────────────────────────

// Form 1 — no params
() -> System.out.println("Hi")

// Form 2 — one param (no brackets needed)
x -> x * x

// Form 3 — multiple params
(x, y) -> x + y

// Form 4 — block body (multiple lines)
(x, y) -> {
int sum = x + y;
return sum;
}

// Form 5 — with explicit types (optional)
(int x, int y) -> x + y