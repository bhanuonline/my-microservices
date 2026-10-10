package com.angle.trading.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Login + legacy auth URLs. Most endpoints just redirect to real pages — they
 * exist so links from old templates / bookmarks don't 500.
 *
 * The only real work is {@link #showLoginPage(String, String, String, Long, String, Model)}
 * which converts the query params set by CustomAuthenticationFailureHandler
 * ({@code ?error}, {@code ?locked&minutes=14}, {@code ?ipBlocked}) into
 * model attributes that templates/auth/login.html renders as flash banners.
 */
@Controller
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    @GetMapping("/callback")
    public String callback(@RequestParam(required = false) String code) {
        return "Authorization Code: " + code;
    }

    @PostMapping("/postback")
    public String postback(@RequestBody String body) {
        System.out.println(body);
        return "OK";
    }

    // ===========================
    // Login
    // ===========================
    @GetMapping("/login")
    public String showLoginPage(@RequestParam(required = false) String error,
                                @RequestParam(required = false) String locked,
                                @RequestParam(required = false) String ipBlocked,
                                @RequestParam(required = false) Long minutes,
                                @RequestParam(required = false) String username,
                                @RequestParam(required = false) String expired,
                                @RequestParam(required = false) String logout,
                                Model model) {
        log.info("Open login page..");
        if (error != null) {
            if (ipBlocked != null) {
                model.addAttribute("error",
                        "Too many failed attempts from your network. Try again in a few minutes.");
            } else if (locked != null) {
                model.addAttribute("locked", true);
                model.addAttribute("lockMinutes", minutes == null ? 15L : minutes);
            } else {
                model.addAttribute("error", "Invalid username or password.");
            }
        }
        if (expired != null) {
            model.addAttribute("info", "Your session expired. Please sign in again.");
        } else if (logout != null) {
            model.addAttribute("info", "You've been signed out.");
        }
        if (username != null) model.addAttribute("username", username);
        return "auth/login";
    }

    // ===========================
    // Register (legacy → signup)
    // ===========================
    @GetMapping("/register")
    public String showRegistrationPage() { return "redirect:/auth/signup"; }

    @PostMapping("/register")
    public String registerUser() { return "redirect:/auth/login"; }

    // ===========================
    // Email Verification
    // ===========================
    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token) { return "redirect:/auth/login"; }

    @PostMapping("/resend-verification")
    public String resendVerificationEmail() { return "redirect:/auth/login"; }

    // ===========================
    // Forgot Password
    // ===========================
    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() { return "redirect:/auth/login"; }

    @PostMapping("/forgot-password")
    public String sendResetLink() { return "redirect:/auth/login"; }

    // ===========================
    // Reset Password
    // ===========================
    @GetMapping("/reset-password")
    public String showResetPasswordPage(@RequestParam String token) { return "redirect:/auth/login"; }

    @PostMapping("/reset-password")
    public String resetPassword() { return "redirect:/auth/login"; }

    // ===========================
    // Change Password
    // ===========================
    @GetMapping("/change-password")
    public String showChangePasswordPage() { return "redirect:/account"; }

    @PostMapping("/change-password")
    public String changePassword() { return "redirect:/dashboard"; }

    // ===========================
    // Two-factor — real controller is TwoFactorController (/auth/2fa).
    // Keeping /auth/resend-otp here as a legacy stub.
    // ===========================
    @PostMapping("/resend-otp")
    public String resendOtp() { return "redirect:/auth/2fa"; }

    // ===========================
    // Account Lock
    // ===========================
    @GetMapping("/locked")
    public String accountLocked() { return "redirect:/auth/login"; }

    // ===========================
    // Access Denied
    // ===========================
    @GetMapping("/access-denied")
    public String accessDenied() { return "auth/access-denied"; }

    // ===========================
    // Session Expired
    // ===========================
    @GetMapping("/session-expired")
    public String sessionExpired() { return "redirect:/auth/login"; }

    // ===========================
    // Invalid Session
    // ===========================
    @GetMapping("/invalid-session")
    public String invalidSession() { return "redirect:/auth/login"; }

    // ===========================
    // OAuth2
    // ===========================
    @GetMapping("/oauth2/success")
    public String oauthSuccess() { return "redirect:/dashboard"; }

    @GetMapping("/oauth2/failure")
    public String oauthFailure() { return "redirect:/auth/login"; }

    // ===========================
    // Profile
    // ===========================
    @GetMapping("/profile")
    public String profile() { return "redirect:/account"; }

    @PostMapping("/profile")
    public String updateProfile() { return "redirect:/auth/profile"; }

    // ===========================
    // Error Page
    // ===========================
    @GetMapping("/error")
    public String error() { return "redirect:/dashboard"; }
}
