package ds.lds;

public class ExponentialSearch {
    // Used in: Infinite arrays, database indexing, search engines

    public static void main(String[] args) {
        int[] arr = {2, 3, 4, 10, 40, 50, 60, 70, 80, 90, 100, 110, 120};
        int target = 70;

        int result = exponentialSearch(arr, target);

        if (result != -1) {
            System.out.println("Element found at index: " + result);
        } else {
            System.out.println("Element not found");
        }
    }

    static int exponentialSearch(int[] arr, int target) {
        if (arr[0] == target) return 0;

        // Find range for binary search by repeated doubling
        int i = 1;
        while (i < arr.length && arr[i] <= target) {
            i = i * 2;
        }

        // Binary search in found range
        return binarySearch(arr, target, i / 2, Math.min(i, arr.length - 1));
    }

    static int binarySearch(int[] arr, int target, int left, int right) {
        while (left <= right) {
            int mid = left + (right - left) / 2;

            if (arr[mid] == target) return mid;
            if (arr[mid] < target) left = mid + 1;
            else right = mid - 1;
        }
        return -1;
    }
}