package interview.topic.jwt.spring.jwt.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @GetMapping("/secure-data")
    public String secureData() {
        return "This is Protected Data - only shows with valid JWT";
    }
}