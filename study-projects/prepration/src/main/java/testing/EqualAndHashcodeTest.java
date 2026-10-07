package testing;


import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class Decisive{
    String name;
    int id;

    public Decisive(String name, int id){
        this.name=name;
        this.id=id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Decisive decisive = (Decisive) o;
        return id == decisive.id && Objects.equals(name, decisive.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, id);
    }
}
public class EqualAndHashcodeTest {
    public static void main(String[] args) {
        //Decisive d1=new Decisive("Decisive",100);
        Decisive d2=new Decisive("Decision",100);
        Decisive d3=new Decisive("Decision",100);

        //Why doesn't hashCode() matter here? quals() is invoked directly, so Java never uses hashCode().
        //because you have overridden equals() and both objects have the same name and id.
        System.out.println(d2.equals(d3));

        //When the object is stored in hash-based collections such as: Then hashcode imp
        //If two objects are equal according to equals(), they must return the same hashCode(); otherwise HashMap/HashSet may behave incorrectly.
        Set<Decisive> set = new HashSet<>();
        set.add(d2);
        set.add(d3);

        System.out.println(set.size());
    }
}
