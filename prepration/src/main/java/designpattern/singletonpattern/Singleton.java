package designpattern.singletonpattern;


final class SingletonEager {

    // Created immediately when class loads
    private static final SingletonEager INSTANCE
            = new SingletonEager();

    private SingletonEager() {}   // block new Singleton()

    public static SingletonEager getInstance() {
        return INSTANCE;     // always ready, never null
    }

    public void doWork() {
        System.out.println("Working: " + hashCode());
    }
}
//serialization-safe
enum SingletonEnum {
    INSTANCE;

    public void doSomething() {
        System.out.println("Enum Singleton");
    }
}
public final class Singleton {

    // volatile ensures visibility across threads (prevents instruction reordering)
    private static volatile Singleton instance;

    // Private constructor — prevents instantiation from outside
    private Singleton() {
        // Optional: guard against reflection-based attacks
        if (SingletonHolder.INSTANCE != null) {
            throw new IllegalStateException("Instance already created.");
        }
    }

    // Inner static holder class — loaded only when getInstance() is called
    private static final class SingletonHolder {
        private static final Singleton INSTANCE = new Singleton();
    }

    // Global access point
    public static Singleton getInstance() {
        return SingletonHolder.INSTANCE;
    }

    // Example method
    public void doSomething() {
        System.out.println("Singleton instance: " + this.hashCode());
    }

    public static Singleton getInstanceDoubleCheck() {
        if (instance == null) {                     // 1st check (no lock)
            synchronized (Singleton.class) {
                if (instance == null) {             // 2nd check (with lock)
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }

    public static void main(String[] args) {

    }
}