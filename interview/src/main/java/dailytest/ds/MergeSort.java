package dailytest.ds;

public class MergeSort {
    public static void main(String[] args) {
        int[] arr ={25,0,1,45,67,10,23,-1,9};



        int len=arr.length-1;
        mergesort(arr,0,len);

        for (int i = 0; i < len; i++) {
            System.out.println(arr[i]);
        }
    }

    private static void mergesort(int[] arr, int left, int right){

    }

    private static void merge(int[] arr, int left, int mid, int right){

    }

}
