package interview.java.core.memory;

import java.util.ArrayList;
import java.util.List;

public class MemoryLeakExample {
    public static void main(String[] args) {
        List<int[]> memoryLeakList = new ArrayList<>();

        while (true) {
            int[] leak = new int[1]; // ~4MB
            memoryLeakList.add(leak); // Keeping reference prevents GC
            System.out.println("Size: " + memoryLeakList.size());
        }
    }
}