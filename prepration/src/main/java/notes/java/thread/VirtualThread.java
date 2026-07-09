package notes.java.thread;

public class VirtualThread {
    public static void main(String[] args) {
        for (int i = 0; i <1000000 ; i++) {
            new Thread(()-> {
                try {
                    Thread.sleep(10);
                    System.out.println(Thread.currentThread().getName());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            }).start();

        }
    }
}
