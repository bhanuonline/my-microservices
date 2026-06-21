package ds.arrayTraversal;

import java.util.*;

/*
Use index when you also need to know where the maximum element is located.
Use value when you only need the maximum number.
if dont need index the use it for loop
 */
public class ArrayTraversal {
    public static void main(String[] args) {
        int[] arr={111,12,23,45,67,90,1,44,100,12};
        findMaximumElement(arr);
        findMinimumElement(arr);
        otherApprochTofinNum(arr);

        secondLargestAndSmallestElement2(arr);
        secondLargestAndSmallestElement3(arr);
        secondLargestAndSmallestElement(arr);
        mathOprationOnArray(arr);
        frequencyCount(arr);


    }

    public static List<Integer> findMultipleMissing(int[] arr, int n) {
        Set<Integer> set = new HashSet<>();
        for (int num : arr) set.add(num);

        List<Integer> missing = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            if (!set.contains(i)) missing.add(i);
        }
        return missing;
    }
    // Array contains n distinct numbers from 0 to n
    public  int missingNumber(int[] nums) {
        int n = nums.length;
        int expectedSum = n * (n + 1) / 2;
        int actualSum = 0;
        for (int num : nums) actualSum += num;
        return expectedSum - actualSum;
    }

    public static int findMissingBinarySearch(int[] arr) {
        int left = 0, right = arr.length - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2;

            // If index and value match, missing is on the right
            if (arr[mid] == mid + 1) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return left + 1;
    }

    public static void findMissingInAp(){
        int[] arr = {2, 4, 6, 10, 12};
        int diff = (arr[arr.length - 1] - arr[0]) / arr.length;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] - arr[i - 1] != diff) {
                System.out.println("Missing Number = " + (arr[i - 1] + diff));
                break;
            }
        }
    }
    private static void frequencyCount(int[] arr) {

    }

    private static void mathOprationOnArray(int[] arr) {

        Arrays.stream(arr).average().getAsDouble();
    }

    private static void secondLargestAndSmallestElement3(int[] arr) {
        int k=2;
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        PriorityQueue<Integer> maxHeap =
                new PriorityQueue<>(Collections.reverseOrder());

        for (int num : arr) {

            // K-th Largest
            minHeap.offer(num);
            if (minHeap.size() > k) {
                minHeap.poll();
            }

            // K-th Smallest
            maxHeap.offer(num);
            if (maxHeap.size() > k) {
                maxHeap.poll();
            }
        }

        System.out.println(k + "th Largest = " + minHeap.peek());
        System.out.println(k + "th Smallest = " + maxHeap.peek());
    }

    private static void secondLargestAndSmallestElement(int[] arr) {
        int num=2;
       int n=arr.length;
        for (int i = 0; i < n-1; i++) {
            int k=i;
            for (int j = i+1; j <n ; j++) {
                if(arr[j]>arr[k]){
                    k=j;
                }
            }
           if(i!=k){
               int temp=arr[k];
               arr[k]=arr[i];
               arr[i]=temp;
           }
        }
        System.out.println("Sort array :"+Arrays.toString(arr));
        System.out.println("Second Largest :"+arr[num-1]+" Second Smallest :"+arr[arr.length-num]);
    }
    private static void secondLargestAndSmallestElement2(int[] arr) {

        int largest = Integer.MIN_VALUE;
        int secondLargest = Integer.MIN_VALUE;

        int smallest = Integer.MAX_VALUE;
        int secondSmallest = Integer.MAX_VALUE;

        for (int num : arr) {

            // Largest and Second Largest
            if (num > largest) {
                secondLargest = largest;
                largest = num;
            } else if (num > secondLargest && num != largest) {
                secondLargest = num;
            }

            // Smallest and Second Smallest
            if (num < smallest) {
                secondSmallest = smallest;
                smallest = num;
            } else if (num < secondSmallest && num != smallest) {
                secondSmallest = num;
            }
        }

        System.out.println("Second Largest : " + secondLargest);
        System.out.println("Second Smallest : " + secondSmallest);
    }

    private static void otherApprochTofinNum(int[] arr) {
        int max = Arrays.stream(arr).max().getAsInt();
        int max1 = Arrays.stream(arr).max().orElseThrow();

        //Not recommended
        //Arrays.sort(arr);
        System.out.println(arr[arr.length - 1]);
    }

    private static void findMinimumElement(int[] arr) {
        int min=arr[0];
        for (int num :arr){
            if(num<min){
                min=num;
            }
        }
        System.out.println("Min element :"+min);
    }

    private static void findMaximumElement(int[] arr) {
        int max=arr[0];
        for (int i = 1; i < arr.length ; i++) {
            if(arr[i]> max){
                max=arr[i];
            }
        }
        System.out.println("Maximum Number: "+max);
    }
}
