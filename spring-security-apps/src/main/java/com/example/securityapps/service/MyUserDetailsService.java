package com.example.securityapps.service;

import com.example.securityapps.entity.AppUser;
import com.example.securityapps.repositery.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MyUserDetailsService implements UserDetailsService {
    @Autowired
    private UserRepository repo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser u = repo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("user not found :"+username));
        return User.withUsername(u.getUsername())
                   .password(u.getPassword())
                   .roles(u.getRole())
                   .disabled(!u.isEnabled())
                   .build();
    }
}