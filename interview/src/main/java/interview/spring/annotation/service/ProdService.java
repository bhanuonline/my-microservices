package interview.spring.annotation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("prod")
public class ProdService {
    public void showProfile() {
        log.info("Running PROD service...");
    }
}
