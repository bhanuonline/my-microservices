package ds.lds;

public class MaxSubarraySum {
    public static void main(String[] args) {
        int[] arr = {-2, 1, -3, 4, -1, 2, 1, -5, 4};

        System.out.println("Array: ");
        printArray(arr);

        int maxSum = kadaneAlgorithm(arr);

        System.out.println("\n\nMaximum Subarray Sum: " + maxSum);
    }

    static int kadaneAlgorithm(int[] arr) {
        int maxSoFar = arr[0];
        int maxEndingHere = arr[0];

        System.out.println("\nStep-by-step:");
        System.out.println("Index 0: maxEndingHere = " + maxEndingHere + ", maxSoFar = " + maxSoFar);

        for (int i = 1; i < arr.length; i++) {
            maxEndingHere = Math.max(arr[i], maxEndingHere + arr[i]);
            maxSoFar = Math.max(maxSoFar, maxEndingHere);

            System.out.println("Index " + i + ": maxEndingHere = " + maxEndingHere + ", maxSoFar = " + maxSoFar);
        }

        return maxSoFar;
    }

    static void printArray(int[] arr) {
        for (int num : arr) {
            System.out.print(num + " ");
        }
    }
}