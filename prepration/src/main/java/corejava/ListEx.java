package corejava;

import java.util.*;
import java.util.stream.Collectors;

public class ListEx {
    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3, 2, 4, 5, 3, 6, 1));
        removeDuplicateFromList(list);

    }

    private static void removeDuplicateFromList(List<Integer> list) {

        //way1
        //List<Integer> unique = list.stream().distinct().collect(Collectors.toList());
        //way2
        Set<Integer> seen = new HashSet<>();
       // list.removeIf(n -> !seen.add(n));


        //way3
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(i).equals(list.get(j))) {
                    list.remove(j);
                    j--; // adjust index after removal
                }
            }
        }
        System.out.println(list);

    }
}
