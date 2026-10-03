package com.example.productquery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * CQRS read-side for Product. No RDBMS — the read model lives in Elasticsearch.
 * JPA auto-configs excluded so the service doesn't demand a DataSource at startup
 * (common-lib pulls spring-boot-starter-data-jpa transitively via other modules).
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
public class ProductQueryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductQueryApplication.class, args);
    }
}
