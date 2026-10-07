package interview.spring.annotation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class ConfigValidator {
    @Value("${app.mode}")
    private String mode;

    @PostConstruct
    public void validateMode() {
        if (!List.of("dev","prod").contains(mode)) {
            throw new IllegalStateException("Invalid mode: " + mode);
        }
    }
}