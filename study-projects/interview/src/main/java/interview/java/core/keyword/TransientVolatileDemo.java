package interview.java.core.keyword;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class TransientVolatileDemo {
    public static void main(String[] args) throws Exception {
        MyEvent event = new MyEvent();

        // Thread 1 updates the flag
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) { }
            event.setFromNetwork(true);
            System.out.println("Thread-1 set fromNetwork = true");
        }).start();

        // Thread 2 reads the flag continuously
        new Thread(() -> {
            while (!event.isFromNetwork()) {
                // waiting until visible
            }
            System.out.println("Thread-2 detected fromNetwork = true");
        }).start();

        Thread.sleep(2000);  // wait threads to finish

        // Now serialize the event
        System.out.println("Before serialization: " + event);

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("event.ser"))) {
            out.writeObject(event);
        }

        // Deserialize the event
        MyEvent restored;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream("event.ser"))) {
            restored = (MyEvent) in.readObject();
        }

        System.out.println("After deserialization: " + restored);
    }
}