package ds.lds;

import java.util.*;

/*
Stock market analysis (moving averages)
Network traffic monitoring
Sensor data analysis
Real-time video processing


 */
public class SlidingWindowMaximum {
    // Used in: Real-time data processing, stock analysis
    // Time: O(n), Space: O(k)
    
    public static void main(String[] args) {
        int[] arr = {1, 3, -1, -3, 5, 3, 6, 7};
        int k = 3;
        
        System.out.println("Array: ");
        printArray(arr);
        System.out.println("\nWindow size: " + k);
        
        int[] result = maxSlidingWindow(arr, k);
        
        System.out.println("\nMaximum in each window:");
        printArray(result);
    }
    
    static int[] maxSlidingWindow(int[] nums, int k) {
        if (nums == null || nums.length == 0) return new int[0];
        
        int n = nums.length;
        int[] result = new int[n - k + 1];
        Deque<Integer> deque = new LinkedList<>();
        
        for (int i = 0; i < n; i++) {
            // Remove elements outside window
            while (!deque.isEmpty() && deque.peekFirst() < i - k + 1) {
                deque.pollFirst();
            }
            
            // Remove smaller elements (they're useless)
            while (!deque.isEmpty() && nums[deque.peekLast()] < nums[i]) {
                deque.pollLast();
            }
            
            deque.offerLast(i);
            
            // Add to result
            if (i >= k - 1) {
                result[i - k + 1] = nums[deque.peekFirst()];
            }
        }
        
        return result;
    }
    
    static void printArray(int[] arr) {
        for (int num : arr) System.out.print(num + " ");
        System.out.println();
    }
}