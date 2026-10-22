package com.example.springcore.profiles;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev") // only registered when spring.profiles.active=dev
public class DevDataSource implements DataSource {

    @Override
    public String getUrl() {
        return "jdbc:h2:mem:devdb  (in-memory, dev only)";
    }
}
