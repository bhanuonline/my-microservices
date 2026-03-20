package interview.java.collection.mapandset;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
class Order{
    int id;

}
public class MapAndSet {
    private Map<String, Order> incOrderMap = new HashMap<>();
    private static Map<String, Order> staOrderMap = new HashMap<>();

    static Map<Integer, String> users = new ConcurrentHashMap<>();


    public void addOrder(String id, Order o) {
        incOrderMap.put(id, o);
    }

    public void addsOrder(String id, Order o) {
        staOrderMap.put(id, o);
    }

    public Order getOrder(String id) {
        return incOrderMap.get(id);
    }
    public static void main(String[] args) {

    Order o1=Order.builder().id(1).build();
    Order o2=Order.builder().id(2).build();

        MapAndSet mapAndSet=new MapAndSet();
        mapAndSet.incOrderMap.put("o1",o1);
        mapAndSet.incOrderMap.put("o2",o2);

        mapAndSet.addsOrder("o3",o1);
        mapAndSet.addsOrder("o4",o2);

        System.out.println(mapAndSet.getOrder("o1"));

        MapAndSet mapAndSet2=new MapAndSet();
        mapAndSet2.incOrderMap.put("o5",o1);
        mapAndSet2.incOrderMap.put("o6",o2);

        mapAndSet2.addsOrder("o7",o1);
        mapAndSet2.addsOrder("o8",o2);



        System.out.println(MapAndSet.staOrderMap);







    }
}
