package com.example.auth.controller.admin;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The /admin dashboard landing page. Just renders {@code admin/dashboard.html}
 * — no data, no logic. The dashboard template itself has the navigation cards.
 */
@Controller
@Profile("jdbc")
@RequestMapping("/admin")
public class AdminHomeController {

    @GetMapping
    public String dashboard() {
        return "admin/dashboard";
    }
}
