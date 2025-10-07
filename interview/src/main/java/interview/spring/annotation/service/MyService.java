package interview.spring.annotation.service;

import interview.spring.annotation.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;

@Slf4j
@Service
public class MyService {

    @Autowired(required=true)
    private AppProperties appProperties;

    @PostConstruct
    public void init() {
        log.info("=== Environment Config ===");
        log.info("App Version: {}", appProperties.getVersion());
        log.info("App Setting: {}",appProperties.getSettings());
    }
}