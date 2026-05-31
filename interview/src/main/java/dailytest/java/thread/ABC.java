package dailytest.java.thread;

//ABC ABC  ABC ABC ABC ABC .....
public class ABC {
    static int state=1;
    static Object lock=new Object();

    public static void main(String[] args) {
        Runnable a=()->{
            for (int j = 0; j < 5; j++) {
                synchronized (lock){
                    while(state!=1){
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    System.out.print("A ");
                    state=2;
                    lock.notifyAll();
                }
            }
        };
        Runnable b=()->{
            for (int j = 0; j < 5; j++) {
                synchronized (lock){
                    while(state!=2){
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    System.out.print("B ");
                    state=3;
                    lock.notifyAll();
                }
            }
        };

        Runnable c=()->{
            for (int j = 0; j < 5; j++) {
                synchronized (lock){
                    while(state!=3){
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    System.out.print("C ");
                    state=1;
                    lock.notifyAll();
                }
            }
        };

        Thread A=new Thread(a);
        Thread B=new Thread(b);
        Thread C=new Thread(c);

        A.start();
        B.start();
        C.start();
    }
}
