package testing;

public class Counter {
    private  int count = 0;

    public synchronized void increment() { count++; } // ✅ atomic + visible
    public synchronized int get()        { return count; }

    public static void main(String[] args) throws InterruptedException {
        Counter c=new Counter();

        Runnable r = () -> {
            for (int i = 0; i < 10_000; i++) {
                c.increment();
            }
        };


        Thread t1=new Thread(r,"T1");
        Thread t2=new Thread(r,"T2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.println("Final count: " + c.get());

    }
}