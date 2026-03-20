package interview.spring.core.config.routingdb;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DataSourceRoutingAspect {

    @Before("@annotation(ReadOnlyConnection)")
    public void setReadDataSource() {
        DBContextHolder.set(DBType.REPLICA);
    }

    @Before("execution(* com.example.service.*.save*(..)) || " +
            "execution(* com.example.service.*.update*(..))")
    public void setWriteDataSource() {
        DBContextHolder.set(DBType.MASTER);
    }

    @After("execution(* com.example.service.*.*(..))")
    public void clear() {
        DBContextHolder.clear();
    }
}