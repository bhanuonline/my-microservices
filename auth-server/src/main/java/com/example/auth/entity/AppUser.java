package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.Data;


@Data
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password;
    private String role;
    private String email;
}