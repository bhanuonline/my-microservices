package interview.java.core.memory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

public class HeapConfigCheck {
    public static void main(String[] args) {
        long initialHeap = Runtime.getRuntime().totalMemory();
        long maxHeap = Runtime.getRuntime().maxMemory();
        
        System.out.println("Initial Heap (Xms): " + initialHeap / (1024 * 1024) + " MB");
        System.out.println("Max Heap (Xmx): " + maxHeap / (1024 * 1024) + " MB");
        
        // Optional: Detailed memory info
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        System.out.println("Heap Memory Usage: " + heapUsage);
    }
}