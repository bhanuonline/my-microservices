package com.example.userservice.customconfiguration;


import com.example.userservice.customconfiguration.service.HelloService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

@Configuration
@ConditionalOnClass(HelloService.class) // Only configure if HelloService class is present
public class HelloServiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean // Only if no other HelloService bean is defined
    public HelloService helloService() {
        return new HelloService();
    }
}