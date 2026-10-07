package testing;

public class SizeDemo {
    public static void main(String[] args) {

        // Array sizes
        int[]    intArr    = new int[10];
        double[] doubleArr = new double[10];
        byte[]   byteArr   = new byte[10];

        System.out.println("int[10]    = " + (intArr.length    * 4) + " bytes"); // 40
        System.out.println("double[10] = " + (doubleArr.length * 8) + " bytes"); // 80
        System.out.println("byte[10]   = " + (byteArr.length   * 1) + " bytes"); // 10

        // Heap size
        Runtime rt = Runtime.getRuntime();
        System.out.println("\nHeap Used  = " +
            (rt.totalMemory() - rt.freeMemory()) / 1024 + " KB");
        System.out.println("Heap Total = " +
            rt.totalMemory() / 1024 + " KB");
    }
}