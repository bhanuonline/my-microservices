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
        if(left<right){
            int pi=partition(arr,left,right);
            quicksort(arr,left,pi-1);
            quicksort(arr,pi+1,right);
        }

    }

    private static int partition(int[] arr, int left, int right) {
        int pii=left+(int)(Math.random()*(right-left+1));
        int pivotIndex = left + (int)(Math.random() * (right - left + 1));
        swap(arr,pivotIndex,right);
        int pi=arr[pivotIndex];
        int i=left-1;

        for (int j = left+1; j <right ; j++) {
            if(arr[j]<pi){
                i++;
                swap(arr, i, j);
            }
        }
        swap(arr, i + 1, right);
        return i+1;
    }

    private static void swap(int[] arr, int i, int j) {
        int temp= arr[i];
        arr[i]= arr[j];
        arr[j]=temp;
    }
}
