package ds.lds;

import java.util.*;

public class TwoSum {
    public static void main(String[] args) {
        int[] arr = {2, 7, 11, 15, 3, 6};
        int target = 9;

        System.out.println("Array: ");
        printArray(arr);
        System.out.println("\nTarget Sum: " + target);

        int[] result = findTwoSum(arr, target);

        if (result[0] != -1) {
            System.out.println("\nPair found: arr[" + result[0] + "] + arr[" + result[1] + "]");
            System.out.println("Values: " + arr[result[0]] + " + " + arr[result[1]] + " = " + target);
        } else {
            System.out.println("\nNo pair found!");
        }
    }

    static int[] findTwoSum(int[] arr, int target) {
        HashMap<Integer, Integer> map = new HashMap<>();

        for (int i = 0; i < arr.length; i++) {
            int complement = target - arr[i];

            if (map.containsKey(complement)) {
                return new int[]{map.get(complement), i};
            }

            map.put(arr[i], i);
        }

        return new int[]{-1, -1};
    }

    static void printArray(int[] arr) {
        for (int num : arr) {
            System.out.print(num + " ");
        }
    }
}