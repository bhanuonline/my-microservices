package ds.lds;

public class SecondLargest {
    public static void main(String[] args) {
        int[] arr = {12, 35, 1, 10, 34, 1};

        System.out.println("Array: ");
        printArray(arr);

        int secondLargest = findSecondLargest(arr);

        if (secondLargest != Integer.MIN_VALUE) {
            System.out.println("\nSecond Largest: " + secondLargest);
        } else {
            System.out.println("\nNo second largest element found!");
        }
    }

    static int findSecondLargest(int[] arr) {
        if (arr.length < 2) {
            return Integer.MIN_VALUE;
        }

        int largest = Integer.MIN_VALUE;
        int secondLargest = Integer.MIN_VALUE;

        for (int num : arr) {
            if (num > largest) {
                secondLargest = largest;
                largest = num;
            } else if (num > secondLargest && num != largest) {
                secondLargest = num;
            }
        }

        return secondLargest;
    }

    static void printArray(int[] arr) {
        for (int num : arr) {
            System.out.print(num + " ");
        }
    }
}