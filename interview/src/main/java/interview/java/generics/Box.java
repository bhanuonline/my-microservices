package interview.java.generics;



// Fixed bound
class Unprecedented1<T extends Number>{
    // "T must extend Number (always Number)"

}
// Variable bound
class Unprecedented<T extends M,M>{
    // "T must extend M (but M can be anything you want)"
    private T value;
    private M boundary;

}
public class Box<T> {
    private T data;

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public <T> void print(T data) {
        System.out.println(data);
    }

    public static void main(String[] args) {
        Box<String> boxs = new Box<>();
        boxs.setData("My Box");
        boxs.print(boxs.getData());

    }
}
