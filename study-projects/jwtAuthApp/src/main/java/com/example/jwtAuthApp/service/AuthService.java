package com.example.jwtAuthApp.service;

import com.example.jwtAuthApp.dto.JwtResponse;
import com.example.jwtAuthApp.dto.LoginRequest;
import com.example.jwtAuthApp.exception.BadRequestException;
import com.example.jwtAuthApp.model.User;
import com.example.jwtAuthApp.repository.UserRepository;
import com.example.jwtAuthApp.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired private UserRepository repo;
    @Autowired private JwtUtil jwtUtil;

    public JwtResponse login(LoginRequest req) {
        User user = repo.findByUsername(req.getUsername())
                .orElseThrow(() -> new BadRequestException("Invalid username or password"));

        if (!new BCryptPasswordEncoder().matches(req.getPassword(), user.getPassword())) {
            throw new BadRequestException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return new JwtResponse(token, user.getUsername(), user.getRole().name());
    }

    public Object loadUserByUsername(String username) {
        return null;
    }
}
