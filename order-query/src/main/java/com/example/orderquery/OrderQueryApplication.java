package com.example.orderquery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * CQRS read-side. No RDBMS — the read model lives in Elasticsearch.
 * JPA auto-configs excluded so the service doesn't demand a DataSource at startup.
 *
 * common-lib brings tracing, metrics, JSON logs for free. We opt out of its
 * JPA primary-tx-manager auto-config by not pulling the JPA starter.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
public class OrderQueryApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderQueryApplication.class, args);
    }
}
