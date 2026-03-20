package ds.lds;

public class DutchNationalFlag {
    public static void main(String[] args) {
        int[] arr = {2, 0, 2, 1, 1, 0, 0, 1, 2, 1, 0};
        
        System.out.println("Original Array:");
        printArray(arr);
        
        sortColors(arr);
        
        System.out.println("\n\nSorted Array:");
        printArray(arr);
    }
    
    static void sortColors(int[] arr) {
        int low = 0, mid = 0, high = arr.length - 1;
        
        while (mid <= high) {
            if (arr[mid] == 0) {
                // Swap arr[low] and arr[mid]
                int temp = arr[low];
                arr[low] = arr[mid];
                arr[mid] = temp;
                low++;
                mid++;
            } else if (arr[mid] == 1) {
                mid++;
            } else {  // arr[mid] == 2
                // Swap arr[mid] and arr[high]
                int temp = arr[mid];
                arr[mid] = arr[high];
                arr[high] = temp;
                high--;
            }
        }
    }
    
    static void printArray(int[] arr) {
        for (int num : arr) {
            System.out.print(num + " ");
        }
    }
}