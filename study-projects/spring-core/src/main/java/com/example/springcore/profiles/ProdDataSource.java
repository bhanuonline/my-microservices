package com.example.springcore.profiles;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod") // only registered when spring.profiles.active=prod
public class ProdDataSource implements DataSource {

    @Override
    public String getUrl() {
        return "jdbc:postgresql://prod-server:5432/proddb  (real DB, prod only)";
    }
}
