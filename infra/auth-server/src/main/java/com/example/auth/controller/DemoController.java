package com.example.auth.controller;

import com.example.auth.user.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/hello")
    public String demo(@AuthenticationPrincipal CustomUserDetails user) {
        return "Hello, " + user.getUsername() + " (ID = " + user.getId() + ", email = " + user.getEmail() + ")";
    }
}