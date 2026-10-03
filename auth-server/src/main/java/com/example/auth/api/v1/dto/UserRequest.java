package com.example.auth.api.v1.dto;

import com.example.auth.dto.UserForm;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

/**
 * JSON body for {@code POST/PUT /api/v1/admin/users}.
 * <p>Companion of {@link com.example.auth.dto.UserForm} — records-shaped for REST.
 * Blank {@code password} on update means "keep existing hash".
 */
public record UserRequest(
        @NotBlank String username,
        String password,        // plaintext on create; null/blank on update = keep
        @Email String email,
        boolean enabled,
        Set<String> roles
) {
    public UserForm toForm() {
        UserForm f = new UserForm();
        f.setUsername(username);
        f.setPassword(password);
        f.setEmail(email);
        f.setEnabled(enabled);
        f.setRoles(roles == null ? Set.of() : roles);
        return f;
    }
}
