package testing;

import java.lang.reflect.Array;
import java.util.Arrays;

public class MergeSort {

    public static void sort(int[] arr) {
        if (arr == null || arr.length < 2) return;
       // mergeSort(arr, 0, arr.length - 1);
        myTestMergeSort(arr,0,arr.length-1);
    }

    public static void main(String[] args) {
        int arr[]={23,7,9,0,-1,18,45};
        myTestMergeSort(arr,0,arr.length-1);
        System.out.println(Arrays.toString(arr));
    }

    private static void myTestMergeSort(int[] arr, int l, int r) {
        if(l<r){
            int mid=l+(r-l)/2;
            myTestMergeSort(arr,l,mid);
            myTestMergeSort(arr,mid+1,r);
            myMerge(arr,l,mid,r);
        }
    }

    private static void myMerge(int[] arr, int l, int mid, int r) {
        int n1=mid-l+1;
        int n2=r-mid;

        int[] l1=new int[n1];
        int[] l2=new int[n2];

        for (int i = 0; i <n1 ; i++) {
            l1[i]=arr[i+l];
        }
        for (int i = 0; i <n2 ; i++) {
            l2[i]=arr[i+mid+1];
        }

        int i=0;
        int j=0;
        int k=l;
        while (i<n1 && j<n2){
            if(l1[i]<l2[j]){
                arr[k]=l1[i];
                i++;
            }
            else{
                arr[k]=l2[j];
                j++;
            }
            k++;
        }

        while (i<n1){
            arr[k]=l1[i];
            i++;
            k++;
        }
        while (j<n2){
            arr[k]=l2[j];
            j++;
            k++;
        }
    }

    private static void mergeSort(int[] arr, int left, int right) {
        if (left >= right) return;
        int mid = (left + right) / 2;
        mergeSort(arr, left, mid);
        mergeSort(arr, mid + 1, right);
        merge(arr, left, mid, right);
    }

    private static void merge(int[] arr, int left, int mid, int right) {
        int[] tmp = new int[right - left + 1];
        int i = left, j = mid + 1, k = 0;

        while (i <= mid && j <= right) {
            tmp[k++] = (arr[i] <= arr[j]) ? arr[i++] : arr[j++];
        }
        while (i <= mid)   tmp[k++] = arr[i++];
        while (j <= right) tmp[k++] = arr[j++];

        System.arraycopy(tmp, 0, arr, left, tmp.length);
    }
}
