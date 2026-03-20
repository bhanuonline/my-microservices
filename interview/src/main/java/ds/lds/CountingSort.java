package ds.lds;

/*
Sorting student grades (0-100)
Sorting ages in demographic data
Network packet sorting
Radix sort base algorithm
 */
public class CountingSort {
    // Used in: Sorting when range is known and small
    // Time: O(n + k), Space: O(k) where k = range
    
    public static void main(String[] args) {
        int[] arr = {4, 2, 2, 8, 3, 3, 1, 7, 4, 5};
        
        System.out.println("Original array:");
        printArray(arr);
        
        countingSort(arr);
        
        System.out.println("\nSorted array:");
        printArray(arr);
    }
    
    static void countingSort(int[] arr) {
        int n = arr.length;
        
        // Find maximum element
        int max = arr[0];
        for (int i = 1; i < n; i++) {
            if (arr[i] > max) max = arr[i];
        }
        
        // Create count array
        int[] count = new int[max + 1];
        int[] output = new int[n];
        
        // Store count of each element
        for (int i = 0; i < n; i++) {
            count[arr[i]]++;
        }
        
        // Change count[i] to actual position
        for (int i = 1; i <= max; i++) {
            count[i] += count[i - 1];
        }
        
        // Build output array
        for (int i = n - 1; i >= 0; i--) {
            output[count[arr[i]] - 1] = arr[i];
            count[arr[i]]--;
        }
        
        // Copy output to arr
        for (int i = 0; i < n; i++) {
            arr[i] = output[i];
        }
    }
    
    static void printArray(int[] arr) {
        for (int num : arr) System.out.print(num + " ");
    }
}