package com.example.userservice;

import com.example.userservice.customconfiguration.service.HelloService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class UserServiceApplication {

	@Autowired
	public HelloService helloService;

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
		//System.out.println("Spring Boot Version: " + SpringBootVersion.getVersion());
	}

	@Autowired
	public void run(HelloService helloService) {
		log.info(helloService.sayHello());
	}

}
