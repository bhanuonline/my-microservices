Q. Why String is Immutable?
Security.
Caching.
Thread Safety.
String Pool support.

Q. Why HashMap key usually String?
Because immutable objects produce consistent hashCode.


8. Describe the Singleton pattern and provide an example of a thread-safe implementation in Java.
10. ow does the hashCode() method relate to the equals() method? What are the implications of overriding one but not the other?
11. What is the purpose of the volatile keyword in Java? How does it relate to the happens-before relationship?
12. Describe the Observer pattern and how it can be implemented using Java’s built-in classes.
13. What is the purpose of the transient keyword in Java?
14. Explain the difference between synchronized collections and concurrent collections in Java.
15. Describe the Garbage Collection process in Java. How would you tune GC for a high-throughput, low-latency application?
16. Describe the process of class loading in Java. How can you implement a custom class loader, and what are some use cases for doing so?
    Loading classes from non-standard locations (e.g., databases, networks)
    Implementing plugin systems
    Modifying bytecode on-the-fly
    Implementing security policies
    Hot-swapping classes in a running application
17. How would you design a highly scalable, distributed caching system using Java?
18. Describe the internals of the ConcurrentHashMap class. How does it achieve its high level of concurrency?
19. What are virtual threads? (Java 21+)