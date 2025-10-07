package interview.spring.config.bean;

import org.springframework.stereotype.Service;

@Service
class SomeDependency {
    @Override
    public String toString() {
        return "SomeDependency instance";
    }
}