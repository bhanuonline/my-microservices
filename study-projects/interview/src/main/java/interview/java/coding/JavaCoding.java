package interview.java.coding;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
class Employee {
    private int id;
    private String name;
    private String department;
    private int salary;
    private boolean bonusEligibility;

    public Employee(int id, String name, String department, int salary) {
        this.id = id;
        this.name = name;
        this.department = department;
        this.salary = salary;

    }

    @Override
    public String toString() {
        return name + " - " + salary + " - Bonus: " + bonusEligibility;
    }

}

public class JavaCoding {
    public static void compressString(String s) {

        if (s == null || s.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        int count = 1;

        for (int i = 1; i < s.length(); i++) {

            if (s.charAt(i) == s.charAt(i - 1)) {
                count++;
            } else {
                sb.append(s.charAt(i - 1)).append(count);
                count = 1;
            }
        }

        // append last character
        sb.append(s.charAt(s.length() - 1)).append(count);
        System.out.println(sb.toString());
    }

    public static int longestUniqueSubstring(String s) {

        int left = 0;
        int maxLen = 0;
        int start = 0;
        HashSet<Character> set = new HashSet<>();

        for (int right = 0; right < s.length(); right++) {
            char ch = s.charAt(right);
            while (set.contains(ch)) {
                set.remove(s.charAt(left));
                left++;
            }
            set.add(ch);
            maxLen = Math.max(maxLen, right - left + 1);

        }
        System.out.println(left);
        System.out.println(s.substring(left, maxLen));
        return maxLen;
    }

    public static String reverseString(String str) {
        char[] charArray = str.toCharArray();
        int left = 0, right = charArray.length - 1;

        while (left < right) {
            char temp = charArray[left];
            charArray[left] = charArray[right];
            charArray[right] = temp;
            left++;
            right--;
        }

        return new String(charArray);
    }

    public static int maxSubArray(int[] nums) {

        int currentSum = nums[0];
        int maxSum = nums[0];

        for (int i = 1; i < nums.length; i++) {

            currentSum = Math.max(nums[i], currentSum + nums[i]);
            maxSum = Math.max(maxSum, currentSum);
        }

        return maxSum;
    }


    public static char findFirstUnique(String str) {
        Map<Character, Integer> charCount = new LinkedHashMap<>();

        for (char c : str.toCharArray()) {
            charCount.put(c, charCount.getOrDefault(c, 0) + 1);
        }

        for (Map.Entry<Character, Integer> entry : charCount.entrySet()) {
            if (entry.getValue() == 1) {
                return entry.getKey();
            }
        }

        return '\0'; // Return null character if no unique character found
    }

    public static void rotate(int[] arr, int k) {

        int n = arr.length;
        k = k % n;
//Reverse all → reverse first K → reverse rest.
        reverse(arr, 0, n - 1);   // full
        reverse(arr, 0, k - 1);   // first k
        reverse(arr, k, n - 1);   // rest
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

    public static HashSet<Integer> findDuplicates2(int[] nums) {
        HashSet<Integer> duplicates = new HashSet<>();
        HashSet<Integer> seen = new HashSet<>();

        for (int num : nums) {
            if (!seen.add(num)) { // If add returns false, it's a duplicate
                duplicates.add(num);
            }
        }

        return duplicates;
    }

    public static List<Integer> findDuplicates(int[] nums) {

        List<Integer> result = new ArrayList<>();

        for (int i = 0; i < nums.length; i++) {

            int index = Math.abs(nums[i]) - 1;

            if (nums[index] < 0) {
                result.add(Math.abs(nums[i]));
            } else {
                nums[index] = -nums[index];
            }
        }

        return result;
    }

    public static List<Employee> processEmployeeData(List<Employee> employees) {
        return employees.stream()
                .filter(e -> e.getDepartment().equals("Engineering"))
                .sorted(Comparator.comparing(Employee::getSalary).reversed())
                .map(e -> {
                    // Complex transformation logic
                    e.setBonusEligibility(calculateBonusEligibility(e));
                    return e;
                })
                .limit(10)
                .collect(Collectors.toList());
    }

    public List<Employee> processEmployeeData1(List<Employee> employees) {

        PriorityQueue<Employee> pq =
                new PriorityQueue<>(Comparator.comparing(Employee::getSalary));

        for (Employee e : employees) {
            if (!"Engineering".equals(e.getDepartment())) continue;

            e.setBonusEligibility(calculateBonusEligibility(e));

            pq.offer(e);
            if (pq.size() > 10) {
                pq.poll();   // remove smallest salary
            }
        }

        List<Employee> result = new ArrayList<>(pq);
        result.sort(Comparator.comparing(Employee::getSalary).reversed());
        return result;
    }


    public static boolean calculateBonusEligibility(Employee e) {
        return e.getSalary() > 80000;
    }
    public static ListNode mergeKLists(ListNode[] lists) {

        PriorityQueue<ListNode> pq =
                new PriorityQueue<>(Comparator.comparingInt(a -> a.val));

        for (ListNode node : lists) {
            if (node != null) pq.offer(node);
        }

        ListNode dummy = new ListNode(0);
        ListNode tail = dummy;

        while (!pq.isEmpty()) {

            ListNode node = pq.poll();
            tail.next = node;
            tail = tail.next;

            if (node.next != null)
                pq.offer(node.next);
        }

        return dummy.next;
    }
    public static int binarySearch(int[] nums, int target) {
        int left = 0;
        int right = nums.length - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2;

            if (nums[mid] == target)
                return mid;
            else if (nums[mid] < target)
                left = mid + 1;
            else
                right = mid - 1;
        }

        return -1; // Target not found
    }



    public static void main(String[] args) {
        JavaCoding.compressString("aabcccccaaa");
        longestUniqueSubstring("abcabcbb");

        List<Employee> employees = List.of(
                new Employee(1, "Amit", "Engineering", 90000),
                new Employee(2, "Rahul", "Engineering", 120000),
                new Employee(3, "Priya", "HR", 50000),
                new Employee(4, "Neha", "Engineering", 75000),
                new Employee(5, "Vikas", "Engineering", 110000),
                new Employee(6, "Karan", "Engineering", 95000),
                new Employee(7, "Rohit", "Sales", 60000),
                new Employee(8, "Anita", "Engineering", 85000),
                new Employee(9, "Suman", "Engineering", 70000),
                new Employee(10, "Pooja", "Engineering", 130000),
                new Employee(11, "Deepak", "Engineering", 98000)
        );

        List<Employee> top10 = processEmployeeData(employees);

        top10.forEach(System.out::println);
    }
}
class ListNode {
    int val;
    ListNode next;
    ListNode(int v) { val = v; }
}

class MedianFinder {

    PriorityQueue<Integer> left =
            new PriorityQueue<>(Collections.reverseOrder()); // max heap

    PriorityQueue<Integer> right =
            new PriorityQueue<>(); // min heap

    public void addNum(int num) {

        if (left.isEmpty() || num <= left.peek())
            left.offer(num);
        else
            right.offer(num);

        // balance heaps
        if (left.size() > right.size() + 1)
            right.offer(left.poll());
        else if (right.size() > left.size())
            left.offer(right.poll());
    }

    public double findMedian() {

        if (left.size() == right.size())
            return (left.peek() + right.peek()) / 2.0;
        else
            return left.peek();
    }
}

