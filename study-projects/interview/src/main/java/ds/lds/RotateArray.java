package ds.lds;

public class RotateArray {
    public static void main(String[] args) {
        int[] arr = {1, 2, 3, 4, 5, 6, 7};
        int k = 3;  // Rotate by 3 positions

        System.out.println("Original Array:");
        printArray(arr);

        rotateRight(arr, k);

        System.out.println("\nArray after rotating right by " + k + " positions:");
        printArray(arr);
    }

    static void rotateRight(int[] arr, int k) {
        int n = arr.length;
        k = k % n;  // Handle k > n

        // Reverse entire array
        reverse(arr, 0, n - 1);

        // Reverse first k elements
        reverse(arr, 0, k - 1);

        // Reverse remaining elements
        reverse(arr, k, n - 1);
    }

    static void reverse(int[] arr, int start, int end) {
        while (start < end) {
            int temp = arr[start];
            arr[start] = arr[end];
            arr[end] = temp;
            start++;
            end--;
        }
    }

    static void printArray(int[] arr) {
        for (int num : arr) {
            System.out.print(num + " ");
        }
        System.out.println();
    }
}