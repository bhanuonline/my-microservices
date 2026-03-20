package interview.spring.core.lifecycle;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PrototypeDestructionPostProcessor implements BeanPostProcessor,
        BeanFactoryAware,
        DisposableBean {
    
    private BeanFactory beanFactory;
    private final List<Object> prototypeBeans = new ArrayList<>();
    
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (beanFactory instanceof ConfigurableListableBeanFactory) {
            BeanDefinition bd = ((ConfigurableListableBeanFactory) beanFactory)
                    .getBeanDefinition(beanName);
            
            if (bd.isPrototype()) {
                synchronized (prototypeBeans) {
                    prototypeBeans.add(bean);
                    System.out.println("Tracking prototype bean: " + beanName);
                }
            }
        }
        return bean;
    }
    
    @Override
    public void destroy() {
        synchronized (prototypeBeans) {
            for (Object bean : prototypeBeans) {
                if (bean instanceof DisposableBean) {
                    try {
                        ((DisposableBean) bean).destroy();
                        System.out.println("Manually destroyed prototype: " + bean);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            prototypeBeans.clear();
        }
    }
}