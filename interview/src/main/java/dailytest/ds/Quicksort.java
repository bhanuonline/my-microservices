package dailytest.ds;

public class Quicksort {
    public static void main(String[] args) {
        int[] arr={12,54,67,3,2,89,0,44};
        int len=arr.length-1;
        quicksort(arr,0,len);

        for (int i = 0; i < len; i++) {
            System.out.println(arr[i]);
        }
    }

    private static void quicksort(int[] arr, int left, int right) {

    }

    private static int partition(int[] arr, int left, int right) {
        return 0;
    }

    private static void swap(int[] arr, int i, int j) {
        int temp= arr[i];
        arr[i]= arr[j];
        arr[j]=temp;
    }
}
