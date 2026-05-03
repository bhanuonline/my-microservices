package ds.lds;

public class TernarySearch {
    // Used in: Optimization problems, finding peak in mountains

    public static void main(String[] args) {
        int[] arr = {1, 3, 5, 7, 9, 11, 13, 15, 14, 12, 10, 8, 6};

        int peak = findPeak(arr, 0, arr.length - 1);
        System.out.println("Peak element: " + arr[peak] + " at index: " + peak);
    }

    static int findPeak(int[] arr, int left, int right) {
        while (right - left > 2) {
            int mid1 = left + (right - left) / 3;
            int mid2 = right - (right - left) / 3;

            if (arr[mid1] < arr[mid2]) {
                left = mid1;
            } else {
                right = mid2;
            }
        }

        // Find max in remaining elements
        int maxIdx = left;
        for (int i = left + 1; i <= right; i++) {
            if (arr[i] > arr[maxIdx]) {
                maxIdx = i;
            }
        }
        return maxIdx;
    }
}