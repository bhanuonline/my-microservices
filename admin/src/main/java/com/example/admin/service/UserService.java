package com.example.admin.service;

import com.example.admin.model.RegistrationDto;
import com.example.admin.model.UserDto;
import com.example.admin.user.MyUser;
import com.example.admin.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Register new user
    @Transactional
    public UserDto register(RegistrationDto request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already taken");
        }

        MyUser user = new MyUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole("USER");

        MyUser saved = userRepository.saveAndFlush(user);
        return toDto(saved);
    }

    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    // Update existing user
    public UserDto updateUser(Long id, RegistrationDto request) {
        MyUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            user.setEmail(request.getEmail());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        MyUser updated = userRepository.save(user);
        return toDto(updated);
    }

    private UserDto toDto(MyUser user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
    }

    public UserDto getByUsername(String username) {
        if(userRepository.findByUsername(username).isPresent()){
            return toDto(userRepository.findByUsername(username).get());
        }
        return null;
    }
}