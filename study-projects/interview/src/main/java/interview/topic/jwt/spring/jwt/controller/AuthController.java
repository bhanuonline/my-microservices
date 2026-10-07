package interview.topic.jwt.spring.jwt.controller;

import interview.topic.jwt.spring.jwt.util.JwtUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class AuthController {

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public String login(@RequestBody AuthRequest authRequest) {
        // For simplicity, we hardcode authentication
        if ("user".equals(authRequest.getUsername()) && "password".equals(authRequest.getPassword())) {
            log.info("checking loging..");
            return jwtUtil.generateToken(authRequest.getUsername());
        } else {
            throw new RuntimeException("Invalid credentials");
        }
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello, This is a Public Endpoint";
    }
}

@Data
class AuthRequest {
    private String username;
    private String password;
}