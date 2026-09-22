package com.angle.trading.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;

/**
 * User dashboard.
 *
 * Works whether security is on or off:
 *   - Security ON  → authentication is populated by Spring, we use its name.
 *   - Security OFF (nosec profile) → authentication is null, we fall back
 *     to "anonymous" so the page still renders.
 */
@Controller
@RequestMapping("/dashboard")
@Slf4j
public class DashboardController {

    @GetMapping
    public String home(Model model, Authentication authentication) {
        String user = (authentication != null) ? authentication.getName() : "anonymous";
        LocalDateTime sessionStart = LocalDateTime.now().withNano(0);
        log.info("User '{}' opened dashboard at {}", user, sessionStart);
        model.addAttribute("user", user);
        model.addAttribute("sessionStart", sessionStart);
        return "dashboard/welcome";
    }
}
