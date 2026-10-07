package dailytest.java.thread;
public class ThreadTest {

    static Object lock=new Object();
   static int number=1;
    public static void main(String[] args) throws InterruptedException {
        Thread t=Thread.currentThread();
        System.out.println(t.getName());
        t.setName("Hello");
        System.out.println(t.getName());

        Runnable  odd=()->{
            synchronized (lock){
                while (number<=10){
                    if(number%2!=0){
                        System.out.println(number +Thread.currentThread().getName());
                        number++;
                        lock.notify();
                    }
                    else {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        };
        Runnable  even=()->{
            synchronized (lock){
                while (number<=10){
                    if(number%2==0){
                        System.out.println(number +Thread.currentThread().getName());
                        number++;
                        lock.notify();
                    }
                    else {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

        };
        Thread t1=new Thread(odd,"odd");
        Thread t2=new Thread(even,"even");
        t1.start();
        t2.start();


    }
}
