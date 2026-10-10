package com.angle.trading.controller;

import com.angle.trading.config.PasswordPolicyProperties;
import com.angle.trading.config.SignupProperties;
import com.angle.trading.user.PasswordPolicyService;
import com.angle.trading.user.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Public signup flow — /auth/signup.
 *
 *   GET  /auth/signup              → signup form (404 if signup disabled)
 *   POST /auth/signup              → create user + redirect to login
 *
 * Protections:
 *   • Config switch (signup.enabled)                       — hard off switch
 *   • Honeypot field "website" — bots fill it, humans don't → rejected silently
 *   • IP rate limit (default 5/hour)                       — Caffeine cache
 *   • Server-side password strength (default min 8 chars)
 *   • Username / email uniqueness in DB (via UserService)
 */
@Slf4j
@Controller
@RequestMapping("/auth/signup")
@RequiredArgsConstructor
public class SignupController {

    private final UserService userService;
    private final SignupProperties props;
    private final PasswordPolicyService passwordPolicy;
    private final PasswordPolicyProperties policyProps;

    /** Per-IP signup counter with 1-hour TTL (fresh after each hour-window). */
    private Cache<String, AtomicInteger> ipCounters;

    @PostConstruct
    void init() {
        this.ipCounters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10_000)
                .build();
        log.info("SignupController initialised — enabled={} defaultRole={} rateLimit={}/hr minPwd={}",
                props.isEnabled(), props.getDefaultRole(),
                props.getIpRateLimitPerHour(), props.getMinPasswordLength());
    }

    @GetMapping
    public String form(Model model,
                       @RequestParam(required = false) String error,
                       @RequestParam(required = false) String username,
                       @RequestParam(required = false) String email) {
        if (!props.isEnabled()) {
            // Hide the page entirely when disabled — don't even let bots probe it.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Signup is disabled");
        }
        // Prefer the strict policy's min-length if the policy is on; otherwise
        // fall back to SignupProperties' legacy value for backward compat.
        int effectiveMin = policyProps.isEnabled()
                ? policyProps.getMinLength()
                : props.getMinPasswordLength();
        model.addAttribute("minPwdLen", effectiveMin);
        model.addAttribute("requireLetterAndDigit", policyProps.isEnabled() && policyProps.isRequireLetterAndDigit());
        model.addAttribute("error",    error);
        model.addAttribute("username", username == null ? "" : username);
        model.addAttribute("email",    email    == null ? "" : email);
        return "auth/signup";
    }

    @PostMapping
    public String submit(@RequestParam String username,
                         @RequestParam String password,
                         @RequestParam(required = false) String email,
                         @RequestParam(name = "website", required = false) String honeypot,
                         HttpServletRequest req,
                         RedirectAttributes ra) {

        if (!props.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Signup is disabled");
        }

        // Honeypot: real browsers hide the field with CSS; bots fill it.
        // Silently redirect to a fake success so bots think they succeeded.
        if (honeypot != null && !honeypot.isBlank()) {
            log.warn("Signup: honeypot tripped from IP {} with username={}", clientIp(req), username);
            return "redirect:/auth/login?signup=ok";
        }

        // IP rate limit.
        String ip = clientIp(req);
        if (props.getIpRateLimitPerHour() > 0) {
            int count = ipCounters.get(ip, k -> new AtomicInteger()).incrementAndGet();
            if (count > props.getIpRateLimitPerHour()) {
                log.warn("Signup: IP {} exceeded rate limit ({} > {})",
                        ip, count, props.getIpRateLimitPerHour());
                ra.addAttribute("error", "Too many signups from your network. Try again later.");
                return "redirect:/auth/signup";
            }
        }

        try {
            username = username == null ? "" : username.trim();
            if (username.length() < 3 || username.length() > 64) {
                throw new IllegalArgumentException("Username must be 3-64 characters");
            }
            if (!username.matches("^[a-zA-Z0-9._-]+$")) {
                throw new IllegalArgumentException(
                        "Username can only contain letters, digits, dot, underscore, dash");
            }
            // Full policy check (length + letter+digit + not-equals-user + common-list).
            // SignupProperties.minPasswordLength is now overridden by the stricter
            // password.policy.* ruleset but we keep it for the UI hint.
            passwordPolicy.validateOrThrow(username, email, password);
            if (userService.usernameExists(username)) {
                throw new IllegalArgumentException("Username already taken");
            }
            if (email != null && !email.isBlank() && userService.emailExists(email)) {
                throw new IllegalArgumentException("Email already in use");
            }

            userService.create(username, password, email, props.getDefaultRole(), true);
            log.info("Signup: new user {} created from IP {}", username, ip);
            return "redirect:/auth/login?signup=ok";

        } catch (IllegalArgumentException e) {
            ra.addAttribute("error", e.getMessage());
            ra.addAttribute("username", username);
            if (email != null) ra.addAttribute("email", email);
            return "redirect:/auth/signup";
        }
    }

    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
