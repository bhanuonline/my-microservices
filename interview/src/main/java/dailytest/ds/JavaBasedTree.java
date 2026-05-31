package dailytest.ds;

import java.util.TreeMap;
import java.util.TreeSet;

public class JavaBasedTree {
    public static void main(String[] args) {
        TreeMap<String, Integer> treeMap=new TreeMap();
        treeMap.put("z",1);
        treeMap.put("a",2);
        treeMap.put("c",1);
        treeMap.put("b",3);
        System.out.println(treeMap);

        TreeSet<String> treeSet=new TreeSet<>();
        treeSet.add("z");
        treeSet.add("a");
        treeSet.add("c");
        treeSet.add("b");
        System.out.println(treeSet);
    }
}
