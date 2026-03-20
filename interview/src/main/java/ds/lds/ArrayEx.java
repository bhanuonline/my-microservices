package ds.lds;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ThreadLocalRandom;


public class ArrayEx {
    public static void main(String[] args) {
        int[] arr = new int[5];
        general(arr);
        findSum(arr);
        findAvg(arr);
        findMaxAndMin(arr);
        countEvenAndOddNumbers(arr);
        reverseArray(arr);
        secondLargest(arr);
        kthLargest(arr);
        move0toend(arr);
        topKFrequentElements(arr);
        sortedArray(arr);
        linearsearch(arr);
        frequencyCount(arr);
        removeDuplicate();
        rotateKArray();
        findMissing();
        //Greedy algo
        greedyProblem();
        maxProfitGreedyAlgo();
        maxProfitByGreedy();
        assignCookies();// or Matching array
        maximumUnitsOnTruck();


    }

    private static void maximumUnitsOnTruck() {
        int[][] boxTypes = {{1,3},{2,2},{3,1}};
        int truckSize = 4;
        Arrays.sort(boxTypes,((a,b)->b[1]-a[1]));
        int totalUnits = 0;
        for (int[] arr:boxTypes){
            int boxes=arr[0];
            int units=arr[1];
            int boxesToTake = Math.min(truckSize, boxes);
            totalUnits =totalUnits+ boxesToTake * units;
            truckSize =truckSize- boxesToTake;
            if (truckSize == 0) {
                break;
            }

        }
        System.out.println(totalUnits);

    }

    private static void assignCookies() {
        //To do that easily, both arrays must be in order.
        //Give the smallest cookie
        //to the least greedy child
        int[] greedy={1,2,3};//child requirement
        int[] cookies={1,2};//available cookies

        Arrays.sort(greedy);Arrays.sort(cookies);

        int child=0;
        int cookie =0;
        while (child<greedy.length && cookie<cookies.length){
            if(greedy[child]<=cookies[cookie]){
                child++;// child setified
            }else {
                cookie++;  // move to next cookie
            }
        }
        System.out.println("Satisfied child :"+child);
    }

    private static void maxProfitByGreedy() {
        int[] prices={12,45,1,5,6,8,23};
        int minP=Integer.MAX_VALUE;
        int maxP=0;

        for(int price:prices){
            if(price<minP){
                minP=price;
            }
            int profit=price-minP;
            if(profit>maxP){
                maxP=profit;
            }
        }
        System.out.println("Max Profit :"+maxP);
    }

    // local max profit
    private static void maxProfitGreedyAlgo() {
        int[] prices={12,45,1,5,6,8,23};
        int minp=Integer.MAX_VALUE;
        int maxp=0;
        for(int price:prices){
            minp=Math.min(price,minp);
            maxp=Math.max(maxp,price-minp);
        }
        System.out.println("Max Profit :"+maxp);


    }

    private static void greedyProblem() {

    }

    private static void findMissing() {
        int[] arr={11,12,13,14,15,17,18,19,20};

        //int expectedSum = n * (n + 1) / 2;
        int start =arr[0];
        int end=arr[arr.length-1];
        int n = end - start + 1;
        int expectedSum=(start+end)*n/2;
        int actualSum = 0;

        for(int num : arr) {
            actualSum += num;
        }
        System.out.println("Missing no is : "+(expectedSum-actualSum));

    }

    private static void rotateKArray() {
        int[] arr = {1, 2, 3, 4, 5, 6, 7};
        int i = 3;
        int n = arr.length;
        //int k = i % n;
        reverse(arr, 0, n - 1);
        reverse(arr, 0, i - 1);
        reverse(arr, i, n - 1);
        System.out.println(Arrays.toString(arr));
    }

    private static void reverse(int[] arr, int start, int end) {
        while (start < end) {
            int temp = arr[start];
            arr[start] = arr[end];
            arr[end] = temp;

            start++;
            end--;
        }
    }


    private static void removeDuplicate() {
        int[] arr = {1, 1, 2, 2, 3, 4, 4};
        int i = 0;
        for (int j = 1; j < arr.length; j++) {
            if (arr[i] != arr[j]) {
                i++;
                arr[i] = arr[j];
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < i + 1; j++) {
            sb.append(arr[j] + " ");
        }
        System.out.println("Remove duplicate :" + sb);
    }

    private static void frequencyCount(int[] arr) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < arr.length; i++) {
            map.put(arr[i], map.getOrDefault(arr[i], 0) + 1);
        }
        System.out.println("Frequency count : " + map);
    }

    private static void linearsearch(int[] i) {
        int search = 50;
        boolean isFound = false;
        for (int j = 0; j < i.length; j++) {
            if (i[j] == search) {
                isFound = true;
                break;
            }
        }
        System.out.println(isFound ? search + " Element Found in Array" : search + " Element not found in Array ");
    }

    private static void sortedArray(int[] arr) {
        boolean isSorted = true;
        for (int j = 0; j < arr.length - 1; j++) {
            if (arr[j] < arr[j + 1]) {
                isSorted = false;
                break;
            }
        }
        if (isSorted) {
            System.out.println(Arrays.toString(arr) + " Array is sorted");
        } else {
            System.out.println(Arrays.toString(arr) + " :Array is not sorted");
        }
    }

    private static void topKFrequentElements(int[] arr) {
        int k = 2;
        Map<Integer, Integer> map = new HashMap<>();

        for (int num : arr) {
            map.put(num, map.getOrDefault(num, 0) + 1);
        }

        PriorityQueue<Integer> pq =
                new PriorityQueue<>((a, b) -> map.get(b) - map.get(a));

        pq.addAll(map.keySet());

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < k; i++) {
            stringBuilder.append(pq.poll()).append(" ");
        }
        System.out.println("Top " + k + " Fequency element :" + stringBuilder);

    }

    private static void move0toend(int[] i) {
        int index = 0;
        for (int j = 0; j < i.length; j++) {
            if (i[j] != 0) {
                int temp = i[index];
                i[index] = i[j];
                i[j] = temp;
                index++;
            }
        }
        System.out.println("Move 0 to end :" + Arrays.toString(i));
    }

    private static void kthLargest(int[] arr) {
        int k = 3;
        PriorityQueue<Integer> pq = new PriorityQueue<>();
        for (int num : arr) {
            pq.add(num);

            if (pq.size() > k) {
                pq.poll();
            }
        }
        System.out.println(k + "th Largest Element: " + pq.peek());
    }

    /**
     * num > largest → update largest
     * num > secondLargest AND num < largest → update secondLargest
     *
     * @param i
     */
    private static void secondLargest(int[] i) {

        int largest = Integer.MIN_VALUE;
        int secondLargest = Integer.MIN_VALUE;
        for (int num : i) {
            if (num > largest) {
                secondLargest = largest;
                largest = num;
            } else if (num > secondLargest && num != largest) {
                secondLargest = num;
            }
        }
        System.out.println("Second Largest :" + secondLargest);
    }

    //without extra space
    private static void reverseArray(int[] arr) {

        int left = 0;
        int right = arr.length - 1;
        while (left < right) {
            int temp = arr[left];
            arr[left] = arr[right];
            arr[right] = temp;

            left++;
            right--;
        }
        System.out.println("Reverse array: " + Arrays.toString(arr));
    }

    private static void countEvenAndOddNumbers(int[] i) {
        int counteven = 0;
        int countodd = 0;

        for (int j = 0; j < i.length; j++) {
            if (i[j] % 2 == 0) {
                counteven += 1;
            } else {
                countodd += 1;
            }
        }
        System.out.println("Even no : " + counteven + ", Odd no :" + countodd);
    }

    private static void findMaxAndMin(int[] i) {
        int letmax = i[0];
        int letmin = i[0];
        for (int j = 0; j < i.length; j++) {
            if (i[j] > letmax) {
                letmax = i[j];
            }
            if (i[j] < letmin) {
                letmin = i[j];
            }
        }
        System.out.println("Max is :" + letmax + " ,and Min is :" + letmin);
    }

    private static void general(int[] i) {
        System.out.println("Object  ref : " + i);
        int address = System.identityHashCode(i);
        System.out.println("Hashcode : " + address);
        for (int j = 0; j < i.length; j++) {
            // i[j] = (int)(Math.random() * 100);
            i[j] = ThreadLocalRandom.current().nextInt(-10, 50); // 1 to 100

        }
        System.out.println("Array :" + Arrays.toString(i));
    }

    private static void findAvg(int[] i) {
        double avg = 0;
        double sum = 0;
        for (int j = 0; j < i.length; j++) {
            sum += i[j];
        }
        avg = sum / i.length;
        System.out.println("Avrage is :" + avg);
    }

    private static void findSum(int[] i) {
        int sum = 0;
        for (int j = 0; j < i.length; j++) {
            sum += i[j];
        }
        System.out.println("Sum is " + sum);
    }
}
