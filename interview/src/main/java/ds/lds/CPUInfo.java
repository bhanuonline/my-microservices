package ds.lds;

public class CPUInfo {
    public static void main(String[] args) {
        
        // Get CPU information
        System.out.println("=== CPU INFORMATION ===\n");
        
        // Number of CPU cores
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("CPU Cores: " + cores);
        
        // CPU Architecture (tells us register size)
        String arch = System.getProperty("os.arch");
        System.out.println("Architecture: " + arch);
        
        if (arch.contains("64")) {
            System.out.println("Register Size: 64-bit (8 bytes)");
        } else if (arch.contains("86") || arch.contains("32")) {
            System.out.println("Register Size: 32-bit (4 bytes)");
        }
        
        System.out.println("\n=== MEMORY INFORMATION ===\n");
        
        // RAM information
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        
        System.out.println("Max Memory (RAM): " + (maxMemory / (1024 * 1024)) + " MB");
        System.out.println("Total Memory: " + (totalMemory / (1024 * 1024)) + " MB");
        System.out.println("Free Memory: " + (freeMemory / (1024 * 1024)) + " MB");
        
        System.out.println("\n⚠️ Note: Java cannot directly access CPU cache size.");
        System.out.println("Use system commands below to check cache size.");
    }
}
