package com.angle.trading.miniapp;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Simple session-based login demo.
 *
 * Flow:
 *   GET  /miniapp          → redirects to /login or /welcome depending on session
 *   GET  /miniapp/login    → shows login form (Thymeleaf template)
 *   POST /miniapp/login    → validates credentials, stores username in HttpSession
 *   GET  /miniapp/welcome  → protected page; redirects to /login if no session
 *   GET  /miniapp/logout   → invalidates the session and returns to /login
 *
 * Later, OAuth replaces the manual credential check with a redirect to Google/GitHub,
 * but the "put the user in the session" idea stays the same.
 */
@Controller
@RequestMapping("/miniapp")
public class MiniAppController {

    // Hardcoded users. In a real app these come from a database + password hashing.
    private static final Map<String, String> USERS = Map.of(
            "alex",  "demo123",
            "admin", "admin123"
    );

    @GetMapping({"", "/"})
    public String home(HttpSession session) {
        return session.getAttribute("user") != null
                ? "redirect:/miniapp/welcome"
                : "redirect:/miniapp/login";
    }

    @GetMapping("/login")
    public String loginPage() {
        // Returns the view name → Thymeleaf renders templates/miniapp/login.html
        return "miniapp/login";
    }

    @PostMapping("/login")
    public String doLogin(
            @RequestParam String username,
            @RequestParam String password,
            HttpSession session,
            Model model
    ) {
        String expected = USERS.get(username);
        if (expected != null && expected.equals(password)) {
            session.setAttribute("user", username);
            return "redirect:/miniapp/welcome";
        }

        // Re-render the login page with an error. Keep the typed username so the
        // user doesn't have to type it again.
        model.addAttribute("error", "Invalid username or password.");
        model.addAttribute("username", username);
        return "miniapp/login";
    }

    @GetMapping("/welcome")
    public String welcome(HttpSession session, Model model) {
        String user = (String) session.getAttribute("user");
        if (user == null) {
            return "redirect:/miniapp/login";
        }
        model.addAttribute("user", user);
        return "miniapp/welcome";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/miniapp/login";
    }
}
