package interview.java.generics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

class  St1 implements Comparable<St1>{
    int mark;
    String name;

    @Override
    public int compareTo(St1 o) {
        return this.mark-o.mark;
    }
}




public class JavaGenerics {
    public static void main(String[] args) {




        List<Integer> l = new ArrayList<>();
        l.add(2);
        l.add(2);
        l.add(4);
        l.add(54);
        l.add(5);
        l.add(23);
        l.add(87);

        // Sort by even numbers first, then odd numbers
        List<Integer> evenOddSorted = l.stream()
                .sorted(Comparator.comparing((Integer n) -> n % 2 != 0)
                        .thenComparing(Integer::intValue)).collect(Collectors.toList());
        System.out.println("Even numbers first, then odd: " + evenOddSorted);

// Sort by custom priority (e.g., 2 first, then ascending)
        List<Integer> customPriority = l.stream()
                .sorted(Comparator.comparing((Integer n) -> n != 2)
                        .thenComparing(Integer::intValue))
                .collect(Collectors.toList());
        //System.out.println("2 first, then others in ascending order: " + customPriority);
        for (int i = 0; i < l.size()-1; i++) {
            for (int j = 0; j < l.size()-1; j++) {
                if(l.get(j)<l.get(j+1)){
                    int temp=l.get(j);
                    l.set(j,l.get(j+1));
                    l.set(j+1,temp);
                }
            }
        }
        System.out.println(l);
        max(l);




    }

    private static<T extends Comparable<T>> void max(List<T> l) {

    }

    private static <T extends Comparator<T>> void maxwm(List<T> l) {
    }

    private static void sortList(List<Integer> l) {


    }
    
    
    public static <T extends Number> void sum(T[] data){
        double s=0.0;
        for (int i = 0; i < data.length; i++) {
            s=data[i].doubleValue();
        }
        System.out.println(s);
    }
    
}
