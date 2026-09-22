package com.angle.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        exclude = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
        }
)
@EnableScheduling
public class AngleAppApplication {

    public static void main(String[] args) {
        System.out.println("hello");
        SpringApplication.run(AngleAppApplication.class, args);
    }
}
