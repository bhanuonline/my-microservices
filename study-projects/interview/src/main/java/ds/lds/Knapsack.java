package ds.lds;

public class Knapsack {
    // Used in: Resource allocation, cargo loading, investment decisions
    // Time: O(n × W), Space: O(n × W)

    public static void main(String[] args) {
        int[] values = {60, 100, 120};
        int[] weights = {10, 20, 30};
        int capacity = 50;

        System.out.println("Items:");
        for (int i = 0; i < values.length; i++) {
            System.out.println("Item " + (i+1) + ": Value=" + values[i] + ", Weight=" + weights[i]);
        }
        System.out.println("Knapsack Capacity: " + capacity);

        int maxValue = knapsack(values, weights, capacity);
        System.out.println("\nMaximum value: " + maxValue);

        // Print selected items
        boolean[] selected = getSelectedItems(values, weights, capacity);
        System.out.println("\nSelected items:");
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                System.out.println("Item " + (i+1) + ": Value=" + values[i] + ", Weight=" + weights[i]);
            }
        }
    }

    static int knapsack(int[] values, int[] weights, int capacity) {
        int n = values.length;
        int[][] dp = new int[n + 1][capacity + 1];

        // Build table bottom-up
        for (int i = 1; i <= n; i++) {
            for (int w = 1; w <= capacity; w++) {
                if (weights[i - 1] <= w) {
                    // Max of including or excluding current item
                    dp[i][w] = Math.max(
                            values[i - 1] + dp[i - 1][w - weights[i - 1]],
                            dp[i - 1][w]
                    );
                } else {
                    dp[i][w] = dp[i - 1][w];
                }
            }
        }

        return dp[n][capacity];
    }

    static boolean[] getSelectedItems(int[] values, int[] weights, int capacity) {
        int n = values.length;
        int[][] dp = new int[n + 1][capacity + 1];

        // Build DP table
        for (int i = 1; i <= n; i++) {
            for (int w = 1; w <= capacity; w++) {
                if (weights[i - 1] <= w) {
                    dp[i][w] = Math.max(
                            values[i - 1] + dp[i - 1][w - weights[i - 1]],
                            dp[i - 1][w]
                    );
                } else {
                    dp[i][w] = dp[i - 1][w];
                }
            }
        }

        // Backtrack to find selected items
        boolean[] selected = new boolean[n];
        int w = capacity;
        for (int i = n; i > 0 && w > 0; i--) {
            if (dp[i][w] != dp[i - 1][w]) {
                selected[i - 1] = true;
                w -= weights[i - 1];
            }
        }

        return selected;
    }
}