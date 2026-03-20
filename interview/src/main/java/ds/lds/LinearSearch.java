package ds.lds;

public class LinearSearch {
    public static void main(String[] args) {
        int[] arr = {64, 34, 25, 12, 22, 11, 90};
        int target = 22;
        
        System.out.println("Array: ");
        printArray(arr);
        
        int index = linearSearch(arr, target);
        
        if (index != -1) {
            System.out.println("\nElement " + target + " found at index: " + index);
        } else {
            System.out.println("\nElement " + target + " not found!");
        }
    }
    
    static int linearSearch(int[] arr, int target) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) {
                return i;
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