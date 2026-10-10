package com.example.productservice.repository;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        // Replace with authentication principal in real app
        return Optional.of("bhanu");  // static username for now
    }
}