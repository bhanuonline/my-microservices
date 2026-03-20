package ds.lds;

/*
Financial data analysis
Image processing (brightest region)
Weather pattern analysis
Network load balancing
 */
public class KadaneAdvanced {
    // Maximum subarray sum, circular array, 2D matrix
    
    public static void main(String[] args) {
        int[] arr = {-2, 1, -3, 4, -1, 2, 1, -5, 4};
        
        System.out.println("Array: ");
        printArray(arr);
        
        // Standard Kadane
        int maxSum = kadane(arr);
        System.out.println("\nMax Subarray Sum: " + maxSum);
        
        // Maximum circular subarray
        int[] circular = {8, -1, 3, 4};
        System.out.println("\nCircular Array: ");
        printArray(circular);
        int circularMax = maxCircularSum(circular);
        System.out.println("Max Circular Subarray Sum: " + circularMax);
        
        // 2D Kadane
        int[][] matrix = {
            {1, 2, -1, -4, -20},
            {-8, -3, 4, 2, 1},
            {3, 8, 10, 1, 3},
            {-4, -1, 1, 7, -6}
        };
        System.out.println("\n2D Matrix Max Submatrix Sum:");
        int max2D = kadane2D(matrix);
        System.out.println("Max Sum: " + max2D);
    }
    
    // Standard Kadane's Algorithm
    static int kadane(int[] arr) {
        int maxSoFar = arr[0];
        int maxEndingHere = arr[0];
        
        for (int i = 1; i < arr.length; i++) {
            maxEndingHere = Math.max(arr[i], maxEndingHere + arr[i]);
            maxSoFar = Math.max(maxSoFar, maxEndingHere);
        }
        
        return maxSoFar;
    }
    
    // Maximum Circular Subarray Sum
    static int maxCircularSum(int[] arr) {
        int n = arr.length;
        
        // Case 1: Normal Kadane
        int maxKadane = kadane(arr);
        
        // Case 2: Circular (total - min subarray)
        int maxWrap = 0;
        for (int i = 0; i < n; i++) {
            maxWrap += arr[i];
            arr[i] = -arr[i];  // Invert
        }
        
        maxWrap = maxWrap + kadane(arr);  // Total + (-minimum)
        
        // Restore array
        for (int i = 0; i < n; i++) {
            arr[i] = -arr[i];
        }
        
        return Math.max(maxKadane, maxWrap);
    }
    
    // 2D Kadane (Maximum sum rectangle)
    static int kadane2D(int[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;
        int maxSum = Integer.MIN_VALUE;
        
        for (int left = 0; left < cols; left++) {
            int[] temp = new int[rows];
            
            for (int right = left; right < cols; right++) {
                // Add current column to temp
                for (int i = 0; i < rows; i++) {
                    temp[i] += matrix[i][right];
                }
                
                // Apply Kadane on temp
                int sum = kadane(temp);
                maxSum = Math.max(maxSum, sum);
            }
        }
        
        return maxSum;
    }
    
    static void printArray(int[] arr) {
        for (int num : arr) System.out.print(num + " ");
        System.out.println();
    }
}