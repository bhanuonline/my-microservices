package com.example.securityapps.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AdminController {

    // Serve an HTML view for the admin dashboard (requires role ADMIN)
    @GetMapping("/admin/home")
    public String adminHome(Model model) {
        model.addAttribute("message", "Welcome to the Admin Dashboard!");
        return "admin-home"; // corresponds to src/main/resources/templates/admin-home.html (Thymeleaf)
    }

    // If you want a JSON response instead of a view
    @GetMapping("/admin/info")
    @ResponseBody
    public String adminInfo() {
        return "Admin-only secure information!";
    }

    // Optional: custom login page for /admin/login
    @GetMapping("/admin/login")
    public String adminLogin() {
        return "admin-login"; // corresponds to admin-login.html template
    }
}