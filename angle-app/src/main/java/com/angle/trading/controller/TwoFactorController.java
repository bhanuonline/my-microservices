package com.angle.trading.controller;

import com.angle.trading.security.TwoFactorAuthenticationFilter;
import com.angle.trading.user.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Second-factor verification after password login.
 *
 *   GET  /auth/2fa         → "Enter the 6-digit code" page
 *   POST /auth/2fa         → verify code; on success set session flag + go
 *                             to /dashboard; on failure stay on page
 *
 * Only reachable AFTER Spring Security authenticates (filter chain requires
 * authenticated principal). The TwoFactorAuthenticationFilter is what
 * routes users here; this controller is the destination + handler.
 */
@Slf4j
@Controller
@RequestMapping("/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final UserService userService;

    @GetMapping
    public String form(Authentication auth, HttpSession session, Model model,
                       @RequestParam(required = false) String error) {
        if (auth == null) return "redirect:/auth/login";
        // If already passed in this session, don't make them do it again.
        if (Boolean.TRUE.equals(session.getAttribute(TwoFactorAuthenticationFilter.SESSION_TWO_FA_PASSED))) {
            return "redirect:/dashboard";
        }
        model.addAttribute("username", auth.getName());
        if (error != null) model.addAttribute("error", "That code didn't match. Try the next one.");
        return "auth/two-factor";
    }

    @PostMapping
    public String verify(Authentication auth,
                          HttpSession session,
                          @RequestParam String code) {
        if (auth == null) return "redirect:/auth/login";
        if (userService.verifyTotpCode(auth.getName(), code)) {
            session.setAttribute(TwoFactorAuthenticationFilter.SESSION_TWO_FA_PASSED, true);
            log.info("2FA passed for {}", auth.getName());
            return "redirect:/dashboard";
        }
        log.info("2FA failed for {}", auth.getName());
        return "redirect:/auth/2fa?error";
    }
}
