package interview.spring.config.bean;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MyBean {
    public void sayHello() {
        log.info("Hello from MyBean!");
    }
}