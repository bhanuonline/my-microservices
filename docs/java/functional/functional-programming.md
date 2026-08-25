OOP thinking:          "How do I change the state?"
FP thinking:           "What is the result of this input?"

KEY PRINCIPLES:

1. Pure Functions       same input → always same output, no side effects
2. Immutability         don't modify data, create new data
3. First-class Functionsfunctions can be passed as arguments
4. No shared state      avoid global variables
5. Declarative style    "what to do" not "how to do it"


// lambda syntax
(params) -> expression
(params) -> { statements; return value; }

// examples
() -> 42                          // no param, returns value
x -> x * x                        // one param
(x, y) -> x + y                   // two params
(x, y) -> { return x + y; }       // block body

▌ STAGE 3 — LEARN FUNCTIONAL INTERFACES  (Java 8+)
───────────────────────────────────────────────────────────────

INTERFACE           SIGNATURE              USE CASE
──────────────────────────────────────────────────────────
Function<T,R>       T -> R                 transform input to output
BiFunction<T,U,R>   (T,U) -> R             two inputs, one output
Predicate<T>        T -> boolean           filter / test condition
BiPredicate<T,U>    (T,U) -> boolean       test with two inputs
Consumer<T>         T -> void              do something, no return
BiConsumer<T,U>     (T,U) -> void          consume two inputs
Supplier<T>         () -> T                produce a value, no input
UnaryOperator<T>    T -> T                 same type in and out
BinaryOperator<T>   (T,T) -> T             two same-type in, one out
Runnable            () -> void             no input, no output

EXAMPLES:

Function<String, Integer>  len    = s -> s.length();
Predicate<Integer>         isEven = n -> n % 2 == 0;
Consumer<String>           print  = s -> System.out.println(s);
Supplier<String>           greet  = () -> "Hello!";
UnaryOperator<Integer>     square = n -> n * n;
BinaryOperator<Integer>    add    = (a, b) -> a + b;

// chaining functions
Function<String, String> trim  = String::trim;
Function<String, String> upper = String::toUpperCase;
Function<String, String> both  = trim.andThen(upper);
both.apply("  hello  ");  // → "HELLO"

// combining predicates
Predicate<Integer> isEven    = n -> n % 2 == 0;
Predicate<Integer> isPositive = n -> n > 0;
Predicate<Integer> both      = isEven.and(isPositive);
Predicate<Integer> either    = isEven.or(isPositive);
Predicate<Integer> notEven   = isEven.negate();

▌ STAGE 4 — LEARN METHOD REFERENCES
───────────────────────────────────────────────────────────────

TYPE                       SYNTAX                LAMBDA EQUIV
──────────────────────────────────────────────────────────
Static method              Class::method         x -> Class.method(x)
Instance method (object)   obj::method           x -> obj.method(x)
Instance method (type)     Class::method         x -> x.method()
Constructor                Class::new            x -> new Class(x)

EXAMPLES:

list.forEach(System.out::println);         // instance on object
list.stream().map(String::toUpperCase);   // instance on type
list.stream().map(Integer::parseInt);     // static method
list.stream().map(Person::new);           // constructor ref


▌ STAGE 5 — LEARN STREAMS  (most important)
───────────────────────────────────────────────────────────────

STREAM PIPELINE:   source → intermediate ops → terminal op

SOURCE METHODS:

list.stream()                   from collection
Arrays.stream(arr)              from array
Stream.of(1, 2, 3)              from values
Stream.iterate(0, n -> n+1)    infinite stream
Stream.generate(Math::random)  infinite generator
IntStream.range(0, 10)          0 to 9
IntStream.rangeClosed(1, 10)   1 to 10

INTERMEDIATE OPS (lazy — returns Stream):

.filter(predicate)              keep matching elements
.map(function)                  transform each element
.flatMap(function)              flatten nested streams
.distinct()                     remove duplicates
.sorted()                       natural sort
.sorted(comparator)             custom sort
.limit(n)                       take first n elements
.skip(n)                        skip first n elements
.peek(consumer)                 debug — see elements passing through
.mapToInt / mapToLong / mapToDouble   to primitive stream

TERMINAL OPS (eager — triggers execution):

.collect(collector)             gather into collection
.forEach(consumer)              consume each element
.count()                        count elements
.findFirst()                    first element (Optional)
.findAny()                      any element (parallel-friendly)
.anyMatch(predicate)            true if any matches
.allMatch(predicate)            true if all match
.noneMatch(predicate)           true if none match
.min(comparator)                minimum (Optional)
.max(comparator)                maximum (Optional)
.reduce(identity, accumulator)  fold to single value
.toArray()                      collect to array
.sum() / average()              on IntStream/LongStream/DoubleStream

▌ STAGE 6 — LEARN COLLECTORS
───────────────────────────────────────────────────────────────

Collectors.toList()                        collect to List
Collectors.toSet()                         collect to Set
Collectors.toMap(k, v)                     collect to Map
Collectors.joining(", ")                   join strings
Collectors.joining(", ", "[", "]")        join with prefix/suffix
Collectors.groupingBy(classifier)          group → Map<K, List<V>>
Collectors.partitioningBy(predicate)       split → Map<Boolean, List>
Collectors.counting()                      count per group
Collectors.summingInt(fn)                  sum per group
Collectors.averagingInt(fn)               average per group
Collectors.toUnmodifiableList()            immutable list (Java 10+)

▌ STAGE 7 — LEARN OPTIONAL  (avoid null)
───────────────────────────────────────────────────────────────

Optional.of(value)              wrap a non-null value
Optional.ofNullable(value)     wrap value that may be null
Optional.empty()               empty Optional

opt.isPresent()                check if value exists
opt.isEmpty()                  check if empty (Java 11+)
opt.get()                      get value (throws if empty)
opt.orElse(defaultVal)         get or return default
opt.orElseGet(() -> compute())  get or compute default
opt.orElseThrow()              get or throw exception
opt.map(fn)                    transform value if present
opt.filter(predicate)          filter value
opt.ifPresent(consumer)        do something if present