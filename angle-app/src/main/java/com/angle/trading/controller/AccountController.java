package com.angle.trading.controller;

import com.angle.trading.persistence.AppUserEntity;
import com.angle.trading.user.PasswordPolicyService;
import com.angle.trading.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Self-service account page for the logged-in user.
 *
 *   GET  /account                 → read-only profile + change forms
 *   POST /account/password        → change my password (needs current)
 *   POST /account/email           → change my email
 *
 * ANY authenticated user can access these — admins and users alike. The
 * controller uses the Spring Security principal, so a user can only ever
 * see/edit THEIR OWN record.
 */
@Slf4j
@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;
    private final PasswordPolicyService passwordPolicy;

    @GetMapping
    public String page(Model model,
                       Authentication auth,
                       @RequestParam(required = false) String flash,
                       @RequestParam(required = false) String error) {
        if (auth == null) return "redirect:/auth/login";
        AppUserEntity me = userService.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Logged-in user not in DB: " + auth.getName()));
        model.addAttribute("me", me);
        if (flash != null) model.addAttribute("flash", flash);
        if (error != null) model.addAttribute("error", error);
        return "account/index";
    }

    @PostMapping("/password")
    public String changePassword(Authentication auth,
                                  @RequestParam String currentPassword,
                                  @RequestParam String newPassword,
                                  @RequestParam String confirmPassword,
                                  RedirectAttributes ra) {
        if (auth == null) return "redirect:/auth/login";
        try {
            if (!newPassword.equals(confirmPassword)) {
                throw new IllegalArgumentException("New password and confirmation do not match");
            }
            AppUserEntity me = userService.findByUsername(auth.getName())
                    .orElseThrow(() -> new IllegalStateException("Logged-in user not in DB"));
            passwordPolicy.validateOrThrow(me.getUsername(), me.getEmail(), newPassword);
            userService.changePassword(auth.getName(), currentPassword, newPassword);
            ra.addAttribute("flash", "Password changed. Use the new password next time.");
        } catch (IllegalArgumentException e) {
            ra.addAttribute("error", e.getMessage());
        }
        return "redirect:/account";
    }

    @PostMapping("/email")
    public String changeEmail(Authentication auth,
                               @RequestParam(required = false) String email,
                               RedirectAttributes ra) {
        if (auth == null) return "redirect:/auth/login";
        try {
            AppUserEntity me = userService.findByUsername(auth.getName())
                    .orElseThrow(() -> new IllegalStateException("Logged-in user not in DB"));
            String normalised = (email == null || email.isBlank()) ? null : email.trim().toLowerCase();
            // Allow clearing. If setting, basic shape check only — real validation
            // comes in Phase F (email verification).
            if (normalised != null && !normalised.contains("@")) {
                throw new IllegalArgumentException("Invalid email format");
            }
            userService.update(me.getId(), normalised, null, null);
            ra.addAttribute("flash", "Email updated");
        } catch (IllegalArgumentException e) {
            ra.addAttribute("error", e.getMessage());
        }
        return "redirect:/account";
    }

    // ---------- 2FA self-service ----------

    /** Start enrolment: show QR + manual key + "enter first code" input. */
    @GetMapping("/2fa/setup")
    public String setupTotp(Authentication auth, Model model, RedirectAttributes ra) {
        if (auth == null) return "redirect:/auth/login";
        try {
            UserService.TotpEnrolment e = userService.startTotpEnrolment(auth.getName());
            model.addAttribute("username",   auth.getName());
            model.addAttribute("qrDataUri",  e.qrDataUri());
            model.addAttribute("secret",     e.secret());
            return "account/2fa-setup";
        } catch (IllegalArgumentException ex) {
            ra.addAttribute("error", ex.getMessage());
            return "redirect:/account";
        }
    }

    /** Confirm enrolment by verifying first code; on success flip totpEnabled on. */
    @PostMapping("/2fa/enable")
    public String enableTotp(Authentication auth,
                              @RequestParam String code,
                              RedirectAttributes ra) {
        if (auth == null) return "redirect:/auth/login";
        try {
            userService.confirmTotpEnrolment(auth.getName(), code);
            ra.addAttribute("flash", "Two-factor authentication enabled. Keep your app safe!");
            return "redirect:/account";
        } catch (IllegalArgumentException ex) {
            ra.addAttribute("error", ex.getMessage());
            return "redirect:/account/2fa/setup";
        }
    }

    /** Disable 2FA — user must prove possession via code OR password. */
    @PostMapping("/2fa/disable")
    public String disableTotp(Authentication auth,
                               @RequestParam String confirm,
                               RedirectAttributes ra) {
        if (auth == null) return "redirect:/auth/login";
        try {
            userService.disableTotp(auth.getName(), confirm);
            ra.addAttribute("flash", "Two-factor authentication disabled");
        } catch (IllegalArgumentException ex) {
            ra.addAttribute("error", ex.getMessage());
        }
        return "redirect:/account";
    }
}
