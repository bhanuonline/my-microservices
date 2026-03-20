package ds.lds;

/*
Sorting large files (external merge sort)
Database sorting operations
Version control systems (Git merge)
Parallel processing algorithms
 */
public class MergeSort {
    // Used in: External sorting, stable sorting, parallel processing
    // Time: O(n log n) guaranteed, Space: O(n)
    
    public static void main(String[] args) {
        int[] arr = {38, 27, 43, 3, 9, 82, 10};
        
        System.out.println("Original array:");
        printArray(arr);
        
        mergeSort(arr, 0, arr.length - 1);
        
        System.out.println("\nSorted array:");
        printArray(arr);
    }
    
    static void mergeSort(int[] arr, int left, int right) {
        if (left < right) {
            int mid = left + (right - left) / 2;
            
            // Sort first and second halves
            mergeSort(arr, left, mid);
            mergeSort(arr, mid + 1, right);
            
            // Merge sorted halves
            merge(arr, left, mid, right);
        }
    }
    
    static void merge(int[] arr, int left, int mid, int right) {
        // Find sizes of two subarrays
        int n1 = mid - left + 1;
        int n2 = right - mid;
        
        // Create temp arrays
        int[] L = new int[n1];
        int[] R = new int[n2];
        
        // Copy data to temp arrays
        for (int i = 0; i < n1; i++) L[i] = arr[left + i];
        for (int j = 0; j < n2; j++) R[j] = arr[mid + 1 + j];
        
        // Merge temp arrays back
        int i = 0, j = 0, k = left;
        
        while (i < n1 && j < n2) {
            if (L[i] <= R[j]) {
                arr[k] = L[i];
                i++;
            } else {
                arr[k] = R[j];
                j++;
            }
            k++;
        }
        
        // Copy remaining elements
        while (i < n1) arr[k++] = L[i++];
        while (j < n2) arr[k++] = R[j++];
    }
    
    static void printArray(int[] arr) {
        for (int num : arr) System.out.print(num + " ");
    }
}