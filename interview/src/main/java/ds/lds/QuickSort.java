package ds.lds;

/*
Java's Arrays.sort() for primitive types
C++ std::sort()
Database query optimization
Operating system task scheduling
 */
public class QuickSort {
    // Used in: Standard library sort (Java Arrays.sort for primitives)
    // Time: O(n log n) average, O(n²) worst, Space: O(log n)
    
    public static void main(String[] args) {
        int[] arr = {10, 7, 8, 9, 1, 5};
        
        System.out.println("Original array:");
        printArray(arr);
        
        quickSort(arr, 0, arr.length - 1);
        
        System.out.println("\nSorted array:");
        printArray(arr);
    }
    
    static void quickSort(int[] arr, int low, int high) {
        if (low < high) {
            // Partition index
            int pi = partition(arr, low, high);
            
            // Recursively sort elements before and after partition
            quickSort(arr, low, pi - 1);
            quickSort(arr, pi + 1, high);
        }
    }
    
    static int partition(int[] arr, int low, int high) {
        // Choose rightmost element as pivot
        int pivot = arr[high];
        int i = low - 1;  // Index of smaller element
        
        for (int j = low; j < high; j++) {
            if (arr[j] < pivot) {
                i++;
                // Swap arr[i] and arr[j]
                int temp = arr[i];
                arr[i] = arr[j];
                arr[j] = temp;
            }
        }
        
        // Swap arr[i+1] and arr[high] (pivot)
        int temp = arr[i + 1];
        arr[i + 1] = arr[high];
        arr[high] = temp;
        
        return i + 1;
    }
    
    static void printArray(int[] arr) {
        for (int num : arr) System.out.print(num + " ");
    }
}