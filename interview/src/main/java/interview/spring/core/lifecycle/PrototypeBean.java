package interview.spring.core.lifecycle;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Lazy
@Component
@Scope("prototype")
class PrototypeBean implements DisposableBean {

    private int instanceId;

    public PrototypeBean() {
        this.instanceId = (int)(Math.random() * 1000);
        System.out.println("Prototype constructor: ID = " + instanceId);
    }

    @PostConstruct
    public void init() {
        System.out.println("Prototype: PostConstruct called");
    }

    @PreDestroy
    public void cleanup() {
        System.out.println("Prototype: PreDestroy called ✗");
        // This will NEVER be called!
    }

    @Override
    public void destroy() {
        System.out.println("Prototype DisposableBean.destroy(): ID = " + instanceId + " ✗ NEVER CALLED!");
    }

    public int getInstanceId() {
        return instanceId;
    }

    public void doWork() {
    }
}

@Component
abstract class SingletonService {

    private final PrototypeBean prototypeBean;
    @Lookup // Spring implements this method
    protected abstract PrototypeBean getPrototypeBean();

    public void doSomething() {
        PrototypeBean bean = getPrototypeBean(); // Fresh instance every time!
        bean.doWork();
    }

    public SingletonService(PrototypeBean prototypeBean) {
        System.out.println(">>> Singleton Service Constructor");
        this.prototypeBean = prototypeBean;
    }
}