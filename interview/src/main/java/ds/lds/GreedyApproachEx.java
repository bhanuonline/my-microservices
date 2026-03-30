package ds.lds;

import java.util.*;

public class GreedyApproachEx {
    public static List<Integer> makeChange(int amount, int[] coins) {
        // Sort coins in descending order (largest first)
        Integer[] boxed = Arrays.stream(coins).boxed().toArray(Integer[]::new);
        Arrays.sort(boxed, Collections.reverseOrder());

        List<Integer> result = new ArrayList<>();

        for (int coin : boxed) {
            while (amount >= coin) {   // pick this coin as many times as it fits
                result.add(coin);
                amount -= coin;
            }
        }

        if (amount == 0) return result;
        return Collections.emptyList(); // no solution found
    }
    public static boolean canJump(int[] nums) {
        int maxReach = 0;

        for (int i = 0; i < nums.length; i++) {
            if (i > maxReach) {
                return false; // can't reach this position
            }
            maxReach = Math.max(maxReach, i + nums[i]);
        }
        System.out.println("maxReach "+maxReach);
        return true;
    }
    public static List<int[]> select(int[][] activities) {
        // Sort by end time (greedy rule)
        Arrays.sort(activities, (a, b) -> a[1] - b[1]);

        List<int[]> result = new ArrayList<>();
        int lastEnd = -1;

        for (int[] act : activities) {
            if (act[0] >= lastEnd) {   // starts after last one ends
                result.add(act);
                lastEnd = act[1];
            }
        }
        return result;
    }
    private static void minCoinsByGreedy() {
        int[] coins={1000,500,200,100,50,20,10,5,2,1};
        int amount=99;
        int count=0;
        StringBuilder sb=new StringBuilder();
        for(int coin: coins){
            if(coin>amount){
                continue;
            }
            int i = amount / coin;
            count = count+ i;    // how many of this coin fit?
            sb.append(coin).append((i>1)?"x"+i:"").append(" ");
            amount    = amount % coin;    // remainder becomes new amount
            if(amount==0){
                break;
            }
        }
        System.out.println("minCoin: "+count+" Used Coins :"+sb);
    }

    public static Map<String, Object> makeChangeVerbose(int amount, int[] coins) {
        Integer[] boxed = Arrays.stream(coins).boxed().toArray(Integer[]::new);
        Arrays.sort(boxed, Collections.reverseOrder());

        List<Integer> result = new ArrayList<>();
        List<String> trace = new ArrayList<>();
        int step = 1;
        int original = amount;

        for (int coin : boxed) {
            if (amount < coin) {
                trace.add(step++ + ". Skip " + coin + "¢ — too large (" + amount + "¢ left)");
                continue;
            }
            while (amount >= coin) {
                trace.add(step++ + ". Pick " + coin + "¢  →  "
                        + amount + " − " + coin + " = " + (amount - coin) + "¢ left");
                result.add(coin);
                amount -= coin;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("amount", original);
        out.put("coins", result);
        out.put("count", result.size());
        out.put("solved", amount == 0);
        out.put("trace", trace);
        return out;
    }

    public static void main(String[] args) {
        int[] coins = {100, 25, 10, 5, 1};
        int amount = 41;

        List<Integer> change = makeChange(amount, coins);

        System.out.println("Amount: " + amount + "¢");
        System.out.println("Coins used: " + change);
        System.out.println("Total coins: " + change.size());

        int[] coinss = {100, 25, 10, 5, 1};

        int[] testCases = {41, 67, 99};

        for (int amountt : testCases) {
            Map<String, Object> res = makeChangeVerbose(amountt, coinss);
            System.out.println("=== Making change for " + res.get("amount") + "¢ ===");

            // Cast is safe — we know the type we stored
            @SuppressWarnings("unchecked")
            List<String> trace = (List<String>) res.get("trace");
            trace.forEach(System.out::println);

            System.out.println("Result: " + res.get("coins"));
            System.out.println("Total coins: " + res.get("count"));
            System.out.println();
        }

        int[][] acts = {{1,3},{2,5},{4,6},{6,8},{5,7}};
        List<int[]> chosen = select(acts);
        System.out.println("Max activities: " + chosen.size());
        chosen.forEach(a ->
                System.out.println("  [" + a[0] + "-" + a[1] + "]"));
        int[] nums={2,3,1,1,4};
        canJump(nums);
    }
}
