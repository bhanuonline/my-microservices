package testing;

public class MemoryCheck {
    public static void main(String[] args) {

        Runtime rt = Runtime.getRuntime();

        long totalHeap = rt.totalMemory();   // total heap given to JVM
        long freeHeap  = rt.freeMemory();    // unused heap
        long usedHeap  = totalHeap - freeHeap; // currently used
        long maxHeap   = rt.maxMemory();     // maximum heap JVM can use

        System.out.println("Total Heap : " + totalHeap / (1024*1024) + " MB");
        System.out.println("Used  Heap : " + usedHeap  / (1024*1024) + " MB");
        System.out.println("Free  Heap : " + freeHeap  / (1024*1024) + " MB");
        System.out.println("Max   Heap : " + maxHeap   / (1024*1024) + " MB");
    }
}