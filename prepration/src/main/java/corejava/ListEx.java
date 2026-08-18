package corejava;

import java.util.*;
import java.util.stream.Collectors;


class MyList<T>{
    private Object[] data;
    private int size=0;

    public MyList(){
        this.data=new Object[10];
    }

    public void add(T item){
        if(size== data.length){
            grow();
        }
        data[size]=item;
        size++;
    }

    public void add(int index,T item){
        checkIndex(index);
        if(size== data.length){
            grow();
        }
        for (int i = size;  size<index ; i--) {
            data[i]=data[i-1];
        }
        data[index]=item;
        size++;
    }

    private void grow() {
        int newCapacity= data.length*2;
        Object[] newData=new Object[newCapacity];
        for (int i = 0; i <size ; i++) {
            newData[i]=data[i];
        }
        data=newData;
    }

    public T get(int index){
        checkIndex(index);
        return (T) data[index];
    }

    private void checkIndex(int index) {
            if(index<0 || index>=size){
                throw new IndexOutOfBoundsException("");
            }
    }

    public void remove(int index){
        checkIndex(index);
        for (int i = index; i <size-1 ; i++) {
            data[i]=data[i+1];
        }
        data[size-1]=null;
        size--;
    }




}

class MyNumIterator implements Iterable<Integer>{

    Integer arr[]={1,2,3,4,5};

    @Override
    public Iterator<Integer> iterator() {
        return Arrays.stream(arr).iterator();
    }
}
public class ListEx {
    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));
        //removeDuplicateFromList(list);

        MyNumIterator numIterator=new MyNumIterator();
        Iterator<Integer> iterator= numIterator.iterator();
        while (iterator.hasNext()){
           Integer i= iterator.next();
            //System.out.println(i);
        }

       Iterator<Integer> it= list.iterator();
        while (it.hasNext()){
            Integer i= it.next();
           // System.out.print(i );
        }
        System.out.println();
        Spliterator<Integer> si= list.spliterator();
       //
    si.tryAdvance(s-> System.out.println(s));
        System.out.println("--");
        si.forEachRemaining(e-> System.out.println(e));

        while (si.tryAdvance(s->System.out.println(s))){

        }
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
