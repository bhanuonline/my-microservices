package interview.java.core.keyword;

import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterExample {
    private final AtomicInteger counter = new AtomicInteger(0);

    public void increment() {
        counter.incrementAndGet();  // atomic operation
    }

    public int getCounter() {
        return counter.get();
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicCounterExample demo = new AtomicCounterExample();

        Runnable task = () -> {
            for (int i = 0; i < 1000; i++) {
                demo.increment();
            }
        };

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(task);
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("Final counter (atomic): " + demo.getCounter());
    }
}