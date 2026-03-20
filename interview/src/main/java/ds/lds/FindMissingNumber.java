package ds.lds;

public class FindMissingNumber {
    public static void main(String[] args) {
        int[] arr = {1, 2, 3, 5, 6, 7, 8};  // Missing: 4
        
        System.out.println("Array: ");
        printArray(arr);
        
        int n = arr.length + 1;  // Should have n elements
        int missing = findMissing(arr, n);
        
        System.out.println("\n\nMissing number: " + missing);
    }
    
    static int findMissing(int[] arr, int n) {
        // Sum of first n natural numbers: n * (n + 1) / 2
        int expectedSum = n * (n + 1) / 2;
        
        int actualSum = 0;
        for (int num : arr) {
            actualSum += num;
        }
        
        return expectedSum - actualSum;
    }
    
    static void printArray(int[] arr) {
        for (int num : arr) {
            System.out.print(num + " ");
        }
    }
}