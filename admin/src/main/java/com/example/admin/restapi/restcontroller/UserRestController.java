package com.example.admin.restapi.restcontroller;

import com.example.admin.model.RegistrationDto;
import com.example.admin.model.UserDto;
import com.example.admin.restapi.dto.ApiResponse;
import com.example.admin.service.UserService;
import jakarta.validation.Valid; // for request validation
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

// REST controller returns JSON instead of HTML views
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserRestController {

    private final UserService userService;

    // 🔹 1️⃣ Register a new user (JSON request)
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> registerUser(@Valid @RequestBody RegistrationDto user) {
        log.info("Received registration request for username: {}", user.getUsername());

        // 🔸 Validate username existence
        if (userService.existsByUsername(user.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT) // 409
                    .body(ApiResponse.of(false, "Username already taken."));
        }

        // 🔸 Validate password confirmation
        if (!user.getPassword().equals(user.getConfirmPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST) // 400
                    .body(ApiResponse.of(false, "Passwords do not match."));
        }

        try {
            // 🔸 Save user to database (service should handle encoding/role setup)
            userService.register(user);

            log.info("User '{}' registered successfully.", user.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED) // 201
                    .body(ApiResponse.of(true, "User registered successfully."));

        } catch (Exception ex) {
            log.error("Registration failed for '{}': {}", user.getUsername(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // 500
                    .body(ApiResponse.of(false, "Registration failed due to a server error."));
        }
    }

    // 🔹 2️⃣ Update existing user details by ID (JSON request)
    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUser(@PathVariable Long userId,
                                        @Valid @RequestBody RegistrationDto request) {
        try {
            UserDto updatedUser = userService.updateUser(userId, request);
            return ResponseEntity.ok(updatedUser); // returns updated user info as JSON

        } catch (IllegalArgumentException ex) {
            log.warn("User update failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.of(false, ex.getMessage()));

        } catch (Exception ex) {
            log.error("Error updating user with id {}: {}", userId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.of(false, "Could not update user. Internal error."));
        }
    }

    // 🔹 3️⃣ Example of fetching user profile by username
    @GetMapping("/{username}")
    public ResponseEntity<?> getUser(@PathVariable String username) {
        try {
            UserDto user = userService.getByUsername(username);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.of(false, "User not found."));
            }
            return ResponseEntity.ok(user);

        } catch (Exception ex) {
            log.error("Error fetching user {}: {}", username, ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.of(false, "Internal server error while fetching user."));
        }
    }

    // 🔹 4️⃣ Global Validation Error Handling (for @Valid DTO errors)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        // Extract first validation error to simplify response
        String errorMsg = ex.getBindingResult().getFieldErrors()
                .stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("Invalid input data");

        log.warn("Validation failed: {}", errorMsg);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.of(false, errorMsg));
    }

    // 🔹 5️⃣ Fallback for uncaught exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGeneralErrors(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.of(false, "Unexpected error occurred: " + ex.getMessage()));
    }
}