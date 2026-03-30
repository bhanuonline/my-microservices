package dailytest.ds;

public class ArrayBasedTest {
    public static void main(String[] args) {
        minCoinsByGreedy();
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
}
