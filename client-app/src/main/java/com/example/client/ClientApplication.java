package com.example.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class ClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    @RestController
    static class Pages {
        @GetMapping("/")
        public String index() {
            return "<a href=\"/protected\">Login via OAuth2.0</a>";
        }

        @GetMapping("/protected")
        public String protectedPage() {
            return "You are logged in via OAuth2.0 Client!";
        }
    }
}