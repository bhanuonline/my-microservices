package interview.java.core.keyword;

public class VolatileExample {
    public static void main(String[] args) throws InterruptedException {
        MyEvent event = new MyEvent();

        // Thread 1: simulates a listener waiting for change
        Thread listener = new Thread(() -> {
            System.out.println("Listener waiting for fromCluster to become true...");
            while (!event.isFromCluster()) {
                // busy waiting
            }
            System.out.println("Listener detected fromCluster = " + event.isFromCluster());
        });

        // Thread 2: simulates another thread (like the event receiver) updating the flag
        Thread changer = new Thread(() -> {
            try {
                Thread.sleep(2000); // simulate delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Changer setting fromCluster = true");
            event.setFromCluster(true);
        });

        listener.start();
        changer.start();

        listener.join();
        changer.join();
    }
}