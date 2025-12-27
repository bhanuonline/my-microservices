package com.example.jwtAuthApp.controller;

import com.example.jwtAuthApp.dto.JwtResponse;
import com.example.jwtAuthApp.dto.LoginRequest;
import com.example.jwtAuthApp.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
