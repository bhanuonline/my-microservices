package com.example.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The user-form's HTML shape, in Java. Backs {@code /admin/users/new} and
 * {@code /admin/users/{id}/edit}.
 *
 * <p>Same rules as {@link ClientForm}: a mutable POJO for Bean Validation +
 * Thymeleaf binding, translated into the {@code AppUser} entity by
 * {@code UserAdminService}.
 *
 * <p>Convention: blank {@code password} on update = "don't touch the existing
 * hash". Non-blank = "BCrypt this new plaintext and store it".
 */
@Data
public class UserForm {

    private Long id;

    @NotBlank
    private String username;

    /** Plaintext on the form; BCrypt-hashed before storage. Blank on edit = unchanged. */
    private String password;

    @Email
    private String email;

    private boolean enabled = true;

    /** Free-form role names (ADMIN, USER, …). */
    private Set<String> roles = new LinkedHashSet<>();
}
