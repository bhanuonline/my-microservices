package interview.java.thread;

import java.util.concurrent.*;
class MyTask implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        int sum = 0;
        for (int i = 1; i <= 10; i++) sum += i;
        Thread.sleep(1000); // simulate work
        return sum;
    }
}
public class ThreadDemo {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Runnable task = () -> System.out.println("Running in: " + Thread.currentThread().getName());

        Thread t = new Thread(task, "my-thread");
        t.start();

        Callable<Integer> taskway2 = () -> {
            Thread.sleep(1000);
            return 42;
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> future = executor.submit(taskway2);

        Integer result = future.get(); // Blocks until result is ready
        System.out.println("Result: " + result); // 42
        executor.shutdown();

        Runnable r = () -> System.out.println("Hello!");
        Thread t1=new Thread(r);
        t1.start();

        Runnable r1=()->{
            for (int i = 0; i < 10; i++) {
                System.out.println(Thread.currentThread().getName()+i);
            }
        };
        Thread t2=new Thread(r1,"Ram");
        t2.start();

        Runnable r2=()->{
            for (int i = 0; i < 10; i++) {
                System.out.println(Thread.currentThread().getName()+i);
            }
        };
        Thread t3=new Thread(r2,"Shyam");
        t3.start();
        t2.join();
        t3.join();

        System.out.println("Main : "+Thread.currentThread().getName());


    }
}
