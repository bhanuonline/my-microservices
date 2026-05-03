package ds.lds;

import java.util.*;

public class LongestIncreasingSubsequence {
    // Used in: Bioinformatics, data compression, pattern matching
    // Time: O(n log n), Space: O(n)

    public static void main(String[] args) {
        int[] arr = {10, 9, 2, 5, 3, 7, 101, 18};

        System.out.println("Array: ");
        printArray(arr);

        int length = lengthOfLIS(arr);
        System.out.println("\n\nLength of LIS: " + length);

        // Also print the actual LIS
        List<Integer> lis = findLIS(arr);
        System.out.println("LIS sequence: " + lis);
    }

    // Binary Search approach - O(n log n)
    static int lengthOfLIS(int[] nums) {
        ArrayList<Integer> tails = new ArrayList<>();

        for (int num : nums) {
            int pos = binarySearch(tails, num);

            if (pos == tails.size()) {
                tails.add(num);
            } else {
                tails.set(pos, num);
            }
        }

        return tails.size();
    }

    static int binarySearch(ArrayList<Integer> tails, int target) {
        int left = 0, right = tails.size();

        while (left < right) {
            int mid = left + (right - left) / 2;
            if (tails.get(mid) < target) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }

        return left;
    }

    // Find actual LIS sequence
    static List<Integer> findLIS(int[] nums) {
        int n = nums.length;
        int[] dp = new int[n];
        int[] parent = new int[n];
        Arrays.fill(dp, 1);
        Arrays.fill(parent, -1);

        int maxLen = 1, maxIdx = 0;

        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (nums[j] < nums[i] && dp[j] + 1 > dp[i]) {
                    dp[i] = dp[j] + 1;
                    parent[i] = j;
                }
            }

            if (dp[i] > maxLen) {
                maxLen = dp[i];
                maxIdx = i;
            }
        }

        // Reconstruct LIS
        List<Integer> lis = new ArrayList<>();
        int curr = maxIdx;
        while (curr != -1) {
            lis.add(0, nums[curr]);
            curr = parent[curr];
        }

        return lis;
    }

    static void printArray(int[] arr) {
        for (int num : arr) System.out.print(num + " ");
    }
}