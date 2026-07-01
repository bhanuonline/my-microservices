package testing;

import lombok.Builder;
import lombok.Setter;
import lombok.ToString;

@Builder
@Setter
@ToString
class User{
    private String name;
    private String address;
    private int mob;
    private int picCode;
    private  String state;




}
public class DecisiveBDP {
    public static void main(String[] args) {
       User u= User.builder().name("Bhanu").build();
        System.out.println(u);
    }
}
