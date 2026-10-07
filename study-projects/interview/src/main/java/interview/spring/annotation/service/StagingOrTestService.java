package interview.spring.annotation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile({"staging", "test"})  // active for either staging OR test
public class StagingOrTestService {
    public void showProfile() {
        log.info("Running Staging/Test service...");
    }
}
