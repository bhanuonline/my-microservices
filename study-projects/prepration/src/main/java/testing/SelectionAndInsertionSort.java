package testing;

import java.util.Arrays;

public class SelectionAndInsertionSort {
    public static void main(String[] args) {
        int[] arr = {5, 2, 8, 1, 9, 3};
       // ss(arr);
        is(arr);
        System.out.println(Arrays.toString(arr));
    }

    private static void is(int[] arr) {
        int len=arr.length;
        for (int i = 1; i <len ; i++) {
            int key=arr[i];
            int k=i-1;
            // Shift larger elements to the right
            while (k>=0 && arr[k] > key){
                    arr[k+1]=arr[k];
                    k--;
            }
            arr[k+1]=key;
        }
    }

    private static void ss(int[] arr) {
        int len=arr.length;
        for (int i = 0; i < len-1; i++) {
            int min=i;
            for (int j = i+1; j < len; j++) {
                if(arr[j]<arr[min]){
                    min=j;
                }
            }
            if(i!=min){
                int temp=arr[min];
                arr[min]=arr[i];
                arr[i]=temp;
            }

        }
    }
}
