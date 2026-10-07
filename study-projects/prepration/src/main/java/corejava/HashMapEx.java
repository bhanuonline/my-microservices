package corejava;

import java.util.*;
import java.util.stream.Collectors;

class Employee {
    int id;
    String name;
    double salary;

    Employee(int id, String name, double salary) {
        this.id = id;
        this.name = name;
        this.salary = salary;
    }
}
public class HashMapEx {
    public static void main(String[] args) {
        //mapIteration();

        // findNonRepeatingChar("aabbcdde");
        findEmpwithHigshtSalary();
        reveseMap();
    }

    private static void mapIteration() {
        Map<Integer,Integer> map=new HashMap<>();
        map.put(1,1);
        map.put(13,13);
        map.put(12,1);
        map.put(15,13);
        map.put(10,1);
        map.put(19,13);
        map.put(-1,1);
        map.put(67,13);

        /*Loop Iteration*/

        // iterate entries (most common)
        for (Map.Entry<Integer, Integer> e : map.entrySet()) {
            System.out.println(e.getKey() + " = " + e.getValue());
        }

        // iterate keys only
        for (Integer key : map.keySet()) {
            System.out.println(key);
        }

        // iterate values only
        for (Integer val : map.values()) {
            System.out.println(val);
        }

        //java 8
        map.forEach((key, val) -> System.out.println(key + " → " + val));

        // entrySet() + Iterator explicitly
        Iterator<Map.Entry<Integer, Integer>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> e = it.next();
            System.out.println(e.getKey() + " → " + e.getValue());
            // it.remove(); ← safe removal during iteration
        }

        // keySet() + get()  [works but AVOID — double lookup, slow]
        for (Integer key : map.keySet()) {
            System.out.println(key + " → " + map.get(key)); // extra O(1) lookup
        }

        // Stream on entrySet()  [Java 8+, for filter/map/collect]
        map.entrySet().stream()
                .filter(e -> e.getValue() > 10)
                .forEach(e -> System.out.println(e.getKey()));

        // Stream on keySet() / values()
        map.keySet().stream().forEach(System.out::println);
        map.values().stream().forEach(System.out::println);

        //  parallelStream()  [Java 8+, multi-threaded iteration]
        map.entrySet().parallelStream()
                .forEach(e -> System.out.println(e.getKey()));

        // replaceAll()  [Java 8+, modify values in-place]
        map.replaceAll((key, val) -> val * 2); // doubles every value
    }

    private static void reveseMap() {
        Map<String, Integer> salary = new HashMap<>();

        salary.put("Bhanu", 50000);
        salary.put("Rahul", 70000);
        salary.put("Amit", 65000);
        salary.put("Drishita", 65000);

        Map<Integer, List<String>> reverse = new HashMap<>();

        for (Map.Entry<String, Integer> m:salary.entrySet()){
            reverse.computeIfAbsent(m.getValue(),k->new ArrayList<>())
                    .add(m.getKey());
        }

        Map<Integer, String> reverse1 = salary.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getValue,
                        Map.Entry::getKey,
                        (existing, incoming) -> existing + ", " + incoming // merge
                ));
        salary.clear();
        System.out.println(reverse1);
    }

    private static void findEmpwithHigshtSalary() {
        Map<String, Integer> salary = new HashMap<>();

        salary.put("Bhanu", 50000);
        salary.put("Rahul", 70000);
        salary.put("Amit", 65000);

        Optional<Map.Entry<String, Integer>> result = salary.entrySet().stream().max(Map.Entry.comparingByValue());
        System.out.println(result.get().getKey());
    }
    private static void findNonRepeatingChar(String s) {
       char cArr[]= s.toCharArray();
       Map<String,Integer>nonRepeatChar=new HashMap<>();
       for (char c:cArr){
          String ss= String.valueOf(c);
           if(!nonRepeatChar.containsKey(ss)){
               nonRepeatChar.put(ss,1);
           }
           else{
               nonRepeatChar.put(ss,nonRepeatChar.getOrDefault(ss,0)+1);
           }
       }
        nonRepeatChar.entrySet().stream()
                .filter(e -> e.getValue() ==1)
                .forEach(e -> System.out.println(e.getKey()));

    }
}
