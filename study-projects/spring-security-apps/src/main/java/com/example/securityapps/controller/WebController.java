package com.example.securityapps.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;

@Controller
public class WebController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("message", "Welcome to the secure page!");
        return "home";
    }

    @GetMapping("/public/info")
    public String publicPage(Model model) {
        model.addAttribute("message", "This page is public (no login required).");
        return "home";
    }
}