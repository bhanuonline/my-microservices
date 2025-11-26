package com.example.admin.controller;

import com.example.admin.model.RegistrationDto;
import com.example.admin.model.UserDto;
import com.example.admin.service.UserService;
import com.example.admin.user.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/users")
@Slf4j
public class UserController {

    private final UserRepository repo;
    private  final UserService userService;

//    @PostMapping("/register")
//    public String register(@RequestBody RegistrationDto dto) {
//        userService.register(dto);
//        return "User registered successfully";
//    }

    // Show registration form
    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new RegistrationDto());
        return "/users/register"; // templates/users/register.html
    }

    // Handle form submission
    @PostMapping("/register")
    public String registerUser(
            @Validated @ModelAttribute("user") RegistrationDto user, // form object with @NotBlank, @Email, etc.
            BindingResult bindingResult,                             // holds validation errors
            RedirectAttributes redirectAttributes,                   // lets you pass messages after redirect
            Model model,
            HttpServletRequest request) throws ServletException {

        // 1️⃣ If DTO validation fails, stay on the registration page
        if (bindingResult.hasErrors()) {
            return "users/register";
        }

        // 2️⃣ Custom checks not covered by annotations
        if (userService.existsByUsername(user.getUsername())) {
            model.addAttribute("registrationError", "Username already taken!");
            return "users/register";
        }

        if (!user.getPassword().equals(user.getConfirmPassword())) {
            model.addAttribute("registrationError", "Passwords do not match!");
            return "users/register";
        }

        // 3️⃣ Register the user
        //    The service should: encode the password, assign a default role, and save the new user.
        try {
            userService.register(user);
        } catch (Exception ex) {
            model.addAttribute("registrationError", "Could not create account. Please try again later.");
            return "users/register";
        }

        // 4️⃣ Auto‑login (optional)
        //    Servlet login uses the raw password; make sure userService.register() only saved the encoded version.
        try {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword());
            request.login(user.getUsername(), user.getPassword());

//When a user registers and you want to automatically log them in without calling request.login(), you can do this:
           // SecurityContextHolder.getContext().setAuthentication(token);

        } catch (ServletException e) {
            // If auto‑login fails, log it but don’t expose details.
            redirectAttributes.addFlashAttribute(
                    "registrationSuccess",
                    "Account created successfully. Please log in.");
            SecurityContext context = SecurityContextHolder.getContext();
            Authentication authentication = context.getAuthentication();
            log.info("Logged-in user: " + authentication.getName());
            return "redirect:/login";
        }

        // 5️⃣ Success message to show on redirected page
        redirectAttributes.addFlashAttribute(
                "registrationSuccess",
                "Registration successful! You are now logged in.");

        // 6️⃣ Redirect to home to avoid resubmission on refresh
        return "redirect:/home";
    }



    // 🔹 NEW: Edit / update user details
    @PutMapping("/{userId}")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable Long userId,
            @RequestBody RegistrationDto request) {
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    @GetMapping("/profile")
    public String dashboard() {
        return "dashboard";
    }
}