package com.fastapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FastApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FastApiApplication.class, args);
    }

}
