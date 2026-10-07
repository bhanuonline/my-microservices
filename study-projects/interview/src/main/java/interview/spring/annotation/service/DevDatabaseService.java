package interview.spring.annotation.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")  // This bean will only be created if 'dev' profile is active
public class DevDatabaseService implements DatabaseService {

    @Override
    public void connect() {
        System.out.println("Connecting to DEV database...");
    }
}