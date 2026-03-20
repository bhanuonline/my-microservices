package ds.lds;

public class BinarySearch {
    public static void main(String[] args) {
        int[] arr = {2, 5, 8, 12, 16, 23, 38, 45, 56, 67, 78};
        int target = 23;
        
        System.out.println("Sorted Array: ");
        printArray(arr);
        
        int index = binarySearch(arr, target);
        
        if (index != -1) {
            System.out.println("\nElement " + target + " found at index: " + index);
        } else {
            System.out.println("\nElement " + target + " not found!");
        }
    }
    
    static int binarySearch(int[] arr, int target) {
        int left = 0;
        int right = arr.length - 1;
        
        while (left <= right) {
            int mid = left + (right - left) / 2;
            
            System.out.println("Checking index " + mid + " (value: " + arr[mid] + ")");
            
            if (arr[mid] == target) {
                return mid;
            } else if (arr[mid] < target) {
                left = mid + 1;  // Search right half
            } else {
                right = mid - 1;  // Search left half
            }
        }
        
        return -1;  // Not found
    }
    
    static void printArray(int[] arr) {
        for (int num : arr) {
            System.out.print(num + " ");
        }
    }
}