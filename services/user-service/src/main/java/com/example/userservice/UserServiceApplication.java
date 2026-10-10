package com.example.userservice;

import com.example.userservice.customconfiguration.service.HelloService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Component scan defaults to this package (com.example.userservice) plus subpackages.
 * The outbox entity + repo live in com.example.common.outbox — need to widen scans.
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {"com.example.userservice", "com.example.common.outbox"})
@EnableJpaRepositories(basePackages = {"com.example.userservice", "com.example.common.outbox"})
public class UserServiceApplication {

	@Autowired
	public HelloService helloService;

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

	@Autowired
	public void run(HelloService helloService) {
		log.info(helloService.sayHello());
	}
}
