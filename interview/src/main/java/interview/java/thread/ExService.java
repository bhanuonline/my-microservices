package interview.java.thread;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExService {
    public static void main(String[] args) {
        Runnable r=()-> System.out.println("Hello"+ Thread.currentThread().getName());

        ExecutorService executorService= Executors.newFixedThreadPool(3);
        executorService.submit(r);
        executorService.submit(r);
        executorService.submit(r);
        executorService.shutdown();
    }
}
