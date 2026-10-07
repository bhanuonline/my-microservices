package com.example.securityapps.controller;

import com.example.securityapps.entity.AppUser;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserDetailsManager userDetailsManager;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserDetailsManager udm, PasswordEncoder pe) {
        this.userDetailsManager = udm;
        this.passwordEncoder = pe;
    }

    @PostMapping("/register")
    public void register(@RequestBody AppUser user) {
        if (userDetailsManager.userExists(user.getUsername())) {
            throw new RuntimeException("Username already exists!");
        }

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(passwordEncoder.encode(user.getPassword()))
                .roles("USER")
                .build();

        userDetailsManager.createUser(userDetails);
    }
}