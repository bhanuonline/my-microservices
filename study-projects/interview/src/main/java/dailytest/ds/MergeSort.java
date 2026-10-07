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

    private static void mergesort(int[] arr, int l, int r){
        if(l<r){
            int m=l+(r-l)/2;
            mergesort(arr,l,m);
            mergesort(arr,m+1,r);
            merge(arr,l,m,r);
        }
    }

    private static void merge(int[] arr, int l, int m, int r){
        int n1=m-l+1;
        int n2=r-m;

        int[] left=new int[n1];
        int[] right=new int[n2];

        for (int i = 0; i <n1 ; i++) {
            left[i]=arr[i+l];
        }
        for (int i = 0; i <n2 ; i++) {
            right[i]=arr[i+m+1];
        }

        int i=0;
        int j=0;
        int k=l;
        while (i<n1 && j<n2){
            if(left[i]<right[j]){
                arr[k]=left[i];
                i++;
            }
            else{
                arr[k]=right[j];
                j++;
            }
            k++;
        }

        while (i<n1){
            arr[k]=left[i];
            i++;
            k++;
        }
        while (j<n2){
            arr[k]=right[j];
            j++;
            k++;
        }
    }
}
