package interview.java.util.list;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
class  Sublime {
    String  name;
    int id;
    String address;
    int mob;

    @Override
    public String toString() {
        return "Sublime{name='" + name + "'}";
    }
}

public class Test {
    public static void main(String[] args) {
        System.out.println("j");

        Sublime s1=new Sublime.SublimeBuilder().id(1)
                .mob(686798)
                .name("Bhanu")
                .address("Delhi").build();
        Sublime s2=new Sublime.SublimeBuilder().id(5)
                .mob(9880989)
                .name("Amit")
                .address("Kanpur").build();
        Sublime s3=new Sublime.SublimeBuilder().id(10)
                .mob(798798)
                .name("Test")
                .address("bangloore").build();
        Sublime s4=new Sublime.SublimeBuilder().id(7)
                .mob(4564646)
                .name("akamal")
                .address("meerut").build();
        Sublime s5=new Sublime.SublimeBuilder().id(7)
                .mob(9878979)
                .name("ChuA")
                .address("meerut").build();

        List<Sublime>sl=new ArrayList<>();
        sl.add(s1);sl.add(s2);sl.add(s3);sl.add(s4);sl.add(s5);

        List<Sublime>sortlist =sl.stream().sorted(Comparator.comparing(Sublime::getName)).collect(Collectors.toList());
        sortlist.forEach(System.out::print);
    }
}
