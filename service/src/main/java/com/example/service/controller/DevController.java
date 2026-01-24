package com.example.service.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DevController {

    @PreAuthorize("hasRole('DEV')")
    @GetMapping("/api/dev/secret")
    public String devSecret() {
        return "Only dev can see this data";
    }
}