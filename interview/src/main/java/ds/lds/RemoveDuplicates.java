package ds.lds;

public class RemoveDuplicates {
    public static void main(String[] args) {
        int[] arr = {1, 1, 2, 2, 2, 3, 4, 4, 5, 5, 5, 6};
        
        System.out.println("Original Array:");
        printArray(arr, arr.length);
        
        int newLength = removeDuplicates(arr);
        
        System.out.println("\n\nArray after removing duplicates:");
        printArray(arr, newLength);
    }
    
    static int removeDuplicates(int[] arr) {
        if (arr.length == 0) return 0;
        
        int j = 0;  // Position for unique elements
        
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] != arr[j]) {
                j++;
                arr[j] = arr[i];
            }
        }
        
        return j + 1;  // New length
    }
    
    static void printArray(int[] arr, int length) {
        for (int i = 0; i < length; i++) {
            System.out.print(arr[i] + " ");
        }
    }
}