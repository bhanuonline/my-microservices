package interview.spring.core;

import interview.spring.core.repository.primary.UserRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Arrays;

@Slf4j
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class})
public class SpringCoreAPPs {

    @PostConstruct
    public void check() {
        log.info("Active Profile "+Arrays.toString(environment.getActiveProfiles()));
    }

    @Autowired
    private Environment environment;


    public static void main(String[] args) {
        ApplicationContext applicationContext = SpringApplication.run(SpringCoreAPPs.class, args);

        // Annotation-based
        ApplicationContext annotationContext = new AnnotationConfigApplicationContext(AppConfig.class);

        // XML-based
        ApplicationContext xmlcontext = new ClassPathXmlApplicationContext("applicationContext.xml");
        log.info("Spring Core Apps starred !!");

        OrderService orderService =
                applicationContext.getBean(OrderService.class);

        //orderService.placeOrder();

        String[] beans = applicationContext.getBeanDefinitionNames();
        //Arrays.sort(beans);

        for (String bean : beans) {
            //log.info(bean);
        }

        //Arrays.stream(applicationContext.getBeanDefinitionNames()).map(applicationContext::getBean).filter(bean -> bean.getClass().getPackageName().startsWith("interview.spring.core")).forEach(bean -> System.out.println(bean.getClass().getName()));

        Arrays.toString(annotationContext.getEnvironment().getActiveProfiles());
    }
}



//@Configuration
@Component
//@ComponentScan(basePackages = "interview.spring.coretest")
class Config {

}

@Component
class AppConfig{

}

@Component
@Slf4j
class CustomBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        log.info("BeanFactoryPostProcessor - modifying bean definitions");

        // You can modify bean definitions here
        BeanDefinition bd =
                beanFactory.getBeanDefinition("orderService");

        // Modify bean definition BEFORE bean creation
        bd.getPropertyValues().add("timeout", 5000);

        log.info("3️⃣ BeanDefinition modified: timeout = 5000");
    }
}

@Component
@Slf4j
class OrderService implements ApplicationEventPublisherAware {

    private int timeout;

    public OrderService() {
        log.info("1️⃣ OrderService constructor called");
    }

    public void setTimeout(int timeout) {
        log.info("4️⃣ setTimeout called with value = " + timeout);
        this.timeout = timeout;
    }

    public void placeOrder() {
        log.info("Order placed with timeout = " + timeout);
    }

    private ApplicationEventPublisher eventPublisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.eventPublisher = publisher;
    }

    public void placeOrder(Order order) {
        // Save order...

        // Publish event to notify other components
        eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId()));
    }
}

// Other components can listen
@Component
class EmailNotifier {

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        // Send confirmation email
    }
}

class OrderPlacedEvent{
    private String orderId;

    public OrderPlacedEvent(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() { return orderId; }
}

@Data
class Order{
    String orderId;
}

@Component
class CustomRegistryPostProcessor
        implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(
            BeanDefinitionRegistry registry) throws BeansException {

        System.out.println("🥇 RegistryPostProcessor called");

        RootBeanDefinition bd =
                new RootBeanDefinition(PaymentService.class);

        registry.registerBeanDefinition("paymentService", bd);
    }

    @Override
    public void postProcessBeanFactory(
            org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory) {

        System.out.println("🥈 postProcessBeanFactory called");
    }
}

class PaymentService {

    public PaymentService() {
        System.out.println("PaymentService created");
    }
}

@Component
class MyBean implements BeanNameAware,
        BeanFactoryAware,
        ApplicationContextAware, EnvironmentAware, ResourceLoaderAware, MessageSourceAware,ApplicationEventPublisherAware,
        InitializingBean, DisposableBean {

    public MyBean() {
        System.out.println("1. Constructor called - bean instance created");
    }

    private String beanName;
    private BeanFactory beanFactory;
    private ApplicationContext applicationContext;

    @Override
    public void setBeanName(String name) {
        System.out.println("3. BeanNameAware - bean name is: " + name);
        this.beanName = name;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        System.out.println("4. BeanFactoryAware - factory injected");
        this.beanFactory = beanFactory;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        System.out.println("5. ApplicationContextAware - context injected");
        this.applicationContext = applicationContext;
    }

    @Override
    public void setEnvironment(Environment environment) {

    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {

    }

    @Override
    public void setMessageSource(MessageSource messageSource) {

    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {

    }

    @PostConstruct
    public void init() {
        System.out.println("7. @PostConstruct - custom initialization");
        // Good for: initialization that requires dependencies
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("8. InitializingBean - afterPropertiesSet()");
    }

    @PreDestroy
    public void cleanup() {
        System.out.println("11. @PreDestroy - cleanup resources");
        // Close connections, release resources, etc.
    }
    @Override
    public void destroy() {
        System.out.println("12. DisposableBean - destroy()");
    }
}

//@Component
 class CustomBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        System.out.println("6. BeanPostProcessor BEFORE - " + beanName);
        // You can wrap the bean, modify it, or return it as-is
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        System.out.println("10. BeanPostProcessor AFTER - " + beanName);
        // Common use: create proxies (AOP, @Transactional, @Async)
        return bean;
    }
}



