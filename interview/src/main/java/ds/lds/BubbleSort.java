package ds.lds;

public class BubbleSort {
    public static void main(String[] args) {
        int[] arr = {64, 34, 25, 12, 22, 11, 90};

        System.out.println("Original Array:");
        printArray(arr);

        bubbleSort(arr);

        System.out.println("\n\nSorted Array:");
        printArray(arr);
    }

    static void bubbleSort(int[] arr) {
        int n = arr.length;

        for (int i = 0; i < n - 1; i++) {
            System.out.println("\nPass " + (i + 1) + ":");

            for (int j = 0; j < n - i - 1; j++) {
                if (arr[j] > arr[j + 1]) {
                    // Swap
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;

                    System.out.print("Swapped " + arr[j+1] + " and " + arr[j] + " -> ");
                    printArray(arr);
                    System.out.println();
                }
            }
        }
    }

    static void printArray(int[] arr) {
        for (int num : arr) {
            System.out.print(num + " ");
        }
    }
}