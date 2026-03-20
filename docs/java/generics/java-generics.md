Problems:
❌ Runtime ClassCastException
❌ Manual casting everywhere
❌ No compile-time safety

✔ Type safe
✔ No casting
✔ Errors caught at compile time

👉 Generics solve this.

✅ 2️⃣ Generic Method
A generic method has its own type parameter, independent of class.
📌 <T> before return type → tells compiler this method is generic
public static <T> void print(T value) {
System.out.println(value);
}

6️⃣ Bounded Generics
Upper Bound – extends <T extends Number>

7️⃣ Wildcards ?
❓ Unbounded Wildcard
List<?> list;
Can read data
Cannot add (except null)
🔼 Upper Bounded Wildcard
List<? extends Number> list;
✔ Read numbers
❌ Cannot add (because exact type unknown)

🔽 Lower Bounded Wildcard
List<? super Integer> list;
✔ Can add Integer
✔ Used when writing data

PECS → Producer Extends, Consumer Super
Read data? → extends
Write data? → super
void read(List<? extends Number> list) { }
void write(List<? super Integer> list) { }


Q1: Why do we need Generics?
Answer:
Type safety
Remove explicit casting
Catch errors at compile time
Code reusability

❓ Q3: Why can’t we create new T()?
Answer:
Because at runtime T doesn’t exist due to type erasure.

Q4: Difference between List<T> and List<?>

| List<T>                     | List<?>      |
| --------------------------- | ------------ |
| Known type                  | Unknown type |
| Can add                     | Cannot add   |
| Used inside generic classes | Used in APIs |


❓ Q5: Difference between ? extends and ? super

| `extends` | `super`       |
| --------- | ------------- |
| Read only | Write allowed |
| Producer  | Consumer      |

❓ Q6: Can we overload methods only by generic types?
void m(List<String> l)
void m(List<Integer> l)

❌ No — both erase to List

❓ Q7: Why Generics don’t support primitives?
Answer:
Generics work with reference types only.
Use wrappers: Integer, Double

❌ Mistake 2: Misusing Wildcards
void add(List<? extends Number> list) {
list.add(10); // ❌
}

Why?
Compiler doesn’t know exact subtype
✔ Correct:

void add(List<? super Integer> list) {
list.add(10);
}


❌ Mistake 3: Overusing <?>
List<?> list;
Use only when:
You don’t care about type
Only reading data

❌ Mistake 5: Trying instanceof T
if (obj instanceof T) { } // ❌
Impossible due to type erasure.

If your method accepts a generic type, prefer wildcards:
// ❌ too restrictive
void process(List<Number> list)
// ✅ flexible
void process(List<? extends Number> list)

Q: Can a static method use class-level T (generics)?
A: ❌ No. Static methods must declare their own <T>.

🔹 Q13. Can a constructor be generic?
✅ YES


🔹 12. Remove with Wildcards
void remove(List<?> list) {
list.remove(0);  // ✅
list.add(null);  // ✅
}
💡 Remove works because it doesn’t add a type.

“Java does not support operator overloading except for String +.”
sum += value; // ❌
Because:
No operator overloading
Number doesn’t define add()