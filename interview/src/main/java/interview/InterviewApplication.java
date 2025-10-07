package interview;

import interview.spring.config.bean.MyBean;
import interview.spring.config.bean.Parrot;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import outside.OutSidebean;

@Slf4j
@ComponentScan(basePackages = {"outside","interview.spring"})
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class InterviewApplication {

    @Autowired
    @Qualifier("parrot3")
    Parrot parrot;

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(InterviewApplication.class, args);
        MyBean myBean = context.getBean(MyBean.class);
        myBean.sayHello();
        OutSidebean outSidebean = context.getBean(OutSidebean.class);
        outSidebean.sayHello();
        InterviewApplication app = context.getBean(InterviewApplication.class);
        app.sayhello();   // Will log "Miki"
       // Parrot parrot=context.getBean(Parrot.class);

        log.info("Stared");
    }

    public void sayhello(){
        log.info(parrot.getName());
    }

    @PostConstruct
    public void initAfterInjection() {
        log.info("Parrot Bean injected: {}", parrot.getName());
    }
}
