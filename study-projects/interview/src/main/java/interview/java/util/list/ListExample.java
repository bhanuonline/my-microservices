package interview.java.util.list;

import lombok.Builder;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;






public class ListExample {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>();

        list.add("ABc");
        list.add("Bhanu");
        list.add("Anika");
        list.add("Kiran");
        list.add("Rohit");
        list.add("Nidhi");
        list.add("nidhi");
        list.add("pratap");
        //System.out.println(list);

        for (String s : list) {
        }
        Iterator<String> i = list.iterator();
        ListIterator li = list.listIterator(list.size());

        while (li.hasPrevious()) {
           // System.out.println(li.previous());
        }




    }
}
