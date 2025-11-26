package com.example.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }


    @GetMapping("/admin/link1")
    public String admin1() { return "admin1"; }

    @GetMapping("/admin/link2")
    public String admin2() { return "admin2"; }

    @GetMapping("/admin/link3")
    public String admin3() { return "admin3"; }

    @GetMapping("/admin/link4")
    public String admin4() { return "admin4"; }

    @GetMapping("/admin/link5")
    public String admin5() { return "admin5"; }

    @GetMapping("/access-denied")
    public String accessDenied(HttpServletRequest request, Model model) {
        String message = (String) request.getAttribute("errorMessage");
        model.addAttribute("errorMessage", message);
        return "access-denied";
    }
}