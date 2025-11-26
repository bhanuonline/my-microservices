package interview.java.core.memory;

public class HeapExample {
    public static void main(String[] args) {
        // Each new keyword allocates object in Heap
        String name = new String("John");  // created in heap

        // Multiple objects stored in heap
        Person p1 = new Person("Alice", 30);
        Person p2 = new Person("Bob", 25);

        // Change object via reference
        p1.age = 31;  // modifies the object in heap

        // Nullify reference - makes object eligible for GC
        p2 = null;

        // Suggest garbage collection
        System.gc();
    }
}

class Person {
    String name; // stored in heap with the object
    int age;
    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }
}