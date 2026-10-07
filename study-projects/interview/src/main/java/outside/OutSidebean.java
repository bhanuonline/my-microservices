package outside;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@ComponentScan
@Configuration
public class OutSidebean {
    public void sayHello() {
        System.out.println("Out Side Bean create!");
    }
}
