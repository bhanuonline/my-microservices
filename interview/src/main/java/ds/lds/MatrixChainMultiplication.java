package ds.lds;

/*
Graphics rendering pipelines
Database query optimization
Compiler optimization
Scientific computing


 */
public class MatrixChainMultiplication {
    // Used in: Compiler optimization, graphics rendering
    // Time: O(n³), Space: O(n²)
    
    public static void main(String[] args) {
        // Array represents dimensions: A1(10x20), A2(20x30), A3(30x40), A4(40x30)
        int[] dimensions = {10, 20, 30, 40, 30};
        
        System.out.println("Matrix dimensions:");
        for (int i = 0; i < dimensions.length - 1; i++) {
            System.out.println("Matrix " + (i+1) + ": " + dimensions[i] + "x" + dimensions[i+1]);
        }
        
        int minCost = matrixChainOrder(dimensions);
        System.out.println("\nMinimum multiplication cost: " + minCost);
    }
    
    static int matrixChainOrder(int[] p) {
        int n = p.length - 1;  // Number of matrices
        int[][] dp = new int[n][n];
        
        // Cost is zero for single matrix
        for (int i = 0; i < n; i++) {
            dp[i][i] = 0;
        }
        
        // L is chain length
        for (int L = 2; L <= n; L++) {
            for (int i = 0; i < n - L + 1; i++) {
                int j = i + L - 1;
                dp[i][j] = Integer.MAX_VALUE;
                
                for (int k = i; k < j; k++) {
                    // Cost of multiplying matrices from i to k and k+1 to j
                    // Plus cost of multiplying the two resulting matrices
                    int cost = dp[i][k] + dp[k + 1][j] + p[i] * p[k + 1] * p[j + 1];
                    
                    if (cost < dp[i][j]) {
                        dp[i][j] = cost;
                    }
                }
            }
        }
        
        return dp[0][n - 1];
    }
}