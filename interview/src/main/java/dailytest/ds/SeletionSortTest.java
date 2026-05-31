package dailytest.ds;

import lombok.Data;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.TreeSet;


@Data
class SecS implements Comparable<SecS>{
    String name;
    int age;
    LocalDate joining;

    @Override
    public int compareTo(SecS o) {
        return this.joining.compareTo(o.joining);
    }
}
public class SeletionSortTest {
    public static void main(String[] args) {
        Integer[] arr={12,9,6,54,34,89,0,1};
        String[] sa={"kamal","samal","kahlan","Naman","hasan","Nalan","hmam","Drishita","Bhanu","Rohit"};
        //ss(arr);
        //ss1(arr);
        //ss2(sa);
        //Arrays.sort(sa,(a,b)->b.compareTo(a));

        System.out.println(Arrays.toString(arr));

        TreeSet<SecS>t=new TreeSet<>();
        SecS s1=new SecS();
        s1.setName("Bhanu");
        s1.setAge(21);
        s1.setJoining(LocalDate.of(2026, 5, 1));

        SecS s2=new SecS();
        s2.setName("Bhanu");
        s2.setAge(22);
        s2.setJoining(LocalDate.of(2026, 5, 1));
        t.add(s1);
        t.add(s2);

        //System.out.println(t);

        SecS one=new SecS();
        one.setAge(10);
        one.setName("kamal");
        one.setJoining(LocalDate.of(2026, 5, 1));

        SecS two=new SecS();
        two.setAge(1);
        two.setName("nakal");
        two.setJoining(LocalDate.of(2026, 1, 21));

        SecS three=new SecS();
        three.setAge(3);
        three.setName("hasan");
        three.setJoining(LocalDate.of(2026, 12, 19));

        SecS four=new SecS();
        four.setAge(23);
        four.setName("khamal");
        four.setJoining(LocalDate.of(2026, 9, 10));

        SecS[] s={one,two,three,four};

        genss(s);
        System.out.println(Arrays.toString(s));

    }

    private static void ss2(String[] sa) {
        int n=sa.length-1;
        for (int i = 0; i < n; i++) {
            int min =i;
            for (int j = i+1; j <n ; j++) {
                if(sa[j].compareTo(sa[min])<0){
                    min=j;
                }
            }
            String temp=sa[min];
            sa[min]=sa[i];
            sa[i]=temp;
        }
    }
    //T can be any type (String, Integer, Employee, etc.)
    //But it must implement Comparable
    public static <T extends Comparable<T>> void genss(T[] arr) {
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            int min = i;
            for (int j = i + 1; j < n; j++) {
                if (arr[j].compareTo(arr[min]) < 0) {
                    min = j;
                }
            }
            T temp = arr[min];
            arr[min] = arr[i];
            arr[i] = temp;
        }
    }
    private static void ss1(Integer[] arr) {
        int n=arr.length;
        for (int i = 0; i < n-1 ; i++) {
            int min=i;
            for (int j = i+1; j < n; j++) {
                if(arr[j]<arr[min]){
                    min=j;
                }
            }
            int temp=arr[min];
            arr[min]=arr[i];
            arr[i]=temp;
        }
    }

    private static void ss(int[] arr) {
        for (int i = 0; i < arr.length-1; i++) {
            int min=i;
            for (int j = i+1; j < arr.length; j++) {
                if(arr[j]<arr[min]){
                    min = j;
                }
            }
            int temp = arr[min];
            arr[min] = arr[i];
            arr[i] = temp;
        }
    }
}
