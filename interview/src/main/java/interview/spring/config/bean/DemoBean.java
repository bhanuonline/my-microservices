package interview.spring.config.bean;

import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
class DemoBean implements BeanNameAware, BeanFactoryAware, InitializingBean, DisposableBean {

    public DemoBean() {
        System.out.println("[Step 2] Constructor: DemoBean is created");
    }

    // Step 3: Dependency Injection happens here
    @Autowired
    private SomeDependency dep;

    // Step 4: Awareness callbacks
    @Override
    public void setBeanName(String name) {
        System.out.println("[Step 4a] BeanNameAware: My bean name is " + name);
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        System.out.println("[Step 4b] BeanFactoryAware: BeanFactory set");
    }

    // Step 5: PostConstruct (Initialization)
    @PostConstruct
    public void initPostConstruct() {
        System.out.println("[Step 5] @PostConstruct: Dependency injected -> " + dep);
    }

    // Step 6: afterPropertiesSet()
    @Override
    public void afterPropertiesSet() {
        System.out.println("[Step 6] InitializingBean.afterPropertiesSet(): More init logic here");
    }

    // Step 7: Custom init-method (if declared directly in config)
    public void customInit() {
        System.out.println("[Step 7] Custom init-method executed");
    }

    // Step 9: Shutdown sequence
    @PreDestroy
    public void preDestroyMethod() {
        System.out.println("[Step 9a] @PreDestroy called before bean is removed");
    }

    @Override
    public void destroy() {
        System.out.println("[Step 9b] DisposableBean.destroy(): Cleanup here");
    }
}