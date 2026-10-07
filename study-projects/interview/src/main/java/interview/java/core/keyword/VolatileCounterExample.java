package interview.java.core.keyword;

public class VolatileCounterExample {
    private volatile int counter = 0;  // shared across threads

    public void increment() {
        counter++;     // not atomic!
    }

    public int getCounter() {
        return counter;
    }

    public static void main(String[] args) throws InterruptedException {
        VolatileCounterExample demo = new VolatileCounterExample();

        Runnable task = () -> {
            for (int i = 0; i < 1000; i++) {
                demo.increment();
            }
        };

        // Run with 10 threads concurrently
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(task);
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("Final counter (volatile): " + demo.getCounter());
    }
}